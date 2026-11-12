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
 * Refreshes saved-wallet public balances independently of the asset-detail dialog.
 * Payloads remain in the existing account/wallet/chain-scoped Room store.
 */
class WalletAssetSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accessToken = TokenStore.readTokenBlocking()?.takeIf { it.isNotBlank() }
        val guest = accessToken == null
        ApiClient.initialize(applicationContext)
        val app = applicationContext as? AirdropHunterApplication ?: return@withContext Result.failure()

        val wallets = try {
            app.appContainer.walletRepository.loadForSession(guest, accessToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        val repository = WalletAssetRepository(RetrofitWalletAssetsRemoteDataSource(ApiClient.service))
        var hadFailure = false
        for (wallet in wallets) {
            val networks = when (wallet.ecosystem) {
                WalletEcosystem.EVM -> EVM_NETWORKS
                WalletEcosystem.BTC -> listOf("bitcoin")
                WalletEcosystem.SOL -> listOf("solana")
                // TRON is not currently exposed by the asset-detail UI/provider contract.
                else -> emptyList()
            }
            for (network in networks) {
                val cadenceKey = SyncCadence.scopedKey(
                    "${SyncCadence.WALLET_ASSET_KEY}:${wallet.id}:${network.lowercase()}",
                    accessToken
                )
                val hasCachedSnapshot = repository.readCached(wallet.id, network, accessToken) != null
                if (hasCachedSnapshot &&
                    !SyncCadence.isDue(applicationContext, cadenceKey, 8, TimeUnit.HOURS)
                ) continue
                try {
                    val snapshot = repository.refresh(wallet.id, wallet.address, network, accessToken)
                    if (shouldMarkSyncCadenceSuccess(snapshot.stale, snapshot.errorMessage, snapshot.throttled)) {
                        SyncCadence.markSuccess(applicationContext, cadenceKey)
                    } else {
                        hadFailure = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    hadFailure = true
                }
            }
        }
        if (hadFailure) Result.retry() else Result.success()
    }

    companion object {
        private val EVM_NETWORKS = listOf(
            "ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche"
        )
    }
}
