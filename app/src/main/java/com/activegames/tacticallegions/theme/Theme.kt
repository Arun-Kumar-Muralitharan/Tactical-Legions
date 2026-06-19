package com.activegames.tacticallegions.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = CyberGreen,
    secondary = CyberBlue,
    tertiary = CyberRed,
    background = DeepCharcoal,
    surface = SurfaceGray,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground
)

@Composable
fun TacticalLegionsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
