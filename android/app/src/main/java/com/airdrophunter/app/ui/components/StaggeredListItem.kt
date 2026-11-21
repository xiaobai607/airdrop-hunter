package com.airdrophunter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppDesignTokens

/**
 * Low-cost first-entry motion for lazy lists.
 * Only the first six visible-order items animate; the rest draw immediately.
 * Stable saveable keys prevent entrance motion from replaying during ordinary scrolling.
 */
@Composable
fun StaggeredListItem(
    itemKey: String,
    index: Int,
    content: @Composable () -> Unit
) {
    val shouldAnimate = index in 0 until AppDesignTokens.Motion.MaxStaggerItems
    val density = LocalDensity.current
    var hasEntered by rememberSaveable(itemKey) { mutableStateOf(!shouldAnimate) }

    if (shouldAnimate) {
        if (!hasEntered) {
            LaunchedEffect(itemKey) {
                val stagger = index * AppDesignTokens.Motion.StaggerMs
                delay(stagger.toLong())
                hasEntered = true
            }
        }
        AnimatedVisibility(
            visible = hasEntered,
            enter = fadeIn(tween(AppDesignTokens.Motion.FastMs)) +
                slideInVertically(
                    animationSpec = tween(AppDesignTokens.Motion.FastMs),
                    initialOffsetY = { with(density) { 8.dp.roundToPx() } }
                )
        ) {
            content()
        }
    } else {
        content()
    }
}
