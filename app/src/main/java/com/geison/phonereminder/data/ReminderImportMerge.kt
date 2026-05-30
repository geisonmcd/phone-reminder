package com.geison.phonereminder.data

object ReminderImportMerge {
    fun merge(
        currentState: AppState,
        importedState: AppState,
    ): AppState {
        return currentState.copy(
            reminders = currentState.reminders + importedState.reminders,
        )
    }
}
