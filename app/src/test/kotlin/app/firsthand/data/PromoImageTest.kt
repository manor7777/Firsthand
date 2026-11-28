package app.firsthand.data

import org.junit.Assert.*
import org.junit.Test

class PromoImageTest {
    private fun blocks(body: String): List<Block> {
        val p = "<p>" + "A long enough paragraph of real story text. ".repeat(4) + "</p>"
        return ExternalArticle.blocks("<html><body><article>$p$body$p$p</article></body></html>", "https://news.example.com/a/1")
    }
    private fun images(b: List<Block>) = b.filterIsInstance<Block.Image>().map { it.url }

    @Test fun dropsPublishersOwnBanners() {
        assertEquals(emptyList<String>(), images(blocks("""<img src="https://cdn.example.com/uploads/festive-campaign-banner.jpg" width="900" height="300">""")))
        assertEquals(emptyList<String>(), images(blocks("""<img src="https://cdn.example.com/u/x1.jpg" alt="Advertise with us">""")))
        assertEquals(emptyList<String>(), images(blocks("""<div class="promo-wrap"><img src="https://cdn.example.com/u/x2.jpg" width="900" height="400"></div>""")))
        assertEquals(emptyList<String>(), images(blocks("""<a href="https://other-agency.com/book"><img src="https://cdn.example.com/u/x3.jpg" width="900" height="400"></a>""")))
    }

    @Test fun dropsStoryboard18Banner() {
        val tag = """<div class="rhs-banner"><picture><img src="https://images.storyboard18.com/storyboard18/2026/10/Storyboard-18-Festive-Final-Banner_300x100-1-2026-10-0ca198953adabefb891bc990cdc99747.gif" width="300" height="100" alt="RHS Festive Banner" title="RHS Festive Banner" class="top-banner-img"/></picture></div>"""
        assertEquals(emptyList<String>(), images(blocks(tag)))
    }

    @Test fun keepsPhotosThatMentionCampaigns() {
        val u = "https://cdn.example.com/u/outdoor-1019x573.jpg"
        assertEquals(listOf(u), images(blocks("""<img src="$u" width="900" height="500" alt="Countdown-led outdoor campaign by an advertising agency, sponsored by Zepto">""")))
    }

    @Test fun keepsStoryPictures() {
        assertEquals(listOf("https://cdn.example.com/u/can.jpg"), images(blocks("""<figure><img src="https://cdn.example.com/u/can.jpg" width="900" height="600"><figcaption>The can</figcaption></figure>""")))
        assertEquals(listOf("https://cdn.example.com/u/big.jpg"), images(blocks("""<a href="https://cdn.example.com/u/big-full.jpg"><img src="https://cdn.example.com/u/big.jpg" width="900" height="600"></a>""")))
        assertEquals(listOf("https://cdn.example.com/u/same.jpg"), images(blocks("""<a href="https://news.example.com/photos/1"><img src="https://cdn.example.com/u/same.jpg" width="900" height="600"></a>""")))
    }

    @Test fun stopsAtMoreFromList() {
        fun para(n: Int) = "<p>" + "Real story text number $n that is long enough to count as an article paragraph. ".repeat(3) + "</p>"
        val tail = (1..20).joinToString("") { """<div><a href="/s/$it"><h3>Other story number $it with a long headline</h3><img src="https://cdn.example.com/u/other$it.jpg" width="400" height="226"></a></div>""" }
        val b = ExternalArticle.blocks("<html><body><main>${para(1)}${para(2)}${para(3)}<section><h2>More from <span>Example</span></h2>$tail</section></main></body></html>", "https://news.example.com/a/1")
        assertEquals(3, b.filterIsInstance<Block.Paragraph>().size)
        assertEquals(0, b.filterIsInstance<Block.Heading>().size)
        assertEquals(0, b.filterIsInstance<Block.Image>().size)
    }

    @Test fun keepsHeadingsInsideTheStory() {
        assertTrue(ExternalArticle.endOfStory("More from Storyboard18"))
        assertTrue(ExternalArticle.endOfStory("Related stories"))
        assertFalse(ExternalArticle.endOfStory("A new front in the zero-sugar cola market"))
        assertFalse(ExternalArticle.endOfStory("Trending Topics"))
        assertFalse(ExternalArticle.endOfStory("More than a million people lost power"))
    }
}
