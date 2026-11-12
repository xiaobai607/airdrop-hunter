package com.airdrophunter.app

import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStoreProjectsLocalDataSource
import com.airdrophunter.app.data.CacheRefreshCoordinator
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.ProjectsRepository
import com.airdrophunter.app.data.ProjectWalletAssignmentRepository
import com.airdrophunter.app.data.RetrofitProjectsRemoteDataSource
import com.airdrophunter.app.data.WalletRepository
import com.airdrophunter.app.data.WalletActivityRepository
import com.airdrophunter.app.data.RetrofitWalletActivityRemoteDataSource
import com.airdrophunter.app.data.CacheStoreWalletActivityLocalDataSource
import com.airdrophunter.app.data.RetrofitWalletRemoteDataSource
import com.airdrophunter.app.data.CacheStoreWalletLocalDataSource
import com.airdrophunter.app.data.ProfileRepository
import com.airdrophunter.app.data.AuthRepository
import com.airdrophunter.app.data.RetrofitAuthRemoteDataSource
import com.airdrophunter.app.data.RetrofitProfileRemoteDataSource
import com.airdrophunter.app.data.AirdropHubRepository
import com.airdrophunter.app.data.AirdropCampaignRepository
import com.airdrophunter.app.data.RetrofitAirdropHubRemoteDataSource
import com.airdrophunter.app.data.AirdropsRepository
import com.airdrophunter.app.data.AccountingRepository
import com.airdrophunter.app.data.CacheStoreAccountingLocalDataSource
import com.airdrophunter.app.data.RetrofitAirdropsRemoteDataSource
import com.airdrophunter.app.data.Web3TransactionRepository
import com.airdrophunter.app.data.Web3TransactionDraftDao
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.TokenStore

/**
 * Lightweight process-level composition root. Feature dependencies are singletons
 * and can be migrated incrementally without adding a DI framework.
 */
class AppContainer {
    val apiService: ApiService by lazy { ApiClient.service }
    val cacheRefreshEngine: CacheRefreshCoordinator by lazy { CacheRefreshCoordinator.shared }

    val projectsRepository: ProjectsRepository by lazy {
        ProjectsRepository(
            remote = RetrofitProjectsRemoteDataSource(apiService),
            local = CacheStoreProjectsLocalDataSource()
        )
    }

    val projectWalletAssignmentRepository: ProjectWalletAssignmentRepository by lazy {
        ProjectWalletAssignmentRepository(apiService)
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(RetrofitWalletRemoteDataSource(apiService), CacheStoreWalletLocalDataSource())
    }

    val walletActivityRepository: WalletActivityRepository by lazy {
        WalletActivityRepository(
            RetrofitWalletActivityRemoteDataSource(apiService),
            CacheStoreWalletActivityLocalDataSource()
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(RetrofitAuthRemoteDataSource(apiService))
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(RetrofitProfileRemoteDataSource(apiService))
    }

    val airdropCampaignRepository: AirdropCampaignRepository by lazy {
        AirdropCampaignRepository(apiService)
    }

    val airdropHubRepository: AirdropHubRepository by lazy {
        AirdropHubRepository(
            remote = RetrofitAirdropHubRemoteDataSource(apiService),
            cacheRefresh = cacheRefreshEngine
        )
    }

    val airdropsRepository: AirdropsRepository by lazy {
        AirdropsRepository(RetrofitAirdropsRemoteDataSource(apiService))
    }

    val web3TransactionRepository: Web3TransactionRepository by lazy {
        val dao = requireNotNull(CacheStore.web3TransactionDraftDao()) {
            "Room database is not initialized"
        }
        Web3TransactionRepository(
            dao = dao,
            scopeProvider = { CacheStore.accountScopeKey(TokenStore.readTokenBlocking()) }
        )
    }

    val accountingRepository: AccountingRepository by lazy {
        AccountingRepository(CacheStoreAccountingLocalDataSource(), apiService, projectsRepository = projectsRepository)
    }

    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
