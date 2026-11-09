package com.airdrophunter.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.airdrophunter.app.AirdropHunterApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Refreshes read-only activity for saved public addresses.
 * Results stay in account/guest-scoped local cache; this worker never creates
 * accounting entries or sends wallet/transaction details to community statistics.
 */
class WalletActivitySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ApiClient.initialize(applicationContext)
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()
        val guest = TokenStore.readTokenBlocking().isNullOrBlank()

        val wallets = try {
            app.appContainer.walletRepository.load(guest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        var hadFailure = false
        for (wallet in wallets) {
            try {
                app.appContainer.walletActivityRepository.refresh(wallet, guest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                hadFailure = true
            }
        }
        if (hadFailure) Result.retry() else Result.success()
    }
}

object WalletActivitySyncScheduler {
    private const val PERIODIC_NAME = "wallet-activity-periodic-refresh"

    fun ensurePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WalletActivitySyncWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
