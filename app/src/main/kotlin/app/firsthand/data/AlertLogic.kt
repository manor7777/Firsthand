package app.firsthand.data

/** Which stories count as "breaking" for sources whose feeds carry no breaking flag (everything except BBC's own data). */
object Breaking {
    /** "BREAKING: ...", "Breaking news - ...", "JUST IN | ...", "Urgent: ...", or a Breaking tag. A title that merely starts with the word ("Breaking Bad") is not. */
    private val lead = Regex("(?i)^\\s*(breaking( news)?|just in|urgent|news alert)\\s*[:\\-–—|]")

    fun isBreaking(s: Story): Boolean =
        s.breaking || lead.containsMatchIn(s.title) || s.tags.any { it.trim().lowercase() in setOf("breaking", "breaking news", "news alert") }
}

/** Quiet hours: no notifications between [start] and [end] (24 h clock, may wrap past midnight). */
object QuietHours {
    fun contains(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }
}

/**
 * Decides what to notify. Each group is one alert rule's current stories ("breaking from Sky", "new Tech stories from BBC",
 * the keyword "Bieber"). A group seen for the first time is only learned (so switching a rule on doesn't fire a burst),
 * later runs notify what is new. A story is notified at most once however many rules match it, and at most [max] per run.
 */
object AlertPlanner {
    class Group(val key: String, val title: String, val stories: List<Story>)
    class Plan(val notify: List<Pair<Story, String>>, val seen: List<String>, val primed: Set<String>)

    fun plan(groups: List<Group>, seen: List<String>, primed: Set<String>, muted: (Story) -> Boolean = { false }, max: Int = 5, keep: Int = 500): Plan {
        val known = seen.toHashSet()
        val newSeen = ArrayList(seen)
        val newPrimed = primed.toMutableSet()
        val queued = LinkedHashMap<String, Pair<Story, String>>()
        fun learn(url: String) { if (known.add(url)) newSeen += url }
        for (g in groups) {
            val stories = g.stories.filterNot(muted)
            if (g.key !in primed) { newPrimed += g.key; stories.forEach { learn(it.url) }; continue }
            for (s in stories) if (s.url !in known && s.url !in queued) queued[s.url] = s to g.title
        }
        val notify = queued.values.take(max)
        queued.keys.forEach(::learn) // the rest are marked seen too, so one busy moment can't pile up later
        return Plan(notify, newSeen.takeLast(keep), newPrimed)
    }
}
