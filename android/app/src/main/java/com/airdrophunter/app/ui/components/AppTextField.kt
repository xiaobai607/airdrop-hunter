package com.airdrophunter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppDesignTokens

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingClear: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)
    val borderColor = when {
        isError -> colors.error
        focused -> colors.primary
        else -> Color.Transparent
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDesignTokens.Control.InputHeight.dp)
            .background(colors.surfaceVariant, shape)
            .then(if (borderColor != Color.Transparent) Modifier.border(if (focused) 1.5.dp else 1.dp, borderColor, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 22.sp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 15.sp)
                    inner()
                }
                if (trailingClear && value.isNotEmpty() && enabled) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "清除输入", tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    )
}
