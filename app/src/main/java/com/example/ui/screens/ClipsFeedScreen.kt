package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.ui.components.HighlightClipCard
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HypeCoral
import com.example.ui.theme.HypeGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.ClipFilterState
import com.example.ui.viewmodel.UiState

@Composable
fun ClipsFeedScreen(
    clipsState: UiState<List<HighlightClip>>,
    allClips: List<HighlightClip>,
    filterState: ClipFilterState,
    onSearchChanged: (String) -> Unit,
    onSelectCategory: (ClipCategory?) -> Unit,
    onToggleStarredOnly: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onClearStreamFilter: () -> Unit,
    onOpenClip: (String) -> Unit,
    onToggleStar: (HighlightClip) -> Unit,
    onLaunchScan: (ScanInputMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalClips = allClips.size
    val peakHype = allClips.maxOfOrNull { it.hypeScore }?.toInt() ?: 0
    val exportedCount = allClips.count { it.exported }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            item(key = "hero_studio_card") {
                StudioHeroSummaryCard(
                    totalClips = totalClips,
                    peakHype = peakHype,
                    exportedCount = exportedCount,
                    onScanVideo = { onLaunchScan(ScanInputMode.VOD_VIDEO) },
                    onScanChat = { onLaunchScan(ScanInputMode.CHAT_AND_URL) }
                )
            }

            if (filterState.streamIdFilter != null) {
                item(key = "active_stream_filter") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Showing highlights from selected stream",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = onClearStreamFilter) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear stream filter",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (totalClips > 0 || filterState.query.isNotEmpty() || filterState.selectedCategory != null) {
                item(key = "search_and_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = filterState.query,
                            onValueChange = onSearchChanged,
                            placeholder = { Text("Search clips, channels, or hooks...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (filterState.query.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChanged("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_clips_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = filterState.sortOrder == com.example.ui.viewmodel.ClipSortOrder.HYPE_DESC,
                                onClick = onToggleSortOrder,
                                label = { Text(filterState.sortOrder.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            FilterChip(
                                selected = filterState.starredOnly,
                                onClick = onToggleStarredOnly,
                                label = { Text("Starred") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = if (filterState.starredOnly) HypeGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            FilterChip(
                                selected = filterState.selectedCategory == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text("All Types") }
                            )

                            ClipCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = filterState.selectedCategory == cat,
                                    onClick = {
                                        onSelectCategory(
                                            if (filterState.selectedCategory == cat) null else cat
                                        )
                                    },
                                    label = { Text(cat.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }

            when (clipsState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = clipsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (clipsState.data.isEmpty()) {
                        item(key = "empty_clips") {
                            EmptyClipsState(
                                hasAnyClips = totalClips > 0,
                                onScanVideo = { onLaunchScan(ScanInputMode.VOD_VIDEO) },
                                onScanChat = { onLaunchScan(ScanInputMode.CHAT_AND_URL) }
                            )
                        }
                    } else {
                        items(
                            items = clipsState.data,
                            key = { it.id }
                        ) { clip ->
                            HighlightClipCard(
                                clip = clip,
                                onOpenClip = { onOpenClip(clip.id) },
                                onToggleStar = { onToggleStar(clip) }
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(76.dp))
            }
        }
    }
}

@Composable
private fun StudioHeroSummaryCard(
    totalClips: Int,
    peakHype: Int,
    exportedCount: Int,
    onScanVideo: () -> Unit,
    onScanChat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_studio),
                    contentDescription = "ClipPulse AI studio banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ObsidianBg.copy(alpha = 0.92f),
                                    ObsidianBg.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AUTO-CLIP STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Highlight Peak Moments",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI scores action & chat surges into ready-to-share clips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD9D2F0)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatTile(
                        label = "AI Clips",
                        value = totalClips.toString(),
                        accent = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Peak Hype",
                        value = if (peakHype > 0) "$peakHype" else "--",
                        accent = HypeGold,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Exported",
                        value = exportedCount.toString(),
                        accent = HypeCoral,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onScanVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("hero_scan_video_button")
                    ) {
                        Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan_video), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onScanChat,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("hero_scan_chat_button")
                    ) {
                        Icon(
                            Icons.Filled.Forum,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat / URL", color = NeonCyan, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyClipsState(
    hasAnyClips: Boolean,
    onScanVideo: () -> Unit,
    onScanChat: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_clips),
                contentDescription = "No clips illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(155.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasAnyClips) "No clips match your filters" else stringResource(R.string.empty_clips_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyClips) "Try clearing your search or category chips to see all AI highlights."
                else stringResource(R.string.empty_clips_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasAnyClips) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onScanVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text(stringResource(R.string.action_scan_video))
                    }
                    OutlinedButton(onClick = onScanChat) {
                        Text(stringResource(R.string.action_scan_chat))
                    }
                }
            }
        }
    }
}
