package com.airdrophunter.app.data

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val email: String,
    val password: String,
    @SerializedName("verification_code") val verificationCode: String? = null
)
data class SendCodeRequest(val email: String)
data class SendCodeResponse(
    @SerializedName("mock_code") val mockCode: String,
    @SerializedName("retry_after_seconds") val retryAfterSeconds: Int,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int
)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    val email: String,
    @SerializedName("display_name") val displayName: String?
)
