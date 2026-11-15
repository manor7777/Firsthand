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

    suspend fun allCategories(names: Collection<String>): Map<String, List<Story>> = coroutineScope {
        names.map { it to async { category(it) } }.associate { (k, v) -> k to v.await() }
    }

    suspend fun article(story: Story): List<Block> =
        runCatching { ArticleParser.parse(get(story.url), story.url) }.getOrDefault(emptyList())

    /** Live pages are always fetched fresh (cache only as offline fallback inside get()). */
    suspend fun live(story: Story): List<LivePost> =
        runCatching { LiveParser.parse(get(story.url), story.url) }.getOrDefault(emptyList())
}
