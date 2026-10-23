package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NotificationWindowSettingsTest {
    @Test
    fun validWindowIsUnchanged() {
        val window = NotificationWindowSettings(startHour = 9, endHour = 20)
        assertSame(window, window.normalized())
    }

    @Test
    fun invertedOrOutOfRangeWindowsAreRepaired() {
        assertEquals(NotificationWindowSettings(22, 23), NotificationWindowSettings(23, 23).normalized())
        assertEquals(NotificationWindowSettings(0, 1), NotificationWindowSettings(0, 0).normalized())
        assertEquals(NotificationWindowSettings(20, 21), NotificationWindowSettings(20, 10).normalized())
        assertEquals(NotificationWindowSettings(0, 23), NotificationWindowSettings(-3, 30).normalized())
    }

    @Test
    fun steppersNeverThrowEvenForCorruptWindows() {
        // Before the fix these windows made the settings steppers call coerceIn with an empty range.
        val corrupt = listOf(
            NotificationWindowSettings(23, 23),
            NotificationWindowSettings(0, 0),
            NotificationWindowSettings(23, 0),
            NotificationWindowSettings(-1, 24),
        )
        corrupt.forEach { window ->
            listOf(-1, 1).forEach { delta ->
                val start = window.withStartHourStep(delta)
                val end = window.withEndHourStep(delta)
                assert(start.startHour < start.endHour) { "$window start $delta -> $start" }
                assert(end.startHour < end.endHour) { "$window end $delta -> $end" }
            }
        }
    }

    @Test
    fun steppersRespectBounds() {
        val window = NotificationWindowSettings(startHour = 9, endHour = 10)
        assertEquals(9, window.withStartHourStep(1).startHour)
        assertEquals(8, window.withStartHourStep(-1).startHour)
        assertEquals(10, window.withEndHourStep(-1).endHour)
        assertEquals(11, window.withEndHourStep(1).endHour)
        assertEquals(23, NotificationWindowSettings(9, 23).withEndHourStep(1).endHour)
        assertEquals(0, NotificationWindowSettings(0, 5).withStartHourStep(-1).startHour)
    }

    @Test
    fun appStateNormalizesWindow() {
        val state = AppState(notificationWindow = NotificationWindowSettings(23, 23))
        assertEquals(NotificationWindowSettings(22, 23), state.normalized().notificationWindow)
    }
}
