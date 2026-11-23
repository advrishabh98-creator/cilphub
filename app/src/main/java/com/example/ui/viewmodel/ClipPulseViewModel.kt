package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ClipAiAnalyzer
import com.example.data.ai.TwitchPageMetadata
import com.example.data.media.VideoClipProcessor
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.data.model.StreamAnalysis
import com.example.data.model.StreamSourceType
import com.example.data.repository.ClipPulseRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ClipPulseVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ScanProgressState {
    data object Idle : ScanProgressState
    data class Working(val stageLabel: String, val detail: String) : ScanProgressState
    data class Completed(val streamId: String, val clipCount: Int) : ScanProgressState
    data class Error(val message: String) : ScanProgressState
}

sealed interface ExportProgressState {
    data object Idle : ExportProgressState
    data object Exporting : ExportProgressState
    data class ReadyToShare(val contentUri: Uri, val clipTitle: String) : ExportProgressState
    data class Error(val message: String) : ExportProgressState
}

enum class ClipSortOrder(val label: String) {
    HYPE_DESC("Top Hype"),
    RECENT_DESC("Newest")
}

data class ClipFilterState(
    val query: String = "",
    val selectedCategory: ClipCategory? = null,
    val starredOnly: Boolean = false,
    val streamIdFilter: String? = null,
    val sortOrder: ClipSortOrder = ClipSortOrder.HYPE_DESC
)

