package app.firsthand.data

import java.net.URI

/**
 * Decides which tapped web links Firsthand takes over: BBC News (bbc.com and bbc.co.uk under /news) plus BBC
 * Weather and Sport *articles*, which use the same page format (Google files them under BBC News too),
 * plus Google share links (share.google, search.app) which are resolved first and only kept if they lead to BBC News.
 * Pure Kotlin so it is unit-tested; the manifest filter is looser and this is the real gate.
 */
object DeepLinks {
    sealed interface Link {
        /** Canonical https://www.bbc.co.uk/news/... address (what the parsers are written against). */
        val url: String
        data class Article(override val url: String) : Link
        data class Live(override val url: String) : Link
        data class Video(override val url: String) : Link
        /** A /news page the reader can't show (section index, ...): hand it to the browser. */
        data class Unsupported(override val url: String) : Link
    }

    private val hosts = setOf("bbc.com", "www.bbc.com", "bbc.co.uk", "www.bbc.co.uk", "m.bbc.co.uk")
    /** Redirecting share links (Google app / Discover "Share"). */
    val shortHosts = setOf("share.google", "search.app")
    private val legacySlug = Regex("[a-z0-9]+(-[a-z0-9]+)*-\\d{5,}")
    private val anyUrl = Regex("https?://[^\\s<>\"']+")

    private fun uri(raw: String?): URI? {
        val u = runCatching { URI(raw ?: return null) }.getOrNull() ?: return null
        if (u.scheme?.lowercase() !in setOf("http", "https")) return null
        if (u.rawUserInfo != null || (u.port != -1 && u.port != 80 && u.port != 443)) return null
        return u
    }

    /** Null when the link is not BBC News at all. */
    fun parse(raw: String?): Link? {
        val u = uri(raw) ?: return null
        if (u.host?.lowercase() !in hosts) return null
        val path = (u.rawPath ?: "").trimEnd('/')
        // Weather/Sport: only their article pages (same format as news articles); the rest of those sites stays in the browser.
        val seg = path.trim('/').split('/')
        if ((seg.firstOrNull() == "weather" || seg.firstOrNull() == "sport") && seg.size >= 3 &&
            seg[seg.size - 2] == "articles" && Regex("[a-z0-9]{8,20}").matches(seg.last())
        ) return Link.Article("https://www.bbc.co.uk$path")
        if (path != "/news" && !path.startsWith("/news/")) return null
        val url = "https://www.bbc.co.uk$path"
        val parts = path.removePrefix("/news").trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            parts.size == 2 && parts[0] == "live" -> Link.Live(url)
            parts.size == 2 && parts[0] == "articles" -> Link.Article(url)
            parts.size == 2 && (parts[0] == "videos" || parts[0] == "av") -> Link.Video(url)
            parts.size == 1 && legacySlug.matches(parts[0]) -> Link.Article(url) // old style: /news/world-europe-12345678
            else -> Link.Unsupported(url)
        }
    }

    /** True for share.google / search.app links that must be followed to find out where they go. */
    fun isShortLink(raw: String?): Boolean = uri(raw)?.host?.lowercase() in shortHosts

    /** The first web address in shared text ("Watch: headline https://share.google/xyz"). */
    fun firstUrl(text: String?): String? = text?.let { anyUrl.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?') }
}
