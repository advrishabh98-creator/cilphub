package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.TwitchPageMetadata
import com.example.data.media.VideoClipProcessor
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodel.ScanProgressState
import kotlinx.coroutines.launch

enum class ScanInputMode(val label: String) {
    VOD_VIDEO("VOD Video"),
    CHAT_AND_URL("Chat & URL")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScanSheet(
    initialMode: ScanInputMode,
    scanState: ScanProgressState,
    onDismiss: () -> Unit,
    onFetchTwitchMetadata: suspend (String) -> Result<TwitchPageMetadata>,
    onStartVideoScan: (videoUri: Uri, title: String, channelName: String, extraContext: String) -> Unit,
    onStartChatScan: (title: String, channelName: String, twitchUrl: String, chatText: String, estimatedMinutes: Int) -> Unit,
    onScanCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedVideoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVideoLabel by rememberSaveable { mutableStateOf("") }

    var streamTitle by rememberSaveable { mutableStateOf("") }
    var channelName by rememberSaveable { mutableStateOf("") }
    var extraContext by rememberSaveable { mutableStateOf("") }

    var twitchUrl by rememberSaveable { mutableStateOf("") }
    var chatLog by rememberSaveable { mutableStateOf("") }
    var estimatedMinutes by rememberSaveable { mutableIntStateOf(60) }
    var isFetchingUrl by remember { mutableStateOf(false) }
    var urlStatusMessage by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUriString = uri.toString()
            selectedVideoLabel = VideoClipProcessor.resolveDisplayName(context, uri)
            if (streamTitle.isBlank()) {
                streamTitle = selectedVideoLabel.substringBeforeLast('.').take(120)
            }
        }
    }

    LaunchedEffect(scanState) {
        if (scanState is ScanProgressState.Completed) {
            onScanCompleted()
        }
    }

    val isWorking = scanState is ScanProgressState.Working

    ModalBottomSheet(
        onDismissRequest = { if (!isWorking) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Automated AI Stream Scan",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detect clutch plays, chat velocity bursts, and viral hooks automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScanInputMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { if (!isWorking) selectedMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ScanInputMode.entries.size
                        ),
                        modifier = Modifier.testTag("scan_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedMode == ScanInputMode.VOD_VIDEO) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedVideoUriString != null) NeonCyan.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedVideoUriString != null) Icons.Filled.CheckCircle else Icons.Filled.VideoFile,
                            contentDescription = null,
                            tint = if (selectedVideoUriString != null) NeonCyan else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedVideoLabel.isNotBlank()) selectedVideoLabel else "Pick a Twitch VOD or gameplay recording",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !isWorking,
                            modifier = Modifier.testTag("pick_video_button")
                        ) {
                            Text(if (selectedVideoUriString == null) "Choose Video File" else "Change Video")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = streamTitle,
                    onValueChange = { if (it.length <= 120) streamTitle = it },
                    label = { Text("Stream Title") },
                    placeholder = { Text("e.g. Ranked Grind - Overtime Madness") },
                    singleLine = true,
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_stream_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = channelName,
                    onValueChange = { if (it.length <= 60) channelName = it },
                    label = { Text("Twitch Channel (optional)") },
                    placeholder = { Text("e.g. shroud") },
                    singleLine = true,
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_channel_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = extraContext,
                    onValueChange = { if (it.length <= 300) extraContext = it },
                    label = { Text("Highlight Focus / Game (optional)") },
                    placeholder = { Text("e.g. Valorant clutches, funny fails, chat reactions") },
                    maxLines = 3,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = twitchUrl,
                        onValueChange = { if (it.length <= 500) twitchUrl = it },
                        label = { Text("Twitch Stream / VOD URL") },
                        placeholder = { Text("https://www.twitch.tv/videos/...") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        singleLine = true,
                        enabled = !isWorking,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("twitch_url_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (twitchUrl.isNotBlank()) {
                                isFetchingUrl = true
                                urlStatusMessage = null
                                scope.launch {
                                    onFetchTwitchMetadata(twitchUrl)
                                        .onSuccess { meta ->
                                            if (meta.title.isNotBlank()) streamTitle = meta.title
                                            if (meta.channelName.isNotBlank()) channelName = meta.channelName
                                            urlStatusMessage = "Loaded public Twitch info."
                                        }
                                        .onFailure { err ->
                                            urlStatusMessage = err.localizedMessage ?: "Could not fetch page metadata."
                                        }
                                    isFetchingUrl = false
                                }
                            }
                        },
                        enabled = !isWorking && !isFetchingUrl && twitchUrl.isNotBlank(),
                        modifier = Modifier.testTag("fetch_twitch_meta_button")
                    ) {
                        if (isFetchingUrl) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Fetch")
                        }
                    }
                }

                if (urlStatusMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = urlStatusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { if (it.length <= 120) streamTitle = it },
                        label = { Text("Stream Title *") },
                        singleLine = true,
                        enabled = !isWorking,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("chat_stream_title_input")
                    )
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { if (it.length <= 60) channelName = it },
                        label = { Text("Channel") },
                        singleLine = true,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "VOD Length: ${estimatedMinutes}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(15, 60, 120, 240).forEach { mins ->
                            TextButton(
                                onClick = { estimatedMinutes = mins },
                                enabled = !isWorking
                            ) {
                                Text(
                                    text = if (mins >= 60) "${mins / 60}h" else "${mins}m",
                                    color = if (estimatedMinutes == mins) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = chatLog,
                    onValueChange = { chatLog = it },
                    label = { Text("Paste Twitch Chat Replay or Transcript *") },
                    placeholder = {
                        Text("[00:14:22] user1: KEKW\n[00:14:25] user2: NO WAY HE CLUTCHED THAT POGGERS...")
                    },
                    leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null) },
                    minLines = 4,
                    maxLines = 7,
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_transcript_input")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (scanState) {
                is ScanProgressState.Working -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = NeonCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = scanState.stageLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = scanState.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                is ScanProgressState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scanState.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                else -> Unit
            }

            val canLaunchScan = when (selectedMode) {
                ScanInputMode.VOD_VIDEO -> selectedVideoUriString != null && !isWorking
                ScanInputMode.CHAT_AND_URL -> streamTitle.isNotBlank() && chatLog.isNotBlank() && !isWorking
            }

            Button(
                onClick = {
                    if (selectedMode == ScanInputMode.VOD_VIDEO) {
                        val uri = selectedVideoUriString?.let { Uri.parse(it) } ?: return@Button
                        onStartVideoScan(uri, streamTitle, channelName, extraContext)
                    } else {
                        onStartChatScan(streamTitle, channelName, twitchUrl, chatLog, estimatedMinutes)
                    }
                },
                enabled = canLaunchScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("run_ai_scan_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isWorking) "Scanning Stream..." else "Detect Top Highlights with AI",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
