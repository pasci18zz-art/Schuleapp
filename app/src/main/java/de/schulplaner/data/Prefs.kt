package de.schulplaner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "geraet")

/** Einstellungen nur für dieses Gerät (werden nicht synchronisiert). */
class Prefs(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val reminderOnKey = booleanPreferencesKey("reminder_on")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")
    private val notifAskedKey = booleanPreferencesKey("notif_asked")

    /** Gewählte Apps für die Verknüpfungen auf der Startseite (Schlüssel → Paketname). */
    val appLinks: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs.asMap().filterKeys { it.name.startsWith("app_") }.mapKeys { it.key.name }.mapValues { it.value.toString() }
    }

    suspend fun setAppLink(key: String, packageName: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = packageName }
    }

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

    // Ansichtsmodus: sofort lesbar (auch beim Start und in Widgets), daher in SharedPreferences.
    private val instant = context.getSharedPreferences("geraet_sofort", Context.MODE_PRIVATE)
    private val _readOnly = MutableStateFlow(instant.getBoolean("read_only", false))

    /** Ansichtsmodus: Auf diesem Gerät kann nichts geändert werden (z. B. für Eltern). */
    val readOnly: StateFlow<Boolean> = _readOnly

    fun setReadOnly(on: Boolean) {
        instant.edit().putBoolean("read_only", on).apply()
        _readOnly.value = on
    }
}
