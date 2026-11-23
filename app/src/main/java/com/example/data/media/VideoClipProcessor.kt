package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.ai.KeyframeSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "VideoClipProcessor"

data class VideoInspectionResult(
    val displayName: String,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val keyframes: List<KeyframeSample>
)

object VideoClipProcessor {

    suspend fun inspectAndSampleKeyframes(
        context: Context,
        videoUri: Uri,
        maxFrames: Int = 10
    ): Result<VideoInspectionResult> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val fileName = resolveDisplayName(context, videoUri)
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1000L)
                ?: 10_000L
            val durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(1)

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1280
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 720

            val frameCount = maxFrames.coerceIn(3, 12)
            val samples = mutableListOf<KeyframeSample>()

            for (i in 0 until frameCount) {
                val fraction = if (frameCount == 1) 0.5 else 0.05 + (0.90 * i / (frameCount - 1))
                val timeUs = (durationMs * 1000.0 * fraction).toLong()
                val rawBitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (rawBitmap != null) {
                    val scaled = scaleBitmapForAi(rawBitmap, maxDimension = 512)
                    if (scaled !== rawBitmap) {
                        rawBitmap.recycle()
                    }
                    samples += KeyframeSample(
                        timestampSec = (timeUs / 1_000_000L).toInt(),
                        bitmap = scaled
                    )
                }
            }

            if (samples.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Could not decode video frames from the selected file.")
                )
            }

            Result.success(
                VideoInspectionResult(
                    displayName = fileName,
                    durationSeconds = durationSeconds,
                    width = width,
                    height = height,
                    keyframes = samples
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect video keyframes", e)
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun trimAndExportMp4(
        context: Context,
        sourceUri: Uri,
        clipId: String,
        startSec: Int,
        endSec: Int
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            val clipsDir = File(context.cacheDir, "clips").apply { mkdirs() }
            val safeId = clipId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "highlight" }
            val outFile = File(clipsDir, "clip_${safeId}_${startSec}_${endSec}.mp4")
            if (outFile.exists()) outFile.delete()

            extractor.setDataSource(context, sourceUri, null)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) {
                return@withContext Result.failure(IllegalStateException("No media tracks found in video."))
            }

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 256 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstTrack = muxer.addTrack(format)
                    trackIndexMap[i] = dstTrack
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        maxBufferSize = max(maxBufferSize, size)
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unsupported video/audio format for MP4 export."))
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, sourceUri)
                val rotation = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rotation != 0) {
                    muxer.setOrientationHint(rotation)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            muxer.start()

            val startUs = startSec.coerceAtLeast(0).toLong() * 1_000_000L
            val endUs = endSec.coerceAtLeast(startSec + 1).toLong() * 1_000_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var basePtsUs: Long? = null

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs > endUs) {
                    break
                }
                val srcTrackIndex = extractor.sampleTrackIndex
                val dstTrackIndex = trackIndexMap[srcTrackIndex]
                if (dstTrackIndex != null) {
                    if (basePtsUs == null) {
                        basePtsUs = sampleTimeUs
                    }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            if (!outFile.exists() || outFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Exported MP4 clip file was empty."))
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, outFile)
            Result.success(contentUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MP4 export failed", e)
            Result.failure(e)
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "twitch_stream.mp4"
    }

    private fun scaleBitmapForAi(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val scale = maxDimension.toFloat() / max(w, h).toFloat()
        val newW = (w * scale).roundToInt().coerceAtLeast(1)
        val newH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}
