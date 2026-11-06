package com.airdrophunter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AirdropDarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.Background,
    secondary = AppColors.Primary,
    onSecondary = AppColors.Background,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceElevated,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    error = AppColors.Danger,
    onError = AppColors.Background
)

@Composable
fun AirdropHunterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirdropDarkColorScheme,
        content = content
    )
}
