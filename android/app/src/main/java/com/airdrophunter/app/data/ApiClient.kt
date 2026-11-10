package com.airdrophunter.app.data

import android.content.Context
import com.airdrophunter.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun initialize(context: Context) {
        TokenStore.initialize(context)
        CacheStore.initialize(context)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = TokenStore.readTokenBlocking()
                val authMode = original.header("X-Airdrop-Auth-Mode")
                val request = if (!CurrentReleaseDataPolicy.PRIVATE_PERSONAL_CLOUD_SYNC_ENABLED) {
                    // The token may remain only as a legacy local Room namespace. Never send it
                    // or any explicit account header to the server in the local-only release.
                    original.newBuilder()
                        .removeHeader("X-Airdrop-Auth-Mode")
                        .removeHeader("Authorization")
                        .build()
                } else when (authMode) {
                    // Internal marker prevents a guest request from inheriting a later account token.
                    "anonymous" -> original.newBuilder()
                        .removeHeader("X-Airdrop-Auth-Mode")
                        .removeHeader("Authorization")
                        .build()
                    "captured" -> original.newBuilder()
                        .removeHeader("X-Airdrop-Auth-Mode")
                        .build()
                    else -> if (original.header("Authorization") != null || token.isNullOrBlank()) original
                        else original.newBuilder().header("Authorization", "Bearer $token").build()
                }
                chain.proceed(request)
            }
            .connectTimeout(70, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(70, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val service: ApiService by lazy {
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
