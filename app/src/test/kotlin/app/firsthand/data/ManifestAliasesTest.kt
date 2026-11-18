package app.firsthand.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The manifest must declare, for BBC and every catalogue publisher, an alias carrying that publisher's domains: Android
 * can't add domains at run time, so this is how "open its links in the app" works for a source once it's added (the app
 * enables the alias). The block is generated: if this fails after editing Sources.kt, run
 *   REGEN_MANIFEST=1 ./gradlew testDebugUnitTest --tests '*ManifestAliasesTest*'
 * and commit the manifest.
 */
class ManifestAliasesTest {
    private val start = "<!-- link-aliases:start"
    private val end = "<!-- link-aliases:end -->"
    private val ind = "        "

    private fun alias(name: String, enabled: Boolean, body: String) = """
$ind<activity-alias
$ind    android:name=".${name.removePrefix("app.firsthand.")}"
$ind    android:targetActivity=".MainActivity"
$ind    android:enabled="$enabled"
$ind    android:exported="true"
$ind    android:label="Firsthand">
$ind    <intent-filter>
$ind        <action android:name="android.intent.action.VIEW" />
$ind        <category android:name="android.intent.category.DEFAULT" />
$ind        <category android:name="android.intent.category.BROWSABLE" />
$body$ind    </intent-filter>
$ind</activity-alias>""".trimStart('\n')

    private fun hosts(h: List<String>) = h.joinToString("") { "$ind        <data android:host=\"$it\" />\n" }

    private fun expected(): String {
        val bbc = alias(
            Sources.BBC_ALIAS, true,
            "$ind        <data android:scheme=\"https\" />\n$ind        <data android:scheme=\"http\" />\n" + hosts(Sources.bbcHosts) +
                "$ind        <data android:path=\"/news\" />\n$ind        <data android:pathPrefix=\"/news/\" />\n" +
                "$ind        <data android:pathPrefix=\"/weather/articles/\" />\n$ind        <data android:pathPattern=\"/sport/.*/articles/.*\" />\n",
        )
        val sources = Sources.catalog.joinToString("\n") { s -> alias(Sources.aliasName(s), false, "$ind        <data android:scheme=\"https\" />\n" + hosts(s.hosts)) }
        return "$ind<!-- BBC News links (/news) plus Weather and Sport articles. Not auto-verified: the user enables them under Settings > Open by default. -->\n" +
            bbc + "\n$ind<!-- One disabled alias per publisher: enabled by the app while that source is added. -->\n" + sources + "\n"
    }

    @Test fun manifestAliasesMatchCatalogue() {
        val f = File("src/main/AndroidManifest.xml").takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        assumeTrue("manifest not found", f.exists())
        // The generated region runs from the line after the start marker up to the end marker's own line.
        fun region(t: String): Pair<Int, Int> {
            val a = t.indexOf(start); val b = t.indexOf(end)
            assertTrue("markers missing from the manifest", a >= 0 && b > a)
            return (t.indexOf('\n', a) + 1) to (t.lastIndexOf('\n', b) + 1)
        }
        if (System.getenv("REGEN_MANIFEST") == "1") {
            val t = f.readText(); val (from, to) = region(t)
            f.writeText(t.substring(0, from) + expected() + t.substring(to))
        }
        val t = f.readText(); val (from, to) = region(t)
        assertEquals("Manifest link aliases are out of date. Run: REGEN_MANIFEST=1 ./gradlew testDebugUnitTest --tests '*ManifestAliasesTest*'", expected(), t.substring(from, to))
    }

    @Test fun everySourceHasHostsAndAUniqueAlias() {
        assertTrue(Sources.catalog.all { it.hosts.isNotEmpty() && it.hosts.all { h -> h == h.lowercase() && '/' !in h } })
        assertEquals(Sources.catalog.size, Sources.catalog.map { Sources.aliasName(it) }.distinct().size)
        assertFalse(Sources.catalog.any { Sources.aliasName(it) == Sources.BBC_ALIAS })
    }

    @Test fun linkMatching() {
        val np = Sources.catalog.first { it.name == "Example Post" }
        val g = Sources.catalog.first { it.name == "The Guardian" }
        val added = listOf(np)
        fun m(u: String) = Sources.matchLink(u, added)?.name
        assertEquals("Example Post", m("https://www.examplepost.com/news/local-news/a52-exampleshire-closed-live-updates-11246014"))
        assertEquals("Example Post", m("https://examplepost.com/whats-on/music-nightlife/first-acts-announced-11246085?utm=x"))
        assertNull(m("https://www.examplepost.com/"))                       // home page: not an article
        assertNull(m("https://www.examplepost.com/news/"))                  // one short segment: a section page
        assertNull(m("https://www.theguardian.com/uk-news/2026/nov/18/some-story")) // not added
        assertNull(m("https://evil.example.com/www.examplepost.com/a/b"))
        assertNull(m("https://user@www.examplepost.com/news/a/b-123456"))
        assertNull(m("ftp://www.examplepost.com/news/a/b-123456"))
        assertEquals("The Guardian", Sources.matchLink("https://www.theguardian.com/uk-news/2026/nov/18/some-story", listOf(np, g))?.name)
        val custom = Sources.resolve(Sources.customEntry("https://feeds.example.org/rss.xml", "Example"))!!
        assertEquals(listOf("example.org", "www.example.org"), custom.hosts)
        assertEquals("Example", Sources.matchLink("https://www.example.org/2026/story-slug-here", listOf(custom))?.name)
    }
}
