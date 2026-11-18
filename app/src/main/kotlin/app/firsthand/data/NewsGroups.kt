package app.firsthand.data

/** One section of My News: a shared category merged across sources ("Tech" = BBC Tech + Sky Technology), or a single follow. */
data class NewsGroup(val key: String, val title: String, val follows: List<String>, val stories: List<Story>)

object NewsGroups {
    /**
     * Groups the followed items for My News, in the user's order. Follows that belong to the same shared category are
     * merged into one group whose stories are interleaved newest first; everything else stays its own group.
     */
    fun build(selected: List<String>, byFollow: Map<String, List<Story>>): List<NewsGroup> {
        val order = LinkedHashMap<String, MutableList<String>>()
        for (f in selected) order.getOrPut(Categories.sharedOf(f)?.let { "cat:$it" } ?: f) { mutableListOf() }.add(f)
        return order.map { (key, follows) ->
            val shared = key.startsWith("cat:")
            val stories = if (follows.size == 1) byFollow[follows[0]].orEmpty()
            else follows.flatMap { byFollow[it].orEmpty() }.distinctBy { it.url }.sortedByDescending { it.publishedMillis }
            NewsGroup(key, if (shared) key.removePrefix("cat:") else Categories.label(follows.first()), follows, stories)
        }
    }
}
