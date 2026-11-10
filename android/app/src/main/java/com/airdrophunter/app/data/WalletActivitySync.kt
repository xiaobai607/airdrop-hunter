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
 * Refreshes read-only activity for saved public addresses.
 * Results stay in account/guest-scoped local cache; this worker never creates
 * accounting entries or sends wallet/transaction details to community statistics.
 */
class WalletActivitySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Bind cadence, API requests, and cache writes to this exact session.
        val accessToken = TokenStore.readTokenBlocking()?.takeIf { it.isNotBlank() }
        val cadenceKey = SyncCadence.scopedKey(SyncCadence.WALLET_ACTIVITY_KEY, accessToken)
        if (!inputData.getBoolean(GlobalSyncScheduler.FORCE_KEY, false) &&
            !SyncCadence.isDue(applicationContext, cadenceKey, 8, TimeUnit.HOURS)) {
            return@withContext Result.success()
        }
        ApiClient.initialize(applicationContext)
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()
        val guest = accessToken == null

        val wallets = try {
            app.appContainer.walletRepository.loadForSession(guest, accessToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        var hadFailure = false
        for (wallet in wallets) {
            try {
                app.appContainer.walletActivityRepository.refresh(wallet, guest, accessToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                hadFailure = true
            }
        }
        if (hadFailure) {
            Result.retry()
        } else {
            SyncCadence.markSuccess(applicationContext, cadenceKey)
            Result.success()
        }
    }
}

object WalletActivitySyncScheduler {
    fun ensurePeriodic(context: Context) = GlobalSyncScheduler.ensurePeriodic(context)
}
