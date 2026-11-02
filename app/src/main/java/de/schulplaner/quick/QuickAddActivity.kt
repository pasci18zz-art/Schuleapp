package de.schulplaner.quick

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        if (app.prefs.readOnly.value) {
            android.widget.Toast.makeText(this, "Ansichtsmodus – hier kann nichts eingetragen werden", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val repo = app.repo
        val blur = applyWindowBlur()
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
                // Frosted Glass hinter dem Sheet: Mit Fenster-Unschärfe nur leicht abgedunkelt,
                // sonst (Unschärfe aus/Energiesparen) ein dunkles, fast deckendes Glas.
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val a by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "scrim")
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = a }
                        .background(if (blur) Color(0x47000000) else Color(0xB80E1015)),
                )
                task?.let { t ->
                    TaskEditSheet(t, isNew = true, onDismiss = { finish() })
                }
            }
        }
    }

    /** Weichzeichner für alles hinter diesem Fenster (Android 12+, wenn das Gerät es gerade erlaubt). */
    private fun applyWindowBlur(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val wm = getSystemService(WindowManager::class.java) ?: return false
        if (!wm.isCrossWindowBlurEnabled) return false
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = (36 * resources.displayMetrics.density).toInt() }
        return true
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
