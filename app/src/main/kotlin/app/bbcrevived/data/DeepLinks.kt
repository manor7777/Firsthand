package app.bbcrevived.data

import java.net.URI

/**
 * Decides which tapped web links BBC Revived takes over: BBC *News* only (bbc.com and bbc.co.uk under /news).
 * Pure Kotlin so it is unit-tested; the manifest filter is deliberately broader/looser and this is the real gate.
 */
object DeepLinks {
    sealed interface Link {
        /** Canonical https://www.bbc.co.uk/news/... address (what the parsers are written against). */
        val url: String
        data class Article(override val url: String) : Link
        data class Live(override val url: String) : Link
        /** A /news page the reader can't show (video, section index, ...): hand it to the browser. */
        data class Unsupported(override val url: String) : Link
    }

    private val hosts = setOf("bbc.com", "www.bbc.com", "bbc.co.uk", "www.bbc.co.uk", "m.bbc.co.uk")
    private val legacySlug = Regex("[a-z0-9]+(-[a-z0-9]+)*-\\d{5,}")

    /** Null when the link is not BBC News at all. */
    fun parse(raw: String?): Link? {
        val u = runCatching { URI(raw ?: return null) }.getOrNull() ?: return null
        if (u.scheme?.lowercase() !in setOf("http", "https")) return null
        if (u.rawUserInfo != null || (u.port != -1 && u.port != 80 && u.port != 443)) return null
        if (u.host?.lowercase() !in hosts) return null
        val path = (u.rawPath ?: "").trimEnd('/')
        if (path != "/news" && !path.startsWith("/news/")) return null
        val url = "https://www.bbc.co.uk$path"
        val parts = path.removePrefix("/news").trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            parts.size == 2 && parts[0] == "live" -> Link.Live(url)
            parts.size == 2 && parts[0] == "articles" -> Link.Article(url)
            parts.size == 1 && legacySlug.matches(parts[0]) -> Link.Article(url) // old style: /news/world-europe-12345678
            else -> Link.Unsupported(url)
        }
    }
}
