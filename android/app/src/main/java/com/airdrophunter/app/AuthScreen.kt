package com.airdrophunter.app
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import retrofit2.HttpException

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("AIRDROP HUNTER", color = AppColors.Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(if (register) "创建账户" else "欢迎回来", style = MaterialTheme.typography.headlineLarge, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
        Text("登录后同步并安全隔离你的项目数据", Modifier.padding(top = 6.dp, bottom = 22.dp), color = AppColors.TextSecondary)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (register) OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("昵称（可选）") }, singleLine = true, colors = authColors())
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = authColors())
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码（至少 8 位）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authColors())
                if (error != null) Text(error.orEmpty(), color = AppColors.Danger)
                Button(onClick = {
                    error = null; loading = true
                    scope.launch {
                        try {
                            val req = AuthRequest(email.trim(), password, nickname.trim().ifBlank { null })
                            val response = withContext(Dispatchers.IO) {
                                if (register) ApiClient.service.register(req) else ApiClient.service.login(req)
                            }
                            TokenStore.saveToken(context, response.accessToken)
                        } catch (e: Exception) {
                            error = if (e is HttpException) when (e.code()) {
                                401 -> "邮箱或密码错误"; 409 -> "该邮箱已注册，请切换到登录"
                                422 -> "请检查邮箱格式和密码长度"; else -> "请求失败（HTTP ${e.code()}）"
                            } else e.message ?: "网络连接失败，请稍后重试"
                        } finally { loading = false }
                    }
                }, enabled = !loading && email.isNotBlank() && password.length >= 8, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.Background)) {
                    Text(if (loading) "请稍候…" else if (register) "注册并登录" else "登录", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { register = !register; error = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (register) "已有账户？返回登录" else "还没有账户？立即注册", color = AppColors.Primary)
                }
            }
        }
    }
}
@Composable
private fun authColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary,
    focusedContainerColor = AppColors.Background, unfocusedContainerColor = AppColors.Background,
    focusedBorderColor = AppColors.Primary, unfocusedBorderColor = AppColors.Border,
    focusedLabelColor = AppColors.Primary, unfocusedLabelColor = AppColors.TextSecondary,
    cursorColor = AppColors.Primary
)
