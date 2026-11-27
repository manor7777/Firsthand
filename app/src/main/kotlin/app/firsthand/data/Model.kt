package app.firsthand.data

data class Story(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val category: String,
    val publishedMillis: Long,
    val isLive: Boolean = false,
    val breaking: Boolean = false,
    /** Publisher name for stories from added sources ("Example Post"); null for BBC. */
    val source: String? = null,
    /** Writers credited in the feed itself (RSS items carry them; BBC's don't, its bylines come from the article page). */
    val authors: List<Author> = emptyList(),
    /** Full article HTML some feeds ship inside the item (content:encoded), so the reader needs no page fetch. */
    val contentHtml: String? = null,
    /** Topic tags the feed gave the story (Reach: media:keywords; any feed's category elements). Used to tell what a story is about. */
    val tags: List<String> = emptyList(),
) {
    /** Who published it, for display: BBC stories carry no source tag (it is the default source). */
    val sourceName: String get() = source ?: "BBC News"
    /** The section to show beside the source ("Business"); the bare "News" of a story opened from a link says nothing, so it is dropped. */
    val sectionName: String get() = category.takeUnless { it.isBlank() || (source == null && it == "News") || it == sourceName }.orEmpty()
}

data class PageMeta(val title: String?, val description: String?, val image: String?, val siteName: String? = null)

data class HomeData(val breaking: List<Story>, val mostRead: List<Story>)

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Quote(val text: String) : Block
    /** A paragraph that is entirely a link (e.g. a video page's "More on this story"): tapping opens [url]. */
    data class Link(val text: String, val url: String) : Block
    data class Image(val url: String, val caption: String?) : Block
    /** A video found on a web page (schema.org VideoObject, <video> tag or og:video): [url] is a direct HLS/MP4 address. */
    data class Video(val url: String, val poster: String?, val durationSec: Int?) : Block
    /** Interactive/video/unknown block: shown as an inline embed (WebView) or link. */
    data class Embed(val url: String) : Block
}

data class Topic(val name: String, val group: String, val url: String)

object Categories {
    private const val N = "https://feeds.bbci.co.uk/news"
    private const val S = "https://feeds.bbci.co.uk/sport"

    /** Followable BBC section feeds. Names for the original presets are kept so saved choices still work. */
    val catalog: List<Topic> = listOf(
        Topic("World", "World", "$N/world/rss.xml"),
        Topic("Africa", "World", "$N/world/africa/rss.xml"),
        Topic("Asia", "World", "$N/world/asia/rss.xml"),
        Topic("Australia", "World", "$N/world/australia/rss.xml"),
        Topic("Europe", "World", "$N/world/europe/rss.xml"),
        Topic("Latin America", "World", "$N/world/latin_america/rss.xml"),
        Topic("Middle East", "World", "$N/world/middle_east/rss.xml"),
        Topic("US & Canada", "World", "$N/world/us_and_canada/rss.xml"),
        Topic("UK", "UK", "$N/uk/rss.xml"),
        Topic("England", "UK", "$N/england/rss.xml"),
        Topic("Scotland", "UK", "$N/scotland/rss.xml"),
        Topic("Wales", "UK", "$N/wales/rss.xml"),
        Topic("Northern Ireland", "UK", "$N/northern_ireland/rss.xml"),
        Topic("Politics", "UK", "$N/politics/rss.xml"),
        Topic("Business", "Business & Tech", "$N/business/rss.xml"),
        Topic("Tech", "Business & Tech", "$N/technology/rss.xml"),
        Topic("Science & Environment", "Science & Health", "$N/science_and_environment/rss.xml"),
        Topic("Health", "Science & Health", "$N/health/rss.xml"),
        Topic("Education", "Science & Health", "$N/education/rss.xml"),
        Topic("Culture", "Culture", "$N/entertainment_and_arts/rss.xml"),
        Topic("Sport", "Sport", "$S/rss.xml"),
        Topic("Football", "Sport", "$S/football/rss.xml"),
        Topic("Cricket", "Sport", "$S/cricket/rss.xml"),
        Topic("Formula 1", "Sport", "$S/formula1/rss.xml"),
        Topic("Rugby Union", "Sport", "$S/rugby-union/rss.xml"),
        Topic("Tennis", "Sport", "$S/tennis/rss.xml"),
        Topic("Golf", "Sport", "$S/golf/rss.xml"),
        Topic("Boxing", "Sport", "$S/boxing/rss.xml"),
        Topic("Athletics", "Sport", "$S/athletics/rss.xml"),
    )

    /** Display name -> RSS feed URL. */
    val feeds: Map<String, String> = catalog.associate { it.name to it.url }

