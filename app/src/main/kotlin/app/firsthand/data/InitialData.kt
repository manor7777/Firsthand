package app.firsthand.data

import kotlinx.serialization.json.*

/**
 * BBC pages embed their data as `window.__INITIAL_DATA__="<JSON-encoded JSON string>"`.
 * Its `data` object maps widget keys to `{ name, data, ... }`; parsers pick widgets by name.
 * Undocumented and may change; fixtures in fixtures/real/ pin the format the tests cover.
 */
internal object InitialData {
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(html: String): JsonObject? = runCatching {
        val m = html.indexOf("window.__INITIAL_DATA__")
        if (m < 0) return null
        var i = html.indexOf('=', m) + 1
        while (i < html.length && html[i].isWhitespace()) i++
        if (html.getOrNull(i) != '"') return null
        var j = i + 1
        while (j < html.length) { val c = html[j]; if (c == '\\') j += 2 else if (c == '"') break else j++ }
        val inner = (json.parseToJsonElement(html.substring(i, j + 1)) as JsonPrimitive).content
        json.parseToJsonElement(inner) as? JsonObject
    }.getOrNull()

    /** `data` of every widget with the given name, in page order. */
    fun widgets(root: JsonObject, name: String): List<JsonElement> =
        (root["data"] as? JsonObject)?.values?.mapNotNull { e ->
            val o = e as? JsonObject
            if (o?.str("name") == name) o["data"] else null
        } ?: emptyList()

    fun JsonElement.str(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
