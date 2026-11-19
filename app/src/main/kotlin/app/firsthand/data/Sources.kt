package app.firsthand.data

import java.net.URI

/** One section of a publisher: a name and its RSS address. */
data class SourceFeed(val name: String, val url: String)

/**
 * A publisher added in Settings. Its [feeds] become followable sections in My News, listed under its name.
 * Everything here runs on plain RSS/Atom feeds; nothing is scraped from the publisher's site except article text
 * when the reader opens a story.
 */
data class Source(
    val id: String, val name: String, val group: String, val feeds: List<SourceFeed>, val custom: Boolean = false,
    /** The publisher's website hostnames, for opening its article links in the app (catalogue sources only). */
    val hosts: List<String> = emptyList(),
) {
    /** The follow key for one of this source's sections ("Mirror · Sport"). A custom source has a single feed, labelled by its name. */
    fun follow(f: SourceFeed) = Categories.feedFollow(f.url, if (custom) name else "$name · ${f.name}")
    val frontPage: SourceFeed get() = feeds.first()
    fun owns(follow: String) = Categories.isFeed(follow) && feeds.any { it.url == Categories.feedUrl(follow) }
}

object Sources {
    /** Stored entries: "p:<id>" for a catalogue source, "c:<https url>|<name>" for one the user added by address. */
    private const val P = "p:"
    private const val C = "c:"

    fun validUrl(u: String): Boolean = u.length in 12..500 && runCatching {
        val x = URI(u)
        x.scheme == "https" && !x.host.isNullOrEmpty() && x.rawUserInfo == null && u.none { it.isWhitespace() || it == '|' }
    }.getOrDefault(false)

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Reach plc titles all serve any section as RSS by adding ?service=rss to its address. */
    private fun reach(host: String, name: String, vararg extra: Pair<String, String>): Source {
        fun f(label: String, path: String) = SourceFeed(label, "https://www.$host/$path?service=rss")
        return Source(
            slug(name), name, "Reach: regional & national",
            hosts = listOf(host, "www.$host"),
            feeds = listOf(f("Front page", ""), f("News", "news/"), f("UK & World News", "news/uk-world-news/"), f("Sport", "sport/"),
                f("Football", "sport/football/"), f("What's On", "whats-on/")) + extra.map { (l, p) -> f(l, p) },
        )
    }

    private fun simple(name: String, group: String, hosts: List<String>, vararg feeds: Pair<String, String>) =
        Source(slug(name), name, group, feeds.map { (n, u) -> SourceFeed(n, u) }, hosts = hosts)

    private const val G = "https://www.theguardian.com"
    private const val SKY = "https://feeds.skynews.com/feeds/rss"
    private const val IND = "https://www.independent.co.uk"
    private const val NYT = "https://rss.nytimes.com/services/xml/rss/nyt"
    private const val NPR = "https://feeds.npr.org"

