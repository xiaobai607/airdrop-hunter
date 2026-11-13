package com.airdrophunter.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppDesignTokens

data class AppNavigationItem(val label: String, val icon: ImageVector)

@Composable
fun AppBottomBar(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val glass = if (dark) AppDesignTokens.Dark.Glass else AppDesignTokens.Light.Glass
    val line = if (dark) AppDesignTokens.Dark.Line else AppDesignTokens.Light.Line
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(glass)
            .drawBehind {
                drawLine(
                    color = line,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        val slotWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = slotWidth * selectedIndex + (slotWidth - 16.dp) / 2,
            animationSpec = tween(AppDesignTokens.Motion.StandardMs, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
            label = "tab-indicator"
        )
        Row(
            Modifier.fillMaxWidth().height(AppDesignTokens.Control.TabHeight.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                Column(
                    modifier = Modifier.weight(1f).height(AppDesignTokens.Control.TabHeight.dp)
                        .appPressFeedback(
                            onClick = { onSelect(index) },
                            scale = AppDesignTokens.Motion.PressButtonScale,
                            role = Role.Tab
                        )
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) colors.primary else colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
        Box(
            Modifier.offset(x = indicatorOffset, y = 52.dp)
                .size(width = 16.dp, height = 4.dp)
                .background(colors.primary, RoundedCornerShape(999.dp))
        )
    }
}
