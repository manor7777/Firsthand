package app.firsthand.data

/**
 * What a blocked keyword is matched against.
 * - [Subject]: the headline and the story's topic tags, i.e. what the story is *about*. A story that only mentions the
 *   word in passing (in its summary or body) is not hidden.
 * - [Anywhere] ("Story"): anywhere in the headline, summary or tags, even in passing.
 */
enum class BlockScope(val key: String, val label: String) {
    Subject("subject", "Subject"),
    Anywhere("story", "Story");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
}

/** A keyword or phrase the user doesn't want to see stories about. Matches whole words, ignoring case ("Bieber" won't match "Bieberstein"). */
class BlockRule(val term: String, val scope: BlockScope) {
    private val re = Regex(
        "(?<![\\p{L}\\p{N}])" + term.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )

    fun matches(s: Story): Boolean {
        if (re.containsMatchIn(s.title)) return true
        if (s.tags.any { re.containsMatchIn(it) }) return true
        return scope == BlockScope.Anywhere && re.containsMatchIn(s.summary)
    }

    /** The stored form: "subject|Justin Bieber". */
    val line get() = scope.key + "|" + term
    override fun equals(other: Any?) = other is BlockRule && other.line.equals(line, ignoreCase = true)
    override fun hashCode() = line.lowercase().hashCode()
}

object Blocking {
    /** A tidy keyword (single spaces, 2-60 characters, none of the characters the stored form uses), or null if it isn't usable. */
    fun clean(raw: String): String? {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        return t.takeIf { it.length in 2..60 && '|' !in it && t.any { c -> c.isLetterOrDigit() } }
    }

    fun parse(line: String): BlockRule? {
        val scope = BlockScope.of(line.substringBefore('|', "")) ?: return null
        val term = clean(line.substringAfter('|', "")) ?: return null
        return BlockRule(term, scope)
    }

    /** Parses stored lines, dropping anything malformed and duplicates. */
    fun parseAll(lines: List<String>): List<BlockRule> = lines.mapNotNull(::parse).distinct()
}
