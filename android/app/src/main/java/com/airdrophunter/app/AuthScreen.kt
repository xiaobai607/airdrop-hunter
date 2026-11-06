package com.airdrophunter.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.AuthRequest
import com.airdrophunter.app.data.SendCodeRequest
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(onGuestMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val networkError = stringResource(R.string.network_error)
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var mockCode by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }
    var sendingCode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    Surface(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Background, AppColors.BackgroundDeep))), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("AIRDROP HUNTER", color = AppColors.Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(if (register) R.string.register_title else R.string.login_title),
                style = MaterialTheme.typography.headlineLarge, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.login_subtitle), Modifier.padding(top = 8.dp, bottom = 24.dp), color = AppColors.TextSecondary)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(email, { email = it; error = null; mockCode = null; verificationCode = ""; cooldown = 0 }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = authColors())
                    OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authColors())
                    if (register) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(verificationCode, { verificationCode = it.filter(Char::isDigit).take(6); error = null },
                                Modifier.weight(1f), label = { Text(stringResource(R.string.auth_verification_code)) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = authColors())
                            Button(onClick = {
                                error = null
                                sendingCode = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) { ApiClient.service.sendVerificationCode(SendCodeRequest(email.trim())) }
                                        mockCode = result.mockCode
                                        cooldown = result.retryAfterSeconds
                                    } catch (e: Exception) {
                                        error = e.message ?: networkError
                                    } finally {
                                        sendingCode = false
                                    }
                                }
                            }, enabled = !sendingCode && cooldown == 0 && email.contains("@"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.Background),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
                                Text(when {
                                    sendingCode -> stringResource(R.string.loading)
                                    cooldown > 0 -> "${cooldown}s"
                                    else -> stringResource(R.string.auth_get_code)
                                }, fontWeight = FontWeight.Bold)
                            }
                        }
                        mockCode?.let { Text(stringResource(R.string.auth_mock_code_notice, it), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall) }
                    }
                    error?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = {
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                val request = AuthRequest(email.trim(), password, verificationCode.takeIf { register })
                                val response = withContext(Dispatchers.IO) {
                                    if (register) ApiClient.service.register(request) else ApiClient.service.login(request)
                                }
                                TokenStore.saveToken(context, response.accessToken)
                            } catch (e: Exception) {
                                error = e.message ?: networkError
                            } finally {
                                loading = false
                            }
                        }
                    }, enabled = !loading && email.isNotBlank() && password.length >= 8 && (!register || verificationCode.length == 6),
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.Background)) {
                        Text(if (loading) stringResource(R.string.please_wait) else stringResource(if (register) R.string.register else R.string.login),
                            fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        register = !register
                        error = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (register) R.string.switch_login else R.string.switch_register), color = AppColors.Primary)
                    }
                }
            }
            if (!register) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onGuestMode, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)) {
                    Text(stringResource(R.string.auth_guest_mode), fontWeight = FontWeight.SemiBold)
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
