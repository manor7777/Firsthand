package app.bbcrevived.data

import app.bbcrevived.data.InitialData.str
import kotlinx.serialization.json.*

/** /news/videos pages: the "media-experience" widget's initialItem holds the clip and its write-up. */
object VideoParser {
    fun parse(html: String, url: String): VideoInfo? {
        val root = InitialData.extract(html) ?: return null
        val item = (InitialData.widgets(root, "media-experience").firstOrNull() as? JsonObject)?.get("initialItem") as? JsonObject ?: return null
        val media = item["mediaItem"] as? JsonObject ?: return null
        val title = (media["title"] as? JsonObject)?.str("content") ?: (item["promo"] as? JsonObject)?.str("headline") ?: return null
        val summary = ((media["summary"] as? JsonObject)?.get("blocks") as? JsonArray)?.let { ArticleParser.convert(it, url) }.orEmpty()
        val clip = ((media["media"] as? JsonObject)?.get("items") as? JsonArray)?.firstOrNull() as? JsonObject
        val vpid = clip?.takeIf { it.str("idType") == "versionID" }?.str("id")?.takeIf { Regex("[a-z0-9]{8}").matches(it) }
        val poster = ((item["promo"] as? JsonObject)?.get("image") as? JsonObject)?.str("src")
            ?.replace(Regex("/standard/\\d+/"), "/standard/800/")
            ?: (clip?.get("holdingImage") as? JsonObject)?.str("url")?.replace("{width}", "800")
        val topics = (item["topicTags"] as? JsonArray).orEmpty().mapNotNull { t ->
            val id = t.str("id") ?: return@mapNotNull null
            val name = t.str("title") ?: return@mapNotNull null
            TopicRef(id, name).takeIf { Regex("[a-z0-9]{6,20}").matches(id) }
        }
        return VideoInfo(
            title = title, summary = summary, versionId = vpid, poster = poster,
            portrait = media.str("orientation") == "portrait",
            durationSec = (clip?.get("duration") as? JsonPrimitive)?.intOrNull, topics = topics,
        )
    }

    /**
     * HLS address for a clip from BBC's media selector JSON, or null (e.g. {"result":"geolocation"} outside the UK).
     * Prefers https HLS; any supplier.
     */
    fun streamFrom(json: String): String? = runCatching {
        val media = Json.parseToJsonElement(json).jsonObject["media"] as? JsonArray ?: return null
        media.asSequence().mapNotNull { it as? JsonObject }
            .filter { it.str("kind")?.startsWith("video") != false }
            .flatMap { ((it["connection"] as? JsonArray) ?: JsonArray(emptyList())).asSequence().mapNotNull { c -> c as? JsonObject } }
            .filter { it.str("transferFormat") == "hls" }
            .sortedByDescending { it.str("protocol") == "https" }
            .firstNotNullOfOrNull { it.str("href")?.takeIf { h -> h.startsWith("http") } }
            ?.replaceFirst("http://", "https://")
    }.getOrNull()
}
