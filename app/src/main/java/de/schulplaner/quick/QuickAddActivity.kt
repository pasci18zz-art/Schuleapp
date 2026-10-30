package de.schulplaner.quick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Task
import de.schulplaner.ui.tasks.TaskDefaults
import de.schulplaner.ui.tasks.TaskEditSheet
import de.schulplaner.ui.theme.SchulTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Durchsichtige Activity für die Schnelleinstellungs-Kachel und das App-Shortcut:
 * zeigt nur das Aufgaben-Sheet, vorausgefüllt mit der aktuellen Stunde.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = app.repo
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle("system")
            val task by produceState<Task?>(null) {
                // Beim Kaltstart kurz warten, bis der Stundenplan aus der Datenbank geladen ist.
                val data = withTimeoutOrNull(2000) {
                    repo.scheduleData.first { it.periods.isNotEmpty() }
                } ?: repo.scheduleData.value
                value = TaskDefaults.quick(LocalDateTime.now(), data)
            }
            SchulTheme(theme) {
                task?.let { t ->
                    TaskEditSheet(t, isNew = true, onDismiss = { finish() })
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
