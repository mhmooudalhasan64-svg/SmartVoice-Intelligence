package com.smartvoice.intelligence

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OrinLibraryDocument(val uri: Uri, val name: String)

@Composable
fun OrinDocumentLibrary() {
    val context = LocalContext.current
    val documents = remember { mutableStateListOf<OrinLibraryDocument>() }
    val preferences = remember { context.getSharedPreferences("orin_document_library", 0) }
    val scope = rememberCoroutineScope()
    var extractedSections by remember { mutableStateOf<List<OrinExtractedSection>>(emptyList()) }
    var selectedDocumentName by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var documentQuestion by remember { mutableStateOf("") }
    var insightText by remember { mutableStateOf<String?>(null) }
    var loadingDocument by remember { mutableStateOf<String?>(null) }
    var libraryStatus by remember { mutableStateOf<String?>(null) }

    fun loadDocuments() {
        documents.clear()
        preferences.getStringSet("document_uris", emptySet()).orEmpty().forEach { value ->
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@forEach
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: "ملف محفوظ"
            documents.add(OrinLibraryDocument(uri, name))
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris ->
        selectedUris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: "ملف محفوظ"
            if (documents.none { it.uri == uri }) documents.add(OrinLibraryDocument(uri, name))
        }
        preferences.edit().putStringSet("document_uris", documents.map { it.uri.toString() }.toSet()).apply()
        libraryStatus = if (selectedUris.isEmpty()) null else "تمت إضافة ${selectedUris.size} ملف/ملفات إلى المكتبة"
    }

    LaunchedEffect(Unit) { loadDocuments() }

    fun extractDocument(document: OrinLibraryDocument) {
        if (loadingDocument != null) return
        loadingDocument = document.name
        libraryStatus = null
        scope.launch {
            try {
                val sections = withContext(Dispatchers.IO) {
                    OrinDocumentReader.read(context, document.uri, document.name)
                }
                extractedSections = sections
                selectedDocumentName = document.name
                searchQuery = ""
                documentQuestion = ""
                insightText = null
                libraryStatus = if (sections.isEmpty()) {
                    "لم يُعثر على نص قابل للاستخراج. قد يكون PDF ممسوحاً ضوئياً ويحتاج إلى OCR."
                } else {
                    "اكتمل استخراج النص من ${sections.size} جزء."
                }
            } catch (error: Exception) {
                libraryStatus = "تعذر قراءة الملف: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                loadingDocument = null
            }
        }
    }

    val visibleSections = remember(extractedSections, searchQuery) {
        if (searchQuery.isBlank()) extractedSections
        else extractedSections.filter { it.text.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مكتبة ملفات ORIN", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("أضف الملفات واستخرج نصوصها وابحث داخلها",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(documents.size.toString(), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    documentPicker.launch(arrayOf(
                        "application/pdf",
                        "text/plain",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Text("إضافة PDF أو TXT أو Word", modifier = Modifier.padding(start = 8.dp))
            }

            if (documents.isEmpty()) {
                Text("لم تتم إضافة ملفات بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                documents.forEach { document ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(document.name, color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(if (loadingDocument == document.name) "جارٍ استخراج النص..." else "اضغط لقراءة المحتوى",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        if (loadingDocument == document.name) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.primary)
                        } else {
                            IconButton(onClick = { extractDocument(document) }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "قراءة الملف",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = {
                            documents.remove(document)
                            preferences.edit().putStringSet("document_uris",
                                documents.map { it.uri.toString() }.toSet()).apply()
                            if (selectedDocumentName == document.name) {
                                selectedDocumentName = null
                                extractedSections = emptyList()
                                searchQuery = ""
                            }
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    document.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة الملف",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            libraryStatus?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            if (selectedDocumentName != null) {
                androidx.compose.material3.HorizontalDivider()
                Text("محتوى: $selectedDocumentName", color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ابحث داخل الملف") },
                    placeholder = { Text("اكتب كلمة أو عبارة") }
                )
                Text("النتائج: ${visibleSections.size} من ${extractedSections.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
                if (visibleSections.isEmpty()) {
                    Text("لا توجد مقاطع مطابقة لعبارة البحث.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    visibleSections.take(40).forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            section.pageNumber?.let { page ->
                                Text("الصفحة $page", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            SelectionContainer {
                                Text(section.text, color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (visibleSections.size > 40) {
                        Text("يتم عرض أول 40 نتيجة لتخفيف الحمل على الواجهة.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (selectedDocumentName != null && extractedSections.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                Text(
                    "أدوات فهم المستند",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        insightText = OrinDocumentInsights.summarize(extractedSections)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إنشاء ملخص من محتوى الملف")
                }
                OutlinedTextField(
                    value = documentQuestion,
                    onValueChange = { documentQuestion = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسأل عن محتوى هذا الملف") },
                    placeholder = { Text("مثلاً: ما الفكرة الرئيسية؟") }
                )
                Button(
                    onClick = {
                        insightText = OrinDocumentInsights.answer(extractedSections, documentQuestion).text
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = documentQuestion.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ابحث عن إجابة في المستند")
                }
                insightText?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                result,
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Text(
                "يدعم استخراج النص من PDF النصي وTXT وDOCX. ملفات PDF المصوّرة تحتاج إلى OCR، والتلخيص الذكي بالمساعد سيأتي في مرحلة الربط.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
