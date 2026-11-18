package app.firsthand.data

import java.net.URI

/** One section of a publisher: a name and its RSS address. */
data class SourceFeed(val name: String, val url: String)

/**
 * A publisher added in Settings. Its [feeds] become followable sections in My News, listed under its name.
 * Everything here runs on plain RSS/Atom feeds; nothing is scraped from the publisher's site except article text
 * when the reader opens a story.
 */
data class Source(val id: String, val name: String, val group: String, val feeds: List<SourceFeed>, val custom: Boolean = false) {
    /** The follow key for one of this source's sections ("Example Post · Sport"). A custom source has a single feed, labelled by its name. */
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
            listOf(f("Front page", ""), f("News", "news/"), f("UK & World News", "news/uk-world-news/"), f("Sport", "sport/"),
                f("Football", "sport/football/"), f("What's On", "whats-on/")) + extra.map { (l, p) -> f(l, p) },
        )
    }

    private fun simple(name: String, group: String, vararg feeds: Pair<String, String>) =
        Source(slug(name), name, group, feeds.map { (n, u) -> SourceFeed(n, u) })

    private const val G = "https://www.theguardian.com"
    private const val SKY = "https://feeds.skynews.com/feeds/rss"
    private const val IND = "https://www.independent.co.uk"
    private const val NYT = "https://rss.nytimes.com/services/xml/rss/nyt"
    private const val NPR = "https://feeds.npr.org"

    val catalog: List<Source> = listOf(
        reach("examplepost.com", "Example Post",
            "Exampletown News" to "news/exampletown-news/", "Local News" to "news/local-news/", "Music & Nightlife" to "whats-on/music-nightlife/",
            "Example FC" to "sport/football/example-fc/", "Example Town FC" to "sport/football/example-town-fc/"),
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
        reach("mylondon.news", "MyLondon", "London News" to "news/london-news/"),
        reach("kentlive.news", "KentLive"), reach("essexlive.news", "EssexLive"), reach("cornwalllive.com", "Cornwall Live"),
        reach("devonlive.com", "Devon Live"), reach("edinburghlive.co.uk", "Edinburgh Live"), reach("glasgowlive.co.uk", "Glasgow Live"),
        reach("dailyrecord.co.uk", "Daily Record"), reach("dublinlive.ie", "Dublin Live"),
        reach("mirror.co.uk", "Mirror", "UK News" to "news/uk-news/", "Politics" to "news/politics/", "World News" to "news/world-news/", "TV" to "tv/"),
        reach("dailystar.co.uk", "Daily Star"),
        simple("The Guardian", "UK national",
            "UK" to "$G/uk/rss", "World" to "$G/world/rss", "Politics" to "$G/politics/rss", "Business" to "$G/uk/business/rss",
            "Technology" to "$G/uk/technology/rss", "Science" to "$G/science/rss", "Sport" to "$G/sport/rss", "Football" to "$G/football/rss",
            "Culture" to "$G/uk/culture/rss", "Environment" to "$G/environment/rss", "Opinion" to "$G/commentisfree/rss", "Lifestyle" to "$G/uk/lifeandstyle/rss"),
        simple("Sky News", "UK national",
            "Home" to "$SKY/home.xml", "UK" to "$SKY/uk.xml", "World" to "$SKY/world.xml", "Politics" to "$SKY/politics.xml",
            "Business" to "$SKY/business.xml", "Technology" to "$SKY/technology.xml", "Entertainment" to "$SKY/entertainment.xml", "Strange News" to "$SKY/strange.xml"),
        simple("The Independent", "UK national",
            "Front page" to "$IND/rss", "UK" to "$IND/news/uk/rss", "World" to "$IND/news/world/rss", "Politics" to "$IND/news/uk/politics/rss",
            "Business" to "$IND/news/business/rss", "Sport" to "$IND/sport/rss", "Tech" to "$IND/tech/rss", "Culture" to "$IND/arts-entertainment/rss",
            "Climate" to "$IND/climate-change/rss"),
        simple("The Telegraph", "UK national", "Front page" to "https://www.telegraph.co.uk/rss.xml"),
        simple("Evening Standard", "UK national", "Front page" to "https://www.standard.co.uk/rss"),
        simple("Metro", "UK national", "Front page" to "https://metro.co.uk/feed/", "News" to "https://metro.co.uk/news/feed/", "Sport" to "https://metro.co.uk/sport/feed/"),
        simple("The Sun", "UK national", "Front page" to "https://www.thesun.co.uk/feed/"),
        simple("Daily Mail", "UK national",
            "Front page" to "https://www.dailymail.co.uk/articles.rss", "News" to "https://www.dailymail.co.uk/news/index.rss",
            "Sport" to "https://www.dailymail.co.uk/sport/index.rss", "Showbiz" to "https://www.dailymail.co.uk/tvshowbiz/index.rss",
            "Science & Tech" to "https://www.dailymail.co.uk/sciencetech/index.rss", "Health" to "https://www.dailymail.co.uk/health/index.rss",
            "Money" to "https://www.dailymail.co.uk/money/index.rss"),
        simple("Al Jazeera", "World", "All news" to "https://www.aljazeera.com/xml/rss/all.xml"),
        simple("France 24", "World", "All news" to "https://www.france24.com/en/rss"),
        simple("DW", "World", "All news" to "https://rss.dw.com/rdf/rss-en-all", "World" to "https://rss.dw.com/rdf/rss-en-world", "Business" to "https://rss.dw.com/rdf/rss-en-bus"),
        simple("Euronews", "World", "All news" to "https://www.euronews.com/rss"),
        simple("NPR", "World",
            "News" to "$NPR/1001/rss.xml", "World" to "$NPR/1004/rss.xml", "Politics" to "$NPR/1014/rss.xml", "Business" to "$NPR/1006/rss.xml",
            "Technology" to "$NPR/1019/rss.xml", "Science" to "$NPR/1007/rss.xml", "Health" to "$NPR/1128/rss.xml"),
        simple("The New York Times", "World",
            "Home" to "$NYT/HomePage.xml", "World" to "$NYT/World.xml", "Europe" to "$NYT/Europe.xml", "Business" to "$NYT/Business.xml",
            "Technology" to "$NYT/Technology.xml", "Science" to "$NYT/Science.xml", "Health" to "$NYT/Health.xml", "Sports" to "$NYT/Sports.xml",
            "Arts" to "$NYT/Arts.xml", "Politics" to "$NYT/Politics.xml"),
        simple("The Conversation UK", "Tech & ideas", "Articles" to "https://theconversation.com/uk/articles.atom"),
        simple("Ars Technica", "Tech & ideas", "Latest" to "https://feeds.arstechnica.com/arstechnica/index"),
        simple("The Verge", "Tech & ideas", "Latest" to "https://www.theverge.com/rss/index.xml"),
    )

    private val byId = catalog.associateBy { it.id }
    val groups: List<String> = catalog.map { it.group }.distinct()

    fun presetEntry(s: Source) = P + s.id
    fun customEntry(url: String, name: String) = "$C$url|${name.trim().replace(Regex("\\s+"), " ")}"

    /** The source an entry stands for, or null if it is unknown or malformed (so a bad import can't break anything). */
    fun resolve(entry: String): Source? = when {
        entry.startsWith(P) -> byId[entry.removePrefix(P)]
        entry.startsWith(C) -> {
            val url = entry.removePrefix(C).substringBeforeLast('|', "")
            val name = entry.substringAfterLast('|', "")
            if (validUrl(url) && name.length in 1..80 && '\n' !in entry) Source("c-" + slug(url), name, "Added by you", listOf(SourceFeed(name, url)), custom = true) else null
        }
        else -> null
    }

    fun resolveAll(entries: List<String>): List<Source> = entries.mapNotNull(::resolve).distinctBy { it.id }
}
