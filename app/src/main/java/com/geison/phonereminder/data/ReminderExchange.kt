package com.geison.phonereminder.data

import java.time.DayOfWeek
import java.util.UUID

object ReminderExchange {
    // Export v1 is a compatibility contract. Keep imports tolerant of this shape
    // and add only optional fields so old backups continue to restore.
    private const val header = "Smart Random Reminder Export v1"
    private const val legacyHeader = "Phone Reminder Export v1"
    private const val separator = "---"
    private const val reminderStart = "Reminder:"
    private const val reminderEnd = "End reminder"
    private const val defaultStartHourLabel = "Default start hour: "
    private const val defaultEndHourLabel = "Default end hour: "
    private const val reminderDaysLabel = "Reminder days: "
    private const val notificationsPerDayLabel = "Notifications per day: "
    private const val cadenceLabel = "Cadence: "
    private const val obsoleteNotificationsPrefix = "Notifications per "
    private const val legacyReminderStartHourLabel = "Start hour: "
    private const val legacyReminderEndHourLabel = "End hour: "

    fun export(state: AppState): String {
        val lines = mutableListOf(
            header,
            "",
            "This file can be imported back into Smart Random Reminder.",
            "Keep each block in the same format when editing by hand.",
            "",
            defaultStartHourLabel + state.notificationWindow.startHour,
            defaultEndHourLabel + state.notificationWindow.endHour,
            reminderDaysLabel + state.reminderDays
                .sortedBy { it.value }
                .joinToString(",") { it.name },
        )

        state.reminders.forEach { reminder ->
            lines += ""
            lines += separator
            lines += reminderStart
            lines += reminder.text.trimEnd()
            lines += reminderEnd
            lines += notificationsPerDayLabel + reminder.schedule.notificationsPerDay
            if (reminder.schedule.cadence != ScheduleCadence.DAILY) {
                lines += cadenceLabel + reminder.schedule.cadence.name
            }
            reminder.schedule.reminderDays?.let { days ->
                lines += reminderDaysLabel + days
                    .sortedBy { it.value }
                    .joinToString(",") { it.name }
            }
        }

        return lines.joinToString("\n") + "\n"
    }

    fun import(rawText: String): AppState {
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val cursor = LineCursor(lines)

        cursor.skipBlanks()
        require(cursor.readLine() in setOf(header, legacyHeader)) {
            "This file is not a Smart Random Reminder export."
        }

        var defaultStartHour: Int? = null
        var defaultEndHour: Int? = null
        var reminderDays = DEFAULT_REMINDER_DAYS
        while (!cursor.isAtEnd && cursor.peekLine() != separator) {
            when {
                cursor.peekLine()?.startsWith(defaultStartHourLabel) == true -> {
                    defaultStartHour = cursor.readNumber(
                        prefix = defaultStartHourLabel,
                        errorLabel = "Default start hour",
                    ).coerceIn(0, 22)
                }

                cursor.peekLine()?.startsWith(defaultEndHourLabel) == true -> {
                    defaultEndHour = cursor.readNumber(
                        prefix = defaultEndHourLabel,
                        errorLabel = "Default end hour",
                    ).coerceIn(1, 23)
                }

                cursor.peekLine()?.startsWith(reminderDaysLabel) == true -> {
                    reminderDays = parseReminderDays(cursor.readLine().removePrefix(reminderDaysLabel))
                }

                else -> cursor.readLine()
            }
        }

        val reminders = mutableListOf<ReminderItem>()
        while (true) {
            cursor.skipBlanks()
            if (cursor.isAtEnd) {
                break
            }

            require(cursor.readLine() == separator) {
                "Expected reminder separator '---'."
            }
            require(cursor.readLine() == reminderStart) {
                "Expected 'Reminder:' after separator."
            }

            val textLines = mutableListOf<String>()
            while (true) {
                val line = cursor.readLineOrNull()
                    ?: throw IllegalArgumentException("Reminder text is missing 'End reminder'.")
                if (line == reminderEnd) {
                    break
                }
                textLines += line
            }

            val reminderText = textLines.joinToString("\n").trim()
            require(reminderText.isNotEmpty()) {
                "Reminder text cannot be blank."
            }

            if (
                cursor.peekLine()?.startsWith(obsoleteNotificationsPrefix) == true &&
                cursor.peekLine()?.startsWith(notificationsPerDayLabel) != true
            ) {
                cursor.readLine()
            }
            val notificationsPerDay = if (cursor.peekLine()?.startsWith(notificationsPerDayLabel) == true) {
                cursor.readNumber(
                    prefix = notificationsPerDayLabel,
                    errorLabel = "Notifications per day",
                ).coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
            } else {
                1
            }

            val importedMetadata = readOptionalReminderMetadata(
                cursor = cursor,
                defaultStartHour = defaultStartHour,
                defaultEndHour = defaultEndHour,
            )
            defaultStartHour = importedMetadata.startHour
            defaultEndHour = importedMetadata.endHour

            reminders += ReminderItem(
                id = UUID.randomUUID().toString(),
                text = reminderText,
                schedule = ScheduleSettings(
                    notificationsPerDay = notificationsPerDay,
                    reminderDays = importedMetadata.reminderDays,
                    cadence = importedMetadata.cadence,
                ),
            )
        }

        val startHour = (defaultStartHour ?: 9).coerceIn(0, 22)
        val endHour = (defaultEndHour ?: 20).coerceIn(startHour + 1, 23)

        return AppState(
            reminders = reminders,
            notificationWindow = NotificationWindowSettings(
                startHour = startHour,
                endHour = endHour,
            ),
            reminderDays = reminderDays,
        )
    }

