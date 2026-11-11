package com.airdrophunter.app.data

import kotlinx.coroutines.CancellationException

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

data class AirdropHubCacheEnvelope(
    val projects: List<AirdropHubDto> = emptyList(),
    val fetchedAtEpochMillis: Long = 0L,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastError: String? = null
)

interface AirdropHubCache {
    suspend fun read(key: String): AirdropHubCacheEnvelope?
    suspend fun write(key: String, value: AirdropHubCacheEnvelope)
}

class RoomAirdropHubCache(
    private val gson: com.google.gson.Gson = com.google.gson.Gson()
) : AirdropHubCache {
    override suspend fun read(key: String): AirdropHubCacheEnvelope? {
        val raw = CacheStore.readPublic(key) ?: return null
        return runCatching { gson.fromJson(raw, AirdropHubCacheEnvelope::class.java) }.getOrNull()
    }

    override suspend fun write(key: String, value: AirdropHubCacheEnvelope) {
        CacheStore.writePublic(key, gson.toJson(value))
    }
}

data class AirdropHubSnapshot(
    val projects: List<AirdropHubDto>,
    val sourceError: String?,
    val refreshDue: Boolean
)

class AirdropHubRepository(
    private val remote: AirdropHubRemoteDataSource,
    private val cache: AirdropHubCache = RoomAirdropHubCache(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun load(chain: String?): AirdropHubSnapshot {
        val key = cacheKey(chain)
        val now = clock()
        val cached = cache.read(key)
        if (cached != null) {
            val age = (now - cached.fetchedAtEpochMillis).coerceAtLeast(0L)
            val retryCoolingDown = cached.lastAttemptAtEpochMillis?.let {
                now >= it && now - it < RETRY_COOLDOWN_MILLIS
            } == true
            return AirdropHubSnapshot(
                projects = cached.projects,
                sourceError = cached.lastError,
                refreshDue = age >= AUTO_REFRESH_INTERVAL_MILLIS && !retryCoolingDown
            )
        }

        // Cold start: fetch the server's last-known-good list first. Any needed
        // provider sync is scheduled by the ViewModel after this result is rendered.
        val projects = remote.getProjects(chain)
        val status = try {
            remote.getStatus()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val lastAttempt = parseTimestamp(status?.last_attempt_at)
        cache.write(
            key,
            AirdropHubCacheEnvelope(
                projects = projects,
                fetchedAtEpochMillis = if (projects.isEmpty() && lastAttempt == null) 0L else now,
                lastAttemptAtEpochMillis = lastAttempt,
                lastError = status?.error
            )
        )
        val refreshDue = lastAttempt == null ||
            (now >= lastAttempt && now - lastAttempt >= AUTO_REFRESH_INTERVAL_MILLIS)
        return AirdropHubSnapshot(projects, status?.error, refreshDue)
    }

    suspend fun refresh(chain: String?): List<AirdropHubDto> {
        val key = cacheKey(chain)
        val attemptedAt = clock()
        val previous = cache.read(key)
        cache.write(
            key,
            (previous ?: AirdropHubCacheEnvelope()).copy(lastAttemptAtEpochMillis = attemptedAt)
        )
        try {
            val response = remote.sync()
            if (!response.ok) throw IllegalStateException("空投数据源同步失败")
            val projects = remote.getProjects(chain)
            val status = try {
                remote.getStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            cache.write(
                key,
                AirdropHubCacheEnvelope(
                    projects = projects,
                    fetchedAtEpochMillis = clock(),
                    lastAttemptAtEpochMillis = attemptedAt,
                    lastError = status?.error
                )
            )
            return projects
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val retained = cache.read(key) ?: previous ?: AirdropHubCacheEnvelope()
            cache.write(
                key,
                retained.copy(
                    lastAttemptAtEpochMillis = attemptedAt,
                    lastError = "数据源暂时不可用，已保留上次数据"
                )
            )
            throw error
        }
    }

    private fun cacheKey(chain: String?): String =
        "airdrop-hub:${chain?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "all"}"

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
            }.getOrNull()
    }

    companion object {
        const val AUTO_REFRESH_INTERVAL_MILLIS = 8 * 60 * 60 * 1000L
        const val RETRY_COOLDOWN_MILLIS = 5 * 60 * 1000L
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

interface AuthRemoteDataSource {
    suspend fun register(request: AuthRequest): AuthResponse
    suspend fun login(request: AuthRequest): AuthResponse
}

class RetrofitAuthRemoteDataSource(
    private val api: ApiService
) : AuthRemoteDataSource {
    override suspend fun register(request: AuthRequest): AuthResponse = api.register(request)
    override suspend fun login(request: AuthRequest): AuthResponse = api.login(request)
}

class AuthRepository(
    private val remote: AuthRemoteDataSource
) {
    suspend fun authenticate(request: AuthRequest, register: Boolean): AuthResponse =
        if (register) remote.register(request) else remote.login(request)
}
