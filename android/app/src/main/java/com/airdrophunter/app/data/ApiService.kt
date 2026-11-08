package com.airdrophunter.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

interface ApiService {
    @POST("api/auth/register") suspend fun register(@Body request: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body request: AuthRequest): AuthResponse
    @GET("api/auth/me") suspend fun getMe(): UserProfileDto
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
    @GET("api/projects/{projectId}/claim-url") suspend fun getClaimUrl(@Path("projectId") projectId: String, @Query("wallet_id") walletId: String): ClaimUrlResponse
    @GET("api/projects/{projectId}/tasks") suspend fun getTasks(@Path("projectId") projectId: String): List<TaskDto>
    @POST("api/projects/{projectId}/tasks") suspend fun createTask(@Path("projectId") projectId: String, @Body request: TaskCreateRequest): TaskDto
    @PATCH("api/projects/{projectId}/tasks/{taskId}") suspend fun updateTask(@Path("projectId") projectId: String, @Path("taskId") taskId: String, @Body request: TaskUpdateRequest): TaskDto
    @DELETE("api/projects/{projectId}/tasks/{taskId}") suspend fun deleteTask(@Path("projectId") projectId: String, @Path("taskId") taskId: String)
    @GET("api/market/prices") suspend fun getMarketPrices(): MarketResponse
    @GET("api/coins") suspend fun getCoins(): List<CoinDto>
    @POST("api/coins") suspend fun addCoin(@Body request: CoinCreateRequest): CoinDto
    @DELETE("api/coins/{coinId}") suspend fun removeCoin(@Path("coinId") coinId: String)
    @GET("api/wallet/check") suspend fun checkWallet(@Query("address") address: String): WalletCheckResponse
    @GET("api/wallets") suspend fun getWallets(): List<WalletDto>
    @POST("api/wallets") suspend fun addWallet(@Body request: WalletWriteRequest): WalletDto
    @DELETE("api/wallets/{walletId}") suspend fun deleteWallet(@Path("walletId") walletId: String)
    @GET("api/stats") suspend fun getStats(): StatsDto
    @GET("api/user/stats") suspend fun getUserStats(): UserStatsDto
    @GET("api/personal-ledger") suspend fun getPersonalLedger(@Query("project_id") projectId: String? = null, @Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0): List<PersonalLedgerEntryDto>
    @PUT("api/personal-ledger/{clientRecordId}") suspend fun upsertPersonalLedger(@Path("clientRecordId") clientRecordId: String, @Body request: PersonalLedgerWriteRequest): PersonalLedgerEntryDto
    @DELETE("api/personal-ledger/{clientRecordId}") suspend fun deletePersonalLedger(@Path("clientRecordId") clientRecordId: String)
    @POST("api/community/projects/{projectOfficialId}/contributions")
    suspend fun addCommunityContribution(
        @Path("projectOfficialId") projectOfficialId: String,
        @Body request: CommunityContributionRequest,
        @Header("Authorization") authorizationHeader: String? = null
    ): CommunityProjectStatsDto
    @GET("api/community/projects/{projectOfficialId}/stats")
    suspend fun getCommunityProjectStats(@Path("projectOfficialId") projectOfficialId: String): CommunityProjectStatsDto
}
