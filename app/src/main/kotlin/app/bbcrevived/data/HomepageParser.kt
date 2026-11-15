package app.bbcrevived.data

import app.bbcrevived.data.InitialData.str
import java.time.Instant
import kotlinx.serialization.json.*

/**
 * Breaking = the lead "nations-news" list (top stories). Most Read = the ranked "onward-journeys" list
 * that contains the most articles (the other ranked list is most-watched video).
 */
object HomepageParser {
    fun parse(html: String): HomeData {
        val root = InitialData.extract(html) ?: return HomeData(emptyList(), emptyList())
        val breaking = (InitialData.widgets(root, "nations-news").firstOrNull() as? JsonArray).orEmpty().mapNotNull(::story)
        val most = InitialData.widgets(root, "onward-journeys")
            .mapNotNull { (it as? JsonObject)?.get("promos") as? JsonArray }
            .filter { l -> l.any { (it as? JsonObject)?.get("rank") != null } }
            .map { l -> l.mapNotNull(::story) }
            .maxByOrNull { it.size }.orEmpty()
        return HomeData(breaking, most)
    }

    private fun story(el: JsonElement): Story? {
        val o = el as? JsonObject ?: return null
        val title = o.str("headline") ?: return null
        val path = o.str("url") ?: return null
        if ("/news/articles/" !in path && "/news/live/" !in path) return null
        val url = (if (path.startsWith("http")) path else "https://www.bbc.co.uk$path").substringBefore('?')
        val img = (o["image"] as? JsonObject)?.str("src")?.replace(Regex("/standard/\\d+/"), "/standard/660/")
        val cat = (o["metadataStripItems"] as? JsonArray)?.firstNotNullOfOrNull { i ->
            if (i.str("type") == "attribution") i.str("text") else null
        } ?: "News"
        val ts = o.str("lastPublished")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        return Story(url, title, "", url, img, cat, ts, "/live/" in url)
    }
}
