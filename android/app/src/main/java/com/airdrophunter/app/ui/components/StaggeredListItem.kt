package com.airdrophunter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
 * First-entry list motion: 8dp upward settle + fade, 40ms stagger, capped at six items.
 * Saveable per stable item key so scrolling away and back does not replay the entrance.
 */
@Composable
fun StaggeredListItem(
    itemKey: String,
    index: Int,
    content: @Composable () -> Unit
) {
    var hasEntered by rememberSaveable(itemKey) { mutableStateOf(false) }
    LaunchedEffect(itemKey) {
        if (!hasEntered) {
            val stagger = index.coerceAtMost(AppDesignTokens.Motion.MaxStaggerItems - 1) *
                AppDesignTokens.Motion.StaggerMs
            delay(stagger.toLong())
            hasEntered = true
        }
    }
    AnimatedVisibility(
        visible = hasEntered,
        enter = fadeIn(tween(AppDesignTokens.Motion.StandardMs)) +
            slideInVertically(tween(AppDesignTokens.Motion.StandardMs)) { 8 },
        exit = fadeOut(tween(AppDesignTokens.Motion.FastMs))
    ) {
        content()
    }
}
