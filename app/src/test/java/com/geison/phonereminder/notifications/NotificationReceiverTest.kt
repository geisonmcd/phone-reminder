package com.geison.phonereminder.notifications

import com.geison.phonereminder.data.ScheduleSettings
import com.geison.phonereminder.data.ScheduleCadence
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
    fun adjustScheduleFrequencyMovesDailyReminderIntoSpacedCadence() {
        val lessOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 1,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )

        assertEquals(1, lessOften.notificationsPerDay)
        assertEquals(ScheduleCadence.FIVE_TIMES_PER_WEEK, lessOften.cadence)
    }

    @Test
    fun adjustScheduleFrequencyMovesWeeklyReminderIntoMonthlyCadence() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 1,
                cadence = ScheduleCadence.WEEKLY,
            ),
            adjustment = FrequencyAdjustment.LESS_OFTEN,
        )

        assertEquals(ScheduleCadence.TWICE_MONTHLY, adjusted.cadence)
    }

    @Test
    fun adjustScheduleFrequencyMovesSpacedCadenceTowardDaily() {
        val adjusted = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 1,
                cadence = ScheduleCadence.THREE_TIMES_PER_WEEK,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(1, adjusted.notificationsPerDay)
        assertEquals(ScheduleCadence.FIVE_TIMES_PER_WEEK, adjusted.cadence)
    }

    @Test
    fun adjustScheduleFrequencyClampsDailyCountToValidRange() {
        val moreOften = adjustScheduleFrequency(
            schedule = ScheduleSettings(
                notificationsPerDay = 50,
            ),
            adjustment = FrequencyAdjustment.MORE_OFTEN,
        )

        assertEquals(50, moreOften.notificationsPerDay)
    }
}
