package app.firsthand.data

import org.junit.Assert.*
import org.junit.Test

class AlertLogicTest {
    private fun st(url: String, title: String = url, tags: List<String> = emptyList(), breaking: Boolean = false, source: String? = null) =
        Story(url, title, "", "https://e.com/$url", null, "News", 0L, breaking = breaking, source = source, tags = tags)

    @Test fun sharedCategoriesMergeAcrossSources() {
        for (n in listOf("Tech", "Technology", "Science & Tech", "tech & science", "Gaming")) assertEquals(n, "Tech", Categories.sharedName(n))
        for (n in listOf("Business", "Money", "Business Affairs", "Economy", "Finance")) assertEquals(n, "Business", Categories.sharedName(n))
        for (n in listOf("World", "UK & World News", "International")) assertEquals("World", Categories.sharedName(n))
        assertEquals("Football", Categories.sharedName("Soccer")); assertEquals("Culture", Categories.sharedName("TV & Radio"))
        assertEquals("Science", Categories.sharedName("Science & Environment")); assertEquals("Environment", Categories.sharedName("Climate"))
        assertEquals("Weird", Categories.sharedName("Strange News")); assertEquals("Crime", Categories.sharedName("Courts"))
        assertNull(Categories.sharedName("Front page")); assertNull(Categories.sharedName("Local News")); assertNull(Categories.sharedName("Cricket"))
        // Every shared name is itself recognised, and every alias lands on a listed category.
        assertTrue(Categories.sharedCategories.all { Categories.sharedName(it) == it })
    }

    @Test fun followsKnowTheirSectionAndPublisher() {
        val sky = Sources.catalog.first { it.name == "Sky News" }
        val tech = sky.follow(sky.feeds.first { it.name == "Technology" })
        assertEquals("Technology", Categories.sectionOf(tech)); assertEquals("Tech", Categories.sharedOf(tech)); assertEquals("Sky News", Categories.publisherOf(tech))
        assertEquals("Tech", Categories.sharedOf("Tech")); assertEquals("BBC News", Categories.publisherOf("Tech"))
        assertEquals("Science", Categories.sharedOf("Science & Environment"))
        assertNull(Categories.sharedOf(sky.follow(sky.frontPage)))          // "Home"/front page stays its own section
        assertNull(Categories.sharedOf(Categories.KW + "bieber")); assertNull(Categories.sharedOf(Categories.authorFollow("A B")))
    }

    @Test fun myNewsMergesSharedCategoriesAndKeepsTheRestApart() {
        val sky = Sources.catalog.first { it.name == "Sky News" }
        val gu = Sources.catalog.first { it.name == "The Guardian" }
        val skyTech = sky.follow(sky.feeds.first { it.name == "Technology" }); val guTech = gu.follow(gu.feeds.first { it.name == "Technology" })
        val skyHome = sky.follow(sky.frontPage)
        fun s(u: String, t: Long) = Story(u, u, "", "https://e.com/$u", null, "x", t)
        val by = mapOf("World" to listOf(s("w1", 5)), "Tech" to listOf(s("b1", 10), s("b2", 4)), skyTech to listOf(s("s1", 8)), guTech to listOf(s("g1", 12), s("b1", 10)), skyHome to listOf(s("h1", 1)))
        val groups = NewsGroups.build(listOf("World", "Tech", skyTech, skyHome, guTech), by)
        assertEquals(listOf("World", "Tech", "Sky News · Home"), groups.map { it.title })           // order of first appearance; front page apart
        val tech = groups[1]
        assertEquals(listOf("Tech", skyTech, guTech), tech.follows)
        assertEquals(listOf("g1", "b1", "s1", "b2"), tech.stories.map { it.id })                     // merged, newest first, duplicate url once
        assertEquals(listOf("w1"), groups[0].stories.map { it.id })                                  // a lone follow keeps its own order
    }

    @Test fun breakingHeadlinesAndTags() {
        assertTrue(Breaking.isBreaking(st("a", "BREAKING: Prime minister resigns")))
        assertTrue(Breaking.isBreaking(st("b", "  Breaking news - fire at tower")))
        assertTrue(Breaking.isBreaking(st("c", "JUST IN | Rates cut")))
        assertTrue(Breaking.isBreaking(st("d", "Something happened", tags = listOf("Politics", "Breaking News"))))
        assertTrue(Breaking.isBreaking(st("e", "Flagged by the source", breaking = true)))
        assertFalse(Breaking.isBreaking(st("f", "Breaking Bad star returns")))
        assertFalse(Breaking.isBreaking(st("g", "Why this isn't breaking news")))
    }

    @Test fun quietHours() {
        assertTrue(QuietHours.contains(23, 22, 7)); assertTrue(QuietHours.contains(3, 22, 7)); assertFalse(QuietHours.contains(12, 22, 7))
        assertTrue(QuietHours.contains(8, 8, 17)); assertFalse(QuietHours.contains(17, 8, 17)); assertFalse(QuietHours.contains(5, 9, 9))
    }

    @Test fun plannerLearnsFirstThenNotifiesNewOnly() {
        val g1 = { l: List<Story> -> AlertPlanner.Group("break:sky", "Breaking news · Sky News", l) }
        // First sight of a rule: nothing fires, everything is learned.
        val p1 = AlertPlanner.plan(listOf(g1(listOf(st("a"), st("b")))), emptyList(), emptySet())
        assertTrue(p1.notify.isEmpty()); assertEquals(setOf("break:sky"), p1.primed); assertEquals(listOf("https://e.com/a", "https://e.com/b"), p1.seen)
        // Next run: only the new story fires.
        val p2 = AlertPlanner.plan(listOf(g1(listOf(st("c"), st("a")))), p1.seen, p1.primed)
        assertEquals(listOf("c"), p2.notify.map { it.first.id }); assertEquals("Breaking news · Sky News", p2.notify[0].second)
        // Same story matched by two rules is notified once, by the first rule.
        val two = listOf(g1(listOf(st("d"))), AlertPlanner.Group("kw:x", "“x”", listOf(st("d"))))
        val p3 = AlertPlanner.plan(two, p2.seen, p2.primed + "kw:x")
        assertEquals(listOf("d"), p3.notify.map { it.first.id }); assertEquals("Breaking news · Sky News", p3.notify[0].second)
        // Muted stories are neither notified nor re-offered later; the cap holds and the overflow is marked seen.
        val many = (1..9).map { st("m$it") }
        val p4 = AlertPlanner.plan(listOf(g1(many)), p3.seen, p3.primed, muted = { it.id == "m1" }, max = 5)
        assertEquals(listOf("m2", "m3", "m4", "m5", "m6"), p4.notify.map { it.first.id })
        assertTrue("https://e.com/m9" in p4.seen && "https://e.com/m1" !in p4.seen)
        assertTrue(AlertPlanner.plan(listOf(g1(many)), p4.seen, p4.primed).notify.size <= 1) // m1 (muted before) is the only one not yet seen
    }

    @Test fun plannerKeepsSeenListBounded() {
        val seen = (1..600).map { "u$it" }
        assertEquals(500, AlertPlanner.plan(emptyList(), seen, emptySet()).seen.size)
    }
}
