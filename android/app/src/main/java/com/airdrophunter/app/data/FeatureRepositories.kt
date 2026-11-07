package com.airdrophunter.app.data

data class ProfileSnapshot(
    val profile: UserProfileDto,
    val stats: UserStatsDto
)

interface ProfileRemoteDataSource {
    suspend fun getProfile(): UserProfileDto
    suspend fun getStats(): UserStatsDto
}

class RetrofitProfileRemoteDataSource(
    private val api: ApiService
) : ProfileRemoteDataSource {
    override suspend fun getProfile(): UserProfileDto = api.getMe()
    override suspend fun getStats(): UserStatsDto = api.getUserStats()
}

class ProfileRepository(
    private val remote: ProfileRemoteDataSource
) {
    suspend fun load(): ProfileSnapshot = ProfileSnapshot(
        profile = remote.getProfile(),
        stats = remote.getStats()
    )
}

interface AirdropHubRemoteDataSource {
    suspend fun getProjects(chain: String?): List<AirdropHubDto>
    suspend fun getStatus(): AirdropHubSyncStatusDto
    suspend fun sync(): AirdropHubRefreshResponse
}

class RetrofitAirdropHubRemoteDataSource(
    private val api: ApiService
) : AirdropHubRemoteDataSource {
    override suspend fun getProjects(chain: String?): List<AirdropHubDto> = api.getAirdropHub(chain)
    override suspend fun getStatus(): AirdropHubSyncStatusDto = api.getAirdropHubStatus()
    override suspend fun sync(): AirdropHubRefreshResponse = api.syncAirdrops()
}

data class AirdropHubSnapshot(
    val projects: List<AirdropHubDto>,
    val sourceError: String?
)

class AirdropHubRepository(
    private val remote: AirdropHubRemoteDataSource
) {
    suspend fun load(chain: String?): AirdropHubSnapshot {
        var projects = remote.getProjects(chain)
        val status = runCatching { remote.getStatus() }.getOrNull()
        if (projects.isEmpty() && status?.last_attempt_at == null) {
            remote.sync()
            projects = remote.getProjects(chain)
        }
        return AirdropHubSnapshot(projects, status?.error)
    }

    suspend fun refresh(chain: String?): List<AirdropHubDto> {
        remote.sync()
        return remote.getProjects(chain)
    }
}

interface AirdropsRemoteDataSource {
    suspend fun getAirdrops(): List<AirdropDto>
    suspend fun createAirdrop(request: AirdropWriteRequest): AirdropDto
}

class RetrofitAirdropsRemoteDataSource(
    private val api: ApiService
) : AirdropsRemoteDataSource {
    override suspend fun getAirdrops(): List<AirdropDto> = api.getAirdrops()
    override suspend fun createAirdrop(request: AirdropWriteRequest): AirdropDto =
        api.createAirdrop(request)
}

class AirdropsRepository(
    private val remote: AirdropsRemoteDataSource
) {
    suspend fun load(): List<AirdropDto> = remote.getAirdrops()

    suspend fun create(request: AirdropWriteRequest): List<AirdropDto> {
        remote.createAirdrop(request)
        return remote.getAirdrops()
    }
}
