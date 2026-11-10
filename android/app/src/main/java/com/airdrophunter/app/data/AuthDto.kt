package com.airdrophunter.app.data

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    val username: String,
    val uid: String
)

data class UserProfileDto(
    @SerializedName("user_id") val userId: String,
    val username: String,
    val uid: String
)
