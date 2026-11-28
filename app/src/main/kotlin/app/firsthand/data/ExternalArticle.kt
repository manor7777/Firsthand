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
    class Parsed(val blocks: List<Block>, val authors: List<Author>, val published: String?, val updated: String?, val paywalled: Boolean = false)

    /** Article text from an HTML fragment or whole page. */
    fun blocks(html: String, baseUrl: String): List<Block> {
        val doc = Jsoup.parse(html, baseUrl)
        val root = container(doc.body() ?: return emptyList())
        return extract(root)
    }

    fun parse(html: String, url: String, source: String?): Parsed {
        val doc = Jsoup.parse(html, url)
        val all = ldAll(doc)
        val ld = all.filter(::isArticle)
        val body = doc.body() ?: return Parsed(emptyList(), emptyList(), null, null)
        var blocks = extract(container(body))
        if (!readable(blocks)) {
            // Some publishers put the whole text in JSON-LD instead of (or before) hydrating the page.
            val text = ld.firstNotNullOfOrNull { it.str("articleBody") }
            if (text != null && text.length > 400) blocks = text.split(Regex("\\n{1,}")).map { it.trim() }.filter { it.length > 1 }.map { Block.Paragraph(it) }
        }
        val authors = ld.flatMap { authorsOf(it["author"]) }.flatMap { RssParser.splitNames(it) }
            .map { it.copy(source = source) }.distinctBy { Author.key(it.name) }
        val video = findVideo(doc, all, url)
        val text = if (readable(blocks)) blocks else emptyList()
        // A page that is mostly a video: the player, then the text if there is any, else the page's description.
        val described = if (text.isEmpty() && video != null) listOfNotNull(
            (all.firstNotNullOfOrNull { it.str("description") } ?: doc.selectFirst("meta[property=og:description]")?.attr("content"))
                ?.trim()?.takeIf { it.length >= 40 && !it.equals(all.firstNotNullOfOrNull { o -> o.str("name") ?: o.str("headline") }, true) }?.let { Block.Paragraph(it) }
        ) else emptyList()
        return Parsed(
            listOfNotNull(video) + text + described, authors,
            ld.firstNotNullOfOrNull { it.str("datePublished") }?.let(::iso), ld.firstNotNullOfOrNull { it.str("dateModified") }?.let(::iso),
            paywalled(ld),
        )
    }

    /** schema.org's own flag: `isAccessibleForFree` false on the article (as a boolean or the string "False"). */
    internal fun paywalled(ld: List<JsonObject>) = ld.any {
        val v = it["isAccessibleForFree"] as? JsonPrimitive
        v != null && v.content.equals("false", true)
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

    private fun isArticle(o: JsonObject) =
        o.str("@type")?.contains("Article", true) == true || (o["@type"] as? JsonArray)?.any { it.str()?.contains("Article", true) == true } == true

    private fun isVideoObject(o: JsonObject) =
        o.str("@type")?.contains("VideoObject", true) == true || (o["@type"] as? JsonArray)?.any { it.str()?.contains("VideoObject", true) == true } == true

    private val streamExt = Regex("(?i)\\.(m3u8|mp4|webm|mov|m4v)(\\?|#|$)")

    /**
     * The page's own video as a direct address, from the standard places: a schema.org VideoObject's contentUrl, a `<video>` tag
     * (or its `<source>`), or an og:video meta tag that points at a file. Embed-only players (YouTube and the like) are not found.
     */
    internal fun findVideo(doc: org.jsoup.nodes.Document, ld: List<JsonObject>, pageUrl: String): Block.Video? {
        fun abs(u: String?) = u?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.net.URI(pageUrl).resolve(it).toString() }.getOrNull() }
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        fun image(e: JsonElement?): String? = when (e) {
            is JsonPrimitive -> e.str(); is JsonArray -> e.firstNotNullOfOrNull { image(it) }; is JsonObject -> e.str("url") ?: e.str("contentUrl"); else -> null
        }
        val objs = ld.filter(::isVideoObject)
        val o = objs.firstOrNull { abs(it.str("contentUrl")) != null && it.str("url")?.substringBefore('?') == pageUrl.substringBefore('?') }
            ?: objs.firstOrNull { abs(it.str("contentUrl")) != null }
        val tag = doc.selectFirst("video[src], video source[src]")
        val metaUrl = doc.select("meta[property]").filter { it.attr("property") in setOf("og:video", "og:video:url", "og:video:secure_url") }.mapNotNull { m ->
            val v = abs(m.attr("content")) ?: return@mapNotNull null
            val type = doc.selectFirst("meta[property=og:video:type]")?.attr("content").orEmpty()
            v.takeIf { streamExt.containsMatchIn(it) || type.startsWith("video/") }
        }.firstOrNull()
        val url = abs(o?.str("contentUrl")) ?: abs(tag?.attr("src"))?.takeIf { !it.startsWith("blob:") } ?: metaUrl ?: return null
        val poster = abs(image(o?.get("thumbnailUrl"))) ?: abs(doc.selectFirst("video[poster]")?.attr("poster"))
            ?: abs(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val dur = o?.str("duration")?.let { runCatching { java.time.Duration.parse(it).seconds.toInt() }.getOrNull() }
        return Block.Video(url, poster, dur)
    }

    /** Every JSON-LD object on the page (handles @graph and arrays). */
    private fun ldAll(doc: org.jsoup.nodes.Document): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonArray -> e.forEach(::walk)
                is JsonObject -> { out += e; (e["@graph"] as? JsonArray)?.forEach(::walk) }
                else -> {}
            }
        }
        for (s in doc.select("script[type=application/ld+json]")) runCatching { walk(Json.parseToJsonElement(s.data())) }
        return out
    }

    private fun authorsOf(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.flatMap(::authorsOf)
        is JsonObject -> listOfNotNull(e.str("name"))
        is JsonPrimitive -> listOfNotNull(e.str())
        else -> emptyList()
    }

    private val bodySelectors = listOf(
        "[itemprop=articleBody]", "[class*=article-body]", "[class*=articleBody]", "[class*=article__body]", "[class*=story-body]",
        "[class*=news-body]", "[class*=entry-content]", "[class*=post-content]", "[data-component=text-block]",
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

    private val instagramPost = Regex("(?i)instagram\\.com/(?:[\\w.]+/)?(p|reel|reels|tv)/([\\w-]+)")

    /** The embeddable address of an Instagram post, from a `blockquote.instagram-media` (or an Instagram `<iframe>`), else null. */
    internal fun instagramEmbed(el: Element): String? {
        val isQuote = el.tagName() == "blockquote" && el.hasClass("instagram-media")
        val isFrame = el.tagName() == "iframe" && instagramPost.containsMatchIn(el.attr("src").ifEmpty { el.attr("data-src") })
        if (!isQuote && !isFrame) return null
        val candidates = if (isFrame) listOf(el.attr("src").ifEmpty { el.attr("data-src") })
        else listOf(el.attr("data-instgrm-permalink")) + el.select("a[href]").map { it.attr("href") }
        val m = candidates.firstNotNullOfOrNull { instagramPost.find(it) } ?: return null
        val kind = if (m.groupValues[1].equals("reels", true)) "reel" else m.groupValues[1].lowercase()
        return "https://www.instagram.com/$kind/${m.groupValues[2]}/embed/"
    }

    private val adTokens = setOf(
        "ad", "ads", "adv", "advert", "adverts", "advertisement", "advertisements", "advertising", "adslot", "adunit", "adsbygoogle",
        "dfp", "mpu", "leaderboard", "sponsored", "taboola", "outbrain", "doubleclick",
    )
    private val adCompound = Regex("(?i)(\\bad[-_ ]?(banner|slot|unit|container|wrapper|placeholder|break|label|space)|(banner|advert\\w*)[-_ ]?ad\\b|adbanner|bannerad)")
    private val adSrc = Regex("(?i)(doubleclick|googlesyndication|googleadservices|adservice|adserver|adsystem|/ads?/|[/_.-]ads?[/_.-]|ad[-_]?banner|banner[-_]?ad|pubads|criteo|taboola|outbrain)")

    /** An element that is, or sits inside, an advertising slot (by class / id / data attributes). */
    internal fun adMarked(e: Element): Boolean {
        val marks = e.className() + " " + e.id() + " " + e.attr("data-ad") + " " + e.attr("data-ad-slot") + " " + e.attr("data-testid") + " " + e.attr("data-component") + " " + e.attr("data-google-query-id")
        if (e.hasAttr("data-ad") || e.hasAttr("data-ad-slot") || e.hasAttr("data-google-query-id") || e.hasAttr("data-ad-client")) return true
        if (e.tagName() == "ins" && e.hasClass("adsbygoogle")) return true
        if (e.tagName() == "a" && e.attr("rel").contains("sponsored", true)) return true
        return marks.split(Regex("[^A-Za-z0-9]+")).any { it.lowercase() in adTokens } || adCompound.containsMatchIn(marks)
    }

    private val endHeading = Regex("(?i)^\\s*(more (from|stories|news|on|like this)|related (stories|articles|news|posts|reading|content)|you (may|might|could) also (like|enjoy)|recommended( for you| stories| reading)?|read next|don.t miss|from around the web|sponsored (content|stories|links)|promoted (stories|content)|around the web)\\b")

    /** True for the heading that opens the tail of a page (more stories, recommendations), not part of the article. */
    internal fun endOfStory(heading: String) = heading.length < 80 && endHeading.containsMatchIn(heading)

    private fun extract(root: Element): List<Block> {
        val out = ArrayList<Block>()
        val seen = HashSet<String>()
        fun skipped(e: Element): Boolean {
            var p: Element? = e
            while (p != null && p !== root) {
                val tag = p.tagName()
                if (tag in setOf("aside", "nav", "footer", "form", "header", "script", "style", "noscript", "button", "iframe")) return true
                if (adMarked(p)) return true
                val marks = p.className() + " " + p.id()
                // Only treat a wrapper as noise when it is small; the real body can legitimately carry words like "share".
                if (noise.containsMatchIn(marks) && p.select("p").sumOf { it.text().length } < 600) return true
                p = p.parent()
            }
            return false
        }
        for (el in root.select("p, h2, h3, blockquote, li, figure, img, iframe")) {
            // The end of the story: a "More from ..." / "Related stories" heading after the text. What follows is a list of other
            // stories and adverts (often one that keeps loading more), never part of this article.
            if (el.tagName() in setOf("h2", "h3") && endOfStory(el.text()) && out.count { it is Block.Paragraph } >= 3 && out.sumOf { (it as? Block.Paragraph)?.text?.length ?: 0 } >= 300) break
            // Instagram posts: shown as a real embed. They sit in "social" wrappers and an iframe is normally skipped, so check first.
            val insta = instagramEmbed(el)
            if (insta != null) {
                var p = el.parent(); var hidden = false
                while (p != null && p !== root) { if (p.tagName() in setOf("aside", "nav", "footer", "form", "header") || adMarked(p)) hidden = true; p = p.parent() }
                if (!hidden && seen.add(insta)) out += Block.Embed(insta)
                continue
            }
            if (el.tagName() == "iframe") continue
            if (skipped(el)) continue
            when (el.tagName()) {
                "p" -> if (el.parents().none { it.tagName() == "blockquote" || it.tagName() == "figure" }) text(el)?.let { if (seen.add(it)) out += Block.Paragraph(it) }
                "li" -> if (el.parent()?.tagName() in setOf("ul", "ol") && el.select("p").isEmpty()) {
                    // Link-only list items are navigation / "read more" lists, not prose.
                    val t = text(el, minLen = 40)
                    if (t != null && el.select("a").sumOf { it.text().length } < t.length * 0.6 && seen.add(t)) out += Block.Paragraph("• $t")
                }
                "h2", "h3" -> text(el, minLen = 4)?.takeIf { it.length < 140 && !boilerplate.matches(it) && seen.add(it) }?.let { out += Block.Heading(it) }
                "blockquote" -> if (el.parents().none { it.tagName() == "blockquote" }) text(el, minLen = 20)?.takeIf { seen.add(it) }?.let { out += Block.Quote(it) }
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

    /** Promo words in a file name, class or id. */
    private val promoNames = Regex("(?i)(^|[^a-z])(banners?|promo(tion|tional)?s?|house-?ads?|advert\\w*|sponsor\\w*)([^a-z]|$)")
    /** In alt text only the unmistakable labels count: news photos legitimately mention campaigns, advertising and sponsors. */
    private val promoAlt = Regex("(?i)(\\bbanner\\b|^\\s*(advert\\w*|sponsor\\w*|promo\\w*)\\b)")

    /**
     * A publisher's own promotion ("Advertise with us", event banners): not a photo of the story. Recognised by promo words in the picture's
     * file name, class or id (or a nearby wrapper's), an alt text that says "banner" or opens with "Advertisement"/"Sponsored", or a
     * clickable picture without a caption that leads to another site.
     */
    internal fun promoImage(img: Element, caption: String?): Boolean {
        val names = img.attr("src").substringBefore('?').substringAfterLast('/') + " " + img.attr("data-src").substringBefore('?').substringAfterLast('/') +
            " " + img.className() + " " + img.id()
        if (promoNames.containsMatchIn(names) || promoAlt.containsMatchIn(img.attr("alt"))) return true
        if (img.parents().take(3).any { promoNames.containsMatchIn(it.className() + " " + it.id()) }) return true
        val link = img.parents().firstOrNull { it.tagName() == "a" }?.absUrl("href").orEmpty()
        if (link.isNotEmpty() && caption.isNullOrBlank()) {
            fun host(u: String) = runCatching { java.net.URI(u).host?.lowercase()?.removePrefix("www.") }.getOrNull()
            val page = host(img.baseUri()); val to = host(link)
            if (page != null && to != null && !(to == page || to.endsWith(".$page") || page.endsWith(".$to")) && !Regex("(?i)\\.(jpe?g|png|webp|gif)(\\?|$)").containsMatchIn(link)) return true
        }
        return false
    }

    private fun image(img: Element?, caption: String?): Block.Image? {
        img ?: return null
        val src = (img.absUrl("src").ifEmpty { img.absUrl("data-src") }.ifEmpty { img.attr("srcset").substringBefore(' ').let { if (it.startsWith("http")) it else "" } })
        if (!src.startsWith("http") || src.endsWith(".svg") || src.contains("pixel", true) || src.contains("logo", true) || src.contains("avatar", true)) return null
        val w = img.attr("width").toIntOrNull(); if (w != null && w < 150) return null
        // Banner ads: known ad-network addresses, or the stock banner shapes (728x90, 320x50, 970x250...) when the size is given.
        if (adSrc.containsMatchIn(src)) return null
        val h = img.attr("height").toIntOrNull()
        if (w != null && h != null && h > 0 && (w.toFloat() / h >= 4f || (w to h) in setOf(300 to 250, 336 to 280, 300 to 600))) return null
        if (img.parents().any { it.tagName() == "a" && it.attr("rel").contains("sponsored", true) }) return null
        if (promoImage(img, caption)) return null
        return Block.Image(src, caption?.trim()?.takeIf { it.isNotEmpty() })
    }
}
