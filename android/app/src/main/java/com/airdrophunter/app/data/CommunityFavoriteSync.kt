package com.airdrophunter.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Retries account-scoped favorite reconciliation; guest favorites never leave Room. */
class CommunityFavoriteSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accessToken = TokenStore.readTokenBlocking()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.success()
        val cadenceKey = SyncCadence.scopedKey(SyncCadence.COMMUNITY_FAVORITES_KEY, accessToken)
        if (!inputData.getBoolean(GlobalSyncScheduler.FORCE_KEY, false) &&
            !SyncCadence.isDue(applicationContext, cadenceKey, 8, TimeUnit.HOURS)) {
            return@withContext Result.success()
        }
        ApiClient.initialize(applicationContext)
        try {
            if (CacheStore.syncCommunityFavorites()) {
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
