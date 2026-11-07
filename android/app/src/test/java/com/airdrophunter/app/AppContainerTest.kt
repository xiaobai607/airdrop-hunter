package com.airdrophunter.app

import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
    @Test
    fun localRepositoriesAreSharedWithinContainer() {
        val container = AppContainer()
        assertSame(container.okxMarketRepository, container.okxMarketRepository)
        assertSame(container.accountingRepository, container.accountingRepository)
    }
}
