package com.airdrophunter.app

import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStoreProjectsLocalDataSource
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.ProjectsRepository
import com.airdrophunter.app.data.RetrofitProjectsRemoteDataSource
import com.airdrophunter.app.data.WalletRepository
import com.airdrophunter.app.data.RetrofitWalletRemoteDataSource
import com.airdrophunter.app.data.CacheStoreWalletLocalDataSource
import com.airdrophunter.app.data.DefaultAssetRepository
import com.airdrophunter.app.data.AssetRepository

/**
 * Lightweight process-level composition root. Feature dependencies are singletons
 * and can be migrated incrementally without adding a DI framework.
 */
class AppContainer {
    val apiService: ApiService by lazy { ApiClient.service }

    val projectsRepository: ProjectsRepository by lazy {
        ProjectsRepository(
            remote = RetrofitProjectsRemoteDataSource(apiService),
            local = CacheStoreProjectsLocalDataSource()
        )
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(RetrofitWalletRemoteDataSource(apiService), CacheStoreWalletLocalDataSource())
    }

    val assetRepository: AssetRepository by lazy { DefaultAssetRepository() }

    val gasTrackerRepository: RealGasTrackerRepository by lazy { RealGasTrackerRepository(apiService) }

    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
