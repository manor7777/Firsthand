package app.firsthand.data

import org.junit.Assert.*
import org.junit.Test

class PaywallTest {
    private fun page(flag: String) = """<html><head><meta property="og:site_name" content="Financial Times">
        <script type="application/ld+json">{"@type":"NewsArticle","isAccessibleForFree":$flag,"datePublished":"2026-11-26T18:36:00Z"}</script></head><body><p>Short teaser.</p></body></html>"""

    @Test fun flagsPaywalledArticles() {
        assertTrue(ExternalArticle.parse(page("false"), "https://www.ft.com/content/x", "Ft").paywalled)
        assertTrue(ExternalArticle.parse(page("\"False\""), "https://www.ft.com/content/x", "Ft").paywalled)
        assertFalse(ExternalArticle.parse(page("true"), "https://www.ft.com/content/x", "Ft").paywalled)
    }

    @Test fun readsSiteName() { assertEquals("Financial Times", ArticleParser.meta(page("true")).siteName) }
}
