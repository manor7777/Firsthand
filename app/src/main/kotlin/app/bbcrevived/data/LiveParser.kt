package app.bbcrevived.data

import app.bbcrevived.data.NextData.str
import kotlinx.serialization.json.*

/**
 * Best-effort live-page timeline. A "post" is any object with an id, a timestamp-ish key and renderable content.
 * Returns empty if the format isn't recognised; the UI then falls back to an inline embed of the page.
 */
object LiveParser {
    private val timeKeys = arrayOf("firstPublished", "published", "lastPublished", "timestamp", "date", "time")

    fun parse(html: String, url: String): List<LivePost> {
        val root = NextData.extract(html) ?: return emptyList()
        val posts = LinkedHashMap<String, LivePost>()
        NextData.walk(root) { o, _ ->
            val id = o.str("id", "assetId", "urn") ?: return@walk
            val time = o.str(*timeKeys) ?: return@walk
            val blocks = ArticleParser.blocksOf(o, url)
            if (blocks.isEmpty()) return@walk
            val pinned = (o["pinned"] as? JsonPrimitive)?.booleanOrNull == true || (o["isPinned"] as? JsonPrimitive)?.booleanOrNull == true
            posts.putIfAbsent(id, LivePost(id, time, o.str("title", "headline"), blocks, pinned))
        }
        // Pinned first, then newest (ISO timestamps sort lexically).
        return posts.values.sortedWith(compareByDescending<LivePost> { it.pinned }.thenByDescending { it.time.orEmpty() })
    }
}
