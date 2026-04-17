package com.smartnoti.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartNotiColors = darkColorScheme(
    primary = BlueAccent,
    onPrimary = TextPrimary,
    primaryContainer = PriorityContainer,
    onPrimaryContainer = PriorityOnContainer,
    secondary = GreenAccent,
    onSecondary = Navy900,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = GreenAccent,
    tertiary = AmberAccent,
    onTertiary = Navy900,
    tertiaryContainer = DigestContainer,
    onTertiaryContainer = DigestOnContainer,
    background = Navy900,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Navy800,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderVariant,
)

@Composable
fun SmartNotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartNotiColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
