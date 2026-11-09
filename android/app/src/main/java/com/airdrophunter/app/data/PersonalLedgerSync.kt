package com.airdrophunter.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
        ApiClient.initialize(applicationContext)
        if (TokenStore.readTokenBlocking().isNullOrBlank()) return@withContext Result.success()
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()
        try {
            if (app.appContainer.accountingRepository.syncPersonalLedger()) {
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
    private const val ONE_TIME_NAME = "private-ledger-sync-retry"
    private const val PERIODIC_NAME = "private-ledger-periodic-sync"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PersonalLedgerSyncWorker>()
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<PersonalLedgerSyncWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
