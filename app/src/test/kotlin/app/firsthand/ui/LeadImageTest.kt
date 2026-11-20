package app.firsthand.ui

import app.firsthand.data.Block
import org.junit.Assert.*
import org.junit.Test

/** The double-picture bug: BBC's branded share image and the body photo must be recognised as the same file. */
class LeadImageTest {
    private val body = listOf(
        Block.Image("https://ichef.bbci.co.uk/ace/standard/800/cpsprodpb/786b/live/57899d60-e0f2-11f1-a701-fddedf244c16.jpg", "cap"),
        Block.Paragraph("text"),
    )

    @Test fun brandedShareImageMatchesBodyPhoto() {
        val d = leadDuplicate("https://ichef.bbci.co.uk/ace/branded_news/1200/cpsprodpb/786b/live/57899d60-e0f2-11f1-a701-fddedf244c16.jpg", body)
        assertEquals(0, d?.index)
    }

    @Test fun feedThumbnailWebpMatches() {
        assertNotNull(leadDuplicate("https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/786b/live/57899d60-e0f2-11f1-a701-fddedf244c16.jpg.webp", body))
    }

    @Test fun differentIdButBodyOpensWithImageIsDuplicate() {
        val d = leadDuplicate("https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/29e9/live/8dd88650-df63-11f1-a701-fddedf244c16.jpg", body)
        assertEquals(0, d?.index)
    }

    @Test fun laterDifferentPhotoIsKept() {
        val text = listOf(Block.Paragraph("intro"), body[0])
        assertNull(leadDuplicate("https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/29e9/live/8dd88650-df63-11f1-a701-fddedf244c16.jpg", text))
        assertNull(leadDuplicate(null, body))
    }
}
