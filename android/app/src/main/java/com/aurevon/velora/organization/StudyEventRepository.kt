package com.aurevon.velora.organization

import java.io.File
import java.time.LocalDate
import java.util.Base64

/**
 * Local persistence for study events explicitly confirmed by the user.
 * One record per line; string fields are Base64 encoded to preserve Arabic text.
 */
class StudyEventRepository(private val storageFile: File) {
    fun list(): List<StudyEventDraft> {
        if (!storageFile.exists()) return emptyList()
        return storageFile.readLines().mapNotNull(::decode)
            .sortedWith(compareBy<StudyEventDraft> { it.date == null }.thenBy { it.date })
    }

    fun saveConfirmed(draft: StudyEventDraft): Boolean {
        return try {
            storageFile.parentFile?.mkdirs()
            storageFile.appendText(encode(draft.copy(requiresConfirmation = false)) + "\n")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun encode(event: StudyEventDraft): String {
        fun b64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        return listOf(
            b64(event.subject),
            event.eventType.name,
            event.date?.toString() ?: "",
            b64(event.dateExpression.orEmpty()),
            b64(event.sourceText)
        ).joinToString("|")
    }

    private fun decode(line: String): StudyEventDraft? {
        return try {
            val fields = line.split("|")
            if (fields.size != 5) return null
            fun fromB64(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
            StudyEventDraft(
                subject = fromB64(fields[0]),
                eventType = StudyEventType.valueOf(fields[1]),
                date = fields[2].takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                dateExpression = fromB64(fields[3]).ifBlank { null },
                sourceText = fromB64(fields[4]),
                requiresConfirmation = false
            )
        } catch (_: Exception) {
            null
        }
    }
}