    val catalog: List<Source> = listOf(
        reach("mirror.co.uk", "Mirror", "UK News" to "news/uk-news/", "Politics" to "news/politics/", "World News" to "news/world-news/", "TV" to "tv/"),
        reach("dailystar.co.uk", "Daily Star"),
        reach("derbytelegraph.co.uk", "Derby Telegraph", "Derby News" to "news/derby-news/"),
        reach("leicestermercury.co.uk", "Leicester Mercury", "Leicester News" to "news/leicester-news/"),
        reach("manchestereveningnews.co.uk", "Manchester Evening News", "Greater Manchester News" to "news/greater-manchester-news/"),
        reach("liverpoolecho.co.uk", "Liverpool Echo", "Liverpool News" to "news/liverpool-news/"),
        reach("birminghammail.co.uk", "Birmingham Live", "Midlands News" to "news/midlands-news/"),
        reach("coventrytelegraph.co.uk", "Coventry Live", "Coventry News" to "news/coventry-news/"),
        reach("stokesentinel.co.uk", "Stoke Sentinel", "Stoke-on-Trent News" to "news/stoke-on-trent-news/"),
        reach("chroniclelive.co.uk", "Chronicle Live", "North East News" to "news/north-east-news/"),
        reach("walesonline.co.uk", "WalesOnline", "Wales News" to "news/wales-news/"),
        reach("bristolpost.co.uk", "Bristol Live", "Bristol News" to "news/bristol-news/"),
        reach("plymouthherald.co.uk", "Plymouth Live", "Plymouth News" to "news/plymouth-news/"),
        reach("hulldailymail.co.uk", "Hull Live", "Hull & East Yorkshire News" to "news/hull-east-yorkshire-news/"),
        reach("nottinghampost.com", "Nottingham Post", "Local News" to "news/local-news/", "Music & Nightlife" to "whats-on/music-nightlife/"),
        reach("mylondon.news", "MyLondon", "London News" to "news/london-news/"),
        reach("kentlive.news", "KentLive"), reach("essexlive.news", "EssexLive"), reach("cornwalllive.com", "Cornwall Live"),
        reach("devonlive.com", "Devon Live"), reach("edinburghlive.co.uk", "Edinburgh Live"), reach("glasgowlive.co.uk", "Glasgow Live"),
        reach("dailyrecord.co.uk", "Daily Record"), reach("dublinlive.ie", "Dublin Live"),
        simple("The Guardian", "UK national", listOf("theguardian.com", "www.theguardian.com"),
            "UK" to "$G/uk/rss", "World" to "$G/world/rss", "Politics" to "$G/politics/rss", "Business" to "$G/uk/business/rss",
            "Technology" to "$G/uk/technology/rss", "Science" to "$G/science/rss", "Sport" to "$G/sport/rss", "Football" to "$G/football/rss",
            "Culture" to "$G/uk/culture/rss", "Environment" to "$G/environment/rss", "Opinion" to "$G/commentisfree/rss", "Lifestyle" to "$G/uk/lifeandstyle/rss"),
        simple("Sky News", "UK national", listOf("news.sky.com", "skynews.com", "www.skynews.com"),
            "Home" to "$SKY/home.xml", "UK" to "$SKY/uk.xml", "World" to "$SKY/world.xml", "Politics" to "$SKY/politics.xml",
            "Business" to "$SKY/business.xml", "Technology" to "$SKY/technology.xml", "Entertainment" to "$SKY/entertainment.xml", "Strange News" to "$SKY/strange.xml"),
        simple("The Independent", "UK national", listOf("independent.co.uk", "www.independent.co.uk"),
            "Front page" to "$IND/rss", "UK" to "$IND/news/uk/rss", "World" to "$IND/news/world/rss", "Politics" to "$IND/news/uk/politics/rss",
            "Business" to "$IND/news/business/rss", "Sport" to "$IND/sport/rss", "Tech" to "$IND/tech/rss", "Culture" to "$IND/arts-entertainment/rss",
            "Climate" to "$IND/climate-change/rss"),
        simple("The Telegraph", "UK national", listOf("telegraph.co.uk", "www.telegraph.co.uk"), "Front page" to "https://www.telegraph.co.uk/rss.xml"),
        simple("Evening Standard", "UK national", listOf("standard.co.uk", "www.standard.co.uk"), "Front page" to "https://www.standard.co.uk/rss"),
        simple("Metro", "UK national", listOf("metro.co.uk", "www.metro.co.uk"), "Front page" to "https://metro.co.uk/feed/", "News" to "https://metro.co.uk/news/feed/", "Sport" to "https://metro.co.uk/sport/feed/"),
        simple("The Sun", "UK national", listOf("thesun.co.uk", "www.thesun.co.uk"), "Front page" to "https://www.thesun.co.uk/feed/"),
        simple("Daily Mail", "UK national", listOf("dailymail.co.uk", "www.dailymail.co.uk"),
            "Front page" to "https://www.dailymail.co.uk/articles.rss", "News" to "https://www.dailymail.co.uk/news/index.rss",
            "Sport" to "https://www.dailymail.co.uk/sport/index.rss", "Showbiz" to "https://www.dailymail.co.uk/tvshowbiz/index.rss",
            "Science & Tech" to "https://www.dailymail.co.uk/sciencetech/index.rss", "Health" to "https://www.dailymail.co.uk/health/index.rss",
            "Money" to "https://www.dailymail.co.uk/money/index.rss"),
        simple("Al Jazeera", "World", listOf("aljazeera.com", "www.aljazeera.com"), "All news" to "https://www.aljazeera.com/xml/rss/all.xml"),
        simple("France 24", "World", listOf("france24.com", "www.france24.com"), "All news" to "https://www.france24.com/en/rss"),
        simple("DW", "World", listOf("dw.com", "www.dw.com"), "All news" to "https://rss.dw.com/rdf/rss-en-all", "World" to "https://rss.dw.com/rdf/rss-en-world", "Business" to "https://rss.dw.com/rdf/rss-en-bus"),
        simple("Euronews", "World", listOf("euronews.com", "www.euronews.com"), "All news" to "https://www.euronews.com/rss"),
        simple("NPR", "World", listOf("npr.org", "www.npr.org"),
            "News" to "$NPR/1001/rss.xml", "World" to "$NPR/1004/rss.xml", "Politics" to "$NPR/1014/rss.xml", "Business" to "$NPR/1006/rss.xml",
            "Technology" to "$NPR/1019/rss.xml", "Science" to "$NPR/1007/rss.xml", "Health" to "$NPR/1128/rss.xml"),
        simple("The New York Times", "World", listOf("nytimes.com", "www.nytimes.com"),
            "Home" to "$NYT/HomePage.xml", "World" to "$NYT/World.xml", "Europe" to "$NYT/Europe.xml", "Business" to "$NYT/Business.xml",
            "Technology" to "$NYT/Technology.xml", "Science" to "$NYT/Science.xml", "Health" to "$NYT/Health.xml", "Sports" to "$NYT/Sports.xml",
            "Arts" to "$NYT/Arts.xml", "Politics" to "$NYT/Politics.xml"),
        simple("The Conversation UK", "Tech & ideas", listOf("theconversation.com"), "Articles" to "https://theconversation.com/uk/articles.atom"),
        simple("Ars Technica", "Tech & ideas", listOf("arstechnica.com", "www.arstechnica.com"), "Latest" to "https://feeds.arstechnica.com/arstechnica/index"),
        simple("The Verge", "Tech & ideas", listOf("theverge.com", "www.theverge.com"), "Latest" to "https://www.theverge.com/rss/index.xml"),
    )

