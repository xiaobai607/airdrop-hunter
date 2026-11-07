package com.airdrophunter.app

import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.OkxMarketRepository

/**
 * Lightweight process-level dependency container.
 * Feature modules can be migrated incrementally without a DI framework.
 */
class AppContainer {
    val apiService: ApiService by lazy { ApiClient.service }

    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
