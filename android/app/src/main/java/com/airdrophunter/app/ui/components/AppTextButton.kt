package com.airdrophunter.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Text-only action with the shared 48dp target, immediate opacity feedback and light haptic. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val actionColor = MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalContentColor provides if (enabled) actionColor else actionColor.copy(alpha = 0.42f)) {
        Row(
            modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .appPressFeedback(
                    onClick = onClick,
                    scale = 1f,
                    enabled = enabled,
                    pressedAlpha = 0.6f,
                    role = Role.Button
                )
                .padding(contentPadding),
            content = content
        )
    }
}
