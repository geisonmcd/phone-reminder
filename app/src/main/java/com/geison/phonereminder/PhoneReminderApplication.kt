package com.geison.phonereminder

import android.app.Application
import com.geison.phonereminder.billing.DonationBillingController
import com.geison.phonereminder.billing.PlayDonationBilling
import com.geison.phonereminder.diagnostics.Diagnostics

class PhoneReminderApplication : Application() {
    val donationBilling: DonationBillingController by lazy {
        PlayDonationBilling(this)
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.initialize(this)
        donationBilling.start { }
    }
}
