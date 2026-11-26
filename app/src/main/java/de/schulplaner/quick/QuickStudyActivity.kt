package de.schulplaner.quick

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.SettingKeys
import de.schulplaner.data.StudyKind
import de.schulplaner.domain.Schedule
import de.schulplaner.study.StudyNotifier
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.study.PauseIcon
import de.schulplaner.ui.study.PlayIcon
import de.schulplaner.ui.study.StartSheet
import de.schulplaner.ui.study.StopIcon
import de.schulplaner.ui.theme.SchulTheme
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Durchsichtige Activity für die Lernzeit-Kachel in den Schnelleinstellungen:
 * Bereich und Fach wählen, Start – die Uhr läuft, ohne die App zu öffnen. Läuft schon eine, kann man sie steuern.
 */
class QuickStudyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (app.prefs.readOnly.value) {
            android.widget.Toast.makeText(this, "Ansichtsmodus – hier kann nichts gestartet werden", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val repo = app.repo
        val blur = applyWindowBlur()
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle("system")
            val season by app.prefs.season.collectAsStateWithLifecycle("standard")
            val subjects by repo.subjects.collectAsStateWithLifecycle()
            val current by repo.currentStudy.collectAsStateWithLifecycle()
            // Beim Kaltstart kurz warten, bis alles aus der Datenbank geladen ist; Fach der aktuellen Stunde vorauswählen.
            val start by produceState<Pair<String, String?>?>(null) {
                withTimeoutOrNull(2000) { repo.scheduleData.first { it.periods.isNotEmpty() } }
                val st = repo.settings.value
                val kind = st[SettingKeys.STUDY_KIND]?.takeIf { it in StudyKind.all } ?: StudyKind.LEARN
                val lesson = Schedule.currentOrLastLesson(LocalDateTime.now(), repo.scheduleData.value)?.slot?.subjectId
                value = kind to (lesson ?: st[SettingKeys.STUDY_SUBJECT]?.ifBlank { null })
            }
            SchulTheme(theme, season) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val a by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "scrim")
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = a }
                        .background(if (blur) Color(0x47000000) else Color(0xB80E1015)),
                )
                val session = current?.takeIf { !it.finished }
                val s = start
                if (session != null) {
                    val subject = subjects.firstOrNull { it.id == session.subjectId }
                    AppSheet(onDismiss = { finish() }) { close ->
                        val c = colors
                        Text(
                            if (session.isRunning) "Lernzeit läuft" else "Lernzeit pausiert",
                            style = T.title.copy(fontSize = T.section.fontSize * 1.4f),
                            color = c.text,
                        )
                        Text(
                            StudyKind.label(session.kind) + (subject?.let { " · ${it.name}" } ?: "") + " · " + StudyNotifier.clock(session.totalMs(System.currentTimeMillis())),
                            style = T.body,
                            color = c.sub,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                if (session.isRunning) "Pause" else "Weiter",
                                {
                                    repo.toggleStudy()
                                    close()
                                },
                                Modifier.weight(1f).height(52.dp),
                                icon = if (session.isRunning) PauseIcon else PlayIcon,
                            )
                            PrimaryButton(
                                "Beenden",
                                {
                                    repo.stopStudy()
                                    close()
                                },
                                Modifier.weight(1f).height(52.dp),
                                icon = StopIcon,
                            )
                        }
                    }
                } else if (s != null) {
                    StartSheet(
                        initialKind = s.first,
                        initialSubject = s.second,
                        subjects = subjects,
                        start = true,
                        onDismiss = { finish() },
                    ) { k, subjectId -> repo.startStudy(k, subjectId, true) }
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
