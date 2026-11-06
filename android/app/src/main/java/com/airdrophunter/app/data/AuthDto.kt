package com.airdrophunter.app.data
import com.google.gson.annotations.SerializedName
data class AuthRequest(val email: String, val password: String, @SerializedName("display_name") val displayName: String? = null)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    val email: String,
    @SerializedName("display_name") val displayName: String?
)
