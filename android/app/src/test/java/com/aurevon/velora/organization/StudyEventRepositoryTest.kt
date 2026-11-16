package com.aurevon.velora.organization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class StudyEventRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesAndReloadsConfirmedArabicStudyEvent() {
        val file = temporaryFolder.newFile("study-events.db")
        val repository = StudyEventRepository(file)
        val event = StudyEventDraft(
            subject = "الفيزياء",
            eventType = StudyEventType.LESSON,
            date = LocalDate.of(2026, 11, 17),
            dateExpression = "بكرة",
            sourceText = "أستاذ الفيزياء قال بكرة عنا فيزياء"
        )

        assertTrue(repository.saveConfirmed(event))
        val loaded = repository.list().single()
        assertEquals(event.subject, loaded.subject)
        assertEquals(event.eventType, loaded.eventType)
        assertEquals(event.date, loaded.date)
        assertEquals(event.sourceText, loaded.sourceText)
        assertFalse(loaded.requiresConfirmation)
    }
}
