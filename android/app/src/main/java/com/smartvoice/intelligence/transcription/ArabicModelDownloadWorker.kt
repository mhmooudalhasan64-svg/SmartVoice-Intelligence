package com.aurevon.velora.transcription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.IOException

/**
 * Runs the large Arabic model download as a user-visible foreground WorkManager
 * task. WorkManager persists the request across Activity destruction and process
 * recreation; the installer resumes the partial archive after network failures.
 */
class ArabicModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val installer = ArabicModelInstaller(applicationContext.filesDir)
        if (installer.isInstalled()) return Result.success()

        setForeground(createForegroundInfo())
        return try {
            installer.install { percent ->
                if (percent != null) {
                    setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, percent).build())
                }
            }
            showFinishedNotification()
            Result.success()
        } catch (error: IOException) {
            if (runAttemptCount < MAX_NETWORK_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR, error.message ?: "تعذر تنزيل النموذج")
                        .build()
                )
            }
        } catch (error: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, error.message ?: "تعذر تثبيت النموذج")
                    .build()
            )
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.aurevon.velora.R.mipmap.ic_launcher)
            .setContentTitle("VELORA • تنزيل نموذج العربية")
            .setContentText("سيستمر التنزيل في الخلفية حتى بعد الخروج من التطبيق")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تنزيلات VELORA",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إظهار تقدم تنزيل نموذج العربية في الخلفية"
                }
            )
        }
    }

    private fun showFinishedNotification() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.aurevon.velora.R.mipmap.ic_launcher)
            .setContentTitle("اكتمل تنزيل نموذج العربية")
            .setContentText("أصبح نموذج التفريغ الصوتي جاهزاً للاستخدام في VELORA")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "velora-arabic-model-download"
        const val KEY_PROGRESS = "download_progress"
        const val KEY_ERROR = "download_error"
        const val CHANNEL_ID = "velora_model_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val MAX_NETWORK_ATTEMPTS = 8
    }
}
