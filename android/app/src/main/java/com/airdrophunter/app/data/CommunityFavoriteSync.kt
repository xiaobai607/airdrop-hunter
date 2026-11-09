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
        if (TokenStore.readTokenBlocking().isNullOrBlank()) return@withContext Result.success()
        if (!SyncCadence.isDue(applicationContext, SyncCadence.COMMUNITY_FAVORITES_KEY, 8, TimeUnit.HOURS)) {
            return@withContext Result.success()
        }
        ApiClient.initialize(applicationContext)
        try {
            if (CacheStore.syncCommunityFavorites()) {
                SyncCadence.markSuccess(applicationContext, SyncCadence.COMMUNITY_FAVORITES_KEY)
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
