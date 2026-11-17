package com.smartvoice.intelligence

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.provider.OpenableColumns

private data class OrinLibraryDocument(val uri: Uri, val name: String)

@Composable
fun OrinDocumentLibrary() {
    val context = LocalContext.current
    val documents = remember { mutableStateListOf<OrinLibraryDocument>() }
    val preferences = remember { context.getSharedPreferences("orin_document_library", 0) }

    fun loadDocuments() {
        documents.clear()
        preferences.getStringSet("document_uris", emptySet()).orEmpty().forEach { value ->
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@forEach
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
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
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: "ملف محفوظ"
            if (documents.none { it.uri == uri }) documents.add(OrinLibraryDocument(uri, name))
        }
        preferences.edit().putStringSet("document_uris", documents.map { it.uri.toString() }.toSet()).apply()
    }

    LaunchedEffect(Unit) { loadDocuments() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "مكتبة ملفات ORIN",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "أضف ملفاتك للاحتفاظ بها داخل مساحة التطبيق",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    documents.size.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    documentPicker.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Text("إضافة ملفات PDF أو مستندات", modifier = Modifier.padding(start = 8.dp))
            }

            if (documents.isEmpty()) {
                Text(
                    "لم تتم إضافة ملفات بعد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                documents.forEach { document ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            document.name,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        IconButton(onClick = {
                            documents.remove(document)
                            preferences.edit().putStringSet(
                                "document_uris",
                                documents.map { it.uri.toString() }.toSet()
                            ).apply()
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    document.uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "إزالة الملف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Text(
                "المرحلة الحالية: حفظ الملفات وإدارتها محلياً. تحليل المحتوى والتلخيص والبحث سيُربط في مرحلة لاحقة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
