package com.airdrophunter.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import org.json.JSONObject
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
        val anonymousContributorId = CacheStore.getOrCreateAnonymousContributorId()
        for (batch in batches) {
            for (entry in batch.entries) {
                val network = entry.network?.takeIf { it.isNotBlank() }
                val walletAddress = entry.walletAddress?.takeIf { it.isNotBlank() }
                val txHash = entry.txHash?.takeIf { it.isNotBlank() }
                if (network == null || (txHash != null && walletAddress == null)) {
                    CacheStore.markCommunitySyncTerminalFailure(
                        entry.id, batch.accountScope, "缺少网络或链上核验钱包地址，未发送公共统计"
                    )
                    continue
                }
                try {
                    val response = ApiClient.service.addCommunityContribution(
                        entry.projectOfficialId,
                        CommunityContributionRequest(
                            event_id = entry.id,
                            network = network,
                            wallet_address = walletAddress.takeIf { txHash != null },
                            tx_hash = txHash,
                            public_contribution_opt_in = true,
                            gas_cost_usd = entry.gasCostUsd,
                            actual_reward_usd = entry.actualRewardUsd,
                            estimated_reward_usd = entry.expectedRewardUsd,
                            actual_reward_confirmed = entry.actualRewardConfirmed,
                            contributor_id = anonymousContributorId,
                            asset_breakdown = LedgerAssetBreakdownCodec.decode(entry.assetBreakdownJson)
                        ),
                        batch.authorizationHeader
                    )
                    CacheStore.completeCommunitySync(
                        entry.id, batch.accountScope, response.verification_tier ?: if (txHash == null) "user_reported" else "transaction_linked"
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: HttpException) {
                    if (isTerminalContributionConflict(error.code())) {
                        val detail = runCatching {
                            JSONObject(error.response()?.errorBody()?.string().orEmpty()).optString("detail")
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        CacheStore.markCommunitySyncTerminalFailure(
                            entry.id, batch.accountScope,
                            detail ?: "HTTP ${error.code()}: 链上核验拒绝或交易重复"
                        )
                        continue
                    }
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

/** Definitive chain rejection and replay conflicts are terminal; provider failures remain retryable. */
internal fun isTerminalContributionConflict(statusCode: Int): Boolean = statusCode == 409 || statusCode == 422
