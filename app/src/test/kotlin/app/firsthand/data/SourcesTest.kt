package app.firsthand.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Feed parsing for added sources. reach_sample.xml follows the shape of a real Reach feed (invented site, places and writers). */
class SourcesTest {
    private fun fx(name: String): String {
        val f = File("../fixtures/sources/$name").takeIf { it.exists() } ?: File("fixtures/sources/$name")
        assumeTrue("fixture $name missing", f.exists())
        return f.readText()
    }

    private val reach by lazy { RssParser.parse(fx("reach_sample.xml"), "Front page", "Example Post") }

    @Test fun reachFeedStories() {
        assertEquals(5, reach.size)
        val s = reach[0]
        assertEquals("Main road closed for hours after lorry fire", s.title)
        assertEquals("The main road is closed in both directions", s.summary)
        assertEquals("https://www.examplepost.com/news/local-news/main-road-closed-after-lorry-fire-1001", s.url)
        assertEquals("Example Post", s.source)
        assertEquals("Local News", s.category)            // the item's own section, not the feed name
        assertEquals("UK & World News", reach[1].category) // entities decoded
        assertTrue(s.publishedMillis > 0)
        assertTrue(reach.all { it.title == it.title.trim() && !it.isLive })
    }

    @Test fun everyStoryShowsItsSource() {
        // BBC stories carry no source tag but are shown as "BBC News" with their section.
        val bbc = Story("u", "t", "", "https://www.bbc.co.uk/news/articles/c1", null, "Business", 0L)
        assertEquals("BBC News", bbc.sourceName); assertEquals("Business", bbc.sectionName)
        // A story opened from a link has the placeholder section "News": drop it rather than show "BBC News · News".
        assertEquals("", Story("u", "t", "", "https://www.bbc.co.uk/news/articles/c2", null, "News", 0L).sectionName)
        assertEquals("Example Post", reach[0].sourceName); assertEquals("Local News", reach[0].sectionName)
        // A custom feed's section is empty or the source's own name: nothing to repeat.
        val custom = Story("u", "t", "", "https://e.com/a", null, "My Blog", 0L, source = "My Blog")
        assertEquals("", custom.sectionName)
    }

    @Test fun reachPicksTheLargeImageNotThe98pxThumbnail() {
        assertTrue(reach.all { it.imageUrl!!.contains("/s615/") })
    }

    @Test fun reachWritersFromAuthorTag() {
        assertEquals(listOf("Alex Morgan", "Jamie Rivers", "Sam Taylor"), reach[0].authors.map { it.name })
        assertEquals(listOf("Casey Park", "Robin Hale"), reach[1].authors.map { it.name })
        assertEquals(listOf("Sam Taylor"), reach[2].authors.map { it.name })
        assertTrue(reach.flatMap { it.authors }.all { it.source == "Example Post" })
        // "Pat Quinn, Local Democracy Reporter, Pat Quinn - Local Democracy Reporter" is one person with a role.
        val ldr = reach[4].authors
        assertEquals(1, ldr.size)
        assertEquals("Pat Quinn", ldr[0].name)
        assertEquals("Local Democracy Reporter", ldr[0].role)
    }

    @Test fun bbcStoriesAreUnchanged() {
        val xml = """<rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel><title>BBC News</title><item><title>Hello</title>
            <description>Sum</description><link>https://www.bbc.co.uk/news/articles/c1?at_medium=RSS&amp;at_campaign=rss</link>
            <pubDate>Wed, 18 Nov 2026 20:08:48 GMT</pubDate>
            <media:thumbnail width="240" height="135" url="https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/a.jpg"/></item></channel></rss>"""
        val s = RssParser.parse(xml, "Top").single()
        assertEquals("https://www.bbc.co.uk/news/articles/c1", s.url)
        assertEquals("https://ichef.bbci.co.uk/ace/standard/660/cpsprodpb/a.jpg", s.imageUrl)
        assertEquals("Top", s.category); assertNull(s.source); assertTrue(s.authors.isEmpty())
        assertTrue(s.publishedMillis > 0)
    }

