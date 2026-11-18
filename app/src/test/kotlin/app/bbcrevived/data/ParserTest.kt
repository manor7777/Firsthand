package app.bbcrevived.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs against real BBC pages captured in fixtures/real (see .github/workflows/fixtures.yml). */
class ParserTest {
    private fun fx(name: String, dir: String = "real"): String {
        val f = File("../fixtures/$dir/$name").takeIf { it.exists() } ?: File("fixtures/$dir/$name")
        assumeTrue("fixture $name missing", f.exists())
        return f.readText()
    }

    @Test fun rss() {
        val s = RssParser.parse(fx("top.xml"), "Top")
        assertTrue(s.size >= 5)
        assertTrue(s.all { it.title.isNotEmpty() && it.url.startsWith("https://www.bbc.co.uk/") && '?' !in it.url })
        assertTrue(s.any { it.imageUrl != null })
    }

    @Test fun homepage() {
        val h = HomepageParser.parse(fx("home.html"))
        assertTrue("breaking ${h.breaking.size}", h.breaking.size >= 5)
        assertTrue("mostRead ${h.mostRead.size}", h.mostRead.size >= 3)
        assertTrue(h.breaking.all { it.url.startsWith("https://www.bbc.co.uk/news/") && it.title.isNotBlank() })
        assertTrue(h.breaking.any { it.imageUrl != null })
    }

    @Test fun video() {
        val v = VideoParser.parse(fx("video.html"), "https://www.bbc.co.uk/news/videos/c3j4r9jgkynzo")!!
        assertEquals("Watch: What's in the Milburn review?", v.title)
        assertEquals("p0pndst8", v.versionId)
        assertTrue(v.portrait)
        assertEquals(119, v.durationSec)
        assertTrue(v.summary.filterIsInstance<Block.Paragraph>().first().text.startsWith("A government-backed review"))
        assertTrue(v.poster!!.startsWith("https://ichef.bbci.co.uk/"))
    }

    @Test fun weatherArticle() {
        val html = fx("weather.html")
        val paras = ArticleParser.parse(html, "https://www.bbc.co.uk/weather/articles/cmvg2p9qlkz9o").filterIsInstance<Block.Paragraph>()
        assertTrue("paragraphs ${paras.size}", paras.size >= 3)
        assertEquals("UK weather: Is any more snow on the way?", ArticleParser.meta(html).title)
    }

    @Test fun bylineAndDates() {
        val a = ArticleParser.info(fx("article.html"))
        assertEquals(listOf(Author("Jenna Moon", null)), a.authors)
        assertEquals("2026-11-15T12:31:20.163Z", a.published)
        assertEquals("2026-11-15T13:31:39.301Z", a.updated)
        // Trailing space in BBC's data is trimmed; role kept.
        assertEquals(listOf(Author("Simon King", "Lead Weather Presenter")), ArticleParser.byline(fx("weather.html")))
    }

    @Test fun pageMetaUsesOpenGraph() {
        val m = ArticleParser.meta(fx("video.html"))
        assertEquals("Watch: What's in the Milburn review?", m.title)
        assertTrue(m.image!!.contains("/branded_news/"))
    }

    @Test fun mediaSelectorStream() {
        val json = """{"media":[{"kind":"video","connection":[
            {"protocol":"http","transferFormat":"dash","href":"http://a/x.mpd"},
            {"protocol":"http","transferFormat":"hls","href":"http://b/x.m3u8"},
            {"protocol":"https","transferFormat":"hls","href":"https://c/x.m3u8"}]}]}"""
        assertEquals("https://c/x.m3u8", VideoParser.streamFrom(json))
        assertNull(VideoParser.streamFrom("""{"result":"geolocation"}"""))
    }

    @Test fun article() {
        val b = ArticleParser.parse(fx("article.html"), "https://www.bbc.co.uk/news/articles/x")
        val paras = b.filterIsInstance<Block.Paragraph>()
        assertTrue("paragraphs ${paras.size}", paras.size >= 5)
        assertTrue(paras.first().text.startsWith("A discussion over a staggered age of consent"))
        assertTrue(b.any { it is Block.Image })
        // headline/byline/related-links must not leak into the body
        assertFalse(paras.any { it.text.startsWith("Staggered age of consent to be discussed after Brand case") })
    }

    @Test fun searchResults() {
        val r = SearchParser.parse(fx("search_news.html", "probe"))
        assertTrue("results ${r.size}", r.size >= 5)
        assertTrue(r.all { it.url.startsWith("https://www.bbc.co.uk/news/") && it.title.isNotBlank() })
    }

    @Test fun relatedTopics() {
        val t = ArticleParser.topics(fx("article.html"))
        assertTrue(t.any { it.name == "Russell Brand" && it.id == "c48yrnxyk79t" })
    }

    @Test fun topicFeed() {
        val s = RssParser.parse(fx("topic_rss.xml", "probe"), "Russell Brand")
        assertTrue(s.isNotEmpty() && s.all { it.url.startsWith("https://www.bbc.co.uk/") })
    }

    @Test fun live() {
        val p = LiveParser.parse(fx("live.html"), "https://www.bbc.co.uk/news/live/x")
        assertTrue("posts ${p.size}", p.size >= 10)
        assertTrue(p.all { it.id.isNotEmpty() && it.blocks.isNotEmpty() })
        assertTrue(p.first().time!!.isNotBlank())
    }
}

class KeywordMatchTest {
    @Test fun keywordsBecomeTopics() {
        assertEquals("Science & Environment", Categories.normalize("kw:science"))
        assertEquals("Science & Environment", Categories.normalize("Science"))
        assertEquals("Football", Categories.normalize("kw:Football"))
        assertEquals("Formula 1", Categories.normalize("kw:f1"))
        assertEquals("US & Canada", Categories.normalize("kw:us and canada"))
        assertEquals("Health", Categories.normalize("Health"))
        assertEquals("kw:diabetes", Categories.normalize("kw:diabetes")) // not a catalogue topic: stays a keyword
        assertEquals("kw:exampletown", Categories.normalize("kw:exampletown"))
    }
}

class FollowIdTest {
    @Test fun topicIds() {
        val f = Categories.topicFollow("c48yrnxyk79t", "Russell Brand")
        assertTrue(Categories.isValid(f)); assertEquals("c48yrnxyk79t", Categories.topicId(f)); assertEquals("Russell Brand", Categories.label(f))
        assertFalse(Categories.isValid("topic:../evil|x"))
    }
}

class TopicPickTest {
    private val topics = listOf(TopicRef("c1aaaaaaaaaa", "Diabetes"), TopicRef("c2aaaaaaaaaa", "Type 1 diabetes"), TopicRef("c3aaaaaaaaaa", "NHS"))
    @Test fun exactNameOnly() {
        assertEquals("c1aaaaaaaaaa", ArticleParser.pickTopic(topics, "diabetes")?.id)
        assertEquals("c1aaaaaaaaaa", ArticleParser.pickTopic(topics, "  Diabetes ")?.id)
        assertNull(ArticleParser.pickTopic(topics, "boy"))
        assertNull(ArticleParser.pickTopic(topics, ""))
    }
}
