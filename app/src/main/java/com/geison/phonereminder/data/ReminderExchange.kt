package com.geison.phonereminder.data

import java.util.UUID

object ReminderExchange {
    private const val header = "Phone Reminder Export v1"
    private const val separator = "---"
    private const val reminderStart = "Reminder:"
    private const val reminderEnd = "End reminder"
    private const val notificationsLabel = "Notifications per week: "
    private const val startHourLabel = "Start hour: "
    private const val endHourLabel = "End hour: "

    fun export(state: AppState): String {
        val lines = mutableListOf(
            header,
            "",
            "This file can be imported back into Phone Reminder.",
            "Keep each block in the same format when editing by hand.",
        )

        state.reminders.forEach { reminder ->
            lines += ""
            lines += separator
            lines += reminderStart
            lines += reminder.text.trimEnd()
            lines += reminderEnd
            lines += notificationsLabel + reminder.schedule.notificationsPerWeek
            lines += startHourLabel + reminder.schedule.startHour
            lines += endHourLabel + reminder.schedule.endHour
        }

        return lines.joinToString("\n") + "\n"
    }

    fun import(rawText: String): List<ReminderItem> {
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val cursor = LineCursor(lines)

        cursor.skipBlanks()
        require(cursor.readLine() == header) {
            "This file is not a Phone Reminder export."
        }

        while (!cursor.isAtEnd && cursor.peekLine() != separator) {
            cursor.readLine()
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

            val notificationsPerWeek = cursor.readNumber(
                prefix = notificationsLabel,
                errorLabel = "Notifications per week",
            ).coerceIn(1, 7)
            val startHour = cursor.readNumber(
                prefix = startHourLabel,
                errorLabel = "Start hour",
            ).coerceIn(0, 22)
            val endHour = cursor.readNumber(
                prefix = endHourLabel,
                errorLabel = "End hour",
            ).coerceIn(startHour + 1, 23)

            reminders += ReminderItem(
                id = UUID.randomUUID().toString(),
                text = reminderText,
                schedule = ScheduleSettings(
                    notificationsPerWeek = notificationsPerWeek,
                    startHour = startHour,
                    endHour = endHour,
                ),
            )
        }

        return reminders
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
