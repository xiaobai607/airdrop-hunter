package com.airdrophunter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val T = AppDesignTokens.Light
private val D = AppDesignTokens.Dark

private val AirdropLightColorScheme = lightColorScheme(
    primary = T.Brand,
    onPrimary = Color.White,
    secondary = T.Success,
    onSecondary = T.Ink1,
    tertiary = T.Brand,
    onTertiary = Color.White,
    error = T.Danger,
    onError = Color.White,
    background = T.Background,
    onBackground = T.Ink1,
    surface = T.Surface,
    onSurface = T.Ink1,
    surfaceVariant = T.Group,
    onSurfaceVariant = T.Ink2,
    outline = T.Stroke,
    outlineVariant = T.Line,
    scrim = T.Scrim
)

private val AirdropDarkColorScheme = darkColorScheme(
    primary = D.Brand,
    onPrimary = Color(0xFF062E2D),
    secondary = D.Success,
    onSecondary = Color(0xFF062E20),
    tertiary = D.Brand,
    onTertiary = Color(0xFF062E2D),
    error = D.Danger,
    onError = Color(0xFF3D080B),
    background = D.Background,
    onBackground = D.Ink1,
    surface = D.Surface,
    onSurface = D.Ink1,
    surfaceVariant = D.Group,
    onSurfaceVariant = D.Ink2,
    outline = D.Stroke,
    outlineVariant = D.Line,
    scrim = D.Scrim
)

/**
 * Explicitly define every Material 3 typography slot used by Compose.
 * Leaving a slot unset silently falls back to Material defaults (for example 45sp
 * display text), which breaks the compact 34/28/22/20/17/15/14/13/12/11sp scale.
 */
private val AirdropTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal)
)

@Composable
fun AirdropHunterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) AirdropDarkColorScheme else AirdropLightColorScheme,
        typography = AirdropTypography,
        content = content
    )
}
