package com.smartvoice.intelligence

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

data class OrinExtractedSection(
    val pageNumber: Int?,
    val text: String
)

object OrinDocumentReader {
    fun read(context: Context, uri: Uri, displayName: String): List<OrinExtractedSection> {
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".pdf") || context.contentResolver.getType(uri) == "application/pdf" ->
                readPdf(context, uri)
            lowerName.endsWith(".docx") ||
                context.contentResolver.getType(uri) ==
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                readDocx(context, uri)
            lowerName.endsWith(".txt") || context.contentResolver.getType(uri)?.startsWith("text/") == true ->
                readText(context, uri)
            else -> throw IllegalArgumentException("صيغة الملف غير مدعومة حالياً")
        }
    }

    private fun readPdf(context: Context, uri: Uri): List<OrinExtractedSection> {
        PDFBoxResourceLoader.init(context)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف PDF")
        input.use { stream ->
            PDDocument.load(BufferedInputStream(stream)).use { document ->
                val stripper = PDFTextStripper()
                return (1..document.numberOfPages).mapNotNull { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document).trim()
                    text.takeIf { it.isNotBlank() }?.let {
                        OrinExtractedSection(page, it)
                    }
                }
            }
        }
    }

    private fun readText(context: Context, uri: Uri): List<OrinExtractedSection> {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("تعذر فتح الملف النصي")
        return text.split(Regex("\\R\\s*\\R"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { OrinExtractedSection(null, it) }
    }

    private fun readDocx(context: Context, uri: Uri): List<OrinExtractedSection> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح مستند Word")
        input.use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setInput(zip, "UTF-8")
                        val paragraphs = mutableListOf<String>()
                        val current = StringBuilder()
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> if (parser.name == "p") current.setLength(0)
                                XmlPullParser.TEXT -> current.append(parser.text)
                                XmlPullParser.END_TAG -> if (parser.name == "p") {
                                    current.toString().trim().takeIf(String::isNotBlank)?.let(paragraphs::add)
                                }
                            }
                            event = parser.next()
                        }
                        return paragraphs.map { OrinExtractedSection(null, it) }
                    }
                    zip.closeEntry()
                }
            }
        }
        throw IllegalArgumentException("لم أجد محتوى Word داخل الملف")
    }
}
