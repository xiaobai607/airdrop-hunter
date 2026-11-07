package com.airdrophunter.app

import com.airdrophunter.app.data.OkxMarketRepository

/**
 * Lightweight process-level dependency container.
 * Feature modules can be migrated incrementally without a DI framework.
 */
class AppContainer {
    val okxMarketRepository: OkxMarketRepository by lazy {
        OkxMarketRepository()
    }
}
