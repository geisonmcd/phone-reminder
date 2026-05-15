package com.geison.phonereminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.diagnostics.Diagnostics

class ScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.log("schedule_refresh_receiver_on_receive")
        NotificationScheduler.scheduleToday(context)
    }

    companion object {
        const val ACTION_REFRESH_SCHEDULE = "com.geison.phonereminder.action.REFRESH_SCHEDULE"
    }
}
