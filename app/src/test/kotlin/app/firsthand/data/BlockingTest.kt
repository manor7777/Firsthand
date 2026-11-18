package app.firsthand.data

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BlockingTest {
    private fun story(title: String, summary: String = "", tags: List<String> = emptyList()) =
        Story("u", title, summary, "https://e.com/$title", null, "News", 0L, tags = tags)

    private val subject = BlockRule("Justin Bieber", BlockScope.Subject)
    private val anywhere = BlockRule("Justin Bieber", BlockScope.Anywhere)

    @Test fun aboutVersusMentioned() {
        val about = story("Justin Bieber announces new tour dates")
        val passing = story("Pop star spotted on holiday", "Justin Bieber also happened to like this yoghurt")
        assertTrue(subject.matches(about)); assertTrue(anywhere.matches(about))
        // The whole point: a passing mention is not "about" him, unless the user asked for story-wide blocking.
        assertFalse(subject.matches(passing))
        assertTrue(anywhere.matches(passing))
    }

    @Test fun tagsCountAsTheSubject() {
        val s = story("Star's surprise return", "Fans react", tags = listOf("Music", "Justin Bieber"))
        assertTrue(subject.matches(s)); assertTrue(anywhere.matches(s))
        assertFalse(subject.matches(story("Star's surprise return", "Fans react", tags = listOf("Music"))))
    }

    @Test fun wholeWordsAnyCaseAndPunctuation() {
        val r = BlockRule("Bieber", BlockScope.Subject)
        assertTrue(r.matches(story("BIEBER back in town")))
        assertTrue(r.matches(story("Justin Bieber's new album")))        // possessive
        assertTrue(r.matches(story("Bieber, Bieber everywhere")))
        assertFalse(r.matches(story("Bieberstein reports quarterly profits"))) // not a whole word
        assertFalse(r.matches(story("Anti-Biebers rally")))
        assertTrue(BlockRule("justin   bieber", BlockScope.Subject).matches(story("Justin Bieber (again)")))   // spacing flexible
        assertTrue(BlockRule("C++", BlockScope.Subject).matches(story("Why C++ still matters")))                  // regex characters are literal
        assertTrue(BlockRule("Man Utd", BlockScope.Subject).matches(story("Man Utd sign striker")))
        assertFalse(BlockRule("cat", BlockScope.Subject).matches(story("Concatenation explained")))
    }

    @Test fun parsingAndCleaning() {
        assertEquals("Justin Bieber", Blocking.clean("  Justin   Bieber "))
        assertNull(Blocking.clean("a")); assertNull(Blocking.clean("   ")); assertNull(Blocking.clean("---")); assertNull(Blocking.clean("a|b c"))
        assertNull(Blocking.clean("x".repeat(61)))
        val r = Blocking.parse("subject|Justin Bieber")!!
        assertEquals(BlockScope.Subject, r.scope); assertEquals("subject|Justin Bieber", r.line)
        assertEquals(BlockScope.Anywhere, Blocking.parse("story|Tory leadership")!!.scope)
        assertNull(Blocking.parse("nonsense|Justin Bieber")); assertNull(Blocking.parse("subject|")); assertNull(Blocking.parse("Justin Bieber"))
        // Duplicates (any case) collapse; malformed lines are dropped.
        assertEquals(2, Blocking.parseAll(listOf("subject|Justin Bieber", "subject|justin bieber", "story|Justin Bieber", "junk")).size)
    }

    @Test fun reachFeedTagsDriveSubjectBlocking() {
        val f = File("../fixtures/sources/example_post.xml").takeIf { it.exists() } ?: File("fixtures/sources/example_post.xml")
        assumeTrue("fixture missing", f.exists())
        val stories = RssParser.parse(f.readText(), "Front page", "Example Post")
        assertTrue(stories[0].tags.containsAll(listOf("Local News", "A52", "Fires", "Traffic & Travel")))
        // Blocking the subject "Theatre Royal Exampletown" removes only the story tagged with it.
        val rule = BlockRule("Theatre Royal Exampletown", BlockScope.Subject)
        assertEquals(1, stories.count { rule.matches(it) })
        assertEquals("Exampletown Theatre Royal cancels all Opera North performances after man dies in 'tragic incident'", stories.first { rule.matches(it) }.title)
        // "Technology" is only a tag on the PlayStation story, so blocking it as a subject hides just that one.
        assertEquals(listOf("PlayStation Network down as thousands of UK gamers report issues"), stories.filter { BlockRule("Technology", BlockScope.Subject).matches(it) }.map { it.title })
    }
}
