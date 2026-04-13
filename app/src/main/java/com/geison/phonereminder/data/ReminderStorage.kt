package com.geison.phonereminder.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        val migratedRaw = raw.replace("notificationsPerDay", "notificationsPerWeek")
        return runCatching { json.decodeFromString<AppState>(migratedRaw) }.getOrDefault(AppState())
    }

    fun save(context: Context, state: AppState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(STATE_KEY, json.encodeToString(state))
            .apply()
    }
}
