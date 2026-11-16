package app.bbcrevived.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches feeds/pages with a tiny disk cache so the app works offline (stale content shown on failure). */
class Repository(ctx: Context) {
    private val client = OkHttpClient()
    private val dir = File(ctx.cacheDir, "http").apply { mkdirs() }
    private val ua = "Mozilla/5.0 (Android) BBCRevived/personal"

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

    /** Stories for one followed item: catalogue feed, BBC topic feed, or keyword search. */
    suspend fun followStories(f: String): List<Story> = when {
        Categories.isKeyword(f) -> search(f.removePrefix(Categories.KW))
        Categories.isTopic(f) -> runCatching { RssParser.parse(get(Categories.topicFeed(Categories.topicId(f))), Categories.topicName(f)) }.getOrDefault(emptyList())
        else -> category(f)
    }

    suspend fun allCategories(follows: Collection<String>): Map<String, List<Story>> = coroutineScope {
        follows.map { it to async { followStories(it) } }.associate { (k, v) -> k to v.await() }
    }

    suspend fun article(story: Story): ArticleContent = runCatching {
        val html = get(story.url)
        ArticleContent(ArticleParser.parse(html, story.url), ArticleParser.topics(html))
    }.getOrDefault(ArticleContent(emptyList(), emptyList()))

    /** Live pages are always fetched fresh (cache only as offline fallback inside get()). */
    suspend fun live(story: Story): List<LivePost> =
        runCatching { LiveParser.parse(get(story.url), story.url) }.getOrDefault(emptyList())
}
