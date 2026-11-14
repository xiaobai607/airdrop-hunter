package com.airdrophunter.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.airdrophunter.app.AirdropHunterApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Retries private ledger restoration/upload. The current release policy exits
 * before either authenticated or guest records are uploaded.
 *
 * Product requirement: guest personal ledgers must also be recoverable from
 * cloud storage. Do not describe guest cloud backup as implemented, and do not
 * simply remove the policy guard: the backend currently has only user-owned
 * ledger rows. Guest sync needs a separate, unguessable, revocable guest scope.
 */
class PersonalLedgerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!CurrentReleaseDataPolicy.PRIVATE_PERSONAL_CLOUD_SYNC_ENABLED) return@withContext Result.success()
        val accessToken = TokenStore.readTokenBlocking()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.success()
        val cadenceKey = SyncCadence.scopedKey(SyncCadence.PERSONAL_LEDGER_KEY, accessToken)
        if (!inputData.getBoolean(GlobalSyncScheduler.FORCE_KEY, false) &&
            !SyncCadence.isDue(applicationContext, cadenceKey, 8, TimeUnit.HOURS)) {
            return@withContext Result.success()
        }
        ApiClient.initialize(applicationContext)
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()
        try {
            if (app.appContainer.accountingRepository.syncPersonalLedger()) {
                SyncCadence.markSuccess(applicationContext, cadenceKey)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object PersonalLedgerSyncScheduler {
    // Retained for compatibility with older call sites; current release is local-only.
    fun enqueue(context: Context) = Unit
    fun ensurePeriodic(context: Context) = Unit
}
