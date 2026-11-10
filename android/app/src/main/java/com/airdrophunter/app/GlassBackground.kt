package com.airdrophunter.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/**
 * Reusable dark glass surface. On Android 12+ the translucent surface layer
 * receives a Gaussian blur; older versions use a stronger dark scrim.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 30.dp,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x55333B4A), RoundedCornerShape(cornerRadius))
                    .blur(blurRadius)
            )
            Box(Modifier.fillMaxSize().background(Color(0x66303A4A), RoundedCornerShape(cornerRadius)))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(cornerRadius)))
        }
        content()
    }
}
