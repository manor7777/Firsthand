package app.bbcrevived.data

import org.junit.Assert.*
import org.junit.Test

// SYNTHETIC fixtures shaped like the real formats. Replace with saved real BBC samples when reachable.
class ParserTest {
    @Test fun rss() {
        val xml = """<rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
          <title>Hello</title><description>Sum</description><link>https://www.bbc.co.uk/news/articles/c1?at=x</link>
          <pubDate>Sat, 14 Nov 2026 10:00:00 GMT</pubDate>
          <media:thumbnail width="240" height="135" url="https://ichef.bbci.co.uk/ace/standard/240/a.jpg"/></item></channel></rss>"""
        val s = RssParser.parse(xml, "World").single()
        assertEquals("Hello", s.title); assertEquals("https://www.bbc.co.uk/news/articles/c1", s.url)
        assertTrue(s.imageUrl!!.contains("/660/")); assertTrue(s.publishedMillis > 0)
    }
    @Test fun homepage() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"breakingNews":[{"headline":"B1","href":"/news/articles/c9"}],
          "mostRead":{"items":[{"title":"M1","href":"/news/articles/c8"}]}}}</script>"""
        val h = HomepageParser.parse(html)
        assertEquals("B1", h.breaking.single().title); assertEquals("M1", h.mostRead.single().title)
    }
    @Test fun article() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"blocks":[
          {"type":"text","model":{"blocks":[{"type":"paragraph","model":{"blocks":[{"type":"fragment","model":{"text":"Hi "}},{"type":"fragment","model":{"text":"there"}}]}}]}},
          {"type":"subheadline","model":{"text":"Head"}},{"type":"interactive","model":{}}]}</script>"""
        val b = ArticleParser.parse(html, "u")
        assertEquals(Block.Paragraph("Hi there"), b[0]); assertEquals(Block.Heading("Head"), b[1]); assertTrue(b[2] is Block.Embed)
    }

    @Test fun live() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"posts":[
          {"id":"a","firstPublished":"2026-11-14T10:00:00Z","pinned":false,"title":"Old","blocks":[{"type":"paragraph","model":{"text":"one"}}]},
          {"id":"b","firstPublished":"2026-11-14T11:00:00Z","pinned":false,"blocks":[{"type":"paragraph","model":{"text":"two"}}]},
          {"id":"c","firstPublished":"2026-11-14T09:00:00Z","pinned":true,"blocks":[{"type":"paragraph","model":{"text":"top"}}]}]}</script>"""
        assertEquals(listOf("c", "b", "a"), LiveParser.parse(html, "u").map { it.id })
    }
}
