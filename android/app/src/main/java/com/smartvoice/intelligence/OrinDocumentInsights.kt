package com.smartvoice.intelligence

data class OrinDocumentAnswer(val text: String, val pageNumbers: List<Int>, val matchedSections: Int)

/** Extractive on-device tools; results are grounded in the selected document text. */
object OrinDocumentInsights {
    fun summarize(sections: List<OrinExtractedSection>, maxSentences: Int = 8): String {
        val candidates = sections.flatMap { section ->
            splitSentences(section.text).map { sentence ->
                Triple(sentence, section.pageNumber, scoreSentence(sentence))
            }
        }.filter { it.first.length >= 25 }
        if (candidates.isEmpty()) return "لم أجد نصاً كافياً لتكوين ملخص."
        val chosen = candidates.sortedByDescending { it.third }.take(maxSentences)
            .sortedBy { item -> candidates.indexOf(item) }
        return chosen.joinToString("\n\n") { item ->
            val sentence = item.first
            val page = item.second
            if (page != null) "$sentence (ص. $page)" else sentence
        }
    }

    fun answer(sections: List<OrinExtractedSection>, question: String): OrinDocumentAnswer {
        val terms = normalize(question).split(Regex("\\s+")).filter { it.length >= 2 }.distinct()
        if (terms.isEmpty()) return OrinDocumentAnswer("اكتب سؤالاً أو كلمات مفتاحية مرتبطة بمحتوى الملف.", emptyList(), 0)
        val chunks = OrinDocumentChunker.chunk(sections)
        val matches = chunks.mapNotNull { chunk ->
            val normalized = normalize(chunk.text)
            val score = terms.count { normalized.contains(it) }
            if (score == 0) null else Triple(chunk, score, normalized.length)
        }.sortedWith(compareByDescending<Triple<OrinDocumentChunk, Int, Int>> { it.second }.thenBy { it.third }).take(5)
        if (matches.isEmpty()) return OrinDocumentAnswer(
            "لم أعثر على مقطع مطابق في النص المستخرج. جرّب كلمات أخرى أو صياغة أبسط.", emptyList(), 0
        )
        val excerpts = matches.map { item ->
            val chunk = item.first
            val excerpt = bestExcerpt(chunk.text, terms)
            if (chunk.pageNumber != null) "• $excerpt (الصفحة ${chunk.pageNumber})" else "• $excerpt"
        }
        return OrinDocumentAnswer(
            "المقاطع الأقرب إلى سؤالك:\n\n" + excerpts.joinToString("\n\n") +
                "\n\nتنبيه: هذه مطابقة واسترجاع من نص الملف، وليست إجابة مولّدة بنموذج لغوي.",
            matches.mapNotNull { it.first.pageNumber }.distinct(), matches.size
        )
    }

    private fun bestExcerpt(text: String, terms: List<String>): String =
        splitSentences(text).maxByOrNull { sentence ->
            val normalized = normalize(sentence)
            terms.count { normalized.contains(it) }
        }?.take(700) ?: text.take(700)

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?؟])\\s+|\\n+")).map(String::trim).filter(String::isNotBlank)

    private fun scoreSentence(sentence: String): Int {
        val words = normalize(sentence).split(Regex("\\s+")).filter { it.length > 2 }
        return words.distinct().size * 2 + if (sentence.length in 45..260) 8 else 0
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()
}
