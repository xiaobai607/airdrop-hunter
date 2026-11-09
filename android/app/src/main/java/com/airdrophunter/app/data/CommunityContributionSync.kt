package com.airdrophunter.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class CommunityContributionSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ApiClient.initialize(applicationContext)
        val batches = CacheStore.getPendingCommunitySyncBatches()
        for (batch in batches) {
            for (entry in batch.entries) {
                try {
                    ApiClient.service.addCommunityContribution(
                        entry.projectOfficialId,
                        CommunityContributionRequest(
                            event_id = entry.id,
                            gas_cost_usd = entry.gasCostUsd,
                            actual_reward_usd = entry.actualRewardUsd
                        ),
                        batch.authorizationHeader
                    )
                    CacheStore.completeCommunitySync(entry.id, batch.accountScope)
                } catch (error: HttpException) {
                    CacheStore.markCommunitySyncAttempt(entry.id, batch.accountScope, "HTTP ${error.code()}")
                    return@withContext Result.retry()
                } catch (error: IOException) {
                    CacheStore.markCommunitySyncAttempt(entry.id, batch.accountScope, error.message ?: "Network unavailable")
                    return@withContext Result.retry()
                } catch (error: Exception) {
                    CacheStore.markCommunitySyncAttempt(entry.id, batch.accountScope, error.message ?: "Unknown sync error")
                    return@withContext Result.retry()
                }
            }
        }
        Result.success()
    }
}

object CommunityContributionSyncScheduler {
    private const val ONE_TIME_NAME = "community-contribution-sync"
    private const val PERIODIC_NAME = "community-contribution-periodic-sync"
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<CommunityContributionSyncWorker>()
            .setConstraints(constraints()).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
    }
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CommunityContributionSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints()).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
