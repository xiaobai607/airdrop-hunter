package com.airdrophunter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppDesignTokens

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: AppHaptic = AppHaptic.LIGHT
) {
    Box(
        modifier = modifier.size(AppDesignTokens.Control.IconHitTarget.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(AppDesignTokens.Radius.Full.dp))
            .appPressFeedback(
                onClick = onClick,
                scale = AppDesignTokens.Motion.PressButtonScale,
                enabled = enabled,
                haptic = haptic,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(24.dp)
        )
    }
}