    @Test fun dcCreatorAndFullTextFeed() {
        val body = "<p>" + "A long paragraph of real article text. ".repeat(20) + "</p>"
        val xml = """<rss xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel><title>T</title>
            <item><title>Story &amp; more</title><link>https://example.com/a?id=7</link><pubDate>Mon, 02 Nov 2026 10:00:00 GMT</pubDate>
            <dc:creator><![CDATA[Ann Smith]]></dc:creator><dc:creator>Bob Jones</dc:creator>
            <media:content url="https://example.com/small.jpg" width="140"/><media:content url="https://example.com/big.jpg" width="1200"/>
            <description>Short</description><content:encoded><![CDATA[$body]]></content:encoded></item></channel></rss>"""
        val s = RssParser.parse(xml, "Front", "Example").single()
        assertEquals("Story & more", s.title)
        assertEquals("https://example.com/a?id=7", s.url) // non-BBC query strings can be the article id
        assertEquals(listOf("Ann Smith", "Bob Jones"), s.authors.map { it.name })
        assertEquals("https://example.com/big.jpg", s.imageUrl)
        assertNotNull(s.contentHtml)
        assertTrue(ExternalArticle.readable(ExternalArticle.blocks(s.contentHtml!!, s.url)) || ExternalArticle.blocks(s.contentHtml!!, s.url).isNotEmpty())
    }

