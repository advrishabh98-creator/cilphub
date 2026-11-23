package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.data.model.formatDurationSeconds
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HypeCoral
import com.example.ui.theme.HypeGold
import com.example.ui.theme.HypeGoldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun HypeScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val (bgColor, contentColor) = when {
        score >= 90 -> HypeGoldContainer to HypeGold
        score >= 75 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Hype score",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "$score",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun CategoryPill(
    category: ClipCategory,
    modifier: Modifier = Modifier
) {
    val accent = when (category) {
        ClipCategory.CLUTCH_PLAY -> NeonCyan
        ClipCategory.BOSS_KILL -> HypeGold
        ClipCategory.CHAT_SPIKE -> ElectricViolet
        ClipCategory.FUNNY_REACTION -> HypeCoral
        ClipCategory.FAIL_MOMENT -> Color(0xFFFF8A65)
        ClipCategory.HIGHLIGHT -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EngagementWaveformCanvas(
    hypeScore: Int,
    startFraction: Float,
    endFraction: Float,
    seed: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = NeonCyan
    val peakColor = HypeCoral
    val dimColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val highlightOverlay = ElectricViolet.copy(alpha = 0.18f)

    Canvas(modifier = modifier) {
        val barCount = 42
        val gap = size.width * 0.008f
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        val clampedStart = startFraction.coerceIn(0f, 0.99f)
        val clampedEnd = endFraction.coerceIn(clampedStart + 0.01f, 1f)

        drawRoundRect(
            color = highlightOverlay,
            topLeft = Offset(clampedStart * size.width, 0f),
            size = Size((clampedEnd - clampedStart) * size.width, size.height),
            cornerRadius = CornerRadius(10f, 10f)
        )

        for (i in 0 until barCount) {
            val pos = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val inRange = pos in clampedStart..clampedEnd
            val wave = abs(sin((i + 1) * 0.65 + (seed % 17) * 0.4)).toFloat()
            val boost = if (inRange) (hypeScore / 100f) * 0.55f + 0.35f else 0.18f
            val barHeight = (size.height * (0.2f + wave * 0.65f) * boost).coerceIn(6f, size.height)
            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f

            val color = when {
                inRange && wave > 0.72f -> peakColor
                inRange -> activeColor
                else -> dimColor
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun HighlightClipCard(
    clip: HighlightClip,
    onOpenClip: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = clip.categoryEnum()
    val hasLocalVideo = !clip.localVideoUri.isNullOrBlank()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (clip.hypeScore >= 90) ElectricViolet.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("clip_card_${clip.id}")
            .clickable(onClick = onOpenClip)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    HypeScoreBadge(score = clip.hypeScore.toInt())
                    CategoryPill(category = category)
                    if (clip.exported) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Exported",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("star_button_${clip.id}")
                ) {
                    Icon(
                        imageVector = if (clip.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (clip.starred) "Remove favorite" else "Favorite clip",
                        tint = if (clip.starred) HypeGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = clip.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (clip.hookCaption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${clip.hookCaption}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform mini preview
            EngagementWaveformCanvas(
                hypeScore = clip.hypeScore.toInt(),
                startFraction = 0.22f,
                endFraction = 0.72f,
                seed = clip.id.hashCode(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = clip.engagementReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${formatDurationSeconds(clip.startSec)} – ${formatDurationSeconds(clip.endSec)} (${clip.durationSeconds}s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (clip.channelName.isNotBlank()) {
                        Text(
                            text = "@${clip.channelName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(ElectricViolet, HypeCoral))
                        )
                ) {
                    Icon(
                        imageVector = if (hasLocalVideo) Icons.Filled.PlayArrow else Icons.Filled.AutoAwesome,
                        contentDescription = if (hasLocalVideo) "Play and trim clip" else "Inspect clip",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (clip.streamTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = clip.streamTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
