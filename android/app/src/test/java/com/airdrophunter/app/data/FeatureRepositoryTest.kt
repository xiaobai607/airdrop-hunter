package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRepositoryTest {
    @Test
    fun authRepositorySelectsRegisterOrLoginOperation() = runBlocking {
        val remote = FakeAuthRemote()
        val repository = AuthRepository(remote)
        repository.authenticate(AuthRequest("tester", "password123"), register = true)
        repository.authenticate(AuthRequest("tester", "password123"), register = false)
        assertEquals(1, remote.registerCalls)
        assertEquals(1, remote.loginCalls)
    }

    @Test
    fun profileRepositoryLoadsProfileAndStatsTogether() = runBlocking {
        val remote = FakeProfileRemote()
        val snapshot = ProfileRepository(remote).load()
        assertEquals("tester", snapshot.profile.username)
        assertEquals(7, snapshot.stats.tracked_projects)
    }

    @Test
    fun hubRepositoryLoadsPublicSourceHealth() = runBlocking {
        val expected = listOf(
            AirdropHubSourceStatusDto(
                source_name = "binance-announcements",
                label = "Binance 官方公告",
                enabled = true,
                status = "success",
                last_item_count = 12
            )
        )
        val remote = FakeHubRemote().apply { sourceStatuses = expected }
        assertEquals(expected, AirdropHubRepository(remote).loadSourceStatuses())
    }

    @Test
    fun hubRepositorySchedulesInitialSyncAfterReturningServerList() = runBlocking {
        val remote = FakeHubRemote()
        val repository = AirdropHubRepository(
            remote, FakeHubCache(), clock = { NOW }
        )
        val snapshot = repository.load(null)
        assertEquals(0, remote.syncCalls)
        assertTrue(snapshot.refreshDue)
        assertTrue(snapshot.projects.isEmpty())
        assertEquals(1, repository.refresh(null).size)
        assertEquals(1, remote.syncCalls)
    }

    @Test
    fun hubRepositorySchedulesRefreshWhenLastAttemptIsOlderThanEightHours() = runBlocking {
        val remote = FakeHubRemote().apply {
            status = AirdropHubSyncStatusDto(last_attempt_at = "2026-01-01T00:00:00Z")
        }
        val repository = AirdropHubRepository(remote, FakeHubCache(), clock = { NOW })
        val snapshot = repository.load("Base")
        assertEquals(0, remote.syncCalls)
        assertTrue(snapshot.refreshDue)
        assertEquals(1, repository.refresh("Base").size)
        assertEquals("Base", remote.lastChain)
    }

    @Test
    fun hubRepositoryDoesNotRefreshWhenServerAttemptIsFresh() = runBlocking {
        val remote = FakeHubRemote().apply {
            status = AirdropHubSyncStatusDto(
                last_attempt_at = "2026-11-11T10:00:00Z",
                last_success_at = "2026-11-11T10:00:00Z"
            )
        }
        val repository = AirdropHubRepository(remote, FakeHubCache(), clock = { NOW })
        val snapshot = repository.load(null)
        assertEquals(0, remote.syncCalls)
        assertTrue(!snapshot.refreshDue)
        assertEquals(1, snapshot.projects.size)
    }

    @Test
    fun hubRepositoryReturnsStaleCacheImmediatelyAndMarksBackgroundRefreshDue() = runBlocking {
        val remote = FakeHubRemote()
        val cache = FakeHubCache().apply {
            values["airdrop-hub:all"] = AirdropHubCacheEnvelope(
                projects = listOf(AirdropHubDto("cached", "Cached", "Base", "", "active", "https://example.org")),
                fetchedAtEpochMillis = NOW - AirdropHubRepository.AUTO_REFRESH_INTERVAL_MILLIS - 1,
                lastAttemptAtEpochMillis = NOW - AirdropHubRepository.AUTO_REFRESH_INTERVAL_MILLIS - 1
            )
        }
        val snapshot = AirdropHubRepository(remote, cache, clock = { NOW }).load(null)
        assertEquals("cached", snapshot.projects.single().id)
        assertTrue(snapshot.refreshDue)
        assertEquals(0, remote.syncCalls)
    }

    @Test
    fun hubRepositoryHonorsRetryCooldownAfterFailedRefresh() = runBlocking {
        val remote = FakeHubRemote()
        val cache = FakeHubCache().apply {
            values["airdrop-hub:all"] = AirdropHubCacheEnvelope(
                projects = emptyList(),
                fetchedAtEpochMillis = 0L,
                lastAttemptAtEpochMillis = NOW - 60_000L
            )
        }
        val snapshot = AirdropHubRepository(remote, cache, clock = { NOW }).load(null)
        assertTrue(!snapshot.refreshDue)
        assertEquals(0, remote.syncCalls)
    }

    @Test
    fun airdropsRepositoryRefreshesAfterCreate() = runBlocking {
        val remote = FakeAirdropsRemote()
        val result = AirdropsRepository(remote).create(
            AirdropWriteRequest(
                name = "Example",
                description = "",
                category = "other",
                chain = "Base",
                status = "unknown",
                reward_details = "",
                eligibility = "",
                official_url = "",
                source_name = "manual",
                source_url = "",
                source_type = "manual",
                verification_status = "unverified"
            )
        )
        assertTrue(remote.created)
        assertEquals(1, result.size)
    }
}

