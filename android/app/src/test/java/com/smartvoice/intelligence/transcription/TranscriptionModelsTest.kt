package com.aurevon.velora.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptionModelsTest {

    @Test
    fun recognizedWordAcceptsValidTimestampAndConfidence() {
        val word = RecognizedWord(
            text = "مرحبا",
            startMs = 120,
            endMs = 540,
            confidence = 0.92f
        )

        assertEquals(120L, word.startMs)
        assertEquals(540L, word.endMs)
    }

    @Test
    fun recognizedWordRejectsNegativeStart() {
        assertThrows(IllegalArgumentException::class.java) {
            RecognizedWord(text = "كلمة", startMs = -1, endMs = 100)
        }
    }

    @Test
    fun recognizedWordRejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException::class.java) {
            RecognizedWord(text = "كلمة", startMs = 200, endMs = 100)
        }
    }

    @Test
    fun transcriptResultJoinsSegmentsInTheirGivenOrder() {
        val firstWord = RecognizedWord("صباح", 0, 300)
        val secondWord = RecognizedWord("الخير", 310, 650)
        val first = TranscriptSegment(0, "صباح", 0, 300, listOf(firstWord))
        val second = TranscriptSegment(1, "الخير", 310, 650, listOf(secondWord))

        val result = TranscriptionResult(
            recordingId = "recording-1",
            languageTag = "ar",
            segments = listOf(first, second),
            engineId = "test-engine",
            modelId = "test-model",
            completedAtEpochMs = 1L
        )

        assertEquals("صباح الخير", result.fullText)
    }
}