    private val byId = catalog.associateBy { it.id }

    /** BBC's website hostnames (its links are handled through the same mechanism as an added source's). */
    val bbcHosts = listOf("www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk", "m.bbc.co.uk")

    /** Name of the manifest alias that makes the app eligible for this source's links; enabled only while the source is added. */
    fun aliasName(s: Source) = "app.firsthand.link." + s.id.replace('-', '_')
    const val BBC_ALIAS = "app.firsthand.link.bbc"

    /** A feed's site hostnames: its own host (minus feed./rss. prefixes), with and without www. Custom feeds use this for Share > Firsthand. */
    fun siteHosts(feedUrl: String): List<String> {
        val h = runCatching { URI(feedUrl).host?.lowercase() }.getOrNull() ?: return emptyList()
        val bare = h.removePrefix("www.").removePrefix("feeds.").removePrefix("rss.")
        return listOf(bare, "www.$bare").distinct()
    }

    /** Does this path look like an article rather than a home or section page? (Two or more segments, or one long slug.) */
    fun looksLikeArticle(path: String): Boolean {
        val segs = path.trim('/').split('/').filter { it.isNotEmpty() }
        return segs.size >= 2 || (segs.size == 1 && segs[0].length >= 20)
    }

    /** The added source a tapped link belongs to, or null (not one of ours, or not an article address). */
    fun matchLink(url: String, added: List<Source>): Source? {
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        if (u.scheme?.lowercase() !in setOf("http", "https") || u.rawUserInfo != null) return null
        val host = u.host?.lowercase() ?: return null
        if (!looksLikeArticle(u.rawPath.orEmpty())) return null
        return added.firstOrNull { host in it.hosts }
    }
    val groups: List<String> = catalog.map { it.group }.distinct()

    fun presetEntry(s: Source) = P + s.id
    fun customEntry(url: String, name: String) = "$C$url|${name.trim().replace(Regex("\\s+"), " ")}"

    /** The source an entry stands for, or null if it is unknown or malformed (so a bad import can't break anything). */
    fun resolve(entry: String): Source? = when {
        entry.startsWith(P) -> byId[entry.removePrefix(P)]
        entry.startsWith(C) -> {
            val url = entry.removePrefix(C).substringBeforeLast('|', "")
            val name = entry.substringAfterLast('|', "")
            if (validUrl(url) && name.length in 1..80 && '\n' !in entry)
                Source("c-" + slug(url), name, "Added by you", listOf(SourceFeed(name, url)), custom = true, hosts = siteHosts(url))
            else null
        }
        else -> null
    }

    fun resolveAll(entries: List<String>): List<Source> = entries.mapNotNull(::resolve).distinctBy { it.id }
}
