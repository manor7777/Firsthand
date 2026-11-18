package app.bbcrevived.data

import app.bbcrevived.data.DeepLinks.Link
import org.junit.Assert.*
import org.junit.Test

class DeepLinksTest {
    @Test fun articlesOnBothDomainsAreCanonicalised() {
        listOf(
            "https://www.bbc.com/news/articles/c1234abcd?at_medium=x#frag",
            "http://bbc.co.uk/news/articles/c1234abcd/",
            "https://m.bbc.co.uk/news/articles/c1234abcd",
        ).forEach {
            assertEquals(it, Link.Article("https://www.bbc.co.uk/news/articles/c1234abcd"), DeepLinks.parse(it))
        }
    }

    @Test fun liveAndLegacyArticles() {
        assertEquals(Link.Live("https://www.bbc.co.uk/news/live/cx2abc"), DeepLinks.parse("https://www.bbc.com/news/live/cx2abc"))
        assertEquals(Link.Article("https://www.bbc.co.uk/news/world-europe-12345678"), DeepLinks.parse("https://www.bbc.co.uk/news/world-europe-12345678"))
    }

    @Test fun unsupportedNewsPagesGoToTheBrowser() {
        listOf("https://www.bbc.com/news", "https://www.bbc.co.uk/news/world", "https://www.bbc.com/news/videos/c999", "https://www.bbc.com/news/av/uk-123")
            .forEach { assertTrue(it, DeepLinks.parse(it) is Link.Unsupported) }
    }

    @Test fun nonNewsAndLookalikesAreRejected() {
        listOf(
            "https://www.bbc.com/sport/football", "https://www.bbc.co.uk/iplayer", "https://www.bbc.co.uk/newsround/123", "https://www.bbc.com/newsbeat",
            "https://bbc.com.evil.com/news/articles/c1", "https://evil.com/news/articles/c1", "https://www.bbc.com@evil.com/news/articles/c1",
            "https://www.bbc.com:8443/news/articles/c1", "ftp://www.bbc.com/news/articles/c1", "javascript:alert(1)", "not a url", "",
        ).forEach { assertNull(it, DeepLinks.parse(it)) }
        assertNull(DeepLinks.parse(null))
    }
}
