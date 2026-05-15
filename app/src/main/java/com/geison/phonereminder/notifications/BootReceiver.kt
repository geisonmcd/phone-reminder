package com.geison.phonereminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.diagnostics.Diagnostics

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.log("boot_receiver_on_receive")
        NotificationScheduler.scheduleToday(context)
    }
}
