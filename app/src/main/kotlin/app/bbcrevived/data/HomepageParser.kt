package app.bbcrevived.data

import app.bbcrevived.data.NextData.str
import kotlinx.serialization.json.JsonObject

/**
 * Best-effort: finds story-like objects in the homepage JSON and sorts them by the key path they sit under.
 * Anything under a path containing "breaking" is Breaking; "most" + "read" is Most Read. Tolerant by design.
 */
object HomepageParser {
    fun parse(html: String): HomeData {
        val root = NextData.extract(html) ?: return HomeData(emptyList(), emptyList())
        val breaking = LinkedHashMap<String, Story>()
        val most = LinkedHashMap<String, Story>()
        NextData.walk(root) { o, path ->
            val p = path.lowercase()
            val bucket = when {
                "breaking" in p -> breaking
                "most" in p && "read" in p -> most
                else -> return@walk
            }
            story(o)?.let { bucket.putIfAbsent(it.url, it) }
        }
        return HomeData(breaking.values.toList(), most.values.toList())
    }

    private fun story(o: JsonObject): Story? {
        val title = o.str("headline", "title", "text") ?: return null
        val href = o.str("href", "url", "link") ?: return null
        if (!href.contains("/news/") && !href.contains("/sport/")) return null
        val url = (if (href.startsWith("http")) href else "https://www.bbc.co.uk$href").substringBefore('?')
        return Story(url, title, o.str("summary", "description").orEmpty(), url, null, "News", 0L, "/live/" in url)
    }
}
