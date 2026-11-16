package com.aurevon.velora.recordings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun listRecordings_returnsOnlyAudioFilesNewestFirstAndReadsDuration() {
        val directory = temporaryFolder.newFolder("recordings")
        val older = File(directory, "older.m4a").apply {
            writeText("audio")
            setLastModified(1_000L)
        }
        val newer = File(directory, "newer.m4a").apply {
            writeText("audio")
            setLastModified(2_000L)
        }
        File(directory, "newer.duration").writeText("125")
        File(directory, "notes.txt").writeText("ignore")
        val repository = RecordingRepository(directory)

        val result = repository.listRecordings()

        assertEquals(listOf(newer, older), result.map { it.file })
        assertEquals(125L, result.first().durationSeconds)
        assertEquals(null, result.last().durationSeconds)
    }

    @Test
    fun deleteRecording_removesAudioAndDurationMetadata() {
        val directory = temporaryFolder.newFolder("recordings")
        val audio = File(directory, "sample.m4a").apply { writeText("audio") }
        val metadata = File(directory, "sample.duration").apply { writeText("42") }
        val repository = RecordingRepository(directory)

        assertTrue(repository.deleteRecording(audio))
        assertFalse(audio.exists())
        assertFalse(metadata.exists())
        assertTrue(repository.listRecordings().isEmpty())
    }

    @Test
    fun deleteRecording_rejectsFilesOutsideRepository() {
        val directory = temporaryFolder.newFolder("recordings")
        val outside = temporaryFolder.newFile("outside.m4a")
        val repository = RecordingRepository(directory)

        assertFalse(repository.deleteRecording(outside))
        assertTrue(outside.exists())
    }
}
