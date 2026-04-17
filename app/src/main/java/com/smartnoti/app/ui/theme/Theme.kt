package com.smartnoti.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartNotiColors = darkColorScheme(
    primary = BlueAccent,
    secondary = GreenAccent,
    tertiary = AmberAccent,
    background = Navy900,
    surface = SurfaceDark,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun SmartNotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartNotiColors,
        typography = AppTypography,
        content = content,
    )
}
