package com.smartvoice.intelligence

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.smartvoice.intelligence.transcription.ArabicModelInstaller
import com.smartvoice.intelligence.transcription.VoskTranscriptionEngine

private val Ink = Color(0xFF111827)
private val Panel = Color(0xFF1F2937)
private val Accent = Color(0xFF7C9EFF)
private val Muted = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartVoiceTheme {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) { HomeScreen() }
                }
            }
        }
    }
}

@Composable
private fun SmartVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

private data class RecordingItem(val file: File, val durationSeconds: Long?)

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("جاهز للتسجيل") }
    var recordings by remember { mutableStateOf(emptyList<RecordingItem>()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var modelInstalled by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("نموذج العربية غير مثبت") }
    var modelProgress by remember { mutableStateOf<Int?>(null) }
    var transcriptText by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var isInstallingModel by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val modelInstaller = remember { ArabicModelInstaller(context.filesDir) }

    fun refreshRecordings() {
        val dir = File(context.filesDir, "recordings")
        recordings = (dir.listFiles()?.filter { it.isFile && it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList())
            .map { file ->
                val durationFile = File(file.parentFile, "${file.nameWithoutExtension}.duration")
                val duration = durationFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
                RecordingItem(file, duration)
            }
    }

    fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        playingName = null
    }

    fun stopRecording() {
        val current = recorder
        try {
            current?.stop()
            activeFile?.let { file ->
                File(file.parentFile, "${file.nameWithoutExtension}.duration").writeText(elapsedSeconds.toString())
            }
            status = "تم حفظ التسجيل"
        } catch (_: RuntimeException) {
            activeFile?.let { file ->
                file.delete()
                File(file.parentFile, "${file.nameWithoutExtension}.duration").delete()
            }
            status = "التسجيل قصير جداً ولم يتم حفظه"
        } finally {
            try { current?.reset() } catch (_: Exception) { }
            try { current?.release() } catch (_: Exception) { }
            recorder = null
            activeFile = null
            isRecording = false
            refreshRecordings()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val dir = File(context.filesDir, "recordings").apply { mkdirs() }
                val file = File(dir, "SV_${System.currentTimeMillis()}.m4a")
                val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                newRecorder.setAudioEncodingBitRate(128000)
                newRecorder.setAudioSamplingRate(44100)
                newRecorder.setOutputFile(file.absolutePath)
                newRecorder.prepare()
                newRecorder.start()
                recorder = newRecorder
                activeFile = file
                recordingStartedAt = System.currentTimeMillis()
                elapsedSeconds = 0
                isRecording = true
                status = "جارٍ التسجيل..."
            } catch (e: Exception) {
                try { recorder?.release() } catch (_: Exception) { }
                recorder = null
                activeFile?.delete()
                activeFile = null
                isRecording = false
                status = "تعذر بدء التسجيل: تحقق من الميكروفون"
            }
        } else {
            status = "لم تمنح إذن الميكروفون"
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        refreshRecordings()
        onDispose {
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            try { player?.release() } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("SmartVoice", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text("مساعدك الصوتي للتوثيق والتنظيم الذكي",
                style = MaterialTheme.typography.bodyLarge, color = Muted)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(Modifier.size(68.dp), shape = CircleShape,
                    color = Accent.copy(alpha = 0.16f)) {
                    Icon(Icons.Default.Mic, null, tint = Accent,
                        modifier = Modifier.padding(18.dp))
                }
                Text("حوّل كلامك إلى تنظيم واضح", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = Color.White,
                    textAlign = TextAlign.Center)
                Text("التسجيل يُحفظ محلياً على جهازك. لا يتم رفع الصوت إلى أي خادم.",
                    color = Muted, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        if (isRecording) stopRecording()
                        else if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFB45353) else Accent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                    Text(
                        if (isRecording) "إيقاف التسجيل • ${elapsedSeconds / 60}:%02d".format(elapsedSeconds % 60)
                        else "بدء تسجيل صوتي",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(status, color = if (status.startsWith("تعذر") || status.startsWith("لم تمنح"))
                    Color(0xFFFFA6A6) else Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("التسجيلات المحفوظة (${recordings.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Color.White)

        if (recordings.isEmpty()) {
            Text("لا توجد تسجيلات بعد. ابدأ تسجيلاً جديداً وسيظهر هنا.",
                color = Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recordings.forEach { item ->
                    RecordingRow(
                        item = item,
                        isPlaying = playingName == item.file.name,
                        onPlay = {
                            stopPlayback()
                            try {
                                val mp = MediaPlayer().apply {
                                    setDataSource(item.file.absolutePath)
                                    setOnCompletionListener {
                                        it.release()
                                        player = null
                                        playingName = null
                                    }
                                    prepare()
                                    start()
                                }
                                player = mp
                                playingName = item.file.name
                            } catch (_: Exception) {
                                stopPlayback()
                                status = "تعذر تشغيل هذا التسجيل"
                            }
                        },
                        onDelete = {
                            if (playingName == item.file.name) stopPlayback()
                            item.file.delete()
                            File(item.file.parentFile, "${item.file.nameWithoutExtension}.duration").delete()
                            refreshRecordings()
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("التفريغ الصوتي العربي", color = Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(modelStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (!modelInstalled) {
                    Text("يتطلب النموذج تنزيلًا اختياريًا بحجم يقارب 318 MB. بعد تثبيته تتم معالجة الصوت محليًا.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            modelStatus = "جارٍ تنزيل النموذج العربي..."
                            scope.launch(Dispatchers.IO) {
                                try {
                                    modelInstaller.install { progress ->
                                        modelProgress = progress
                                    }
                                    modelInstalled = true
                                    modelStatus = "اكتمل تثبيت النموذج العربي"
                                } catch (error: Exception) {
                                    modelStatus = "فشل التثبيت: ${error.message ?: "خطأ غير معروف"}"
                                } finally {
                                    modelProgress = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isInstallingModel,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(modelProgress?.let { "تنزيل النموذج • $it%" } ?: "تنزيل نموذج العربية")
                    }
                } else {
                    Button(
                        onClick = {
                            val latest = recordings.firstOrNull()
                            if (latest == null) {
                                modelStatus = "سجّل صوتًا أولاً"
                            } else if (!isTranscribing) {
                                isTranscribing = true
                                transcriptText = null
                                modelStatus = "جارٍ تفريغ أحدث تسجيل..."
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val engine = VoskTranscriptionEngine(
                                            modelInstaller.installedDirectory,
                                            File(context.cacheDir, "transcription")
                                        )
                                        val result = engine.transcribe(
                                            recordingId = latest.file.nameWithoutExtension,
                                            audioFile = latest.file,
                                            languageTag = "ar",
                                            onProgress = { percent ->
                                                modelProgress = percent
                                            }
                                        )
                                        transcriptText = result.fullText.ifBlank { "لم يتم التعرف على كلام واضح." }
                                        modelStatus = "اكتمل التفريغ محليًا"
                                    } catch (error: Exception) {
                                        modelStatus = "تعذر التفريغ: ${error.message ?: "خطأ غير معروف"}"
                                    } finally {
                                        isTranscribing = false
                                        modelProgress = null
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = recordings.isNotEmpty() && !isTranscribing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isTranscribing) "جارٍ التفريغ..." else "تفريغ أحدث تسجيل")
                    }
                    modelProgress?.let {
                        Text("تقدم المعالجة: $it%", color = Accent,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                transcriptText?.let { text ->
                    Text("النص المستخرج", color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("مساحات التنظيم", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Color.White)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            FeatureRow(Icons.Default.TaskAlt, "المهام", "استخراج الأعمال المطلوب تنفيذها")
            FeatureRow(Icons.Default.CalendarMonth, "المواعيد", "تمييز التواريخ الصريحة عن المقترحات")
            FeatureRow(Icons.Default.Lightbulb, "الأفكار والملاحظات", "حفظ الأفكار وربطها بمصدرها")
        }
        Spacer(Modifier.height(2.dp))
        Text("نسخة تأسيسية • 0.2.0", modifier = Modifier.fillMaxWidth(),
            color = Muted, style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(item.file.lastModified()) {
        SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.getDefault())
            .format(Date(item.file.lastModified()))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(date, color = Color.White, fontWeight = FontWeight.Medium)
                val durationLabel = item.durationSeconds?.let { "${it / 60}:%02d".format(it % 60) } ?: "المدة غير متاحة"
                Text("$durationLabel • %.1f MB".format(item.file.length() / (1024.0 * 1024.0)),
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = if (isPlaying) "يعمل" else "تشغيل",
                    tint = Accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف التسجيل",
                    tint = Color(0xFFFF9292))
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector,
                       title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp),
                color = Accent.copy(alpha = 0.12f)) {
                Icon(icon, null, tint = Accent, modifier = Modifier.padding(9.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
