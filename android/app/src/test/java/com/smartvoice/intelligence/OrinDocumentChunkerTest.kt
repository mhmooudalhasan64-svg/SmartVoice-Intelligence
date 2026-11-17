package com.smartvoice.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrinDocumentChunkerTest {

    @Test
    fun chunksA200PageDocumentWithoutLosingPageReferences() {
        val sections = (1..200).map { page ->
            OrinExtractedSection(
                pageNumber = page,
                text = (1..8).joinToString(" ") {
                    "Page $page contains educational material and important details."
                }
            )
        }

        val chunks = OrinDocumentChunker.chunk(sections, maxChars = 180, overlapChars = 24)

        assertTrue(chunks.isNotEmpty())
        assertEquals((0 until chunks.size).toList(), chunks.map { it.chunkIndex })
        assertEquals((1..200).toSet(), chunks.mapNotNull { it.pageNumber }.toSet())
        assertTrue(chunks.all { it.text.length <= 180 })
        assertTrue(chunks.all { it.text.isNotBlank() })
    }

    @Test
    fun longSentenceIsSplitWithoutDuplicatingThePreviousBuffer() {
        val prefix = "Opening sentence for this page."
        val longSentence = "A".repeat(520)
        val sections = listOf(OrinExtractedSection(7, "$prefix $longSentence"))

        val chunks = OrinDocumentChunker.chunk(sections, maxChars = 160, overlapChars = 20)

        assertTrue(chunks.size >= 4)
        assertTrue(chunks.all { it.text.length <= 160 })
        assertEquals(1, chunks.count { it.text.contains(prefix) })
        assertTrue(chunks.all { it.pageNumber == 7 })
    }

    @Test
    fun blankSectionsAreIgnored() {
        val chunks = OrinDocumentChunker.chunk(
            listOf(
                OrinExtractedSection(1, "   "),
                OrinExtractedSection(2, "A useful sentence with enough content.")
            )
        )

        assertFalse(chunks.isEmpty())
        assertEquals(setOf(2), chunks.mapNotNull { it.pageNumber }.toSet())
    }
}
