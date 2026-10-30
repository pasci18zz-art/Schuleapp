package de.schulplaner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "geraet")

/** Einstellungen nur für dieses Gerät (werden nicht synchronisiert). */
class Prefs(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val reminderOnKey = booleanPreferencesKey("reminder_on")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")
    private val notifAskedKey = booleanPreferencesKey("notif_asked")

    /** "system", "light" oder "dark" */
    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "system" }

    /** Erinnerung am Vorabend an HA und Klausuren für morgen. */
    val reminderOn: Flow<Boolean> = context.dataStore.data.map { it[reminderOnKey] ?: true }
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[reminderMinuteKey] ?: (18 * 60) }
    val notificationsAsked: Flow<Boolean> = context.dataStore.data.map { it[notifAskedKey] ?: false }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[themeKey] = value }
    }

    suspend fun setReminder(on: Boolean, minute: Int) {
        context.dataStore.edit {
            it[reminderOnKey] = on
            it[reminderMinuteKey] = minute
        }
    }

    suspend fun setNotificationsAsked() {
        context.dataStore.edit { it[notifAskedKey] = true }
    }

    suspend fun reminderSnapshot(): Pair<Boolean, Int> = reminderOn.first() to reminderMinute.first()
}
