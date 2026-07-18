package com.joel.minimallauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val MinimalColors = darkColorScheme(
    primary = Color(0xFFF3F1EB), onPrimary = Color(0xFF0B0B0B),
    background = Color(0xFF0B0B0B), onBackground = Color(0xFFF3F1EB),
    surface = Color(0xFF111111), onSurface = Color(0xFFF3F1EB),
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFAAA7A0),
    outline = Color(0xFF3B3A38), error = Color(0xFFFFB4AB)
)

private val HighContrastColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF111111), onSurfaceVariant = Color.White,
    outline = Color.White, error = Color(0xFFFFDAD6)
)

@Composable
fun JoelMinimalTheme(highContrast: Boolean, largeText: Boolean, content: @Composable () -> Unit) {
    val typography = if (largeText) MinimalTypography.copy(
        displayLarge = MinimalTypography.displayLarge.copy(fontSize = 66.sp, lineHeight = 72.sp),
        headlineSmall = MinimalTypography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 32.sp),
        bodyLarge = MinimalTypography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 32.sp),
        bodyMedium = MinimalTypography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodySmall = MinimalTypography.bodySmall.copy(fontSize = 16.sp, lineHeight = 23.sp),
        labelLarge = MinimalTypography.labelLarge.copy(fontSize = 17.sp)
    ) else MinimalTypography
    MaterialTheme(
        colorScheme = if (highContrast) HighContrastColors else MinimalColors,
        typography = typography,
        content = content
    )
}
