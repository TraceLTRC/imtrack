package xyz.tracel.imtrack.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The **Reminder after** setting: how many hours a Stopwatch runs before its Reminder. Device-local. */
class ReminderSettings @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val reminderAfterHours: Flow<Int> =
        dataStore.data.map { it[ReminderAfterHours] ?: DefaultHours }.distinctUntilChanged()

    /** Stores [hours]; returns false, storing nothing, when it's under [MinHours]. */
    suspend fun setReminderAfterHours(hours: Int): Boolean {
        if (hours < MinHours) return false
        dataStore.edit { it[ReminderAfterHours] = hours }
        return true
    }

    companion object {
        const val DefaultHours = 4
        const val MinHours = 1
        private val ReminderAfterHours = intPreferencesKey("reminder_after_hours")
    }
}
