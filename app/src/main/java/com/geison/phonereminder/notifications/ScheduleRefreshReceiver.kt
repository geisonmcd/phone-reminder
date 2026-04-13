package com.geison.phonereminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.scheduleToday(context)
    }

    companion object {
        const val ACTION_REFRESH_SCHEDULE = "com.geison.phonereminder.action.REFRESH_SCHEDULE"
    }
}
