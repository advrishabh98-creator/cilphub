package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = DeepVioletContainer,
    onPrimaryContainer = SoftLavender,
    secondary = NeonCyan,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = NeonCyan,
    tertiary = HypeCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5C1130),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = ObsidianBg,
    onBackground = StudioTextPrimary,
    surface = ObsidianBg,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurface,
    onSurfaceVariant = StudioTextMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurface,
    surfaceContainerHigh = StudioSurfaceHigh,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = Color(0xFF282145),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3E0011)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECDCFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2F5EB),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = HypeCoral,
    onTertiary = Color.White,
    background = LightStudioBg,
    onBackground = Color(0xFF1A152B),
    surface = LightStudioSurface,
    onSurface = Color(0xFF1A152B),
    surfaceVariant = LightStudioSurfaceHigh,
    onSurfaceVariant = Color(0xFF534B70),
    surfaceContainer = LightStudioSurface,
    surfaceContainerHigh = LightStudioSurfaceHigh,
    outline = Color(0xFFD0C7E8)
)

val ClipPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipPulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipPulseTypography,
        shapes = ClipPulseShapes,
        content = content
    )
}
