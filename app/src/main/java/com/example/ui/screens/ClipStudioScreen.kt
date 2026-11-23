package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.data.model.formatDurationSeconds
import com.example.ui.components.EngagementWaveformCanvas
import com.example.ui.components.HypeScoreBadge
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HypeGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.ExportProgressState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipStudioScreen(
    clip: HighlightClip,
    streamDurationSec: Int,
    exportState: ExportProgressState,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onSaveDetails: (title: String, hookCaption: String, category: ClipCategory, startSec: Int, endSec: Int) -> Unit,
    onExportMp4: (startSec: Int, endSec: Int) -> Unit,
    onResetExportState: () -> Unit,
    onMarkSharedSummary: () -> Unit,
    onDeleteClip: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val maxTimelineSec = max(streamDurationSec, clip.endSec.toInt() + 15).coerceAtLeast(30)

    var title by remember(clip.id) { mutableStateOf(clip.title) }
    var hookCaption by remember(clip.id) { mutableStateOf(clip.hookCaption) }
    var category by remember(clip.id) { mutableStateOf(clip.categoryEnum()) }
    var startSec by remember(clip.id) { mutableIntStateOf(clip.startSec.toInt()) }
    var endSec by remember(clip.id) { mutableIntStateOf(clip.endSec.toInt()) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var currentPosSec by remember { mutableIntStateOf(startSec) }

    val hasLocalVideo = !clip.localVideoUri.isNullOrBlank()

    LaunchedEffect(exportState) {
        if (exportState is ExportProgressState.ReadyToShare) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, exportState.contentUri)
                putExtra(Intent.EXTRA_SUBJECT, exportState.clipTitle)
                putExtra(Intent.EXTRA_TEXT, "${exportState.clipTitle}\n${hookCaption}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ClipPulse MP4"))
            onResetExportState()
        }
    }

    LaunchedEffect(isPlaying, startSec, endSec, videoViewRef) {
        while (isPlaying) {
            val view = videoViewRef
            if (view != null) {
                val posSec = view.currentPosition / 1000
                currentPosSec = posSec
                if (posSec >= endSec) {
                    view.seekTo(startSec * 1000)
                }
            }
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clip_studio_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.testTag("studio_star_button")
                    ) {
                        Icon(
                            imageVector = if (clip.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite clip",
                            tint = if (clip.starred) HypeGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDeleteClip,
                        modifier = Modifier.testTag("studio_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete clip"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Preview Player or Engagement Pulse Stage
                Surface(
                    color = ObsidianBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (hasLocalVideo && videoError == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .background(Color.Black)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(clip.localVideoUri))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                seekTo(startSec * 1000)
                                                start()
                                                isPlaying = true
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                videoError = "Source video URI permission is no longer active on this device. Re-pick the VOD or share the highlight card."
                                                isPlaying = false
                                                true
                                            }
                                            videoViewRef = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                ) {
                                    Surface(
                                        color = ObsidianBg.copy(alpha = 0.78f),
                                        shape = CircleShape
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val v = videoViewRef ?: return@IconButton
                                                if (v.isPlaying) {
                                                    v.pause()
                                                    isPlaying = false
                                                } else {
                                                    if (v.currentPosition / 1000 >= endSec || v.currentPosition / 1000 < startSec) {
                                                        v.seekTo(startSec * 1000)
                                                    }
                                                    v.start()
                                                    isPlaying = true
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause preview" else "Play clip loop",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    Surface(
                                        color = ObsidianBg.copy(alpha = 0.78f),
                                        shape = CircleShape
                                    ) {
                                        IconButton(
                                            onClick = {
                                                videoViewRef?.seekTo(startSec * 1000)
                                                videoViewRef?.start()
                                                isPlaying = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Replay,
                                                contentDescription = "Restart clip loop",
                                                tint = NeonCyan
                                            )
                                        }
                                    }

                                    Surface(
                                        color = ObsidianBg.copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "LOOP ${formatDurationSeconds(currentPosSec.toLong())}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "ENGAGEMENT PULSE WINDOW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan
                                    )
                                    HypeScoreBadge(score = clip.hypeScore.toInt())
                                }
                                EngagementWaveformCanvas(
                                    hypeScore = clip.hypeScore.toInt(),
                                    startFraction = startSec.toFloat() / maxTimelineSec.toFloat(),
                                    endFraction = endSec.toFloat() / maxTimelineSec.toFloat(),
                                    seed = clip.id.hashCode(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                )
                                if (videoError != null) {
                                    Text(
                                        text = videoError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Timeline Scrubber & Fine Trim
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCut,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Trim Window",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "${formatDurationSeconds(startSec.toLong())} – ${formatDurationSeconds(endSec.toLong())} (${endSec - startSec}s)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NeonCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        RangeSlider(
                            value = startSec.toFloat()..endSec.toFloat(),
                            onValueChange = { range ->
                                val newStart = range.start.roundToInt().coerceIn(0, maxTimelineSec - 1)
                                val newEnd = range.endInclusive.roundToInt().coerceIn(newStart + 1, maxTimelineSec)
                                startSec = newStart
                                endSec = newEnd
                            },
                            valueRange = 0f..maxTimelineSec.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = ElectricViolet,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier.testTag("trim_range_slider")
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { if (startSec > 0) startSec -= 1 },
                                    enabled = startSec > 0
                                ) { Text("-1s Start") }
                                OutlinedButton(
                                    onClick = { if (startSec + 1 < endSec) startSec += 1 },
                                    enabled = startSec + 1 < endSec
                                ) { Text("+1s Start") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { if (endSec - 1 > startSec) endSec -= 1 },
                                    enabled = endSec - 1 > startSec
                                ) { Text("-1s End") }
                                OutlinedButton(
                                    onClick = { if (endSec < maxTimelineSec) endSec += 1 },
                                    enabled = endSec < maxTimelineSec
                                ) { Text("+1s End") }
                            }
                        }
                    }
                }

                // AI Rationale
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Why This Pops Off",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HypeScoreBadge(score = clip.hypeScore.toInt())
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = clip.engagementReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Editable Metadata
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { if (it.length <= 120) title = it },
                            label = { Text("Clip Title") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_clip_title_input")
                        )

                        OutlinedTextField(
                            value = hookCaption,
                            onValueChange = { if (it.length <= 180) hookCaption = it },
                            label = { Text("Shorts / TikTok Hook Overlay") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_clip_hook_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ClipCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveDetails(title, hookCaption, category, startSec, endSec)
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_clip_edits_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_changes))
                        }
                    }
                }

                // Export / Share Action
                if (exportState is ExportProgressState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (hasLocalVideo) {
                    Button(
                        onClick = { onExportMp4(startSec, endSec) },
                        enabled = exportState !is ExportProgressState.Exporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("export_mp4_button")
                    ) {
                        if (exportState is ExportProgressState.Exporting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Trimming MP4 Clip...")
                        } else {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.export_mp4_clip),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onMarkSharedSummary()
                            val summaryText = buildString {
                                appendLine("ClipPulse Highlight (${clip.hypeScore}/100 Hype)")
                                appendLine(title)
                                if (hookCaption.isNotBlank()) appendLine("Hook: \"$hookCaption\"")
                                appendLine("Timestamp: ${formatDurationSeconds(startSec.toLong())} - ${formatDurationSeconds(endSec.toLong())}")
                                if (clip.channelName.isNotBlank()) appendLine("Channel: twitch.tv/${clip.channelName}")
                                appendLine("Reason: ${clip.engagementReason}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, summaryText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Timestamp"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("share_summary_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share_clip_summary),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
