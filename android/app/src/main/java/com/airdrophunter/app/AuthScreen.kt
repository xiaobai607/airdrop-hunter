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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.AuthRepository
import com.airdrophunter.app.data.AuthRequest
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun AuthScreen(onGuestMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as AirdropHunterApplication
    val authRepository = remember(application) { application.appContainer.authRepository }
    val requestInFlight = remember { AtomicBoolean(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val validUsername = username.matches(Regex("^[A-Za-z0-9]{4,20}$"))
    val validPassword = password.length in 8..30

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
            Text(stringResource(R.string.auth_screen_welcome),
                style = MaterialTheme.typography.headlineLarge, color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.auth_screen_subtitle),
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
                        label = { Text(stringResource(R.string.auth_screen_username)) },
                        placeholder = { Text(stringResource(R.string.auth_screen_username_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        isError = username.isNotEmpty() && !validUsername,
                        supportingText = {
                            if (username.isNotEmpty() && !validUsername) Text(stringResource(R.string.auth_screen_username_invalid))
                        },
                        colors = authColors()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_screen_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = password.isNotEmpty() && !validPassword,
                        supportingText = {
                            Text(if (password.isNotEmpty() && !validPassword) stringResource(R.string.auth_screen_password_length) else stringResource(R.string.auth_screen_password_length_hint))
                        },
                        colors = authColors()
                    )
                    error?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            if (!requestInFlight.compareAndSet(false, true)) return@Button
                            error = null
                            loading = true
                            scope.launch {
                                try {
                                    val request = AuthRequest(username, password)
                                    val response = withContext(Dispatchers.IO) {
                                        authRepository.authenticate(request, false)
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
                                                e.code() == 429 -> context.getString(R.string.auth_screen_too_many_requests)
                                                e.code() == 400 && (detail.contains("已存在") || detail.contains("已被占用")) ->
                                                    context.getString(R.string.auth_screen_username_exists)
                                                e.code() == 400 && detail.isNotBlank() -> detail
                                                e.code() == 401 -> context.getString(R.string.auth_screen_login_failed)
                                                else -> context.getString(R.string.auth_screen_request_failed)
                                            }
                                        }
                                        is IOException -> context.getString(R.string.auth_screen_server_waking)
                                        else -> context.getString(R.string.auth_screen_network_failed)
                                    }
                                } finally {
                                    loading = false
                                    requestInFlight.set(false)
                                }
                            }
                        },
                        enabled = !loading && !requestInFlight.get() && validUsername && validPassword,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary, contentColor = AppColors.Background
                        )
                    ) {
                        Text(if (loading) stringResource(R.string.auth_screen_wait) else stringResource(R.string.auth_screen_login),
                            fontWeight = FontWeight.Bold)
                    }
                    Text(
                        context.getString(R.string.auth_screen_register_unavailable),
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = onGuestMode,
                enabled = !loading && !requestInFlight.get(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppColors.Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
            ) {
                Text(stringResource(R.string.auth_screen_guest), fontWeight = FontWeight.SemiBold)
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
