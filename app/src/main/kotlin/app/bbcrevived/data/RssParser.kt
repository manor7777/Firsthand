package app.bbcrevived.data

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Parses BBC RSS 2.0 (with media:thumbnail). Public feed format, stable for many years. */
object RssParser {
    fun parse(xml: String, category: String): List<Story> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Hardening: no DTDs / external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val items = doc.getElementsByTagName("item")
        val out = ArrayList<Story>()
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            fun text(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val link = text("link").substringBefore('?')
            if (link.isEmpty()) continue
            var img: String? = null
            val thumbs = e.getElementsByTagName("media:thumbnail")
            for (t in 0 until thumbs.length) {
                val el = thumbs.item(t) as Element
                val u = el.getAttribute("url")
                val w = el.getAttribute("width").toIntOrNull() ?: 0
                if (u.isNotEmpty() && (img == null || w >= 240)) img = u
            }
            out += Story(
                id = link,
                title = text("title"),
                summary = text("description"),
                url = link,
                imageUrl = img?.replace("/240/", "/660/"),
                category = category,
                publishedMillis = parseDate(text("pubDate")),
                isLive = "/live/" in link,
            )
        }
        return out
    }

    private fun parseDate(s: String): Long = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH).parse(s)!!.time
    }.getOrDefault(0L)
}
