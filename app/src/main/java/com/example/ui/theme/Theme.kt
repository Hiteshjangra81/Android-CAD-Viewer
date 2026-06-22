package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SciFiDarkColorScheme = darkColorScheme(
    primary = CyberNeonCyan,
    secondary = CyberAmber,
    tertiary = CyberLaserOrange,
    background = CyberOnyx,
    surface = CyberSteel,
    onPrimary = CyberOnyx,
    onSecondary = CyberOnyx,
    onBackground = CyberWhite,
    onSurface = CyberWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SciFiDarkColorScheme,
        typography = Typography,
        content = content
    )
}
