package com.airdrophunter.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

enum class AppHaptic { LIGHT, MEDIUM, WARNING }

/**
 * Shared tactile contract: visual response and haptic feedback start on pointer-down,
 * never later than the first frame. Use 0.96 for cards and 0.94 for buttons/icon actions.
 */
fun Modifier.appPressFeedback(
    onClick: () -> Unit,
    scale: Float = 0.94f,
    enabled: Boolean = true,
    haptic: AppHaptic = AppHaptic.LIGHT,
    pressedAlpha: Float = 1f,
    pressedOverlay: Color? = null,
    role: Role = Role.Button
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    val overlay = pressedOverlay ?: Color.Black.copy(alpha = 0.06f)
    val target = if (pressed && enabled) scale else 1f
    val animatedScale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        label = "app-press-scale"
    )

    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            val constant = when (haptic) {
                AppHaptic.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
                AppHaptic.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
                AppHaptic.WARNING -> HapticFeedbackConstants.REJECT
            }
            view.performHapticFeedback(constant)
        }
    }

    this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
        alpha = if (pressed && enabled) pressedAlpha.coerceIn(0f, 1f) else 1f
    }.drawWithContent {
        drawContent()
        if (pressed && enabled) drawRect(overlay)
    }.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick
    )
}
