package com.airdrophunter.app

import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStoreProjectsLocalDataSource
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.ProjectsRepository
import com.airdrophunter.app.data.RetrofitProjectsRemoteDataSource

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

    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