    @Test fun atomFeed() {
        val xml = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Site</title>
            <entry><title>Atom story</title><link rel="self" href="https://x.com/self"/><link rel="alternate" href="https://x.com/story"/>
            <published>2026-11-18T10:00:00Z</published><author><name>Cat Dog</name></author><summary>Sum</summary></entry></feed>"""
        val p = RssParser.parseFeed(xml, "Articles", "X")
        assertEquals("Site", p.title)
        val s = p.stories.single()
        assertEquals("https://x.com/story", s.url); assertEquals("Cat Dog", s.authors.single().name)
        assertEquals(java.time.Instant.parse("2026-11-18T10:00:00Z").toEpochMilli(), s.publishedMillis)
    }

    @Test fun nameSplitting() {
        assertTrue(RssParser.splitNames("newsdesk@x.com").isEmpty())
        assertTrue(RssParser.splitNames("Staff Writer").isEmpty())
        assertEquals(listOf("A One", "B Two"), RssParser.splitNames("x@y.com (A One, B Two, Local Democracy Reporter)").map { it.name })
        assertEquals("A One", RssParser.splitNames("By A One").single().name)
    }

    @Test fun catalogueIsSane() {
        assertTrue(Sources.catalog.size > 30)
        assertEquals(Sources.catalog.size, Sources.catalog.map { it.id }.distinct().size)
        for (s in Sources.catalog) {
            assertTrue(s.name, s.feeds.isNotEmpty())
            assertEquals(s.name, s.feeds.size, s.feeds.map { it.url }.distinct().size)
            assertTrue(s.feeds.all { Sources.validUrl(it.url) })
            assertTrue(s.feeds.all { Categories.isValid(s.follow(it)) })
        }
        val np = Sources.catalog.first { it.name == "Mirror" }
        assertTrue(np.feeds.any { it.url == "https://www.mirror.co.uk/news/uk-news/?service=rss" })
        assertEquals("https://www.mirror.co.uk/?service=rss", np.frontPage.url)
        assertEquals("Mirror · Front page", Categories.label(np.follow(np.frontPage)))
    }

    @Test fun entriesRoundTripAndRejectJunk() {
        val g = Sources.catalog.first { it.name == "The Guardian" }
        assertEquals(g, Sources.resolve(Sources.presetEntry(g)))
        val c = Sources.resolve(Sources.customEntry("https://example.com/feed.xml", "  My   Blog "))!!
        assertEquals("My Blog", c.name); assertTrue(c.custom); assertEquals("https://example.com/feed.xml", c.frontPage.url)
        assertEquals("My Blog", Categories.label(c.follow(c.frontPage)))
        assertNull(Sources.resolve("p:nope")); assertNull(Sources.resolve("c:http://insecure.example.com/f|X"))
        assertNull(Sources.resolve("c:https://exa mple.com/f|X")); assertNull(Sources.resolve("c:https://example.com/f|"))
        assertFalse(Categories.isValid("feed:javascript:alert(1)|X")); assertFalse(Categories.isValid("feed:https://a.com/f|"))
        assertTrue(c.owns(c.follow(c.frontPage)))
        assertTrue(Categories.isXAuthor(Categories.xauthorFollow("Ann  Lee")) && Categories.xauthorName(Categories.xauthorFollow("Ann  Lee")) == "Ann Lee")
    }

    @Test fun articleTextSkipsNavigationAndPromos() {
        val para = "This is a genuine paragraph of the story with enough words to count as article prose for the reader. "
        val html = """<html><head><script type="application/ld+json">{"@context":"https://schema.org","@type":"NewsArticle",
              "author":[{"@type":"Person","name":"Ann Lee"}],"datePublished":"2026-11-18T20:08:48+00:00","dateModified":"2026-11-18T21:00:00Z"}</script></head>
            <body><header><nav><p>Home News Sport Weather and other long navigation words here</p></nav></header>
            <article><h1>Headline</h1><div class="byline">By Ann Lee</div>
            <p>Paragraph 1. ${para.repeat(2)}</p><aside><p>${para}</p></aside>
            <p>Paragraph 2. ${para.repeat(2)}</p><div class="related-articles"><p>READ MORE: some other story that is promoted here for you</p></div>
            <h2>A subheading</h2><figure><img src="/img/a.jpg" width="600"><figcaption>Cap here</figcaption></figure>
            <p>Paragraph 3. ${para.repeat(2)}</p><p>Sign up to our newsletter for the latest news every day</p><blockquote>A quoted line that is long enough.</blockquote>
            </article><footer><p>${para}</p></footer></body></html>"""
        val r = ExternalArticle.parse(html, "https://example.com/a", "Example")
        assertEquals(3, r.blocks.count { it is Block.Paragraph })
        assertTrue(r.blocks.any { it is Block.Heading && it.text == "A subheading" })
        assertTrue(r.blocks.any { it is Block.Image && it.url == "https://example.com/img/a.jpg" && it.caption == "Cap here" })
        assertTrue(r.blocks.any { it is Block.Quote })
        assertTrue(r.blocks.none { it is Block.Paragraph && ("READ MORE" in it.text || "newsletter" in it.text || "navigation" in it.text) })
        assertEquals("Ann Lee", r.authors.single().name); assertEquals("Example", r.authors.single().source)
        assertEquals("2026-11-18T20:08:48Z", r.published); assertEquals("2026-11-18T21:00:00Z", r.updated)
    }

    @Test fun unreadablePageGivesNoBlocks() {
        assertTrue(ExternalArticle.parse("<html><body><p>Subscribe to read</p></body></html>", "https://e.com/a", null).blocks.isEmpty())
        assertTrue(ExternalArticle.parse("not html at all", "https://e.com/a", null).blocks.isEmpty())
    }

    @Test fun `any shared web page can be read, social and front pages cannot`() {
        assertTrue(Sources.isReadableWebLink("https://www.example-news.co.uk/world/2026/11/18/some-story"))
        assertTrue(Sources.isReadableWebLink("http://blog.example.com/post/1"))
        assertFalse(Sources.isReadableWebLink("https://www.example.com/"))
        assertFalse(Sources.isReadableWebLink("https://www.youtube.com/watch?v=abc"))
        assertFalse(Sources.isReadableWebLink("https://m.facebook.com/story/1"))
        assertFalse(Sources.isReadableWebLink("ftp://example.com/a/b"))
        assertFalse(Sources.isReadableWebLink("https://user:pw@example.com/a"))
    }

    @Test fun `site names come from the address`() {
        assertEquals("Example News", Sources.siteName("https://www.example-news.co.uk/a/b"))
        assertEquals("Example", Sources.siteName("https://m.example.com/a"))
        assertEquals("Theregister", Sources.siteName("https://www.theregister.com/x/y"))
        assertEquals("Blog", Sources.siteName("https://amp.blog.com/p"))
    }

    @Test fun pageVideoFromJsonLd() {
        val html = """<html><head><meta property="og:image" content="https://img.example.com/og.jpg"/>
            <script type="application/ld+json">{"@context":"http://schema.org","@type":"VideoObject","name":"Gamers celebrate","description":"Gamers celebrate",
            "thumbnailUrl":"https://img.example.com/t.jpg","contentUrl":"https://cdn.example.com/v/abs/index.m3u8?format=redirect","duration":"PT1M12S",
            "url":"https://www.example.com/video/shorts/gamers-1"}</script></head><body><main><p>x</p></main></body></html>"""
        val p = ExternalArticle.parse(html, "https://www.example.com/video/shorts/gamers-1?playlist=a", "Example")
        assertEquals(Block.Video("https://cdn.example.com/v/abs/index.m3u8?format=redirect", "https://img.example.com/t.jpg", 72), p.blocks.single())
    }

    @Test fun pageVideoFromVideoTagAndPlainPageHasNone() {
        val tag = """<html><body><video poster="/p.jpg"><source src="/clip.mp4" type="video/mp4"></video></body></html>"""
        assertEquals(Block.Video("https://www.example.com/clip.mp4", "https://www.example.com/p.jpg", null), ExternalArticle.parse(tag, "https://www.example.com/a/b", null).blocks.single())
        assertTrue(ExternalArticle.parse("<html><body><p>hi</p></body></html>", "https://www.example.com/a/b", null).blocks.isEmpty())
    }
}
