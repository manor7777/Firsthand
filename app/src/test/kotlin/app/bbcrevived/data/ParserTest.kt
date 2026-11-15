package app.bbcrevived.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs against real BBC pages captured in fixtures/real (see .github/workflows/fixtures.yml). */
class ParserTest {
    private fun fx(name: String): String {
        val f = File("../fixtures/real/$name").takeIf { it.exists() } ?: File("fixtures/real/$name")
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

    @Test fun article() {
        val b = ArticleParser.parse(fx("article.html"), "https://www.bbc.co.uk/news/articles/x")
        val paras = b.filterIsInstance<Block.Paragraph>()
        assertTrue("paragraphs ${paras.size}", paras.size >= 5)
        assertTrue(paras.first().text.startsWith("A discussion over a staggered age of consent"))
        assertTrue(b.any { it is Block.Image })
        // headline/byline/related-links must not leak into the body
        assertFalse(paras.any { it.text.startsWith("Staggered age of consent to be discussed after Brand case") })
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
