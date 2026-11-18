package app.bbcrevived.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches feeds/pages with a tiny disk cache so the app works offline (stale content shown on failure). */
class Repository(ctx: Context) {
    companion object {
        /** Bylines of articles read this session (url -> story, writers), so author pages include them instantly. */
        private val read = java.util.concurrent.ConcurrentHashMap<String, Pair<Story, List<Author>>>()
    }

    private val client = OkHttpClient()
    private val dir = File(ctx.cacheDir, "http").apply { mkdirs() }
    private val ua = "Mozilla/5.0 (Android) Firsthand/personal"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val f = File(dir, url.hashCode().toString())
        try {
            val req = Request.Builder().url(url).header("User-Agent", ua).build()
            client.newCall(req).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                r.body!!.string().also { f.writeText(it) }
            }
        } catch (e: Exception) {
            if (f.exists()) f.readText() else throw e
        }
    }

    suspend fun category(name: String): List<Story> =
        runCatching { RssParser.parse(get(Categories.feeds.getValue(name)), name) }.getOrDefault(emptyList())

    suspend fun top(): List<Story> =
        runCatching { RssParser.parse(get(Categories.TOP), "Top") }.getOrDefault(emptyList())

    /** Homepage JSON first; falls back to RSS if the undocumented format has changed. */
    suspend fun home(): HomeData {
        val parsed = runCatching { HomepageParser.parse(get(Categories.HOMEPAGE)) }.getOrDefault(HomeData(emptyList(), emptyList()))
        if (parsed.breaking.isNotEmpty() && parsed.mostRead.isNotEmpty()) return parsed
        val top = top()
        return HomeData(
            breaking = parsed.breaking.ifEmpty { top.take(6) },
            mostRead = parsed.mostRead.ifEmpty { top.drop(6).take(10) },
        )
    }

    /** Live BBC search as a feed: two pages of results, newest first. Used for keyword follows. */
    suspend fun search(term: String): List<Story> = coroutineScope {
        val q = java.net.URLEncoder.encode(term, "UTF-8")
        listOf(1, 2).map { page ->
            async { runCatching { SearchParser.parse(get("https://www.bbc.co.uk/search?q=$q&d=NEWS_GNL" + if (page > 1) "&page=$page" else "")) }.getOrDefault(emptyList()) }
        }.flatMap { it.await() }.distinctBy { it.url }.sortedByDescending { it.publishedMillis }
    }

    /**
     * Finds the real BBC topic for a word. BBC has no topic index, so read the "Related topics" tags on the
     * top search results and pick the one whose name is exactly the word.
     */
    suspend fun resolveTopic(term: String): TopicRef? = coroutineScope {
        val urls = search(term).filter { "/news/articles/" in it.url }.take(12).map { it.url }
        // No results at all usually means we're offline: signal "try again later" rather than "no such topic".
        if (urls.isEmpty()) throw IllegalStateException("no search results")
        val topics = urls.map { u -> async { runCatching { ArticleParser.topics(get(u)) }.getOrDefault(emptyList()) } }.flatMap { it.await() }
        ArticleParser.pickTopic(topics, term)
    }

    /**
     * Articles by a writer. BBC has no author pages, so: search BBC for the name, open the top results (disk-cached)
     * and keep the ones whose byline credits them, plus anything by them already read in the app. Newest first.
     */
    suspend fun authorStories(name: String): List<Story> = coroutineScope {
        val want = Author.key(name)
        val already = read.values.filter { (_, a) -> a.any { Author.key(it.name) == want } }.map { it.first }
        val candidates = search(name).filter { DeepLinks.parse(it.url) is DeepLinks.Link.Article }.take(15)
        val verified = candidates.map { s ->
            async { s.takeIf { runCatching { ArticleParser.byline(get(s.url)) }.getOrDefault(emptyList()).any { Author.key(it.name) == want } } }
        }.mapNotNull { it.await() }
        (verified + already).distinctBy { it.url }.sortedByDescending { it.publishedMillis }
    }

    /** The byline of one article (empty if none/unreadable). */
    suspend fun byline(url: String): List<Author> = runCatching { ArticleParser.byline(get(url)) }.getOrDefault(emptyList())

    /** Bylines for many articles, a few at a time (disk-cached), reported as each one arrives. */
    suspend fun bylines(urls: List<String>, onEach: (String, List<Author>) -> Unit) = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        urls.map { u -> async { gate.withPermit { runCatching { ArticleParser.byline(get(u)) }.getOrNull()?.let { onEach(u, it) } } } }
            .forEach { it.await() }
    }

    /** Stories for one followed item: catalogue feed, BBC topic feed, or keyword search. */
    suspend fun followStories(f: String): List<Story> = when {
        Categories.isKeyword(f) -> search(f.removePrefix(Categories.KW))
        Categories.isAuthor(f) -> authorStories(Categories.authorName(f))
        Categories.isTopic(f) -> runCatching { RssParser.parse(get(Categories.topicFeed(Categories.topicId(f))), Categories.topicName(f)) }.getOrDefault(emptyList())
        else -> category(f)
    }

    suspend fun allCategories(follows: Collection<String>): Map<String, List<Story>> = coroutineScope {
        follows.map { it to async { followStories(it) } }.associate { (k, v) -> k to v.await() }
    }

    suspend fun article(story: Story): ArticleContent = runCatching {
        val html = get(story.url)
        val info = ArticleParser.info(html)
        if (info.authors.isNotEmpty()) read[story.url] = story to info.authors
        ArticleContent(ArticleParser.parse(html, story.url), ArticleParser.topics(html), info)
    }.getOrDefault(ArticleContent(emptyList(), emptyList()))

    /** A story for a tapped BBC link: headline/summary/image from the page, or a bare placeholder if it can't be read in time. */
    suspend fun storyFromLink(link: DeepLinks.Link): Story {
        val m = withTimeoutOrNull(6_000) { runCatching { ArticleParser.meta(get(link.url)) }.getOrNull() }
        return Story(
            id = link.url, title = m?.title ?: "BBC News", summary = m?.description.orEmpty(), url = link.url,
            imageUrl = m?.image, category = "News", publishedMillis = 0L, isLive = link is DeepLinks.Link.Live,
        )
    }

    /** Clip details for a /news/videos page. */
    suspend fun video(story: Story): VideoInfo? = runCatching { VideoParser.parse(get(story.url), story.url) }.getOrNull()

    /** A playable HLS stream for a clip, trying the media sets BBC's own web and mobile players use. Null if none (geo/format). */
    suspend fun videoStream(versionId: String): String? = withContext(Dispatchers.IO) {
        for (set in listOf("mobile-phone-main", "pc", "iptv-all")) {
            val url = "https://open.live.bbc.co.uk/mediaselector/6/select/version/2.0/mediaset/$set/vpid/$versionId/format/json"
            val hls = runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r -> VideoParser.streamFrom(r.body!!.string()) }
            }.getOrNull()
            if (hls != null) return@withContext hls
        }
        null
    }

    /**
     * Follows a share.google / search.app link's HTTP redirects (no browser needed: they are plain 301/302s) and
     * returns where it ends up. Stops as soon as it reaches a BBC News address, without loading that page.
     */
    suspend fun resolveShortLink(url: String): String = withContext(Dispatchers.IO) {
        val noFollow = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var cur = url
        repeat(6) {
            if (DeepLinks.parse(cur) != null) return@withContext cur
            val next = runCatching {
                noFollow.newCall(Request.Builder().url(cur).header("User-Agent", ua).build()).execute().use { r ->
                    if (r.isRedirect) r.header("Location")?.let { loc -> java.net.URI(cur).resolve(loc).toString() } else null
                }
            }.getOrNull() ?: return@withContext cur
            cur = next
        }
        cur
    }

    /** Live pages are always fetched fresh (cache only as offline fallback inside get()). */
    suspend fun live(story: Story): List<LivePost> =
        runCatching { LiveParser.parse(get(story.url), story.url) }.getOrDefault(emptyList())
}
