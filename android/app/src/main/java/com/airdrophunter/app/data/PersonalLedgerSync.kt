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
 * Retries account-private ledger restoration/upload while authenticated.
 * Guest ledgers are local-only and are deliberately never sent to this worker.
 */
class PersonalLedgerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (TokenStore.readTokenBlocking().isNullOrBlank()) return@withContext Result.success()
        if (!SyncCadence.isDue(applicationContext, SyncCadence.PERSONAL_LEDGER_KEY, 8, TimeUnit.HOURS)) {
            return@withContext Result.success()
        }
        ApiClient.initialize(applicationContext)
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()
        try {
            if (app.appContainer.accountingRepository.syncPersonalLedger()) {
                SyncCadence.markSuccess(applicationContext, SyncCadence.PERSONAL_LEDGER_KEY)
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
    fun enqueue(context: Context) = GlobalSyncScheduler.enqueueNow(context)
    fun ensurePeriodic(context: Context) = GlobalSyncScheduler.ensurePeriodic(context)
}
