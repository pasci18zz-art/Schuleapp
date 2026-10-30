package de.schulplaner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "geraet")

/** Einstellungen nur für dieses Gerät (werden nicht synchronisiert). */
class Prefs(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")

    /** "system", "light" oder "dark" */
    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "system" }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[themeKey] = value }
    }
}
