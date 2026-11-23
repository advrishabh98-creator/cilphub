package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StreamSourceType(val label: String) {
    VIDEO_FILE("VOD Video"),
    CHAT_TRANSCRIPT("Chat & Transcript"),
    TWITCH_URL("Twitch VOD")
}

enum class AnalysisStatus(val label: String) {
    ANALYZING("Scanning"),
    READY("Ready"),
    FAILED("Failed")
}

enum class ClipCategory(val label: String) {
    CLUTCH_PLAY("Clutch Play"),
    FUNNY_REACTION("Funny Reaction"),
    CHAT_SPIKE("Chat Spike"),
    FAIL_MOMENT("Fail Moment"),
    BOSS_KILL("Boss Kill"),
    HIGHLIGHT("Highlight")
}

@Keep
@IgnoreExtraProperties
data class StreamAnalysis(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val channelName: String = "",
    val sourceType: String = StreamSourceType.VIDEO_FILE.name,
    val sourceRef: String = "",
    val durationSeconds: Long = 0L,
    val clipCount: Long = 0L,
    val peakHypeScore: Long = 0L,
    val summary: String = "",
    val status: String = AnalysisStatus.READY.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun sourceTypeEnum(): StreamSourceType =
        enumValues<StreamSourceType>().firstOrNull { it.name == sourceType } ?: StreamSourceType.VIDEO_FILE

    fun statusEnum(): AnalysisStatus =
        enumValues<AnalysisStatus>().firstOrNull { it.name == status } ?: AnalysisStatus.READY
}

@Keep
@IgnoreExtraProperties
data class HighlightClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val streamId: String = "",
    val streamTitle: String = "",
    val channelName: String = "",
    val title: String = "",
    val hookCaption: String = "",
    val engagementReason: String = "",
    val category: String = ClipCategory.HIGHLIGHT.name,
    val startSec: Long = 0L,
    val endSec: Long = 15L,
    val hypeScore: Long = 80L,
    val starred: Boolean = false,
    val exported: Boolean = false,
    val localVideoUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): ClipCategory =
        enumValues<ClipCategory>().firstOrNull { it.name == category } ?: ClipCategory.HIGHLIGHT

    val durationSeconds: Int
        get() = (endSec - startSec).coerceAtLeast(1L).toInt()
}

fun formatDurationSeconds(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600L
    val minutes = (clamped % 3600L) / 60L
    val seconds = clamped % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
