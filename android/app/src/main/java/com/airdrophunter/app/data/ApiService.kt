package com.airdrophunter.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/send-code") suspend fun sendVerificationCode(@Body request: SendCodeRequest): SendCodeResponse
    @POST("api/auth/register") suspend fun register(@Body request: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body request: AuthRequest): AuthResponse
    @GET("api/sources") suspend fun getSources(): List<SourceDto>
    @GET("api/airdrops") suspend fun getAirdrops(): List<AirdropDto>
    @GET("api/airdrops/hub") suspend fun getAirdropHub(@Query("chain") chain: String? = null): List<AirdropHubDto>
    @GET("api/airdrops/hub/status") suspend fun getAirdropHubStatus(): AirdropHubSyncStatusDto
    @POST("api/airdrops/sync") suspend fun syncAirdrops(): AirdropHubRefreshResponse
    @POST("api/airdrops/hub/refresh") suspend fun refreshAirdropHub(): AirdropHubRefreshResponse
    @POST("api/airdrops") suspend fun createAirdrop(@Body request: AirdropWriteRequest): AirdropDto
    @GET("api/projects") suspend fun getProjects(): List<ProjectDto>
    @GET("api/projects/{projectId}") suspend fun getProject(@Path("projectId") projectId: String): ProjectDto
    @POST("api/projects") suspend fun createProject(@Body request: ProjectWriteRequest): ProjectDto
    @PUT("api/projects/{projectId}") suspend fun updateProject(@Path("projectId") projectId: String, @Body request: ProjectWriteRequest): ProjectDto
    @DELETE("api/projects/{projectId}") suspend fun deleteProject(@Path("projectId") projectId: String)
    @GET("api/projects/{projectId}/tasks") suspend fun getTasks(@Path("projectId") projectId: String): List<TaskDto>
    @POST("api/projects/{projectId}/tasks") suspend fun createTask(@Path("projectId") projectId: String, @Body request: TaskCreateRequest): TaskDto
    @PATCH("api/projects/{projectId}/tasks/{taskId}") suspend fun updateTask(@Path("projectId") projectId: String, @Path("taskId") taskId: String, @Body request: TaskUpdateRequest): TaskDto
    @DELETE("api/projects/{projectId}/tasks/{taskId}") suspend fun deleteTask(@Path("projectId") projectId: String, @Path("taskId") taskId: String)
    @GET("api/market/prices") suspend fun getMarketPrices(): MarketResponse
    @GET("api/coins") suspend fun getCoins(): List<CoinDto>
    @POST("api/coins") suspend fun addCoin(@Body request: CoinCreateRequest): CoinDto
    @DELETE("api/coins/{coinId}") suspend fun removeCoin(@Path("coinId") coinId: String)
    @GET("api/wallet/check") suspend fun checkWallet(@Query("address") address: String): WalletCheckResponse
    @GET("api/wallet/sync") suspend fun syncWallet(@Query("public_key") publicKey: String): WalletSyncResponse
    @GET("api/wallet") suspend fun getWallets(): List<WalletDto>
    @GET("api/stats") suspend fun getStats(): StatsDto
    @GET("api/user/stats") suspend fun getUserStats(): UserStatsDto
}
