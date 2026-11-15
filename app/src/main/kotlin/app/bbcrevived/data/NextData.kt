package app.bbcrevived.data

import kotlinx.serialization.json.*

/** Shared helpers for reading the Next.js `__NEXT_DATA__` blob embedded in BBC pages (undocumented; may change). */
internal object NextData {
    private val re = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(html: String): JsonElement? =
        re.find(html)?.groupValues?.get(1)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    fun JsonElement.str(vararg keys: String): String? {
        val o = this as? JsonObject ?: return null
        for (k in keys) (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { return it }
        return null
    }

    /** Depth-first walk giving each object its key path. */
    fun walk(el: JsonElement, path: String = "", visit: (JsonObject, String) -> Unit) {
        when (el) {
            is JsonObject -> { visit(el, path); el.forEach { (k, v) -> walk(v, "$path/$k", visit) } }
            is JsonArray -> el.forEach { walk(it, path, visit) }
            else -> {}
        }
    }
}
