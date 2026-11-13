package com.airdrophunter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppDesignTokens

enum class AppButtonStyle { PRIMARY, SECONDARY, TEXT, DANGER }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = true,
    heightDp: Int = if (style == AppButtonStyle.TEXT) 44 else AppDesignTokens.Control.ButtonHeight,
    leadingIcon: ImageVector? = null,
    fontSizeSp: Int = 17
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)
    val active = enabled && !loading
    val background = when (style) {
        AppButtonStyle.PRIMARY -> if (active) colors.primary else colors.primary.copy(alpha = 0.18f)
        AppButtonStyle.SECONDARY -> Color.Transparent
        AppButtonStyle.TEXT -> Color.Transparent
        AppButtonStyle.DANGER -> if (active) colors.error else colors.error.copy(alpha = 0.16f)
    }
    val foreground = when (style) {
        AppButtonStyle.PRIMARY -> if (active) colors.onPrimary else colors.primary.copy(alpha = 0.82f)
        AppButtonStyle.SECONDARY, AppButtonStyle.TEXT -> if (active) colors.primary else colors.onSurfaceVariant
        AppButtonStyle.DANGER -> if (active) colors.onError else colors.error.copy(alpha = 0.82f)
    }
    val border = if (style == AppButtonStyle.SECONDARY && active) colors.primary.copy(alpha = 0.7f) else Color.Transparent
    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(heightDp.dp)
            .background(background, shape)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, shape) else Modifier)
            .appPressFeedback(
                onClick = onClick,
                enabled = active,
                haptic = if (style == AppButtonStyle.DANGER) AppHaptic.WARNING else AppHaptic.LIGHT,
                pressedOverlay = Color.Black.copy(alpha = 0.08f),
                role = Role.Button
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = foreground,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = foreground,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
