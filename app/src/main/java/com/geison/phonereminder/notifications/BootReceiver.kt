package com.geison.phonereminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.diagnostics.Diagnostics

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.event(
            "boot_receiver",
            mapOf("action" to (intent.action ?: "unknown").substringAfterLast('.')),
        )
        runCatching {
            NotificationScheduler.scheduleToday(context)
        }.onFailure { error ->
            Diagnostics.recordNonFatal(
                area = "boot_reschedule_failed",
                throwable = error,
            )
        }
    }
}
