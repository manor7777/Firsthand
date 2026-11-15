package app.bbcrevived.data

import app.bbcrevived.data.InitialData.str
import java.time.Instant
import kotlinx.serialization.json.*

/** BBC search page (www.bbc.co.uk/search?q=...&d=NEWS_GNL): the "search-results" widget lists matching articles. */
object SearchParser {
    fun parse(html: String): List<Story> {
        val root = InitialData.extract(html) ?: return emptyList()
        val items = ((InitialData.widgets(root, "search-results").firstOrNull() as? JsonObject)
            ?.get("initialResults") as? JsonObject)?.get("items") as? JsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("url")?.substringBefore('?') ?: return@mapNotNull null
            if ("/news/articles/" !in url && "/news/live/" !in url) return@mapNotNull null // skip Sounds/iPlayer/etc.
            Story(
                id = url, title = o.str("headline")?.trim() ?: return@mapNotNull null, summary = o.str("description").orEmpty(), url = url,
                imageUrl = (o["image"] as? JsonObject)?.str("src")?.replace(Regex("/standard/\\d+/"), "/standard/660/"),
                category = "News",
                publishedMillis = o.str("datePublished")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                isLive = "/live/" in url,
            )
        }
    }
}
