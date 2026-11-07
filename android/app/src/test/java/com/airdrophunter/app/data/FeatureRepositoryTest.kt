package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRepositoryTest {
    @Test
    fun profileRepositoryLoadsProfileAndStatsTogether() = runBlocking {
        val remote = FakeProfileRemote()
        val snapshot = ProfileRepository(remote).load()
        assertEquals("tester", snapshot.profile.username)
        assertEquals(7, snapshot.stats.tracked_projects)
    }

    @Test
    fun hubRepositoryTriggersInitialSyncOnlyWhenNoAttemptExists() = runBlocking {
        val remote = FakeHubRemote()
        val snapshot = AirdropHubRepository(remote).load(null)
        assertEquals(1, remote.syncCalls)
        assertEquals(1, snapshot.projects.size)
    }

    @Test
    fun hubRepositoryDoesNotRepeatInitialSyncAfterAnAttempt() = runBlocking {
        val remote = FakeHubRemote().apply {
            status = AirdropHubSyncStatusDto(last_attempt_at = "2026-01-01T00:00:00Z")
        }
        AirdropHubRepository(remote).load("Base")
        assertEquals(0, remote.syncCalls)
        assertEquals("Base", remote.lastChain)
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

private class FakeProfileRemote : ProfileRemoteDataSource {
    override suspend fun getProfile() = UserProfileDto("user-1", "tester", "123456")
    override suspend fun getStats() = UserStatsDto(tracked_projects = 7)
}

private class FakeHubRemote : AirdropHubRemoteDataSource {
    var syncCalls = 0
    var lastChain: String? = null
    var status = AirdropHubSyncStatusDto()
    private val items = listOf(AirdropHubDto("1", "Example", "Base", "desc", "active", "https://example.org"))
    override suspend fun getProjects(chain: String?): List<AirdropHubDto> {
        lastChain = chain
        return if (chain == null) items else items.filter { it.chain == chain }
    }
    override suspend fun getStatus() = status
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
