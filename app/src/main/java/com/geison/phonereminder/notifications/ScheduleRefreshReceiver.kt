package com.geison.phonereminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.diagnostics.Diagnostics

class ScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.event(
            "schedule_refresh_receiver",
            mapOf("action" to (intent.action ?: "unknown").substringAfterLast('.')),
        )
        runCatching {
            NotificationScheduler.scheduleToday(context)
        }.onFailure { error ->
            Diagnostics.recordNonFatal(
                area = "schedule_refresh_receiver_failed",
                throwable = error,
            )
        }
    }

    companion object {
        const val ACTION_REFRESH_SCHEDULE = "com.geison.phonereminder.action.REFRESH_SCHEDULE"
    }
}
