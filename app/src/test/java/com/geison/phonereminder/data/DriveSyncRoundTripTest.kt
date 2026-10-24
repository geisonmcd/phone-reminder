package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * Sync must be idempotent through the real Drive file format: after "Sync now" the compare has to
 * show zero differences. Reproduces the 1.0.38 bug where reminders whose inner lines ended with a
 * space always showed as "edited on both".
 */
class DriveSyncRoundTripTest {
    private val now = 1_800_000_000_000L

    private fun reminder(
        id: String,
        text: String,
        cadence: ScheduleCadence = ScheduleCadence.DAILY,
        perDay: Int = 1,
        days: Set<DayOfWeek>? = null,
        updated: Long = 1_778_413_258_669L,
    ) = ReminderItem(
        id = id,
        text = text,
        createdAtEpochMillis = updated,
        schedule = ScheduleSettings(notificationsPerDay = perDay, reminderDays = days, cadence = cadence),
        updatedAtEpochMillis = updated,
    )

    private val ownerLikeReminders = listOf(
        // Inner line ending with a space: the exact shape of the 3 reminders in the bug report.
        reminder(
            "zak",
            "Tem que tentar emular o Zak, mesmo que não consiga, o processo de tentar vai te fazer melhor que você \n" +
                "era antes.\n\nNão desista.",
            cadence = ScheduleCadence.WEEKLY,
        ),
        reminder(
            "fronteira",
            "Fronteira final: abandonar o codex e caderno. \nO que quero a minha vida nos próximos anos?",
            cadence = ScheduleCadence.TWICE_MONTHLY,
        ),
        reminder(
            "depressao",
            "Cuidado com a depressão. \n\n- dormir cedo\n- treinar\t\n- sol de manhã",
            cadence = ScheduleCadence.THREE_TIMES_PER_WEEK,
        ),
        reminder("gym", "gym.pq. Quero voltar amanhã porque me sinto bem 💪", cadence = ScheduleCadence.MONTHLY),
        reminder("daily", "Beber água", perDay = 4, days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
        reminder("format", "Linhas que parecem formato:\n---\nReminder:\nNotifications per day: 3\nId: fake", ScheduleCadence.FIVE_TIMES_PER_WEEK),
        reminder("paused", "Pausado por enquanto", cadence = ScheduleCadence.PAUSED, days = DayOfWeek.entries.toSet()),
    )

    private val local = AppState(
        reminders = ownerLikeReminders,
        notificationWindow = NotificationWindowSettings(startHour = 7, endHour = 22),
        settingsUpdatedAtEpochMillis = 0L,
    )

    private fun upload(state: AppState) = ReminderExchange.export(state, includeSyncMetadata = true)

    private fun download(local: AppState, file: String) =
        DriveSync.resolveRemote(local, ReminderExchange.parse(file), legacyTimestampMillis = now)

    @Test
    fun syncNowThenCompareShowsNoDifferences() {
        val remote = download(local, upload(local))

        assertEquals(DriveSync.Differences(), DriveSync.diff(local, remote))
    }

    @Test
    fun driveFileKeepsReminderTextExactly() {
        val remote = download(local, upload(local))

        assertEquals(local.reminders.map { it.text.trimEnd() }, remote.reminders.map { it.text })
        assertEquals(local.reminders.map { it.schedule }, remote.reminders.map { it.schedule })
        assertEquals(local.reminders.map { it.id }, remote.reminders.map { it.id })
    }

    @Test
    fun repeatedSyncsStayInSync() {
        var phone = local
        repeat(3) {
            val merged = DriveSync.merge(phone, download(phone, upload(phone)), now)
            val uploaded = upload(merged)
            phone = merged
            assertEquals(DriveSync.Differences(), DriveSync.diff(phone, download(phone, uploaded)))
        }
        assertEquals(local.reminders, phone.reminders)
    }

    @Test
    fun driveFileWrittenWithStrippedSpacesConvergesWithoutDataChanges() {
        // A Drive file as 1.0.38 effectively saw it: trailing spaces on inner lines gone.
        val lossyFile = upload(local).lines().joinToString("\n") { it.trimEnd() }
        val remote = download(local, lossyFile)

        assertEquals(DriveSync.Differences(), DriveSync.diff(local, remote))

        val merged = DriveSync.merge(local, remote, now)
        assertEquals(local.reminders, merged.reminders)
        assertEquals(DriveSync.Differences(), DriveSync.diff(merged, download(merged, upload(merged))))
    }

    @Test
    fun crlfDriveFileIsNotADifference() {
        val crlfFile = upload(local).replace("\n", "\r\n")

        assertEquals(DriveSync.Differences(), DriveSync.diff(local, download(local, crlfFile)))
    }

    @Test
    fun whitespaceOnlyChangesAreNotEdits() {
        val remote = local.copy(
            reminders = local.reminders.map { it.copy(text = "  " + it.text.replace("\n", " \r\n") + "\n\n") },
        )

        assertEquals(DriveSync.Differences(), DriveSync.diff(local, remote))
    }

    @Test
    fun realTextEditIsStillADifference() {
        val remote = local.copy(
            reminders = local.reminders.map {
                if (it.id == "zak") it.copy(text = it.text + " Nunca.", updatedAtEpochMillis = now) else it
            },
        )

        assertEquals(DriveSync.Differences(editedOnBoth = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals(remote.reminders.first { it.id == "zak" }.text, merged.reminders.first { it.id == "zak" }.text)
    }

    @Test
    fun cadenceChangeIsStillADifference() {
        val remote = local.copy(
            reminders = local.reminders.map {
                if (it.id == "gym") it.copy(schedule = it.schedule.copy(cadence = ScheduleCadence.WEEKLY)) else it
            },
        )

        assertEquals(DriveSync.Differences(editedOnBoth = 1), DriveSync.diff(local, remote))
    }

    @Test
    fun legacyFileMatchesTextDespiteStrippedSpaces() {
        val legacyFile = ReminderExchange.export(local).lines().joinToString("\n") { it.trimEnd() }
        val remote = download(local, legacyFile)

        assertEquals(local.reminders.map { it.id }, remote.reminders.map { it.id })
        assertEquals(DriveSync.Differences(), DriveSync.diff(local, remote))
    }

    @Test
    fun txtImportStillWorksWithTrailingSpaces() {
        val imported = ReminderExchange.import(ReminderExchange.export(local))

        assertEquals(local.reminders.map { it.text }, imported.reminders.map { it.text })
    }
}
