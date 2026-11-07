package de.schulplaner.ui.today

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.schulplaner.data.TaskType

/** Einstellungen der Startseite, nur für dieses Gerät: welche Aufgabenarten die Kachel zeigt, wie der Ferien-Countdown zählt. */
object HomePrefs {
    const val FERIEN_DAYS = "tage"
    const val FERIEN_SCHOOL = "schultage"
    const val FERIEN_BOTH = "beides"

    private var loaded = false

    /** Aufgabenarten in der Aufgaben-Kachel (Standard: nur Hausaufgaben). */
    var taskKinds by mutableStateOf(setOf(TaskType.HOMEWORK))
        private set

    /** Ferien-Countdown: Kalendertage, Schultage oder beides. */
    var ferienMode by mutableStateOf(FERIEN_BOTH)
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("startseite", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        p.getString("aufgaben_arten", null)?.let { raw -> taskKinds = raw.split(',').filter { it.isNotBlank() }.toSet() }
        ferienMode = p.getString("ferien_zaehlung", FERIEN_BOTH) ?: FERIEN_BOTH
    }

    fun setTaskKinds(ctx: Context, kinds: Set<String>) {
        taskKinds = kinds
        prefs(ctx).edit().putString("aufgaben_arten", kinds.joinToString(",")).apply()
    }

    fun setFerienMode(ctx: Context, mode: String) {
        ferienMode = mode
        prefs(ctx).edit().putString("ferien_zaehlung", mode).apply()
    }
}
