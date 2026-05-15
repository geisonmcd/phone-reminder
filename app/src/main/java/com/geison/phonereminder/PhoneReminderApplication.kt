package com.geison.phonereminder

import android.app.Application
import com.geison.phonereminder.diagnostics.Diagnostics

class PhoneReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.initialize(this)
    }
}
