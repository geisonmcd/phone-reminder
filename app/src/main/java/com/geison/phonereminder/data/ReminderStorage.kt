package com.geison.phonereminder.data

import android.content.Context
import com.geison.phonereminder.diagnostics.Diagnostics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ReminderStorage {
    private const val PREFS_NAME = "phone_reminder_prefs"
    private const val STATE_KEY = "app_state"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context): AppState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(STATE_KEY, null) ?: return AppState()
        return runCatching {
            val migrated = migrateLegacyState(json.parseToJsonElement(raw))
            json.decodeFromJsonElement<AppState>(migrated)
        }.getOrElse { error ->
            Diagnostics.recordNonFatal(
                area = "storage_load_failed",
                throwable = error,
                keys = mapOf(
                    "raw_length" to raw.length.toString(),
                ),
            )
            AppState()
        }
    }

    fun save(context: Context, state: AppState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(STATE_KEY, json.encodeToString(state))
            .apply()
    }

    private fun migrateLegacyState(element: JsonElement): JsonElement {
        val root = element as? JsonObject ?: return element
        val reminders = root["reminders"] as? JsonArray ?: return element
        val migrationCreatedAt = System.currentTimeMillis()

        val reminderObjects = reminders.mapNotNull { it as? JsonObject }
        val legacyWindow = reminderObjects.firstNotNullOfOrNull { reminderObject ->
            val scheduleObject = reminderObject["schedule"] as? JsonObject ?: return@firstNotNullOfOrNull null
            val startHour = scheduleObject["startHour"]?.jsonPrimitive?.intOrNull ?: return@firstNotNullOfOrNull null
            val endHour = scheduleObject["endHour"]?.jsonPrimitive?.intOrNull ?: return@firstNotNullOfOrNull null
            startHour to endHour
        } ?: (9 to 20)

        val migratedReminders = reminders.map { reminderElement ->
            val reminderObject = reminderElement as? JsonObject ?: return@map reminderElement
            val scheduleObject = reminderObject["schedule"] as? JsonObject ?: return@map reminderElement

            buildJsonObject {
                reminderObject.forEach { (key, value) ->
                    if (key == "schedule") {
                        put(
                            "schedule",
                            buildJsonObject {
                                scheduleObject.forEach { (scheduleKey, scheduleValue) ->
                                    if (
                                        scheduleKey != "startHour" &&
                                        scheduleKey != "endHour"
                                    ) {
                                        put(scheduleKey, scheduleValue)
                                    }
                                }
                                put(
                                    "notificationsPerDay",
                                    scheduleObject["notificationsPerDay"]?.jsonPrimitive?.intOrNull ?: 1,
                                )
                            },
                        )
                    } else {
                        put(key, value)
                    }
                }
                if ("createdAtEpochMillis" !in reminderObject) {
                    put("createdAtEpochMillis", migrationCreatedAt)
                }
            }
        }

        return buildJsonObject {
            root.forEach { (key, value) ->
                if (key == "reminders") {
                    put("reminders", JsonArray(migratedReminders))
                } else if (key != "notificationWindow") {
                    put(key, value)
                }
            }
            if ("notificationWindow" in root) {
                root["notificationWindow"]?.let { put("notificationWindow", it) }
            } else {
                put(
                    "notificationWindow",
                    buildJsonObject {
                        put("startHour", legacyWindow.first)
                        put("endHour", legacyWindow.second)
                    },
                )
            }
        }
    }
}
