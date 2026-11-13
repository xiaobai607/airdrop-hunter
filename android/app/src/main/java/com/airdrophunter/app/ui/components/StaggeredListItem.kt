package com.airdrophunter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.airdrophunter.app.ui.theme.AppDesignTokens

/**
 * Low-cost first-entry motion for lazy lists.
 * Only the first three visible-order items animate; the rest draw immediately.
 * Stable saveable keys prevent entrance motion from replaying during ordinary scrolling.
 */
@Composable
fun StaggeredListItem(
    itemKey: String,
    index: Int,
    content: @Composable () -> Unit
) {
    val shouldAnimate = index in 0 until AppDesignTokens.Motion.MaxStaggerItems
    var hasEntered by rememberSaveable(itemKey) { mutableStateOf(!shouldAnimate) }

    if (shouldAnimate) {
        if (!hasEntered) {
            LaunchedEffect(itemKey) {
                val stagger = index.coerceAtMost(2) * AppDesignTokens.Motion.StaggerMs
                delay(stagger.toLong())
                hasEntered = true
            }
        }
        AnimatedVisibility(
            visible = hasEntered,
            enter = fadeIn(tween(AppDesignTokens.Motion.FastMs))
        ) {
            content()
        }
    } else {
        content()
    }
}
