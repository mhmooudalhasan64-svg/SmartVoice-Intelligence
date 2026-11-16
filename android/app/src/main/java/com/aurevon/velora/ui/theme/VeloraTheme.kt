package com.aurevon.velora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF111827)
internal val Panel = Color(0xFF1F2937)
internal val Accent = Color(0xFF7C9EFF)
internal val Muted = Color(0xFF9CA3AF)

@Composable
internal fun VeloraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
