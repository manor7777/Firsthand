package app.firsthand.data

import app.firsthand.data.InitialData.str
import kotlinx.serialization.json.*

/** Live pages: the "stream" widget holds the posts (newest first, first page only). */
object LiveParser {
    fun parse(html: String, url: String): List<LivePost> {
        val root = InitialData.extract(html) ?: return emptyList()
        val results = (InitialData.widgets(root, "stream").firstOrNull() as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
        val posts = results.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val id = o.str("urn") ?: return@mapNotNull null
            val blocks = ((o["content"] as? JsonObject)?.get("model") as? JsonObject)?.get("blocks") as? JsonArray
            val converted = withoutEmbeds(blocks?.let { ArticleParser.convert(it, url) }.orEmpty())
            if (converted.isEmpty()) return@mapNotNull null
            val opts = o["options"] as? JsonObject
            fun flag(k: String) = (opts?.get(k) as? JsonPrimitive)?.booleanOrNull == true
            LivePost(
                id = id,
                time = o.str("timestamp"),
                title = o["headline"]?.let(ArticleParser::leafText)?.takeIf { it.isNotBlank() },
                blocks = converted,
                pinned = flag("isPinned") || flag("pinned"),
                breaking = flag("isBreakingNews"),
            )
        }
        return posts.sortedByDescending { it.pinned } // stable: keeps the page's newest-first order
    }

    /**
     * Live posts are shown as plain text and pictures. Interactive and video blocks come through as an "embed" of the whole
     * BBC page, which would put the website's own header and player inside the post, so they are dropped.
     */
    internal fun withoutEmbeds(blocks: List<Block>): List<Block> = blocks.filterNot { it is Block.Embed }
}
