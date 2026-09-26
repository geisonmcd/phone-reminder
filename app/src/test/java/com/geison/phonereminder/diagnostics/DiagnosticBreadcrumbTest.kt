package com.geison.phonereminder.diagnostics

import com.geison.phonereminder.diagnostics.DiagnosticBreadcrumb.sanitizeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            reminderChannelImportance = "low",
            ignoringBatteryOptimizations = false,
            powerSaveMode = true,
            deviceIdleMode = false,
        ).attributes()

        assertEquals(
            linkedMapOf(
                "notifications_enabled" to "false",
                "post_notifications_granted" to "true",
                "reminder_channel_blocked" to "true",
                "reminder_channel_importance" to "low",
                "ignoring_battery_optimizations" to "false",
                "power_save_mode" to "true",
                "device_idle_mode" to "false",
            ),
            attributes,
        )
    }

    @Test
    fun sanitizeValueRedactsEmailsQuotesAndUris() {
        val value = (
            "failed for ada@example.com file content://media/notes/private-reminder " +
                "\"call the doctor tomorrow morning\""
            ).sanitizeValue()

        assertFalse(value.contains("ada@example.com"))
        assertFalse(value.contains("call the doctor"))
        assertFalse(value.contains("content://"))
        assertTrue(value.contains("[redacted]"))
        assertTrue(value.contains("[redacted-uri]"))
    }

    @Test
    fun redactedFailureDropsMessagesAndKeepsTheClassChain() {
        val root = IllegalArgumentException("secret reminder text ada@example.com")
        val wrapped = IllegalStateException(
            "wrapper says \"call the doctor tomorrow\"",
            root,
        )

        val redacted = DiagnosticBreadcrumb.redactedFailure("import_failed", wrapped)
        val rendered = listOf(redacted.toString(), redacted.message, redacted.cause?.message)
            .joinToString(separator = " ")

        assertEquals(
            "import_failed:IllegalStateException>IllegalArgumentException",
            redacted.message,
        )
        assertEquals("IllegalStateException>IllegalArgumentException", DiagnosticBreadcrumb.exceptionChain(wrapped))
        assertNull(redacted.cause)
        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("ada@example.com"))
        assertFalse(rendered.contains("doctor"))
        assertEquals(wrapped.stackTrace.toList(), redacted.stackTrace.toList())
    }
}
