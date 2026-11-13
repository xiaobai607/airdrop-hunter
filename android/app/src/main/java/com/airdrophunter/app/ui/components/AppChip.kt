package com.airdrophunter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppDesignTokens

@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)
    Box(
        modifier = modifier
            // Keep the visible capsule compact while preserving the 48dp accessibility hit target.
            .heightIn(min = 48.dp)
            .appPressFeedback(onClick, scale = AppDesignTokens.Motion.PressButtonScale, enabled = enabled, role = Role.Tab)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(AppDesignTokens.Control.ChipHeight.dp)
                .background(
                    if (!enabled) colors.surfaceVariant.copy(alpha = 0.48f)
                    else if (selected) colors.primary else colors.surfaceVariant,
                    shape
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = when {
                    !enabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                    selected -> colors.onPrimary
                    else -> colors.onSurface
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
