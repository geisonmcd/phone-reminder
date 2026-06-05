package com.geison.phonereminder.notifications

import com.geison.phonereminder.data.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationReceiverTest {
    @Test
    fun adjustScheduleFrequencyHalvesDailyCount() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 10,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )

        assertEquals(5, adjusted.notificationsPerDay)
    }

    @Test
    fun adjustScheduleFrequencyDoublesDailyCount() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 5,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(10, adjusted.notificationsPerDay)
    }

    @Test
    fun adjustScheduleFrequencyClampsToValidRange() {
        val lessOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 1,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )
        val moreOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 50,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(1, lessOften.notificationsPerDay)
        assertEquals(50, moreOften.notificationsPerDay)
    }
}
