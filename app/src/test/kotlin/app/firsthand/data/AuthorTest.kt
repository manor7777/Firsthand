package app.firsthand.data

import org.junit.Assert.*
import org.junit.Test

class AuthorTest {
    @Test fun sameWriterRegardlessOfCaseAndSpacing() {
        assertEquals(Author.key("Simon King"), Author.key(" simon   KING "))
        assertNotEquals(Author.key("Simon King"), Author.key("Simon Kingston"))
    }

    @Test fun authorFollows() {
        val f = Categories.authorFollow("  Michael   Race ")
        assertEquals("author:Michael Race", f)
        assertTrue(Categories.isAuthor(f) && Categories.isValid(f))
        assertEquals("Michael Race", Categories.authorName(f))
        assertEquals("by Michael Race", Categories.label(f))
        assertEquals(f, Categories.normalize(f)) // survives settings import
        assertFalse(Categories.isValid("author:"))
        assertFalse(Categories.isValid("author:x"))
    }
}
