package com.smartvoice.intelligence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class RecordingForegroundService : Service() {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        try {
            val directory = File(filesDir, "recordings").apply { mkdirs() }
            val file = File(directory, "SV_${System.currentTimeMillis()}.m4a")
            val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(128000)
            active.setAudioSamplingRate(44100)
            active.setOutputFile(file.absolutePath)
            active.prepare()
            active.start()
            recorder = active
            outputFile = file
            startedAt = System.currentTimeMillis()
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, true).putLong(KEY_STARTED, startedAt).apply()
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            outputFile?.delete()
            outputFile = null
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        val current = recorder
        try {
            current?.stop()
            outputFile?.let { file ->
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0)
                File(file.parentFile, "${file.nameWithoutExtension}.duration").writeText(seconds.toString())
            }
        } catch (_: RuntimeException) {
            outputFile?.delete()
            outputFile?.let { File(it.parentFile, "${it.nameWithoutExtension}.duration").delete() }
        } finally {
            try { current?.reset() } catch (_: Exception) { }
            try { current?.release() } catch (_: Exception) { }
            recorder = null
            outputFile = null
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, false).remove(KEY_STARTED).apply()
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VELORA — التسجيل مستمر")
            .setContentText("يعمل التسجيل في الخلفية. اضغط إيقاف لإنهائه.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف التسجيل", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تسجيل VELORA في الخلفية",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار دائم أثناء التسجيل الصوتي"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.aurevon.velora.action.START_RECORDING"
        const val ACTION_STOP = "com.aurevon.velora.action.STOP_RECORDING"
        const val PREFS = "velora_recording"
        const val KEY_ACTIVE = "recording_active"
        const val KEY_STARTED = "recording_started"
        private const val CHANNEL_ID = "velora_recording"
        private const val NOTIFICATION_ID = 4101
    }
}