private const val NOW = 1_794_398_400_000L

private class FakeHubCache : AirdropHubCache {
    val values = mutableMapOf<String, AirdropHubCacheEnvelope>()
    override suspend fun read(key: String): AirdropHubCacheEnvelope? = values[key]
    override suspend fun write(key: String, value: AirdropHubCacheEnvelope) { values[key] = value }
}

private class FakeProfileRemote : ProfileRemoteDataSource {
    override suspend fun getProfile() = UserProfileDto("user-1", "tester", "123456")
    override suspend fun getStats() = UserStatsDto(tracked_projects = 7)
}

private class FakeHubRemote : AirdropHubRemoteDataSource {
    var syncCalls = 0
    var lastChain: String? = null
    var status = AirdropHubSyncStatusDto()
    var sourceStatuses = emptyList<AirdropHubSourceStatusDto>()
    private val items = listOf(AirdropHubDto("1", "Example", "Base", "desc", "active", "https://example.org"))
    override suspend fun getProjects(chain: String?): List<AirdropHubDto> {
        lastChain = chain
        if (syncCalls == 0 && status.last_success_at == null) return emptyList()
        return if (chain == null) items else items.filter { it.chain == chain }
    }
    override suspend fun getStatus() = status
    override suspend fun getSourceStatuses() = sourceStatuses
    override suspend fun sync(): AirdropHubRefreshResponse {
        syncCalls++
        return AirdropHubRefreshResponse(true, items.size, status)
    }
}

private class FakeAirdropsRemote : AirdropsRemoteDataSource {
    var created = false
    private val item = AirdropDto(
        id = "1", name = "Example", description = "", category = "other", chain = "Base",
        status = "unknown", reward_details = "", eligibility = "", official_url = "",
        source_name = "manual", source_url = "", source_type = "manual",
        verification_status = "unverified", starts_at = null, ends_at = null,
        last_checked_at = null, created_at = "2026-01-01T00:00:00Z", updated_at = "2026-01-01T00:00:00Z"
    )
    override suspend fun getAirdrops(): List<AirdropDto> = if (created) listOf(item) else emptyList()
    override suspend fun createAirdrop(request: AirdropWriteRequest): AirdropDto {
        created = true
        return item
    }
}

private class FakeAuthRemote : AuthRemoteDataSource {
    var registerCalls = 0
    var loginCalls = 0
    private val response = AuthResponse("token", "bearer", "user-1", "tester", "123456")
    override suspend fun register(request: AuthRequest): AuthResponse {
        registerCalls++
        return response
    }
    override suspend fun login(request: AuthRequest): AuthResponse {
        loginCalls++
        return response
    }
}