class ClipPulseViewModel(
    private val repository: ClipPulseRepository,
    private val aiAnalyzer: ClipAiAnalyzer,
    private val currentUserId: String
) : ViewModel() {

    val streamsState: StateFlow<UiState<List<StreamAnalysis>>> = repository.observeStreams(currentUserId)
        .map<List<StreamAnalysis>, UiState<List<StreamAnalysis>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing streams", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe streams"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val rawClipsState: StateFlow<UiState<List<HighlightClip>>> = repository.observeClips(currentUserId)
        .map<List<HighlightClip>, UiState<List<HighlightClip>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing clips", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe clips"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _filterState = MutableStateFlow(ClipFilterState())
    val filterState: StateFlow<ClipFilterState> = _filterState.asStateFlow()

    val filteredClipsState: StateFlow<UiState<List<HighlightClip>>> = combine(
        rawClipsState,
        _filterState
    ) { clipsUiState, filter ->
        when (clipsUiState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> clipsUiState
            is UiState.Success -> {
                val q = filter.query.trim().lowercase()
                val filtered = clipsUiState.data.filter { clip ->
                    val matchesQuery = q.isEmpty() ||
                        clip.title.lowercase().contains(q) ||
                        clip.channelName.lowercase().contains(q) ||
                        clip.streamTitle.lowercase().contains(q) ||
                        clip.hookCaption.lowercase().contains(q)
                    val matchesCategory = filter.selectedCategory == null ||
                        clip.categoryEnum() == filter.selectedCategory
                    val matchesStar = !filter.starredOnly || clip.starred
                    val matchesStream = filter.streamIdFilter == null ||
                        clip.streamId == filter.streamIdFilter
                    matchesQuery && matchesCategory && matchesStar && matchesStream
                }
                val sorted = when (filter.sortOrder) {
                    ClipSortOrder.HYPE_DESC -> filtered.sortedByDescending { it.hypeScore }
                    ClipSortOrder.RECENT_DESC -> filtered
                }
                UiState.Success(sorted)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val allClips: StateFlow<List<HighlightClip>> = rawClipsState
        .map { (it as? UiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _scanState = MutableStateFlow<ScanProgressState>(ScanProgressState.Idle)
    val scanState: StateFlow<ScanProgressState> = _scanState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportProgressState>(ExportProgressState.Idle)
    val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(query = query)
    }

    fun selectCategoryFilter(category: ClipCategory?) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun toggleStarredOnly() {
        _filterState.value = _filterState.value.copy(starredOnly = !_filterState.value.starredOnly)
    }

    fun toggleSortOrder() {
        val next = if (_filterState.value.sortOrder == ClipSortOrder.HYPE_DESC) {
            ClipSortOrder.RECENT_DESC
        } else {
            ClipSortOrder.HYPE_DESC
        }
        _filterState.value = _filterState.value.copy(sortOrder = next)
    }

    fun filterByStream(streamId: String?) {
        _filterState.value = _filterState.value.copy(streamIdFilter = streamId)
    }

    fun resetScanState() {
        _scanState.value = ScanProgressState.Idle
    }

    fun resetExportState() {
        _exportState.value = ExportProgressState.Idle
    }

    suspend fun fetchTwitchMetadata(url: String): Result<TwitchPageMetadata> {
        return aiAnalyzer.fetchTwitchMetadata(url)
    }

    fun scanVideoStream(
        appContext: Context,
        videoUri: Uri,
        customTitle: String,
        channelName: String,
        extraContext: String
    ) {
        viewModelScope.launch {
            var createdStreamId: String? = null
            var extractedBitmaps: List<android.graphics.Bitmap> = emptyList()
            try {
                _scanState.value = ScanProgressState.Working(
                    stageLabel = "Sampling Keyframes",
                    detail = "Inspecting VOD timeline & extracting visual frames..."
                )

                val inspection = VideoClipProcessor.inspectAndSampleKeyframes(appContext, videoUri)
                    .getOrElse { throw it }
                extractedBitmaps = inspection.keyframes.map { it.bitmap }

                val resolvedTitle = customTitle.trim()
                    .ifBlank { inspection.displayName.substringBeforeLast('.') }
                    .take(120)
                    .ifBlank { "Twitch Stream VOD" }

                val streamId = repository.createStreamScan(
                    title = resolvedTitle,
                    channelName = channelName,
                    sourceType = StreamSourceType.VIDEO_FILE,
                    sourceRef = inspection.displayName,
                    durationSeconds = inspection.durationSeconds
                ).getOrElse { throw it }
                createdStreamId = streamId

                _scanState.value = ScanProgressState.Working(
                    stageLabel = "AI Highlight Scan",
                    detail = "Gemini is scoring action peaks, clutch moments & hooks..."
                )

                val aiResult = aiAnalyzer.analyzeStreamVideo(
                    title = resolvedTitle,
                    channelName = channelName,
                    durationSeconds = inspection.durationSeconds,
                    keyframes = inspection.keyframes,
                    extraContext = extraContext
                )

                _scanState.value = ScanProgressState.Working(
                    stageLabel = "Saving Clips",
                    detail = "Syncing ${aiResult.clips.size} highlight clips to your studio..."
                )

                val clipCount = repository.completeStreamScanWithClips(
                    streamId = streamId,
                    streamTitle = resolvedTitle,
                    channelName = channelName,
                    durationSeconds = inspection.durationSeconds,
                    summary = aiResult.streamSummary,
                    localVideoUri = videoUri.toString(),
                    candidates = aiResult.clips
                ).getOrElse { throw it }

                _scanState.value = ScanProgressState.Completed(streamId, clipCount)
                _snackbarMessages.tryEmit("Found $clipCount high-engagement clips!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video scan failed", e)
                val message = e.localizedMessage ?: "Stream scan failed."
                createdStreamId?.let { repository.failStreamScan(it, message) }
                _scanState.value = ScanProgressState.Error(message)
            } finally {
                extractedBitmaps.forEach { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
        }
    }

    fun scanChatOrTranscript(
        title: String,
        channelName: String,
        twitchUrl: String,
        chatOrTranscript: String,
        estimatedDurationMinutes: Int
    ) {
        viewModelScope.launch {
            var createdStreamId: String? = null
            try {
                val safeTitle = title.trim().ifBlank { "Twitch Stream Highlight Scan" }.take(120)
                val durationSec = (estimatedDurationMinutes.coerceIn(1, 720)) * 60
                val sourceType = if (twitchUrl.isNotBlank() && chatOrTranscript.isBlank()) {
                    StreamSourceType.TWITCH_URL
                } else {
                    StreamSourceType.CHAT_TRANSCRIPT
                }
                val sourceRef = twitchUrl.trim().ifBlank { "Chat/Transcript Log" }.take(500)

                _scanState.value = ScanProgressState.Working(
                    stageLabel = "Creating Scan",
                    detail = "Preparing stream highlight session..."
                )

                val streamId = repository.createStreamScan(
                    title = safeTitle,
                    channelName = channelName,
                    sourceType = sourceType,
                    sourceRef = sourceRef,
                    durationSeconds = durationSec
                ).getOrElse { throw it }
                createdStreamId = streamId

                _scanState.value = ScanProgressState.Working(
                    stageLabel = "AI Hype Detection",
                    detail = "Gemini is scanning emote bursts, chat velocity & dialogue peaks..."
                )

                val aiResult = aiAnalyzer.analyzeChatOrTranscript(
                    title = safeTitle,
                    channelName = channelName,
                    twitchUrl = twitchUrl,
                    chatOrTranscript = chatOrTranscript,
                    estimatedDurationSec = durationSec
                )

                _scanState.value = ScanProgressState.Working(
                    stageLabel = "Saving Clips",
                    detail = "Saving ${aiResult.clips.size} timestamped highlights..."
                )

                val clipCount = repository.completeStreamScanWithClips(
                    streamId = streamId,
                    streamTitle = safeTitle,
                    channelName = channelName,
                    durationSeconds = durationSec,
                    summary = aiResult.streamSummary,
                    localVideoUri = null,
                    candidates = aiResult.clips
                ).getOrElse { throw it }

                _scanState.value = ScanProgressState.Completed(streamId, clipCount)
                _snackbarMessages.tryEmit("Detected $clipCount viral moments!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat/transcript scan failed", e)
                val message = e.localizedMessage ?: "Chat analysis failed."
                createdStreamId?.let { repository.failStreamScan(it, message) }
                _scanState.value = ScanProgressState.Error(message)
            }
        }
    }

    fun toggleClipStarred(clip: HighlightClip) {
        viewModelScope.launch {
            repository.toggleClipStarred(clip.id, !clip.starred)
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to update favorite.") }
        }
    }

    fun updateClipDetails(
        clipId: String,
        title: String,
        hookCaption: String,
        category: ClipCategory,
        startSec: Int,
        endSec: Int
    ) {
        viewModelScope.launch {
            repository.updateClipDetails(clipId, title, hookCaption, category, startSec, endSec)
                .onSuccess { _snackbarMessages.tryEmit("Clip updated.") }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save clip edits.") }
        }
    }

    fun exportAndShareMp4(
        appContext: Context,
        clip: HighlightClip,
        startSec: Int,
        endSec: Int
    ) {
        val uriStr = clip.localVideoUri
        if (uriStr.isNullOrBlank()) {
            _exportState.value = ExportProgressState.Error("No local video file attached to this clip.")
            return
        }
        viewModelScope.launch {
            _exportState.value = ExportProgressState.Exporting
            val result = VideoClipProcessor.trimAndExportMp4(
                context = appContext,
                sourceUri = Uri.parse(uriStr),
                clipId = clip.id,
                startSec = startSec,
                endSec = endSec
            )
            result.onSuccess { contentUri ->
                repository.markClipExported(clip.id)
                _exportState.value = ExportProgressState.ReadyToShare(contentUri, clip.title)
            }.onFailure { err ->
                _exportState.value = ExportProgressState.Error(
                    err.localizedMessage ?: "Could not trim MP4 segment."
                )
            }
        }
    }

    fun markClipSharedAsSummary(clipId: String) {
        viewModelScope.launch {
            repository.markClipExported(clipId)
        }
    }

    fun deleteClip(clipId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteClip(clipId)
                .onSuccess {
                    _snackbarMessages.tryEmit("Clip deleted.")
                    onDeleted()
                }
                .onFailure {
                    _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to delete clip.")
                }
        }
    }

    fun deleteStream(streamId: String) {
        viewModelScope.launch {
            repository.deleteStreamWithClips(streamId)
                .onSuccess {
                    if (_filterState.value.streamIdFilter == streamId) {
                        _filterState.value = _filterState.value.copy(streamIdFilter = null)
                    }
                    _snackbarMessages.tryEmit("Stream and its clips removed.")
                }
                .onFailure {
                    _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to delete stream.")
                }
        }
    }
}
