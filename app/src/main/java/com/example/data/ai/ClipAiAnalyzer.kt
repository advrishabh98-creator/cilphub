package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ClipCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "ClipAiAnalyzer"
private const val MODEL_NAME = "gemini-3.8-flash"

data class KeyframeSample(
    val timestampSec: Int,
    val bitmap: Bitmap
)

data class GeneratedClipCandidate(
    val title: String,
    val hookCaption: String,
    val engagementReason: String,
    val category: ClipCategory,
    val startSec: Int,
    val endSec: Int,
    val hypeScore: Int
)

data class AiStreamAnalysisResult(
    val streamSummary: String,
    val clips: List<GeneratedClipCandidate>
)

data class TwitchPageMetadata(
    val title: String,
    val channelName: String,
    val description: String
)

class ClipAiAnalyzer {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val highlightSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "streamSummary" to Schema.string(
                    description = "2-sentence summary of the most engaging stream arcs and overall energy."
                ),
                "clips" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(
                                description = "Punchy, clickable Twitch clip title under 80 chars."
                            ),
                            "hookCaption" to Schema.string(
                                description = "Short vertical-video hook caption under 70 chars for TikTok/Shorts."
                            ),
                            "engagementReason" to Schema.string(
                                description = "Concise explanation of why viewers will rewatch, share, or spam chat here."
                            ),
                            "category" to Schema.enumeration(
                                values = ClipCategory.entries.map { it.name },
                                description = "Best matching highlight category."
                            ),
                            "startSec" to Schema.integer(
                                description = "Clip start timestamp in seconds."
                            ),
                            "endSec" to Schema.integer(
                                description = "Clip end timestamp in seconds (must be greater than startSec, ideally 12-55s long)."
                            ),
                            "hypeScore" to Schema.integer(
                                description = "Predicted viral engagement score from 1 to 100."
                            )
                        )
                    ),
                    description = "Between 2 and 6 of the highest-engagement clip segments sorted by hypeScore descending."
                )
            )
        )
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = highlightSchema
            }
        )
    }

    suspend fun analyzeStreamVideo(
        title: String,
        channelName: String,
        durationSeconds: Int,
        keyframes: List<KeyframeSample>,
        extraContext: String
    ): AiStreamAnalysisResult {
        val safeDuration = durationSeconds.coerceAtLeast(10)
        val promptIntro = buildString {
            appendLine("You are an elite Twitch clip editor and viral Shorts/Reels producer.")
            appendLine("Analyze these evenly-sampled keyframes from a Twitch stream VOD to identify the 3 to 5 most engaging, clip-worthy moments.")
            appendLine("Stream Title: $title")
            if (channelName.isNotBlank()) appendLine("Twitch Channel: $channelName")
            appendLine("Total Video Duration: ${safeDuration}s")
            if (extraContext.isNotBlank()) appendLine("Creator Context / Focus: $extraContext")
            appendLine("Rules:")
            appendLine("- Every clip MUST satisfy 0 <= startSec < endSec <= $safeDuration.")
            appendLine("- Prefer tight, punchy 12s to 50s clip windows around visual action peaks, scoreboard swings, clutch moments, boss fights, chat overlay spikes, or strong streamer reactions.")
            appendLine("- Return realistic, varied hypeScores (60-99) and crisp vertical-video hookCaptions.")
        }

        val inputContent = content {
            text(promptIntro)
            keyframes.forEach { sample ->
                text("Keyframe at t=${sample.timestampSec}s:")
                image(sample.bitmap)
            }
        }

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(inputContent)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("AI model returned an empty response.")
            parseAnalysisJson(jsonText, maxDurationSec = safeDuration)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video analysis timed out", e)
            throw IllegalStateException("AI stream scan timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to analyze stream video.", e)
        }
    }

    suspend fun analyzeChatOrTranscript(
        title: String,
        channelName: String,
        twitchUrl: String,
        chatOrTranscript: String,
        estimatedDurationSec: Int
    ): AiStreamAnalysisResult {
        val safeDuration = estimatedDurationSec.coerceAtLeast(60)
        val trimmedInput = chatOrTranscript.take(14_000)
        val prompt = buildString {
            appendLine("You are an elite Twitch highlight detector specializing in chat velocity, emote bursts, and stream transcript momentum.")
            appendLine("Analyze the following Twitch stream chat replay / transcript to find the 3 to 6 most engaging, viral moments.")
            appendLine("Stream Title: $title")
            if (channelName.isNotBlank()) appendLine("Twitch Channel: $channelName")
            if (twitchUrl.isNotBlank()) appendLine("Twitch VOD URL: $twitchUrl")
            appendLine("Estimated Stream Duration: ${safeDuration}s")
            appendLine("Look for: dense emote bursts (KEKW, OMEGALUL, POGGERS, PogChamp, LUL, W, CLUTCH), rapid message spikes, screaming/laughter transcripts, intense callouts, or comedic timing.")
            appendLine("Constraints:")
            appendLine("- Every clip MUST satisfy 0 <= startSec < endSec <= $safeDuration.")
            appendLine("- Use timestamps found in the log if present (converting HH:MM:SS or MM:SS into seconds), or map evenly across the stream duration.")
            appendLine("- Keep clip durations between 15s and 55s.")
            appendLine("")
            appendLine("--- STREAM LOG / TRANSCRIPT ---")
            appendLine(trimmedInput)
        }

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("AI model returned an empty response.")
            parseAnalysisJson(jsonText, maxDurationSec = safeDuration)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat/transcript analysis timed out", e)
            throw IllegalStateException("AI analysis timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat/transcript analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to analyze chat/transcript.", e)
        }
    }

    suspend fun fetchTwitchMetadata(rawUrl: String): Result<TwitchPageMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val normalized = rawUrl.trim().let {
                    if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
                }
                val request = Request.Builder()
                    .url(normalized)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipPulse/1.0")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Twitch returned HTTP ${response.code}")
                        )
                    }
                    val html = response.body?.string().orEmpty()
                    val ogTitle = extractMetaContent(html, "og:title")
                        ?: extractHtmlTitle(html)
                        ?: ""
                    val ogDesc = extractMetaContent(html, "og:description") ?: ""
                    val channelGuess = extractTwitchChannelFromUrl(normalized)
                    if (ogTitle.isBlank() && channelGuess.isBlank()) {
                        Result.failure(IllegalStateException("No public metadata found on page."))
                    } else {
                        Result.success(
                            TwitchPageMetadata(
                                title = ogTitle.removeSuffix(" - Twitch").trim().take(120),
                                channelName = channelGuess.take(60),
                                description = ogDesc.take(300)
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Twitch metadata: ${e.message}")
                Result.failure(e)
            }
        }

    private fun parseAnalysisJson(jsonString: String, maxDurationSec: Int): AiStreamAnalysisResult {
        val root = JSONObject(jsonString)
        val summary = root.optString("streamSummary", "AI highlight scan completed.")
            .trim()
            .take(980)
            .ifBlank { "AI highlight scan completed." }

        val clipsArray = root.optJSONArray("clips")
            ?: throw IllegalStateException("AI response did not contain any clips.")

        val candidates = mutableListOf<GeneratedClipCandidate>()
        for (i in 0 until clipsArray.length()) {
            val item = clipsArray.optJSONObject(i) ?: continue
            val rawStart = item.optInt("startSec", 0).coerceIn(0, (maxDurationSec - 2).coerceAtLeast(0))
            val rawEnd = item.optInt("endSec", rawStart + 20)
            val safeEnd = rawEnd.coerceIn(rawStart + 1, maxDurationSec.coerceAtLeast(rawStart + 1))

            val rawCategory = item.optString("category", ClipCategory.HIGHLIGHT.name)
            val category = enumValues<ClipCategory>().firstOrNull { it.name == rawCategory }
                ?: ClipCategory.HIGHLIGHT

            val title = item.optString("title", "Highlight #${i + 1}")
                .trim()
                .take(120)
                .ifBlank { "Highlight #${i + 1}" }
            val hook = item.optString("hookCaption", "Watch this moment!")
                .trim()
                .take(180)
            val reason = item.optString("engagementReason", "High intensity stream moment.")
                .trim()
                .take(500)
            val hype = item.optInt("hypeScore", 80).coerceIn(1, 100)

            candidates += GeneratedClipCandidate(
                title = title,
                hookCaption = hook,
                engagementReason = reason,
                category = category,
                startSec = rawStart,
                endSec = safeEnd,
                hypeScore = hype
            )
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException("AI scan finished but found 0 clip candidates.")
        }

        return AiStreamAnalysisResult(
            streamSummary = summary,
            clips = candidates.sortedByDescending { it.hypeScore }
        )
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val regex = Regex(
            """<meta[^>]+property=["']$property["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractHtmlTitle(html: String): String? {
        val regex = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractTwitchChannelFromUrl(url: String): String {
        val match = Regex("""twitch\.tv/(?!videos/)([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE).find(url)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }
}
