package com.airdrophunter.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class CommunityContributionSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!inputData.getBoolean(GlobalSyncScheduler.FORCE_KEY, false) &&
            !SyncCadence.isDue(applicationContext, SyncCadence.COMMUNITY_KEY, 15, TimeUnit.MINUTES)) {
            return@withContext Result.success()
        }
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
                            actual_reward_usd = entry.actualRewardUsd,
                            asset_breakdown = LedgerAssetBreakdownCodec.decode(entry.assetBreakdownJson)
                        ),
                        batch.authorizationHeader
                    )
                    CacheStore.completeCommunitySync(entry.id, batch.accountScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
        SyncCadence.markSuccess(applicationContext, SyncCadence.COMMUNITY_KEY)
        Result.success()
    }
}

object CommunityContributionSyncScheduler {
    fun enqueue(context: Context) = GlobalSyncScheduler.enqueueNow(context, forceCommunityContribution = true)
    fun ensurePeriodic(context: Context) = GlobalSyncScheduler.ensurePeriodic(context)
}
