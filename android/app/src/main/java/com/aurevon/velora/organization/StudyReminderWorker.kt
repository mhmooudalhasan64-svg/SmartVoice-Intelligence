package com.aurevon.velora.organization

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurevon.velora.MainActivity
import com.aurevon.velora.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Persistent, OS-scheduled reminder for a user-confirmed study event.
 * WorkManager persists scheduled work across app restarts and device reboot.
 */
class StudyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val subject = inputData.getString(KEY_SUBJECT) ?: return Result.failure()
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val date = inputData.getString(KEY_DATE) ?: return Result.failure()

        createNotificationChannel(applicationContext)
        val typeLabel = when (type) {
            StudyEventType.EXAM.name -> "امتحان"
            StudyEventType.HOMEWORK.name -> "واجب"
            else -> "حصة دراسية"
        }
        val title = "تذكير من VELORA"
        val body = "لديك $typeLabel في $subject اليوم"

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            date.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notifySafely(applicationContext, date.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "velora_study_reminders"
        private const val CHANNEL_NAME = "تذكيرات الدراسة"
        private const val KEY_SUBJECT = "subject"
        private const val KEY_TYPE = "event_type"
        private const val KEY_DATE = "event_date"

        fun schedule(context: Context, event: StudyEventDraft): Boolean {
            val eventDate = event.date ?: return false
            val target = ZonedDateTime.of(
                eventDate,
                LocalTime.of(8, 0),
                ZoneId.systemDefault()
            )
            val delayMillis = (target.toInstant().toEpochMilli() - System.currentTimeMillis())
                .coerceAtLeast(0L)
            val uniqueId = UUID.nameUUIDFromBytes(
                "${event.subject}|${event.eventType}|${eventDate}|${event.sourceText}"
                    .toByteArray(Charsets.UTF_8)
            ).toString()
            val input = Data.Builder()
                .putString(KEY_SUBJECT, event.subject)
                .putString(KEY_TYPE, event.eventType.name)
                .putString(KEY_DATE, eventDate.toString())
                .build()
            val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
                .setInputData(input)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag("velora-study-reminder")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "study-reminder-$uniqueId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            return true
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات الحصص والواجبات والامتحانات المؤكدة"
                }
                manager.createNotificationChannel(channel)
            }
        }

        @SuppressLint("MissingPermission")
        private fun notifySafely(
            context: Context,
            notificationId: Int,
            notification: android.app.Notification
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
