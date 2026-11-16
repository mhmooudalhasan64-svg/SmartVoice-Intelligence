package com.aurevon.velora.organization

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId

enum class StudyEventType {
    LESSON,
    HOMEWORK,
    EXAM
}

/**
 * A proposed study-calendar item extracted from a voice transcript.
 *
 * This is deliberately a draft: callers must show the source transcript and
 * obtain user confirmation before persisting or scheduling the item.
 */
data class StudyEventDraft(
    val subject: String,
    val eventType: StudyEventType,
    val date: LocalDate?,
    val dateExpression: String?,
    val sourceText: String,
    val requiresConfirmation: Boolean = true
)

/**
 * Conservative first-pass extraction for common Arabic study announcements.
 *
 * This parser is not an AI model. It only creates a draft when it can identify
 * a supported school subject; ambiguous or unsupported language is not guessed.
 */
class VoiceMessageDraftParser(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private data class SubjectRule(
        val canonicalName: String,
        val aliases: List<String>
    )

    private val subjects = listOf(
        SubjectRule("الفيزياء", listOf("فيزياء", "الفيزياء")),
        SubjectRule("الكيمياء", listOf("كيمياء", "الكيمياء")),
        SubjectRule("الرياضيات", listOf("رياضيات", "الرياضيات")),
        SubjectRule("اللغة العربية", listOf("عربي", "العربي", "اللغة العربية")),
        SubjectRule("اللغة الإنجليزية", listOf("انكليزي", "إنكليزي", "انجليزي", "إنجليزي", "اللغة الانجليزية")),
        SubjectRule("التاريخ", listOf("تاريخ", "التاريخ")),
        SubjectRule("الجغرافيا", listOf("جغرافيا", "الجغرافيا")),
        SubjectRule("الفلسفة", listOf("فلسفة", "الفلسفة")),
        SubjectRule("علم الأحياء", listOf("احياء", "أحياء", "علم الاحياء", "علم الأحياء"))
    )

    fun parse(transcript: String, today: LocalDate = LocalDate.now(zoneId)): StudyEventDraft? {
        val source = transcript.trim()
        if (source.isEmpty()) return null

        val normalized = normalize(source)
        val subject = subjects.firstOrNull { rule ->
            rule.aliases.any { alias -> containsPhrase(normalized, normalize(alias)) }
        } ?: return null

        val (date, dateExpression) = when {
            containsPhrase(normalized, "بعد بكرة") || containsPhrase(normalized, "بعد بكرا") ->
                today.plusDays(2) to "بعد بكرة"
            containsPhrase(normalized, "بكرة") || containsPhrase(normalized, "بكرا") ||
                containsPhrase(normalized, "غدا") ->
                today.plusDays(1) to "غداً"
            containsPhrase(normalized, "اليوم") ->
                today to "اليوم"
            else -> null to null
        }

        val eventType = when {
            listOf("امتحان", "اختبار", "فحص").any { containsPhrase(normalized, it) } ->
                StudyEventType.EXAM
            listOf("واجب", "وظيفة", "حل").any { containsPhrase(normalized, it) } ->
                StudyEventType.HOMEWORK
            else -> StudyEventType.LESSON
        }

        return StudyEventDraft(
            subject = subject.canonicalName,
            eventType = eventType,
            date = date,
            dateExpression = dateExpression,
            sourceText = source
        )
    }

    private fun normalize(value: String): String {
        val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutMarks
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace(Regex("[\\p{Punct}،؛؟!]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        return Regex("(^|\\s)${Regex.escape(phrase)}($|\\s)").containsMatchIn(text)
    }
}
