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
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Single entry point for app-wide background sync.
 *
 * A lightweight dispatcher runs every 15 minutes and enqueues one serialized
 * chain. Individual workers decide whether their own data source is due:
 * public contributions 15m; wallet activity/private ledger/favorites 8h.
 */
class GlobalSyncDispatcherWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        GlobalSyncScheduler.enqueueSyncChain(applicationContext)
        return Result.success()
    }
}

object GlobalSyncScheduler {
    private const val PERIODIC_NAME = "global-sync-dispatcher"
    private const val CHAIN_NAME = "global-sync-serialized-chain"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<GlobalSyncDispatcherWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal const val FORCE_KEY = "force_sync"

    fun enqueueNow(
        context: Context,
        forceCommunityContribution: Boolean = false,
        forceWalletActivity: Boolean = false,
        forcePersonalLedger: Boolean = false,
        forceFavorites: Boolean = false
    ) = enqueueSyncChain(context.applicationContext, forceCommunityContribution, forceWalletActivity, forcePersonalLedger, forceFavorites)

    internal fun enqueueSyncChain(
        context: Context,
        forceCommunityContribution: Boolean = false,
        forceWalletActivity: Boolean = false,
        forcePersonalLedger: Boolean = false,
        forceFavorites: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val network = constraints()
        val contribution = OneTimeWorkRequestBuilder<CommunityContributionSyncWorker>()
            .setInputData(workDataOf(FORCE_KEY to forceCommunityContribution))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val walletActivity = OneTimeWorkRequestBuilder<WalletActivitySyncWorker>()
            .setInputData(workDataOf(FORCE_KEY to forceWalletActivity))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val personalLedger = OneTimeWorkRequestBuilder<PersonalLedgerSyncWorker>()
            .setInputData(workDataOf(FORCE_KEY to forcePersonalLedger))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val favorites = OneTimeWorkRequestBuilder<CommunityFavoriteSyncWorker>()
            .setInputData(workDataOf(FORCE_KEY to forceFavorites))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // KEEP prevents startup, periodic, and event-driven triggers from creating
        // overlapping copies of the same multi-source sync pipeline.
        workManager.beginUniqueWork(CHAIN_NAME, ExistingWorkPolicy.KEEP, contribution)
            .then(walletActivity)
            .then(personalLedger)
            .then(favorites)
            .enqueue()
    }
}

internal object SyncCadence {
    const val COMMUNITY_KEY = "community_contribution_last_success"
    const val WALLET_ACTIVITY_KEY = "wallet_activity_last_success"
    const val PERSONAL_LEDGER_KEY = "personal_ledger_last_success"
    const val COMMUNITY_FAVORITES_KEY = "community_favorites_last_success"
    private const val PREFERENCES = "global_sync_cadence_v1"

    fun isDue(context: Context, key: String, interval: Long, unit: TimeUnit): Boolean {
        val last = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(key, 0L)
        return isSyncCadenceDue(last, System.currentTimeMillis(), unit.toMillis(interval))
    }

    fun markSuccess(context: Context, key: String) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, System.currentTimeMillis())
            .apply()
    }
}

internal fun isSyncCadenceDue(lastSuccessAtMillis: Long, nowMillis: Long, intervalMillis: Long): Boolean {
    require(intervalMillis > 0)
    return lastSuccessAtMillis <= 0L ||
        nowMillis < lastSuccessAtMillis ||
        nowMillis - lastSuccessAtMillis >= intervalMillis
}
