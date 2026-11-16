package com.aurevon.velora.recordings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aurevon.velora.R

/**
 * Keeps an active microphone recording in a user-visible foreground service
 * while the app is backgrounded. The recording controller owns MediaRecorder.
 */
class RecordingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                createChannel()
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("VELORA يسجل الآن")
                    .setContentText("التسجيل مستمر حتى عند الانتقال إلى تطبيق آخر")
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "حالة التسجيل",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إشعار دائم عند استمرار التسجيل الصوتي في الخلفية"
                }
            )
        }
    }

    companion object {
        const val ACTION_START = "com.aurevon.velora.recordings.START_RECORDING"
        const val ACTION_STOP = "com.aurevon.velora.recordings.STOP_RECORDING"
        private const val CHANNEL_ID = "velora_recording_service"
        private const val NOTIFICATION_ID = 4102
    }
}
