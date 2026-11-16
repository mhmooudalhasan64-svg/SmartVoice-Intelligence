package com.aurevon.velora.organization

import android.content.Context

/**
 * Schedules reminders only for events explicitly confirmed by the user.
 */
object StudyReminderScheduler {
    fun scheduleConfirmedEvent(context: Context, event: StudyEventDraft): Boolean {
        if (event.requiresConfirmation || event.date == null) return false
        return StudyReminderWorker.schedule(context.applicationContext, event)
    }
}