    private fun parseReminderDays(rawDays: String): Set<DayOfWeek> {
        if (rawDays.isBlank()) {
            return emptySet()
        }

        return rawDays.split(",")
            .map { rawDay ->
                DayOfWeek.valueOf(rawDay.trim().uppercase())
            }
            .toSet()
    }

    private class LineCursor(private val lines: List<String>) {
        private var index = 0

        val isAtEnd: Boolean
            get() = index >= lines.size

        fun peekLine(): String? = lines.getOrNull(index)

        fun skipBlanks() {
            while (peekLine()?.isBlank() == true) {
                index += 1
            }
        }

        fun readLine(): String {
            return readLineOrNull() ?: throw IllegalArgumentException("Unexpected end of file.")
        }

        fun readLineOrNull(): String? {
            return lines.getOrNull(index++)?.trimEnd()
        }

        fun readNumber(prefix: String, errorLabel: String): Int {
            val line = readLine()
            require(line.startsWith(prefix)) {
                "Expected '$errorLabel' line."
            }
            return line.removePrefix(prefix).trim().toIntOrNull()
                ?: throw IllegalArgumentException("$errorLabel must be a number.")
        }
    }

    private fun readOptionalReminderMetadata(
        cursor: LineCursor,
        defaultStartHour: Int?,
        defaultEndHour: Int?,
    ): ImportedReminderMetadata {
        var startHour = defaultStartHour
        var endHour = defaultEndHour
        var reminderDays: Set<DayOfWeek>? = null
        var cadence = ScheduleCadence.DAILY

        while (true) {
            val line = cursor.peekLine()?.trimEnd() ?: break
            if (line.isBlank() || line == separator) {
                break
            }

            when {
                line.startsWith(legacyReminderStartHourLabel) -> {
                    val importedStartHour = cursor.readNumber(
                        prefix = legacyReminderStartHourLabel,
                        errorLabel = "Start hour",
                    ).coerceIn(0, 22)
                    startHour = startHour ?: importedStartHour
                }

                line.startsWith(legacyReminderEndHourLabel) -> {
                    val importedEndHour = cursor.readNumber(
                        prefix = legacyReminderEndHourLabel,
                        errorLabel = "End hour",
                    ).coerceIn(1, 23)
                    endHour = endHour ?: importedEndHour
                }

                line.startsWith(reminderDaysLabel) -> {
                    reminderDays = parseReminderDays(cursor.readLine().removePrefix(reminderDaysLabel))
                }

                line.startsWith(cadenceLabel) -> {
                    val rawCadence = cursor.readLine().removePrefix(cadenceLabel).trim()
                    cadence = ScheduleCadence.entries.firstOrNull { it.name == rawCadence }
                        ?: ScheduleCadence.DAILY
                }

                else -> cursor.readLine()
            }
        }

        return ImportedReminderMetadata(
            startHour = startHour,
            endHour = endHour,
            reminderDays = reminderDays,
            cadence = cadence,
        )
    }

    private data class ImportedReminderMetadata(
        val startHour: Int?,
        val endHour: Int?,
        val reminderDays: Set<DayOfWeek>?,
        val cadence: ScheduleCadence,
    )
}
