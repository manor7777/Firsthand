package app.firsthand.data

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parses RSS 2.0, RSS 1.0/RDF and Atom feeds into [Story]s. BBC's feeds were the first target; the same parser reads
 * any publisher's feed (Reach, Guardian, Sky, ...): images from media:content / media:thumbnail / enclosure,
 * writers from dc:creator / author, and full text from content:encoded when the feed ships it.
 */
object RssParser {
    class Parsed(val title: String?, val stories: List<Story>)

    fun parse(xml: String, category: String, source: String? = null): List<Story> = parseFeed(xml, category, source).stories

    fun parseFeed(xml: String, category: String, source: String? = null): Parsed {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Hardening: no DTDs / external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml.trimStart('﻿', ' ', '\n', '\r', '\t').toByteArray()))
        var items = doc.getElementsByTagName("item")
        val atom = items.length == 0
        if (atom) items = doc.getElementsByTagName("entry")
        val channelTitle = doc.getElementsByTagName("title").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val out = ArrayList<Story>()
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            val link = (if (atom) atomLink(e) else direct(e, "link").ifEmpty { direct(e, "guid").takeIf { it.startsWith("http") }.orEmpty() })
                .substringBefore('#').trim()
            if (!link.startsWith("http")) continue
            // BBC appends tracking queries; other publishers' query strings can be the article id, so only strip BBC's.
            val url = if ("bbc.co.uk" in link || "bbc.com" in link) link.substringBefore('?') else link
            val html = direct(e, "content:encoded").ifEmpty { if (atom) direct(e, "content") else "" }
            val summaryRaw = direct(e, "description").ifEmpty { direct(e, "summary") }
            out += Story(
                id = url,
                title = plain(direct(e, "title")),
                summary = plain(summaryRaw),
                url = url,
                imageUrl = image(e, summaryRaw + html),
                category = if (source != null) direct(e, "category").ifEmpty { sectionFromUrl(url) ?: category } else category,
                publishedMillis = parseDate(direct(e, "pubDate").ifEmpty { direct(e, "published").ifEmpty { direct(e, "updated").ifEmpty { direct(e, "dc:date") } } }),
                isLive = source == null && "/live/" in url,
                source = source,
                authors = if (source != null) authors(e).map { it.copy(source = source) } else emptyList(),
                contentHtml = html.takeIf { it.length > 400 },
                tags = tags(e),
            )
        }
        return Parsed(channelTitle, out)
    }

    /** First direct child with this tag (item-level, so a feed's own <title> or <link> is never mistaken for an item's). */
    private fun direct(e: Element, tag: String): String {
        var n = e.firstChild
        while (n != null) {
            if (n is Element && n.tagName == tag) return n.textContent.trim()
            n = n.nextSibling
        }
        return ""
    }

    private fun directAll(e: Element, tag: String): List<Element> {
        val out = ArrayList<Element>(); var n = e.firstChild
        while (n != null) { if (n is Element && n.tagName == tag) out += n; n = n.nextSibling }
        return out
    }

    private val dated = Regex("^https?://[^/]+/([a-z][a-z0-9-]{1,30})/\\d{4}/\\d{1,2}/\\d{1,2}/")
    private val shout = setOf("ai", "ml", "hpc", "os", "it", "uk", "us", "eu", "pc", "tv", "5g", "ev")

    /** For feeds that tag nothing: the section in a dated article address ("/security/2026/11/18/..." is Security), or null. */
    internal fun sectionFromUrl(url: String): String? =
        dated.find(url)?.groupValues?.get(1)?.split('-')?.joinToString(" ") {
            when { it == "and" -> "&"; it in shout -> it.uppercase(); else -> it.replaceFirstChar(Char::uppercase) }
        }

    /** Topic tags: every <category> plus comma-separated <media:keywords>, deduplicated. */
    internal fun tags(e: Element): List<String> =
        (directAll(e, "category").flatMap { listOf(it.textContent, it.getAttribute("term")) } + direct(e, "media:keywords").split(','))
            .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length in 2..80 }.distinctBy { it.lowercase() }

    private fun atomLink(e: Element): String {
        val links = directAll(e, "link")
        return (links.firstOrNull { it.getAttribute("rel") == "alternate" } ?: links.firstOrNull { it.getAttribute("rel").isEmpty() } ?: links.firstOrNull())
            ?.getAttribute("href").orEmpty()
    }

    /** Best picture: BBC thumbnails (upsized), else the widest media:content/thumbnail, enclosure image, or first <img> in the text. */
    private fun image(e: Element, text: String): String? {
        var best: String? = null; var bestW = -1
        for (tag in listOf("media:content", "media:thumbnail")) {
            for (el in directAll(e, tag)) {
                val u = el.getAttribute("url")
                val type = el.getAttribute("type")
                if (u.isEmpty() || (type.isNotEmpty() && !type.startsWith("image")) || el.getAttribute("medium") in setOf("video", "audio")) continue
                val w = el.getAttribute("width").toIntOrNull() ?: 0
                // BBC lists several sizes; any >=240 wins (kept from the original parser). Elsewhere prefer the widest.
                if (best == null || w > bestW || (tag == "media:thumbnail" && w >= 240 && bestW < 240)) { best = u; bestW = w }
            }
        }
        if (best == null) best = directAll(e, "enclosure").firstOrNull { it.getAttribute("type").startsWith("image") }?.getAttribute("url")?.takeIf { it.isNotEmpty() }
        if (best == null) best = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
        return best?.replace("/240/", "/660/")?.replace("&amp;", "&")
    }

    /**
     * Writers of one item. Reach: `<author>newsdesk@site.com (Ann Smith, Bob Jones, Local Democracy Reporter)</author>`;
     * most others: `<dc:creator>Ann Smith</dc:creator>` (one per writer or comma separated); Atom: `<author><name>Ann Smith</name></author>`.
     */
    internal fun authors(e: Element): List<Author> {
        val raw = ArrayList<String>()
        directAll(e, "dc:creator").forEach { raw += it.textContent }
        directAll(e, "author").forEach { a ->
            val name = a.getElementsByTagName("name").item(0)?.textContent
            raw += name ?: a.textContent
        }
        return raw.flatMap(::splitNames).distinctBy { Author.key(it.name) }
    }

    private val role = Regex("(?i)\\b(reporter|editor|correspondent|writer|staff|newsdesk|news desk|team|desk|agency|reach|ldrs?|democracy|contributor|columnist|producer)\\b")

    /** "newsdesk@x.com (A B, C D, Local Democracy Reporter, A B - Local Democracy Reporter)" -> A B (Local Democracy Reporter), C D. */
    fun splitNames(raw: String): List<Author> {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return emptyList()
        // RSS <author> is "email (Name)"; take what's in the brackets. A bare email address has no name.
        Regex("^\\S+@\\S+\\s*\\((.*)\\)$").find(s)?.let { s = it.groupValues[1] }
        if (Regex("^\\S+@\\S+$").matches(s)) return emptyList()
        val found = s.split(',', ';').mapNotNull { part ->
            // "Pat Quinn - Local Democracy Reporter": the name, plus the role if it reads like one.
            val bits = part.trim().split(Regex("\\s+[-\u2013\u2014|]\\s+"), limit = 2)
            val name = bits[0].trim().removePrefix("By ").removePrefix("by ").trim()
            when {
                name.length < 3 || name.length > 60 || '@' in name -> null
                role.containsMatchIn(name) -> null // a bare role ("Local Democracy Reporter") is not a person
                else -> Author(name, bits.getOrNull(1)?.trim()?.takeIf { role.containsMatchIn(it) })
            }
        }
        // A writer named twice (bare, then with a role) is one person; keep the entry that has the role.
        return found.groupBy { Author.key(it.name) }.values.map { g -> g.firstOrNull { it.role != null } ?: g.first() }
    }

    private fun plain(s: String): String =
        s.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss",
    )

    internal fun parseDate(s: String): Long {
        if (s.isBlank()) return 0L
        runCatching { return java.time.Instant.parse(s).toEpochMilli() }
        runCatching { return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        for (p in dateFormats) {
            val t = runCatching {
                SimpleDateFormat(p, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.trim())?.time
            }.getOrNull()
            if (t != null) return t
        }
        return 0L
    }
}
