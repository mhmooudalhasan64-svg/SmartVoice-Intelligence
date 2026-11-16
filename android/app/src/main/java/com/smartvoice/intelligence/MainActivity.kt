package com.aurevon.velora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import java.io.FileOutputStream
import java.time.LocalDate
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aurevon.velora.config.RemoteConfigRepository
import com.aurevon.velora.recordings.RecordingController
import com.aurevon.velora.recordings.RecordingItem
import com.aurevon.velora.recordings.StopRecordingResult
import com.aurevon.velora.organization.StudyEventDraft
import com.aurevon.velora.organization.StudyEventRepository
import com.aurevon.velora.organization.StudyEventType
import com.aurevon.velora.organization.StudyReminderScheduler
import com.aurevon.velora.organization.StudyReminderWorker
import com.aurevon.velora.organization.VoiceMessageDraftParser
import com.aurevon.velora.transcription.ArabicModelController
import com.aurevon.velora.transcription.ArabicModelInstallState
import com.aurevon.velora.transcription.TranscriptionController
import com.aurevon.velora.ui.components.FeatureRow
import com.aurevon.velora.ui.components.RecordingRow
import com.aurevon.velora.ui.theme.Accent
import com.aurevon.velora.ui.theme.Ink
import com.aurevon.velora.ui.theme.Muted
import com.aurevon.velora.ui.theme.Panel
import com.aurevon.velora.ui.theme.VeloraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeloraTheme {
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
private fun HomeScreen() {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("جاهز للتسجيل") }
    var recordings by remember { mutableStateOf(emptyList<RecordingItem>()) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var modelProgress by remember { mutableStateOf<Int?>(null) }
    var transcriptText by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var pendingStudyDraft by remember { mutableStateOf<StudyEventDraft?>(null) }
    var confirmedStudyEvents by remember { mutableStateOf(emptyList<StudyEventDraft>()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val recordingController = remember { RecordingController(context) }
    val modelController = remember { ArabicModelController(context) }
    val remoteConfigRepository = remember { RemoteConfigRepository() }
    val remoteSettings by remoteConfigRepository.settings.collectAsState()
    val studyEventRepository = remember { StudyEventRepository(java.io.File(context.filesDir, "study/confirmed-events.db")) }
    val voiceMessageParser = remember { VoiceMessageDraftParser() }
    val modelState by modelController.state.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        remoteConfigRepository.refresh()
        StudyReminderWorker.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refreshRecordings() {
        recordings = recordingController.listRecordings()
    }


    fun refreshStudyEvents() {
        confirmedStudyEvents = studyEventRepository.list()
    }

    fun analyzeRecording(item: RecordingItem) {
        if (!remoteSettings.voiceMessageAnalysisEnabled) {
            modelStatus = "ميزة تحليل الرسائل الصوتية متوقفة مؤقتاً"
            return
        }
        if (modelState !is ArabicModelInstallState.Ready) {
            modelStatus = "ثبّت نموذج العربية أولاً لتحليل التسجيل محلياً"
            return
        }
        if (isTranscribing) return
        isTranscribing = true
        pendingStudyDraft = null
        transcriptText = null
        modelStatus = "يراجع ORIN التسجيل الصوتي..."
        scope.launch {
            try {
                val transcriptionController = TranscriptionController(
                    modelDirectory = modelController.installedDirectory,
                    cacheDirectory = context.cacheDir
                )
                val result = transcriptionController.transcribe(
                    recordingId = item.file.nameWithoutExtension,
                    audioFile = item.file,
                    languageTag = "ar"
                )
                val transcript = result.fullText.trim()
                transcriptText = transcript.ifBlank { "لم يتم التعرف على كلام واضح." }
                val draft = voiceMessageParser.parse(transcript)
                if (draft == null) {
                    modelStatus = "تم التفريغ، لكن لم أستطع تحديد مادة بوضوح. راجع النص المستخرج."
                } else {
                    pendingStudyDraft = draft
                    modelStatus = "استخرج ORIN مسودة تحتاج إلى تأكيدك"
                }
            } catch (error: Exception) {
                modelStatus = "تعذر تحليل التسجيل: " + (error.message ?: "خطأ غير معروف")
            } finally {
                isTranscribing = false
            }
        }
    }


    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val directory = java.io.File(context.filesDir, "recordings")
                directory.mkdirs()
                val imported = java.io.File(directory, "SV_IMPORTED_" + System.currentTimeMillis() + ".m4a")
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح الملف المحدد")
                input.use { source ->
                    FileOutputStream(imported).use { destination -> source.copyTo(destination) }
                }
                refreshRecordings()
                modelStatus = "تم استيراد التسجيل. اضغط رمز ORIN بجانبه لتحليله."
            } catch (error: Exception) {
                modelStatus = "تعذر استيراد التسجيل: " + (error.message ?: "خطأ غير معروف")
            }
        }
    }

    fun stopPlayback() {
        recordingController.stopPlayback()
        playingName = null
    }

    fun stopRecording() {
        status = when (recordingController.stopRecording(elapsedSeconds)) {
            StopRecordingResult.Saved -> "تم حفظ التسجيل"
            StopRecordingResult.TooShort -> "التسجيل قصير جداً ولم يتم حفظه"
        }
        isRecording = false
        refreshRecordings()
    }

    fun startRecording() {
        if (recordingController.startRecording()) {
            recordingStartedAt = System.currentTimeMillis()
            elapsedSeconds = 0
            isRecording = true
            status = "جارٍ التسجيل..."
        } else {
            isRecording = false
            status = "تعذر بدء التسجيل: تحقق من الميكروفون"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else status = "لم تمنح إذن الميكروفون"
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        refreshRecordings()
        refreshStudyEvents()
        studyEventRepository.list()
            .filter { event -> event.date?.isBefore(LocalDate.now()) == false }
            .forEach { event -> StudyReminderScheduler.scheduleConfirmedEvent(context, event) }
        modelController.refresh()
        onDispose {
            recordingController.release()
            modelController.close()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("VELORA", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text("مساعدك الصوتي للتوثيق والتنظيم الذكي",
                style = MaterialTheme.typography.bodyLarge, color = Muted)
        }

        AccountAuthCard()

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
                            startRecording()
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
                        onAnalyze = { analyzeRecording(item) },
                        onPlay = {
                            val started = recordingController.play(item.file) {
                                playingName = null
                            }
                            if (started) {
                                playingName = item.file.name
                            } else {
                                playingName = null
                                status = "تعذر تشغيل هذا التسجيل"
                            }
                        },
                        onDelete = {
                            if (playingName == item.file.name) stopPlayback()
                            if (recordingController.deleteRecording(item.file)) {
                                refreshRecordings()
                            } else {
                                status = "تعذر حذف التسجيل"
                            }
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
                val installState = modelState
                val defaultModelStatus = when (installState) {
                    ArabicModelInstallState.NotInstalled -> "نموذج العربية غير مثبت"
                    ArabicModelInstallState.Ready -> "نموذج العربية جاهز على الجهاز"
                    is ArabicModelInstallState.Downloading -> "جارٍ تنزيل النموذج العربي..."
                    is ArabicModelInstallState.Failed -> "فشل التثبيت: ${installState.message}"
                }
                Text(modelStatus ?: defaultModelStatus, color = Muted,
                    style = MaterialTheme.typography.bodySmall)
                if (installState !is ArabicModelInstallState.Ready) {
                    if (installState is ArabicModelInstallState.NotInstalled ||
                        installState is ArabicModelInstallState.Failed
                    ) {
                        Text("يتطلب النموذج تنزيلًا اختياريًا بحجم يقارب 318 MB. بعد تثبيته تتم معالجة الصوت محليًا.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            modelStatus = null
                            scope.launch { modelController.install() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = installState !is ArabicModelInstallState.Downloading && remoteSettings.backgroundModelDownloadsEnabled,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val progress = (installState as? ArabicModelInstallState.Downloading)?.progressPercent
                        Text(progress?.let { "تنزيل النموذج • $it%" } ?:
                            if (installState is ArabicModelInstallState.Downloading) "جارٍ التنزيل..." else if (!remoteSettings.backgroundModelDownloadsEnabled) "التنزيل متوقف مؤقتاً" else "تنزيل نموذج العربية")
                    }
                } else {
                    Button(
                        onClick = {
                            val latest = recordings.firstOrNull()
                            if (latest == null) modelStatus = "سجّل صوتًا أولاً"
                            else analyzeRecording(latest)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = recordings.isNotEmpty() && !isTranscribing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isTranscribing) "جارٍ التحليل..." else "إرسال أحدث تسجيل إلى ORIN")
                    }
                    modelProgress?.let {
                        Text("تقدم المعالجة: $it%", color = Accent,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }

                Button(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34445A))
                ) {
                    Text("اختيار تسجيل صوتي من الجهاز")
                }
                transcriptText?.let { text ->
                    Text("النص المستخرج", color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                pendingStudyDraft?.let { draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF263448)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("راجع ما فهمه ORIN", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("المادة: " + draft.subject, color = Color.White)
                            val typeLabel = when (draft.eventType) {
                                StudyEventType.LESSON -> "حصة دراسية"
                                StudyEventType.HOMEWORK -> "واجب"
                                StudyEventType.EXAM -> "امتحان"
                            }
                            Text("النوع: " + typeLabel, color = Color.White)
                            Text("التاريخ: " + (draft.date?.toString() ?: "غير محدد — لن أضع تاريخاً من عندي"), color = Color.White)
                            Text("المصدر: " + draft.sourceText, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (studyEventRepository.saveConfirmed(draft)) {
                                            pendingStudyDraft = null
                                            refreshStudyEvents()
                                            val reminderScheduled = StudyReminderScheduler.scheduleConfirmedEvent(
                                                context, draft.copy(requiresConfirmation = false)
                                            )
                                            modelStatus = if (reminderScheduled) {
                                                "تمت إضافة الحدث وجدولة إشعار الساعة 8 صباحاً في يومه"
                                            } else {
                                                "تمت إضافة الحدث، لكن لم يُجدول إشعار لأن التاريخ غير محدد أو مضى"
                                            }
                                        } else {
                                            modelStatus = "تعذر حفظ الحدث على الجهاز"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("تأكيد وإضافة") }
                                Button(
                                    onClick = {
                                        pendingStudyDraft = null
                                        modelStatus = "تم تجاهل المسودة ولم يُحفظ شيء"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555D6B))
                                ) { Text("تجاهل") }
                            }
                        }
                    }
                }
                if (confirmedStudyEvents.isNotEmpty()) {
                    Text("الأحداث المؤكدة", color = Accent, fontWeight = FontWeight.Bold)
                    confirmedStudyEvents.take(5).forEach { event ->
                        val eventLabel = when (event.eventType) {
                            StudyEventType.LESSON -> "حصة"
                            StudyEventType.HOMEWORK -> "واجب"
                            StudyEventType.EXAM -> "امتحان"
                        }
                        Text(
                            (event.date?.toString() ?: "بلا تاريخ") + " • " + event.subject + " • " + eventLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