    /** A followed item is either a catalogue topic name or a free keyword stored as "kw:<word>". */
    const val KW = "kw:"
    private val aliases = mapOf(
        "science" to "Science & Environment", "environment" to "Science & Environment", "science and environment" to "Science & Environment",
        "technology" to "Tech", "entertainment" to "Culture", "arts" to "Culture", "entertainment & arts" to "Culture", "entertainment and arts" to "Culture",
        "us" to "US & Canada", "usa" to "US & Canada", "canada" to "US & Canada", "us and canada" to "US & Canada", "america" to "US & Canada",
        "f1" to "Formula 1", "formula one" to "Formula 1", "formula1" to "Formula 1", "rugby" to "Rugby Union", "soccer" to "Football",
        "ni" to "Northern Ireland", "uk news" to "UK", "world news" to "World", "business news" to "Business",
    )

    /** Finds the catalogue topic a free-text keyword refers to, or null: exact name, alias, then a unique partial match. */
    fun matchKeyword(raw: String): String? {
        val k = raw.removePrefix(KW).trim().lowercase()
        if (k.isEmpty()) return null
        catalog.firstOrNull { it.name.lowercase() == k }?.let { return it.name }
        aliases[k]?.let { return it }
        if (k.length >= 4) catalog.filter { it.name.lowercase().startsWith(k) }.singleOrNull()?.let { return it.name }
        return null
    }

    /** Maps saved/imported names onto current ones: old names are renamed and keywords that match a topic become that topic. */
    fun normalize(f: String): String {
        if (f == "Science") return "Science & Environment"
        return if (isKeyword(f)) matchKeyword(f) ?: f else f
    }
    fun isKeyword(f: String) = f.startsWith(KW)

    /** Real BBC topics are stored as "topic:<id>|<name>" and read from feeds.bbci.co.uk/news/topics/<id>/rss.xml. */
    const val TOPIC = "topic:"
    fun isTopic(f: String) = f.startsWith(TOPIC)
    fun topicId(f: String) = f.removePrefix(TOPIC).substringBefore('|')
    fun topicName(f: String) = f.substringAfter('|')
    fun topicFollow(id: String, name: String) = "$TOPIC$id|$name"
    fun topicFeed(id: String) = "https://feeds.bbci.co.uk/news/topics/$id/rss.xml"
    /**
     * Shared categories: different sources' "Tech" / "Technology" / "Science & Tech" are one, so My News groups them together
     * and alerts can follow a category across every source. Anything not listed (a local section, a front page) stays its own thing.
     */
    val sharedCategories = listOf(
        "World", "UK", "Politics", "Business", "Tech", "Science", "Environment", "Health", "Education", "Culture",
        "Sport", "Football", "Crime", "Opinion", "Lifestyle", "Weird",
    )
    private val sharedAliases: Map<String, String> = buildMap {
        fun add(to: String, vararg names: String) = names.forEach { put(it, to) }
        add("World", "world", "world news", "international", "global", "global news", "uk & world news", "uk and world news", "foreign", "global development")
        add("UK", "uk", "uk news", "britain", "british news")
        add("Politics", "politics", "uk politics", "us politics", "government", "westminster", "election", "elections")
        add("Business", "business", "business news", "business affairs", "economy", "money", "finance", "markets", "companies", "business & finance", "personal finance", "consumer", "work")
        add("Tech", "tech", "technology", "science & tech", "science and tech", "science and technology", "tech & science", "gadgets", "cyber", "internet", "ai", "gaming", "games", "security", "ai & ml", "software", "networks", "personal tech", "devops")
        add("Science", "science", "science & environment", "science and environment", "space")
        add("Environment", "environment", "climate", "climate change", "climate crisis", "green", "nature", "wildlife")
        add("Health", "health", "health & wellbeing", "wellbeing", "medicine", "nhs", "social care")
        add("Education", "education", "schools", "universities", "students")
        add("Culture", "culture", "entertainment", "entertainment & arts", "arts & entertainment", "arts", "showbiz", "tv", "tv & radio", "tv & film", "music", "film", "books", "celebrity", "celebrities", "celeb news", "theatre")
        add("Sport", "sport", "sports")
        add("Football", "football", "soccer")
        add("Crime", "crime", "law & order", "courts", "police", "law and order")
        add("Opinion", "opinion", "comment", "commentisfree", "columnists", "analysis", "editorial")
        add("Lifestyle", "lifestyle", "life & style", "life and style", "food", "travel", "fashion", "home & garden", "property", "relationships")
        add("Weird", "weird", "weird news", "strange news", "odd news", "offbeat", "quirky")
    }

