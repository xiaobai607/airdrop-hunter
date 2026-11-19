package com.airdrophunter.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Airdrop Hunter visual tokens aligned with the approved ledger UI specification.
 * Keep legacy aliases until all screens have completed their page-level migration.
 */
object AppDesignTokens {
    object Light {
        val Brand = Color(0xFF1677FF)
        val BrandPressed = Color(0xFF0E5FD8)
        val BrandSoft = Color(0xFFEAF2FF)
        val Success = Color(0xFF00B42A)
        val Warning = Color(0xFFFF7D00)
        val Danger = Color(0xFFF53F3F)
        val WarningSoft = Color(0xFFFFF3E0)
        val DangerSoft = Color(0xFFFFECE8)
        val Ink1 = Color(0xFF1D2129)
        val Ink2 = Color(0xFF4E5969)
        val Ink3 = Color(0xFF86909C)
        val Ink4 = Color(0xFFC9CDD4)
        val Background = Color(0xFFF7F8FA)
        val Group = Color(0xFFF2F3F5)
        val Surface = Color(0xFFFFFFFF)
        val Glass = Color(0xF2FFFFFF)
        val Line = Color(0xFFE5E6EB)
        val Stroke = Color(0xFFE5E6EB)
        val Scrim = Color(0x52000000)
    }

    object Dark {
        val Brand = Color(0xFF4B9AFF)
        val BrandPressed = Color(0xFF2678E8)
        val BrandSoft = Color(0xFF172B45)
        val Success = Color(0xFF30D158)
        val Warning = Color(0xFFFF9F0A)
        val Danger = Color(0xFFFF453A)
        val WarningSoft = Color(0xFF33270F)
        val DangerSoft = Color(0xFF3A1D1C)
        val Ink1 = Color(0xFFFFFFFF)
        val Ink2 = Color(0xFFAEAEB2)
        val Ink3 = Color(0xFF8E8E93)
        val Ink4 = Color(0xFF48484A)
        val Background = Color(0xFF121214)
        val Group = Color(0xFF1C1C1E)
        val Surface = Color(0xFF1E1E23)
        val Glass = Color(0xAD1E1E23)
        val Line = Color(0xFF34343A)
        val Stroke = Color(0xFF34343A)
        val Scrim = Color(0x8F000000)
    }

    object Radius {
        const val Sm = 8
        const val Xl = 16
        const val Lg = 12
        const val Md = 8
        const val Full = 999
    }

    object Spacing {
        const val Xs = 4
        const val Sm = 8
        const val Md = 12
        const val Lg = 16
        const val Xl = 20
        const val Xxl = 24
        const val Xxxl = 32
    }

    object Motion {
        const val FastMs = 150
        const val StandardMs = 250
        const val TabMs = 200
        const val PageMs = 320
        const val SheetMs = 380
        const val DialogMs = 240
        const val ExitMs = 260
        const val NumberRollMs = 400
        const val StaggerMs = 40
        const val MaxStaggerItems = 6
        const val PressCardScale = 0.96f
        const val PressButtonScale = 0.94f
    }

    object Control {
        const val ButtonHeight = 48
        const val InputHeight = 48
        const val IconHitTarget = 48
        const val ListRowHeight = 56
        const val TwoLineRowHeight = 64
        const val TabHeight = 56
        const val ChipHeight = 34
    }
}

/** Compatibility aliases: migrate screens to MaterialTheme.colorScheme during page passes. */
object AppColors {
    val Background = AppDesignTokens.Light.Background
    val BackgroundDeep = AppDesignTokens.Light.Background
    val Surface = AppDesignTokens.Light.Surface
    val SurfaceElevated = AppDesignTokens.Light.Surface
    val Primary = AppDesignTokens.Light.Brand
    val PrimaryPressed = AppDesignTokens.Light.BrandPressed
    val AccentBlue = AppDesignTokens.Light.Brand
    val AccentPurple = AppDesignTokens.Light.Brand
    val TextPrimary = AppDesignTokens.Light.Ink1
    val TextSecondary = AppDesignTokens.Light.Ink2
    val TextHelper = AppDesignTokens.Light.Ink3
    val TextDisabled = AppDesignTokens.Light.Ink4
    val Border = AppDesignTokens.Light.Stroke
    val Success = AppDesignTokens.Light.Success
    val SuccessBackground = Color(0xFFE8F8EC)
    val MutedBackground = AppDesignTokens.Light.Group
    val Danger = AppDesignTokens.Light.Danger
    val DangerBackground = AppDesignTokens.Light.DangerSoft
    val NavIndicator = AppDesignTokens.Light.BrandSoft
    val Navigation = AppDesignTokens.Light.Glass
    val TealTint = AppDesignTokens.Light.BrandSoft
    val TealSoft = Color(0xFFD7E7FF)
}
