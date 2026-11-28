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

    @Test fun keepsStoryPictures() {
        assertEquals(listOf("https://cdn.example.com/u/can.jpg"), images(blocks("""<figure><img src="https://cdn.example.com/u/can.jpg" width="900" height="600"><figcaption>The can</figcaption></figure>""")))
        assertEquals(listOf("https://cdn.example.com/u/big.jpg"), images(blocks("""<a href="https://cdn.example.com/u/big-full.jpg"><img src="https://cdn.example.com/u/big.jpg" width="900" height="600"></a>""")))
        assertEquals(listOf("https://cdn.example.com/u/same.jpg"), images(blocks("""<a href="https://news.example.com/photos/1"><img src="https://cdn.example.com/u/same.jpg" width="900" height="600"></a>""")))
    }
}
