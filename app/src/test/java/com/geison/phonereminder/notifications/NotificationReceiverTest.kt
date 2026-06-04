package com.geison.phonereminder.notifications

import com.geison.phonereminder.data.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationReceiverTest {
    @Test
    fun adjustScheduleFrequencyDecreasesByDailyStep() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerWeek = 6,
                notificationsPerDay = 2,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )

        assertEquals(4, adjusted.notificationsPerWeek)
        assertEquals(2, adjusted.notificationsPerDay)
    }

    @Test
    fun adjustScheduleFrequencyIncreasesByDailyStep() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerWeek = 4,
                notificationsPerDay = 2,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(6, adjusted.notificationsPerWeek)
        assertEquals(2, adjusted.notificationsPerDay)
    }

    @Test
    fun adjustScheduleFrequencyClampsToValidRange() {
        val lessOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerWeek = 1,
                notificationsPerDay = 1,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )
        val moreOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerWeek = 7,
                notificationsPerDay = 1,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(1, lessOften.notificationsPerWeek)
        assertEquals(7, moreOften.notificationsPerWeek)
    }
}
