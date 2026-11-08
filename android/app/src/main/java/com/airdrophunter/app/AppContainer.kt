package com.airdrophunter.app

import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStoreProjectsLocalDataSource
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.ProjectsRepository
import com.airdrophunter.app.data.RetrofitProjectsRemoteDataSource
import com.airdrophunter.app.data.WalletRepository
import com.airdrophunter.app.data.CacheStoreWalletLocalDataSource
import com.airdrophunter.app.data.DefaultAssetRepository
import com.airdrophunter.app.data.AssetRepository
import com.airdrophunter.app.data.ProfileRepository
import com.airdrophunter.app.data.AuthRepository
import com.airdrophunter.app.data.RetrofitAuthRemoteDataSource
import com.airdrophunter.app.data.RetrofitProfileRemoteDataSource
import com.airdrophunter.app.data.AirdropHubRepository
import com.airdrophunter.app.data.RetrofitAirdropHubRemoteDataSource
import com.airdrophunter.app.data.AirdropsRepository
import com.airdrophunter.app.data.AccountingRepository
import com.airdrophunter.app.data.CacheStoreAccountingLocalDataSource
import com.airdrophunter.app.data.RetrofitAirdropsRemoteDataSource

class AppContainer {
    val apiService: ApiService by lazy { ApiClient.service }

    val projectsRepository: ProjectsRepository by lazy {
        ProjectsRepository(
            remote = RetrofitProjectsRemoteDataSource(apiService),
            local = CacheStoreProjectsLocalDataSource()
        )
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(CacheStoreWalletLocalDataSource())
    }

    val assetRepository: AssetRepository by lazy { DefaultAssetRepository() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(RetrofitAuthRemoteDataSource(apiService))
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(RetrofitProfileRemoteDataSource(apiService))
    }

    val airdropHubRepository: AirdropHubRepository by lazy {
        AirdropHubRepository(RetrofitAirdropHubRemoteDataSource(apiService))
    }

    val airdropsRepository: AirdropsRepository by lazy {
        AirdropsRepository(RetrofitAirdropsRemoteDataSource(apiService))
    }

    val accountingRepository: AccountingRepository by lazy {
        AccountingRepository(CacheStoreAccountingLocalDataSource())
    }

    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
