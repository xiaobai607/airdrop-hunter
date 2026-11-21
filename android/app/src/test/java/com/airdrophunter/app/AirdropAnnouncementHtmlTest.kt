package com.airdrophunter.app

import org.junit.Assert.assertTrue
import org.junit.Test

class AirdropAnnouncementHtmlTest {
    @Test
    fun preservesOriginalAnnouncementMarkupWithoutRewritingIt() {
        val original = """<h2>Campaign details</h2><p>Reward: 10 USDT</p><img src="https://cdn.example.test/banner.png">"""

        val document = announcementHtmlDocument(original)

        assertTrue(document.contains("<body>$original</body>"))
    }

    @Test
    fun keepsScriptsAndActiveNetworkCapabilitiesDisabledByPolicy() {
        val document = announcementHtmlDocument("<p>Official announcement</p>")

        assertTrue(document.contains("default-src 'none'"))
        assertTrue(document.contains("script-src 'none'"))
        assertTrue(document.contains("connect-src 'none'"))
        assertTrue(document.contains("frame-src 'none'"))
        assertTrue(document.contains("img-src https: data:"))
    }

    @Test
    fun keepsWideTablesScrollableAndMediaWithinViewport() {
        val document = announcementHtmlDocument("<table><tr><td>Wide content</td></tr></table>")

        assertTrue(document.contains("table { max-width: 100%; border-collapse: collapse; display: block; overflow-x: auto; }"))
        assertTrue(document.contains("img, video { max-width: 100%; height: auto; }"))
    }
}
