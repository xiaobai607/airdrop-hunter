package com.airdrophunter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AirdropLightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.Surface,
    secondary = Color(0xFF16845B),
    onSecondary = Color.White,
    tertiary = Color(0xFF7A58FF),
    onTertiary = Color.White,
    error = Color(0xFFE45F62),
    onError = Color.White,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.MutedBackground,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border
)

private val AirdropDarkColorScheme = darkColorScheme(
    primary = Color(0xFF69C6C1),
    onPrimary = Color(0xFF062E2D),
    secondary = Color(0xFF4BD39A),
    onSecondary = Color(0xFF062E20),
    tertiary = Color(0xFF9B83FF),
    onTertiary = Color(0xFF1B1045),
    error = Color(0xFFFF777A),
    onError = Color(0xFF3D080B),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF1A2026),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF29313A),
    onSurfaceVariant = Color(0xFFB7C0CA),
    outline = Color(0xFF3D4854)
)

@Composable
fun AirdropHunterTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) AirdropDarkColorScheme else AirdropLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
