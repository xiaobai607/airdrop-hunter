package com.airdrophunter.app

import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
    @Test
    fun repositoriesAreSharedWithinContainer() {
        val container = AppContainer()
        assertSame(container.okxMarketRepository, container.okxMarketRepository)
        assertSame(container.authRepository, container.authRepository)
        assertSame(container.profileRepository, container.profileRepository)
        assertSame(container.airdropHubRepository, container.airdropHubRepository)
        assertSame(container.airdropsRepository, container.airdropsRepository)
        assertSame(container.walletRepository, container.walletRepository)
        assertSame(container.projectsRepository, container.projectsRepository)
    }
}
