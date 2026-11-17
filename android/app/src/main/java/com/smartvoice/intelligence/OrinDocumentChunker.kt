package com.smartvoice.intelligence

/**
 * Splits extracted document text into bounded passages while preserving the source page.
 * This keeps large documents manageable for retrieval and later AI requests.
 */
data class OrinDocumentChunk(
    val pageNumber: Int?,
    val chunkIndex: Int,
    val text: String
)

object OrinDocumentChunker {
    const val DEFAULT_MAX_CHARS = 2800
    const val DEFAULT_OVERLAP_CHARS = 350

    fun chunk(
        sections: List<OrinExtractedSection>,
        maxChars: Int = DEFAULT_MAX_CHARS,
        overlapChars: Int = DEFAULT_OVERLAP_CHARS
    ): List<OrinDocumentChunk> {
        require(maxChars > 0) { "maxChars must be positive" }
        require(overlapChars >= 0 && overlapChars < maxChars) {
            "overlapChars must be non-negative and smaller than maxChars"
        }

        val result = mutableListOf<OrinDocumentChunk>()
        var globalIndex = 0
        sections.forEach { section ->
            val text = section.text.trim()
            if (text.isBlank()) return@forEach

            val sentences = text.split(Regex("(?<=[.!?؟])\\s+|\\n+"))
                .map(String::trim)
                .filter(String::isNotBlank)
            var current = StringBuilder()

            fun emit() {
                val value = current.toString().trim()
                if (value.isNotBlank()) {
                    result += OrinDocumentChunk(section.pageNumber, globalIndex++, value)
                }
            }

            sentences.forEach { sentence ->
                if (sentence.length > maxChars) {
                    if (current.isNotEmpty()) {
                        emit()
                        current.setLength(0)
                    }
                    var offset = 0
                    while (offset < sentence.length) {
                        val end = minOf(offset + maxChars, sentence.length)
                        val piece = sentence.substring(offset, end).trim()
                        if (piece.isNotBlank()) {
                            result += OrinDocumentChunk(section.pageNumber, globalIndex++, piece)
                        }
                        if (end == sentence.length) break
                        offset = (end - overlapChars).coerceAtLeast(offset + 1)
                    }
                } else {
                    val extra = if (current.isEmpty()) 0 else 1
                    if (current.length + extra + sentence.length > maxChars) {
                        val previous = current.toString()
                        emit()
                        val carry = previous.takeLast(overlapChars).trim()
                        current = StringBuilder()
                        if (carry.isNotBlank()) current.append(carry).append(' ')
                    }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(sentence)
                }
            }
            emit()
        }
        return result
    }
}