    /** The shared category a section name belongs to ("Technology" -> "Tech"), or null if it isn't one. */
    fun sharedName(section: String): String? = sharedAliases[section.trim().lowercase()]

    /** The section name of a follow: a BBC section, a source's section ("Sky News · Technology" -> "Technology"), or a BBC topic. */
    fun sectionOf(f: String): String? = when {
        f in feeds -> f
        isFeed(f) -> feedLabel(f).substringAfter(" · ", "").ifEmpty { null }
        isTopic(f) -> topicName(f)
        else -> null
    }

    /** The shared category a follow belongs to, if any. */
    fun sharedOf(f: String): String? = sectionOf(f)?.let(::sharedName)

    /** Who publishes a follow's stories, for display: BBC for its sections and topics, else the source name. */
    fun publisherOf(f: String): String = when {
        isFeed(f) -> feedLabel(f).substringBefore(" · ")
        else -> "BBC News"
    }

    /** Followed writers are stored as "author:<Name>" (stories found via BBC search, kept only if their byline matches). */
    const val AUTHOR = "author:"
    fun isAuthor(f: String) = f.startsWith(AUTHOR)
    fun authorName(f: String) = f.removePrefix(AUTHOR)
    fun authorFollow(name: String) = AUTHOR + name.trim().replace(Regex("\\s+"), " ")
    /** A section of an added source: "feed:<rss url>|<Source> · <Section>". */
    const val FEED = "feed:"
    fun isFeed(f: String) = f.startsWith(FEED)
    fun feedUrl(f: String) = f.removePrefix(FEED).substringBefore('|')
    fun feedLabel(f: String) = f.substringAfter('|', "")
    fun feedFollow(url: String, label: String) = "$FEED$url|$label"
    /** A writer from an added source, by name: "xauthor:<Name>". Their page lists their stories from the feeds you follow. */
    const val XAUTHOR = "xauthor:"
    fun isXAuthor(f: String) = f.startsWith(XAUTHOR)
    fun xauthorName(f: String) = f.removePrefix(XAUTHOR)
    fun xauthorFollow(name: String) = XAUTHOR + name.trim().replace(Regex("\\s+"), " ")
    fun isAnyAuthor(f: String) = isAuthor(f) || isXAuthor(f)
    /** Follows that read from BBC News: its section feeds, topics and writers. (Keywords and other publishers' follows are not.) */
    fun isBbc(f: String) = f in feeds || isTopic(f) || isAuthor(f)
    fun isValid(f: String) = f in feeds || isKeyword(f) ||
        (isFeed(f) && Sources.validUrl(feedUrl(f)) && feedLabel(f).length in 1..120 && '\n' !in f) ||
        (isXAuthor(f) && xauthorName(f).length in 2..80 && '\n' !in f) || (isTopic(f) && topicId(f).matches(Regex("[a-z0-9]{6,20}"))) ||
        (isAuthor(f) && authorName(f).length in 2..80 && '\n' !in f)
    fun label(f: String) = when {
        isKeyword(f) -> "“${f.removePrefix(KW)}”"
        isAuthor(f) -> "by ${authorName(f)}"
        isXAuthor(f) -> "by ${xauthorName(f)}"
        isFeed(f) -> feedLabel(f)
        isTopic(f) -> topicName(f)
        else -> f
    }
    const val TOP = "https://feeds.bbci.co.uk/news/rss.xml"
    const val HOMEPAGE = "https://www.bbc.co.uk/news"
}

data class LivePost(
    val id: String,
    val time: String?,
    val title: String?,
    val blocks: List<Block>,
    val pinned: Boolean,
    val breaking: Boolean = false,
)

data class TopicRef(val id: String, val name: String)
/** A credited writer from the article's byline. BBC publishes no author ID or page, only name and role. */
data class Author(val name: String, val role: String?, val source: String? = null) {
    companion object {
        /** Comparable form of a name: "Simon King " and "simon  king" are the same person. */
        fun key(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

/** Byline and dates of an article (ISO-8601 instants), shown under the headline. */
data class ArticleInfo(val authors: List<Author>, val published: String?, val updated: String?, val paywalled: Boolean = false)

data class ArticleContent(
    val blocks: List<Block>, val topics: List<TopicRef>,
    val info: ArticleInfo = ArticleInfo(emptyList(), null, null),
)

/** A BBC /news/videos page: the clip's version ID (for BBC's media selector) plus its text. */
data class VideoInfo(
    val title: String,
    val summary: List<Block>,
    val versionId: String?,
    val poster: String?,
    val portrait: Boolean,
    val durationSec: Int?,
    val topics: List<TopicRef>,
)
