package com.geison.phonereminder.data

import java.util.UUID

object ReminderExchange {
    private const val header = "Smart Random Reminder Export v1"
    private const val separator = "---"
    private const val reminderStart = "Reminder:"
    private const val reminderEnd = "End reminder"
    private const val defaultStartHourLabel = "Default start hour: "
    private const val defaultEndHourLabel = "Default end hour: "
    private const val notificationsLabel = "Notifications per week: "
    private const val notificationsPerDayLabel = "Notifications per day: "

    fun export(state: AppState): String {
        val lines = mutableListOf(
            header,
            "",
            "This file can be imported back into Smart Random Reminder.",
            "Keep each block in the same format when editing by hand.",
            "",
            defaultStartHourLabel + state.notificationWindow.startHour,
            defaultEndHourLabel + state.notificationWindow.endHour,
        )

        state.reminders.forEach { reminder ->
            lines += ""
            lines += separator
            lines += reminderStart
            lines += reminder.text.trimEnd()
            lines += reminderEnd
            lines += notificationsLabel + reminder.schedule.notificationsPerWeek
            lines += notificationsPerDayLabel + reminder.schedule.notificationsPerDay
        }

        return lines.joinToString("\n") + "\n"
    }

    fun import(rawText: String): AppState {
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val cursor = LineCursor(lines)

        cursor.skipBlanks()
        require(cursor.readLine() == header) {
            "This file is not a Smart Random Reminder export."
        }

        var defaultStartHour: Int? = null
        var defaultEndHour: Int? = null
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

            val rawNotificationsPerWeek = cursor.readNumber(
                prefix = notificationsLabel,
                errorLabel = "Notifications per week",
            )
            val notificationsPerDay = if (cursor.peekLine()?.startsWith(notificationsPerDayLabel) == true) {
                cursor.readNumber(
                    prefix = notificationsPerDayLabel,
                    errorLabel = "Notifications per day",
                ).coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
            } else {
                1
            }
            val notificationsPerWeek = snapWeeklyCount(rawNotificationsPerWeek, notificationsPerDay)

            if (cursor.peekLine()?.startsWith("Start hour: ") == true) {
                defaultStartHour = defaultStartHour ?: cursor.readNumber(
                    prefix = "Start hour: ",
                    errorLabel = "Start hour",
                ).coerceIn(0, 22)
            }
            if (cursor.peekLine()?.startsWith("End hour: ") == true) {
                defaultEndHour = defaultEndHour ?: cursor.readNumber(
                    prefix = "End hour: ",
                    errorLabel = "End hour",
                ).coerceIn(1, 23)
            }

            reminders += ReminderItem(
                id = UUID.randomUUID().toString(),
                text = reminderText,
                schedule = ScheduleSettings(
                    notificationsPerWeek = notificationsPerWeek,
                    notificationsPerDay = notificationsPerDay,
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
        )
    }

    private fun snapWeeklyCount(
        value: Int,
        notificationsPerDay: Int,
    ): Int {
        val minValue = notificationsPerDay
        val maxValue = notificationsPerDay * 7
        val coerced = value.coerceIn(minValue, maxValue)
        val remainder = coerced % notificationsPerDay
        return if (remainder == 0) {
            coerced
        } else {
            (coerced + notificationsPerDay - remainder).coerceAtMost(maxValue)
        }
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
}
