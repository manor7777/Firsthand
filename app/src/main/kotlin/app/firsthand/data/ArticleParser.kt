package app.firsthand.data

import app.firsthand.data.InitialData.str
import kotlinx.serialization.json.*

/** Converts BBC's block model (type/model/blocks) into native [Block]s. */
object ArticleParser {
    private val skip = setOf("headline", "byline", "metadata", "topicList", "promoList", "link", "contributor", "caption", "advert")
    private val embeds = setOf("video", "mediaplayer", "media-player", "interactive", "embed", "include", "socialembed", "iframe", "webview", "chart", "poll", "oembed")

    fun parse(html: String, articleUrl: String): List<Block> {
        val root = InitialData.extract(html) ?: return emptyList()
        val blocks = (InitialData.widgets(root, "article").firstOrNull() as? JsonObject)
            ?.get("content")?.jsonObject?.get("model")?.jsonObject?.get("blocks") as? JsonArray ?: return emptyList()
        return convert(blocks, articleUrl)
    }

    /** Headline/summary/lead image from the page's Open Graph tags, for stories opened from a link. */
    fun meta(html: String): PageMeta {
        fun unescape(v: String) = v.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").trim()
        fun og(name: String): String? {
            val tag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.firstOrNull {
                Regex("(property|name)\\s*=\\s*[\"']og:$name[\"']", RegexOption.IGNORE_CASE).containsMatchIn(it)
            } ?: return null
            return Regex("content\\s*=\\s*\"([^\"]*)\"|content\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)
                ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.let(::unescape)?.takeIf { it.isNotEmpty() }
        }
        val title = og("title") ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let(::unescape)
        return PageMeta(title?.removeSuffix(" - BBC News")?.trim()?.takeIf { it.isNotEmpty() }, og("description"), og("image"))
    }

    /** Writers credited in the article's byline block (name + role, e.g. "Business reporter"). */
    fun byline(html: String): List<Author> {
        val blocks = articleBlocks(html) ?: return emptyList()
        return blocks.mapNotNull { it as? JsonObject }.filter { it.str("type") == "byline" }.flatMap { b ->
            ((b["model"] as? JsonObject)?.get("contributors") as? JsonArray).orEmpty().mapNotNull { c ->
                val name = c.str("name")?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.length >= 2 } ?: return@mapNotNull null
                Author(name, c.str("role")?.trim()?.takeIf { it.isNotEmpty() })
            }
        }.distinctBy { Author.key(it.name) }
    }

    /** First-published and last-updated times (ISO-8601) from the metadata block. */
    fun published(html: String): Pair<String?, String?> {
        val m = articleBlocks(html)?.mapNotNull { it as? JsonObject }?.firstOrNull { it.str("type") == "metadata" }?.get("model") as? JsonObject
            ?: return null to null
        return m.str("firstPublishedDateTime") to m.str("lastPublishedDateTime")
    }

    fun info(html: String): ArticleInfo = published(html).let { (p, u) -> ArticleInfo(byline(html), p, u) }

    private fun articleBlocks(html: String): JsonArray? {
        val root = InitialData.extract(html) ?: return null
        return (InitialData.widgets(root, "article").firstOrNull() as? JsonObject)
            ?.get("content")?.jsonObject?.get("model")?.jsonObject?.get("blocks") as? JsonArray
    }

    /** BBC's "Related topics" for an article (each has a followable topic feed). */
    fun topics(html: String): List<TopicRef> {
        val root = InitialData.extract(html) ?: return emptyList()
        val arr = (InitialData.widgets(root, "article").firstOrNull() as? JsonObject)?.get("topics") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { t ->
            val id = t.str("id") ?: t.str("url")?.substringAfterLast('/') ?: return@mapNotNull null
            val name = t.str("title") ?: return@mapNotNull null
            TopicRef(id, name).takeIf { Regex("[a-z0-9]{6,20}").matches(id) }
        }
    }

    /** The topic whose name is exactly the search term (case-insensitive), if any. */
    fun pickTopic(topics: List<TopicRef>, term: String): TopicRef? {
        val want = term.trim().lowercase()
        return if (want.isEmpty()) null else topics.firstOrNull { it.name.trim().lowercase() == want }
    }

    fun convert(blocks: JsonArray, url: String): List<Block> {
        val out = ArrayList<Block>()
        var embedded = false
        for (b in blocks) {
            val o = b as? JsonObject ?: continue
            val type = o.str("type") ?: continue
            val m = o["model"] as? JsonObject ?: continue
            when {
                type == "paragraph" -> text(m).takeIf { it.isNotBlank() }?.let { t -> out += wholeLink(m, t) ?: Block.Paragraph(t) }
                type == "fragment" -> m.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { out += Block.Paragraph(it) }
                type in setOf("subheadline", "crosshead", "subheading") -> text(m).takeIf { it.isNotBlank() }?.let { out += Block.Heading(it) }
                type == "text" -> (m["blocks"] as? JsonArray)?.let { out += convert(it, url) }
                type == "unorderedList" || type == "orderedList" ->
                    (m["blocks"] as? JsonArray)?.forEachIndexed { i, item ->
                        val t = leafText(item)
                        if (t.isNotBlank()) out += Block.Paragraph((if (type == "orderedList") "${i + 1}. " else "• ") + t)
                    }
                type.lowercase() in setOf("quote", "pullquote", "pull-quote") -> text(m).takeIf { it.isNotBlank() }?.let { out += Block.Quote(it) }
                type == "image" -> image(m)?.let { out += it }
                type in skip -> {}
                type.lowercase() in embeds -> if (!embedded) { out += Block.Embed(url); embedded = true }
            }
        }
        return out
    }

    /** The paragraph as a [Block.Link] when its only content is one BBC link (urlLink with a locator), else null. */
    private fun wholeLink(m: JsonObject, text: String): Block.Link? {
        val kids = (m["blocks"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        val link = kids.singleOrNull()?.takeIf { it.str("type") == "urlLink" } ?: return null
        val loc = (link["model"] as? JsonObject)?.str("locator")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = if (loc.startsWith("/")) "https://www.bbc.co.uk$loc" else loc
        return Block.Link(text, url).takeIf { url.startsWith("https://") || url.startsWith("http://") }
    }

    private fun image(m: JsonObject): Block.Image? {
        val src = (m["image"] as? JsonObject)?.str("src") ?: return null
        val caption = m["caption"]?.let(::leafText)?.takeIf { it.isNotBlank() }
        return Block.Image(src.replace(Regex("/standard/\\d+/"), "/standard/800/"), caption)
    }

    private fun text(m: JsonObject): String = m.str("text")?.trim() ?: leafText(m)

    /** Concatenates all string "text" leaves under an element (BBC nests fragments inside paragraphs). */
    internal fun leafText(el: JsonElement): String {
        val sb = StringBuilder()
        fun go(e: JsonElement) {
            when (e) {
                is JsonArray -> e.forEach(::go)
                is JsonObject -> {
                    val t = e["text"] as? JsonPrimitive
                    if (t != null && t.isString) sb.append(t.content) else e.values.forEach(::go)
                }
                else -> {}
            }
        }
        go(el)
        return sb.toString().trim()
    }
}
