package com.touchgrass.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The one color scheme both screens share. Light only - no dark variant yet. */
@Composable
fun TouchGrassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color(0xFFFBF8FF)
        ),
        content = content
    )
}
