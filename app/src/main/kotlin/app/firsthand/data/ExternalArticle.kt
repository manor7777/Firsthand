package app.firsthand.data

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Turns another publisher's article into native [Block]s. No site-specific rules: it uses, in order, the full text a feed
 * shipped (content:encoded), then the page's own markup (the `<article>` / article-body element's paragraphs, headings,
 * quotes and pictures), and keeps the writers and dates from the page's JSON-LD when present. If nothing readable is
 * found the reader falls back to the feed summary with an "Open original" button.
 */
object ExternalArticle {
    class Parsed(val blocks: List<Block>, val authors: List<Author>, val published: String?, val updated: String?)

    /** Article text from an HTML fragment or whole page. */
    fun blocks(html: String, baseUrl: String): List<Block> {
        val doc = Jsoup.parse(html, baseUrl)
        val root = container(doc.body() ?: return emptyList())
        return extract(root)
    }

    fun parse(html: String, url: String, source: String?): Parsed {
        val doc = Jsoup.parse(html, url)
        val ld = ldJson(doc)
        val body = doc.body() ?: return Parsed(emptyList(), emptyList(), null, null)
        var blocks = extract(container(body))
        if (!readable(blocks)) {
            // Some publishers put the whole text in JSON-LD instead of (or before) hydrating the page.
            val text = ld.firstNotNullOfOrNull { it.str("articleBody") }
            if (text != null && text.length > 400) blocks = text.split(Regex("\\n{1,}")).map { it.trim() }.filter { it.length > 1 }.map { Block.Paragraph(it) }
        }
        val authors = ld.flatMap { authorsOf(it["author"]) }.flatMap { RssParser.splitNames(it) }
            .map { it.copy(source = source) }.distinctBy { Author.key(it.name) }
        return Parsed(
            if (readable(blocks)) blocks else emptyList(), authors,
            ld.firstNotNullOfOrNull { it.str("datePublished") }?.let(::iso), ld.firstNotNullOfOrNull { it.str("dateModified") }?.let(::iso),
        )
    }

    /** Enough real text to show instead of the summary: a few paragraphs, or one long one. */
    fun readable(b: List<Block>): Boolean {
        val chars = b.filterIsInstance<Block.Paragraph>().sumOf { it.text.length }
        return b.count { it is Block.Paragraph } >= 3 && chars >= 500
    }

    private fun iso(s: String): String? = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toString() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(s).toString() }.getOrNull()

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.str(k: String): String? = this[k].str()

    /** NewsArticle-ish objects from every JSON-LD script on the page (handles @graph and arrays). */
    private fun ldJson(doc: org.jsoup.nodes.Document): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonArray -> e.forEach(::walk)
                is JsonObject -> { out += e; (e["@graph"] as? JsonArray)?.forEach(::walk) }
                else -> {}
            }
        }
        for (s in doc.select("script[type=application/ld+json]")) runCatching { walk(Json.parseToJsonElement(s.data())) }
        return out.filter { o -> o.str("@type")?.contains("Article", true) == true || (o["@type"] as? JsonArray)?.any { it.str()?.contains("Article", true) == true } == true }
    }

    private fun authorsOf(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.flatMap(::authorsOf)
        is JsonObject -> listOfNotNull(e.str("name"))
        is JsonPrimitive -> listOfNotNull(e.str())
        else -> emptyList()
    }

    private val bodySelectors = listOf(
        "[itemprop=articleBody]", "[class*=article-body]", "[class*=articleBody]", "[class*=article__body]", "[class*=story-body]",
        "[class*=entry-content]", "[class*=post-content]", "[data-component=text-block]",
    )

    /** The element holding the story: a known article-body hook, else the `<article>` with most text, else the page body. */
    private fun container(body: Element): Element {
        for (sel in bodySelectors) {
            val el = body.select(sel).maxByOrNull { it.select("p").sumOf { p -> p.text().length } }
            if (el != null && el.select("p").sumOf { it.text().length } >= 400) return el
        }
        body.select("article").maxByOrNull { it.select("p").sumOf { p -> p.text().length } }
            ?.takeIf { it.select("p").sumOf { p -> p.text().length } >= 300 }?.let { return it }
        return body.selectFirst("main") ?: body
    }

    private val noise = Regex("(?i)(related|newsletter|promo|advert|sponsor|comment|share|social|signup|sign-up|subscribe|recommend|outbrain|taboola|trending|more-from|read-more|byline|author-bio|breadcrumb|cookie|consent|paywall|toolbar)")
    private val boilerplate = Regex("(?i)^(read more|read next|read also|more:|related:|advertisement|sign up|subscribe|follow us|follow @|get the latest|click here|share this|join our|download our|get our|download the app|for more stories|more from|have your say|comments? (are|have)|you can |.{0,60}\\bnewsletter\\b).*")

    private fun extract(root: Element): List<Block> {
        val out = ArrayList<Block>()
        val seen = HashSet<String>()
        fun skipped(e: Element): Boolean {
            var p: Element? = e
            while (p != null && p !== root) {
                val tag = p.tagName()
                if (tag in setOf("aside", "nav", "footer", "form", "header", "script", "style", "noscript", "button", "iframe")) return true
                val marks = p.className() + " " + p.id()
                // Only treat a wrapper as noise when it is small; the real body can legitimately carry words like "share".
                if (noise.containsMatchIn(marks) && p.select("p").sumOf { it.text().length } < 600) return true
                p = p.parent()
            }
            return false
        }
        for (el in root.select("p, h2, h3, blockquote, li, figure, img")) {
            if (skipped(el)) continue
            when (el.tagName()) {
                "p" -> if (el.parents().none { it.tagName() == "blockquote" || it.tagName() == "figure" }) text(el)?.let { if (seen.add(it)) out += Block.Paragraph(it) }
                "li" -> if (el.parent()?.tagName() in setOf("ul", "ol") && el.select("p").isEmpty()) {
                    // Link-only list items are navigation / "read more" lists, not prose.
                    val t = text(el, minLen = 40)
                    if (t != null && el.select("a").sumOf { it.text().length } < t.length * 0.6 && seen.add(t)) out += Block.Paragraph("• $t")
                }
                "h2", "h3" -> text(el, minLen = 4)?.takeIf { it.length < 140 && !boilerplate.matches(it) && seen.add(it) }?.let { out += Block.Heading(it) }
                "blockquote" -> text(el, minLen = 20)?.takeIf { seen.add(it) }?.let { out += Block.Quote(it) }
                "figure" -> image(el.selectFirst("img"), el.selectFirst("figcaption")?.text())?.takeIf { seen.add(it.url) }?.let { out += it }
                "img" -> if (el.parents().none { it.tagName() == "figure" }) image(el, null)?.takeIf { seen.add(it.url) }?.let { out += it }
            }
        }
        return out
    }

    private fun text(el: Element, minLen: Int = 25): String? {
        val t = el.text().replace(Regex("\\s+"), " ").trim()
        return t.takeIf { it.length >= minLen && !boilerplate.matches(it) }
    }

    private fun image(img: Element?, caption: String?): Block.Image? {
        img ?: return null
        val src = (img.absUrl("src").ifEmpty { img.absUrl("data-src") }.ifEmpty { img.attr("srcset").substringBefore(' ').let { if (it.startsWith("http")) it else "" } })
        if (!src.startsWith("http") || src.endsWith(".svg") || src.contains("pixel", true) || src.contains("logo", true) || src.contains("avatar", true)) return null
        val w = img.attr("width").toIntOrNull(); if (w != null && w < 150) return null
        return Block.Image(src, caption?.trim()?.takeIf { it.isNotEmpty() })
    }
}
