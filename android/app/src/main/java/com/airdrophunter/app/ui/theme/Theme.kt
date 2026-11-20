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
    // Four-level scale from the approved UI specification: 18 / 16 / 14 / 12sp.
    displayLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")
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
