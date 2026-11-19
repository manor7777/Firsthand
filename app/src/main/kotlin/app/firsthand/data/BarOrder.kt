package app.firsthand.data

/**
 * The order of the bottom bar's buttons, which the user sets by pressing and holding a button and dragging it.
 * Always holds all four ids once each (a stored order from an older version or a bad import is repaired), and
 * Breaking is simply left out of what is shown while BBC News isn't a source. Pure Kotlin so it is unit-tested.
 */
object BarOrder {
    const val FOR_YOU = "foryou"
    const val BREAKING = "breaking"
    const val MY_NEWS = "mynews"
    const val SEARCH = "search"
    val default = listOf(FOR_YOU, BREAKING, MY_NEWS, SEARCH)

    /** Known ids in the given order, duplicates dropped, anything missing added at the end. */
    fun normalise(raw: List<String>): List<String> {
        val kept = raw.map { it.trim() }.filter { it in default }.distinct()
        return kept + default.filter { it !in kept }
    }

    fun parse(stored: String?): List<String> = normalise(stored?.split(',').orEmpty())
    fun encode(order: List<String>): String = normalise(order).joinToString(",")

    /** What the bar shows: Breaking only exists with BBC News. */
    fun visible(order: List<String>, bbcOn: Boolean): List<String> = normalise(order).filter { bbcOn || it != BREAKING }

    /** The order to store after dragging the visible buttons: their new order first, any hidden ones keep their place after. */
    fun merge(visible: List<String>, stored: List<String>): List<String> = normalise(visible + stored)

    /** The home page is the first button that is a page: the first one, or the second when Search comes first. */
    fun home(visible: List<String>): String = visible.firstOrNull { it != SEARCH } ?: FOR_YOU
}
