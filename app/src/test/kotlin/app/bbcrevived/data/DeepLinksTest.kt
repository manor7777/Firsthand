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
        listOf("https://www.bbc.com/news", "https://www.bbc.co.uk/news/world")
            .forEach { assertTrue(it, DeepLinks.parse(it) is Link.Unsupported) }
    }

    @Test fun videoPages() {
        // The real target of a Google share link (query string from Discover dropped).
        assertEquals(
            Link.Video("https://www.bbc.co.uk/news/videos/c3j4r9jgkynzo"),
            DeepLinks.parse("https://www.bbc.co.uk/news/videos/c3j4r9jgkynzo?shem=dsdf,sharefoc,agadiscoversdl,,sh/x/discover/m1/4"),
        )
        assertTrue(DeepLinks.parse("https://www.bbc.com/news/av/uk-12345678") is Link.Video)
    }

    @Test fun weatherAndSportArticles() {
        assertEquals(
            Link.Article("https://www.bbc.co.uk/weather/articles/cmvg2p9qlkz9o"),
            DeepLinks.parse("https://www.bbc.com/weather/articles/cmvg2p9qlkz9o?shem=dsdf,sharefoc,agadiscoversdl,,sh/x/discover/m1/4"),
        )
        assertEquals(
            Link.Article("https://www.bbc.co.uk/sport/football/articles/c0abc123xyzo"),
            DeepLinks.parse("https://www.bbc.co.uk/sport/football/articles/c0abc123xyzo"),
        )
        // The rest of Weather/Sport is not taken over.
        listOf("https://www.bbc.co.uk/weather", "https://www.bbc.co.uk/weather/2643743", "https://www.bbc.co.uk/sport/football", "https://www.bbc.co.uk/sport/football/live/c1")
            .forEach { assertNull(it, DeepLinks.parse(it)) }
    }

    @Test fun googleShareLinks() {
        assertTrue(DeepLinks.isShortLink("https://share.google/EuaV7JI0KoM2JP2RD"))
        assertTrue(DeepLinks.isShortLink("https://search.app/abc"))
        assertFalse(DeepLinks.isShortLink("https://share.google.evil.com/x"))
        assertFalse(DeepLinks.isShortLink("https://www.bbc.com/news/articles/c1"))
        assertNull(DeepLinks.parse("https://share.google/EuaV7JI0KoM2JP2RD")) // must be resolved first
    }

    @Test fun firstUrlInSharedText() {
        assertEquals("https://share.google/EuaV7JI0KoM2JP2RD", DeepLinks.firstUrl("Watch: What's in the Milburn review? https://share.google/EuaV7JI0KoM2JP2RD"))
        assertEquals("https://www.bbc.co.uk/news/articles/c1", DeepLinks.firstUrl("(see https://www.bbc.co.uk/news/articles/c1)."))
        assertNull(DeepLinks.firstUrl("no link here"))
    }

    @Test fun nonNewsAndLookalikesAreRejected() {
        listOf(
            "https://www.bbc.co.uk/iplayer", "https://www.bbc.co.uk/newsround/123", "https://www.bbc.com/newsbeat",
            "https://bbc.com.evil.com/news/articles/c1", "https://evil.com/news/articles/c1", "https://www.bbc.com@evil.com/news/articles/c1",
            "https://www.bbc.com:8443/news/articles/c1", "ftp://www.bbc.com/news/articles/c1", "javascript:alert(1)", "not a url", "",
        ).forEach { assertNull(it, DeepLinks.parse(it)) }
        assertNull(DeepLinks.parse(null))
    }
}
