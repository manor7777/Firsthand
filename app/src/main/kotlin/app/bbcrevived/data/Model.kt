package app.bbcrevived.data

data class Story(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val category: String,
    val publishedMillis: Long,
    val isLive: Boolean = false,
)

data class HomeData(val breaking: List<Story>, val mostRead: List<Story>)

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Quote(val text: String) : Block
    data class Image(val url: String, val caption: String?) : Block
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
        Topic("Science", "Science & Health", "$N/science_and_environment/rss.xml"),
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
    fun isKeyword(f: String) = f.startsWith(KW)
    fun label(f: String) = if (isKeyword(f)) "“${f.removePrefix(KW)}”" else f
    const val TOP = "https://feeds.bbci.co.uk/news/rss.xml"
    const val HOMEPAGE = "https://www.bbc.co.uk/news"
}

data class LivePost(
    val id: String,
    val time: String?,
    val title: String?,
    val blocks: List<Block>,
    val pinned: Boolean,
)
