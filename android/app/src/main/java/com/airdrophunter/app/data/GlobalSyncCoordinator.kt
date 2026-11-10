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
 * A lightweight dispatcher runs every 15 minutes and schedules each source into
 * its own unique WorkManager queue. Failures retry independently. Individual
 * workers decide whether their own data source is due:
 * public contributions 15m; wallet activity 8h. Personal cloud sync is disabled in this release.
 */
class GlobalSyncDispatcherWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        GlobalSyncScheduler.enqueueSyncTasks(applicationContext)
        return Result.success()
    }
}

/** Current release stores every personal record locally; private cloud sync is deferred. */
internal object CurrentReleaseDataPolicy {
    const val PRIVATE_PERSONAL_CLOUD_SYNC_ENABLED = false
}

object GlobalSyncScheduler {
    private const val PERIODIC_NAME = "global-sync-dispatcher"

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
    ) = enqueueSyncTasks(context.applicationContext, forceCommunityContribution, forceWalletActivity, forcePersonalLedger, forceFavorites)

    internal fun enqueueSyncTasks(
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


        // Each source has its own unique queue. A failure in one source retries
        // independently and cannot block unrelated ledger, wallet, or favorite work.
        workManager.enqueueUniqueWork(
            "global-sync-community-contribution",
            if (forceCommunityContribution) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            contribution
        )
        workManager.enqueueUniqueWork(
            "global-sync-wallet-activity",
            if (forceWalletActivity) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            walletActivity
        )

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

    fun scopedKey(key: String, accessToken: String?): String =
        key + ":" + CacheStore.accountScopeKey(accessToken)
}

internal fun isSyncCadenceDue(lastSuccessAtMillis: Long, nowMillis: Long, intervalMillis: Long): Boolean {
    require(intervalMillis > 0)
    return lastSuccessAtMillis <= 0L ||
        nowMillis < lastSuccessAtMillis ||
        nowMillis - lastSuccessAtMillis >= intervalMillis
}
