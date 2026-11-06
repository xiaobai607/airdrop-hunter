package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.AuthRequest
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

@Composable
fun AuthScreen(onGuestMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AppColors.Background, AppColors.BackgroundDeep))
        ),
        color = Color.Transparent
    ) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("AIRDROP HUNTER", color = AppColors.Primary, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Text(if (register) "创建账号" else "欢迎回来",
                style = MaterialTheme.typography.headlineLarge, color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold)
            Text(if (register) "设置账号和密码，立即开始使用" else "使用账号和密码登录",
                Modifier.padding(top = 8.dp, bottom = 24.dp), color = AppColors.TextSecondary)
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("账号") },
                        placeholder = { Text("例如 xiaobai001") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        colors = authColors()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { if (register) Text("密码至少 8 位") },
                        colors = authColors()
                    )
                    error?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            error = null
                            loading = true
                            scope.launch {
                                try {
                                    val request = AuthRequest(username.trim(), password)
                                    val response = withContext(Dispatchers.IO) {
                                        if (register) ApiClient.service.register(request)
                                        else ApiClient.service.login(request)
                                    }
                                    TokenStore.saveToken(context, response.accessToken)
                                } catch (e: Exception) {
                                    error = when (e) {
                                        is HttpException -> {
                                            val detail = try {
                                                JSONObject(e.response()?.errorBody()?.string().orEmpty())
                                                    .optString("detail")
                                            } catch (_: Exception) { "" }
                                            when {
                                                detail == "该账号已被占用" -> detail
                                                detail.isNotBlank() -> detail
                                                e.code() == 401 -> "账号或密码错误"
                                                else -> "请求失败，请检查网络后重试"
                                            }
                                        }
                                        else -> "网络连接失败，请稍后重试"
                                    }
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && username.trim().length >= 3 &&
                            username.matches(Regex("^[A-Za-z0-9_]+$")) && password.length >= 8,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary, contentColor = AppColors.Background
                        )
                    ) {
                        Text(if (loading) "请稍候…" else if (register) "注册并登录" else "登录",
                            fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { register = !register; error = null },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (register) "已有账号？返回登录" else "没有账号？立即注册",
                            color = AppColors.Primary)
                    }
                }
            }
            if (!register) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onGuestMode,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.Primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Text("游客浏览", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun authColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary,
    focusedContainerColor = AppColors.Surface, unfocusedContainerColor = AppColors.Surface,
    focusedBorderColor = AppColors.Primary, unfocusedBorderColor = AppColors.Border,
    focusedLabelColor = AppColors.Primary, unfocusedLabelColor = AppColors.TextSecondary,
    cursorColor = AppColors.Primary
)
