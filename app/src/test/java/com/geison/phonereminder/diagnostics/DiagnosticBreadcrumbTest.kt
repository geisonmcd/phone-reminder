package com.geison.phonereminder.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBreadcrumbTest {
    @Test
    fun formatKeepsABareEventName() {
        assertEquals("schedule_today", DiagnosticBreadcrumb.format("schedule_today"))
    }

    @Test
    fun formatRendersAttributesInInsertionOrder() {
        val breadcrumb = DiagnosticBreadcrumb.format(
            name = "schedule_today",
            attributes = linkedMapOf(
                "planned" to "4",
                "scheduled" to "3",
                "failed" to "1",
            ),
        )

        assertEquals("schedule_today planned=4 scheduled=3 failed=1", breadcrumb)
    }

    @Test
    fun formatStripsNewlinesAndTruncatesValues() {
        val breadcrumb = DiagnosticBreadcrumb.format(
            name = "non_fatal",
            attributes = mapOf(
                "area" to "schedule_alarm_failed",
                "detail" to "line one\nline two\r" + "x".repeat(200),
            ),
        )

        val detail = breadcrumb.substringAfter("detail=")
        assertTrue(breadcrumb.startsWith("non_fatal area=schedule_alarm_failed detail="))
        assertFalse(detail.contains('\n'))
        assertFalse(detail.contains('\r'))
        assertEquals(DiagnosticBreadcrumb.MAX_VALUE_LENGTH, detail.length)
    }

    @Test
    fun formatSanitizesEventNamesAndKeys() {
        assertEquals(
            "show_reminder result_code=skipped",
            DiagnosticBreadcrumb.format(
                name = "Show Reminder",
                attributes = mapOf("Result Code" to "skipped"),
            ),
        )
    }

    @Test
    fun deliveryReadinessAttributesUseStableKeys() {
        val attributes = DeliveryReadiness(
            notificationsEnabled = false,
            postNotificationsGranted = true,
            reminderChannelBlocked = true,
            ignoringBatteryOptimizations = false,
        ).attributes()

        assertEquals(
            linkedMapOf(
                "notifications_enabled" to "false",
                "post_notifications_granted" to "true",
                "reminder_channel_blocked" to "true",
                "ignoring_battery_optimizations" to "false",
            ),
            attributes,
        )
    }
}
