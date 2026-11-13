package com.airdrophunter.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Text-only action with the shared 44dp target, immediate opacity feedback and light haptic. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
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
