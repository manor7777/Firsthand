package app.bbcrevived.data

import app.bbcrevived.data.InitialData.str
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

    fun convert(blocks: JsonArray, url: String): List<Block> {
        val out = ArrayList<Block>()
        var embedded = false
        for (b in blocks) {
            val o = b as? JsonObject ?: continue
            val type = o.str("type") ?: continue
            val m = o["model"] as? JsonObject ?: continue
            when {
                type == "paragraph" -> text(m).takeIf { it.isNotBlank() }?.let { out += Block.Paragraph(it) }
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
