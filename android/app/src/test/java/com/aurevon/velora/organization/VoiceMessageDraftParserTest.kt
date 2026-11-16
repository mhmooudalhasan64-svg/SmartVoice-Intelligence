package com.aurevon.velora.organization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VoiceMessageDraftParserTest {
    private val parser = VoiceMessageDraftParser()
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun parsesTeacherAnnouncementAsTomorrowPhysicsLessonDraft() {
        val draft = parser.parse("أستاذ الفيزياء قال بكرة عنا فيزياء", today)

        requireNotNull(draft)
        assertEquals("الفيزياء", draft.subject)
        assertEquals(StudyEventType.LESSON, draft.eventType)
        assertEquals(today.plusDays(1), draft.date)
        assertEquals("بكرة", draft.dateExpression)
        assertEquals("أستاذ الفيزياء قال بكرة عنا فيزياء", draft.sourceText)
        assertTrue(draft.requiresConfirmation)
    }

    @Test
    fun parsesExamAndHomeworkTypes() {
        val exam = parser.parse("بكرا امتحان كيمياء", today)
        val homework = parser.parse("اليوم واجب رياضيات", today)

        assertEquals(StudyEventType.EXAM, requireNotNull(exam).eventType)
        assertEquals(today.plusDays(1), exam.date)
        assertEquals(StudyEventType.HOMEWORK, requireNotNull(homework).eventType)
        assertEquals(today, homework.date)
    }

    @Test
    fun leavesDateUnknownWhenTranscriptHasNoDate() {
        val draft = parser.parse("عنا فيزياء", today)

        assertEquals("الفيزياء", requireNotNull(draft).subject)
        assertNull(draft.date)
        assertNull(draft.dateExpression)
    }

    @Test
    fun doesNotGuessUnsupportedSubject() {
        assertNull(parser.parse("بكرة عنا درس مهم", today))
    }

    @Test
    fun recognizesDayAfterTomorrow() {
        val draft = parser.parse("بعد بكرة عنا فيزياء", today)

        assertEquals(today.plusDays(2), requireNotNull(draft).date)
        assertTrue(requireNotNull(draft).requiresConfirmation)
    }
}
