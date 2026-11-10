package com.airdrophunter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AirdropLightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.Surface,
    secondary = AppColors.Primary,
    onSecondary = AppColors.Surface,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.MutedBackground,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    error = AppColors.Danger,
    onError = AppColors.Surface
)

@Composable
fun AirdropHunterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirdropLightColorScheme,
        content = content
    )
}