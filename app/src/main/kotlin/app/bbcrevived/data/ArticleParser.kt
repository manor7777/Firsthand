package app.bbcrevived.data

import app.bbcrevived.data.NextData.str
import kotlinx.serialization.json.*

/**
 * Best-effort article body extraction from `__NEXT_DATA__`. Text-like containers become native blocks;
 * interactive/video/unknown block types become [Block.Embed] pointing at the article URL.
 */
object ArticleParser {
    private val textTypes = setOf("paragraph", "text")
    private val headTypes = setOf("subheadline", "crosshead", "subheading")
    private val quoteTypes = setOf("quote", "pullquote", "pull-quote")
    private val embedTypes = setOf("interactive", "embed", "video", "media-player", "iframe", "webview", "chart", "poll")

    fun parse(html: String, articleUrl: String): List<Block> {
        val root = NextData.extract(html) ?: return emptyList()
        val out = ArrayList<Block>()
        fun visit(el: JsonElement) {
            when (el) {
                is JsonArray -> el.forEach(::visit)
                is JsonObject -> {
                    val type = el.str("type")?.lowercase()
                    when {
                        type in textTypes -> leafText(el).takeIf { it.isNotBlank() }?.let { out += Block.Paragraph(it) }
                        type in headTypes -> leafText(el).takeIf { it.isNotBlank() }?.let { out += Block.Heading(it) }
                        type in quoteTypes -> leafText(el).takeIf { it.isNotBlank() }?.let { out += Block.Quote(it) }
                        type == "image" -> firstUrl(el)?.let { out += Block.Image(it, null) }
                        type in embedTypes -> out += Block.Embed(articleUrl)
                        else -> el.values.forEach(::visit)
                    }
                }
                else -> {}
            }
        }
        visit(root)
        return out
    }

    /** Concatenates all string "text" leaves under an element (BBC nests fragments inside paragraphs). */
    private fun leafText(el: JsonElement): String {
        val sb = StringBuilder()
        fun go(e: JsonElement) {
            when (e) {
                is JsonArray -> e.forEach(::go)
                is JsonObject -> {
                    (e["text"] as? JsonPrimitive)?.takeIf { it.isString }?.let { sb.append(it.content) }
                    e.forEach { (k, v) -> if (k != "text") go(v) }
                }
                else -> {}
            }
        }
        go(el)
        return sb.toString().trim()
    }

    private fun firstUrl(el: JsonElement): String? {
        var found: String? = null
        fun go(e: JsonElement) {
            if (found != null) return
            when (e) {
                is JsonArray -> e.forEach(::go)
                is JsonObject -> e.values.forEach(::go)
                is JsonPrimitive -> if (e.isString && e.content.startsWith("http") && Regex("""\.(jpe?g|png|webp)""").containsMatchIn(e.content)) found = e.content.replace("{width}", "800")
                else -> {}
            }
        }
        go(el)
        return found
    }
}
