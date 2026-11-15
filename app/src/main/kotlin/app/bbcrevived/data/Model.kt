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

object Categories {
    /** Display name -> RSS feed URL. */
    val feeds = linkedMapOf(
        "World" to "https://feeds.bbci.co.uk/news/world/rss.xml",
        "UK" to "https://feeds.bbci.co.uk/news/uk/rss.xml",
        "Tech" to "https://feeds.bbci.co.uk/news/technology/rss.xml",
        "Business" to "https://feeds.bbci.co.uk/news/business/rss.xml",
        "Sport" to "https://feeds.bbci.co.uk/sport/rss.xml",
        "Science" to "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
        "Health" to "https://feeds.bbci.co.uk/news/health/rss.xml",
        "Culture" to "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
    )
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
