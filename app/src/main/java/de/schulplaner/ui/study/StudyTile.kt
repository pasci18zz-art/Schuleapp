package de.schulplaner.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.SettingKeys
import de.schulplaner.data.StudyKind
import de.schulplaner.data.StudySession
import de.schulplaner.domain.Schedule
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.MenuEntry
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/** Symbol und Farbe je Lern-Art. */
object StudyLook {
    fun icon(kind: String): ImageVector = when (kind) {
        StudyKind.HOMEWORK -> Sym.House
        StudyKind.TALK -> Sym.Presentation
        else -> Sym.Pen
    }

    fun color(kind: String): Color = when (kind) {
        StudyKind.HOMEWORK -> Color(0xFF5B7FDB)
        StudyKind.TALK -> Color(0xFFF08C2E)
        else -> Color(0xFF2F9E6E)
    }

    /** "0:42:07" bzw. "42:07". */
    fun clock(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec / 60) % 60
        val s = sec % 60
        val mm = m.toString().padStart(2, '0')
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }

    /** "1:05 h" oder "23 min". */
    fun short(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60}:${(min % 60).toString().padStart(2, '0')} h" else "$min min"
    }
}

/** Aktuelle Zeit, die sich jede Sekunde erneuert, solange [ticking]. */
@Composable
fun rememberTicker(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun weekStart(date: LocalDate = LocalDate.now()): Long =
    Schedule.mondayOf(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Lernzeit: links die helle Klappzahlen-Uhr, rechts Start/Pause.
 * Start öffnet die Auswahl (Art und Fach), danach läuft die Uhr.
 */
@Composable
fun StudyTile(modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val current by repo.currentStudy.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val sessions by repo.sessions.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val cur: StudySession? = current
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current
    val running = cur?.isRunning == true
    val now = rememberTicker(running)
    val kind = cur?.kind ?: settings[SettingKeys.STUDY_KIND]?.takeIf { it in StudyKind.all } ?: StudyKind.LEARN
    val subjectId = cur?.subjectId ?: settings[SettingKeys.STUDY_SUBJECT]?.ifBlank { null }
    val subject = subjects.firstOrNull { it.id == subjectId }
    val total = cur?.totalMs(now) ?: 0L
    val monday = weekStart()
    val week = sessions.filter { it.startedAt >= monday }.sumOf { it.totalMs(now) }
    // null = zu, true = neue Session starten, false = laufende ändern
    var picker by remember { mutableStateOf<Boolean?>(null) }

    GlassCard(modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Clock, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("Lernzeit", style = T.section, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text("Woche ${StudyLook.short(week)}", style = T.smallBold, color = c.sub)
            if (onOpen != null) Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipTimer(total, Modifier.weight(1f), height = 92.dp, dim = cur == null)
            if (!readOnly) {
                Column(Modifier.padding(start = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .pressable({ if (cur == null) picker = true else repo.toggleStudy() })
                            .shadow(12.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                            .clip(CircleShape)
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (running) PauseIcon else PlayIcon, if (running) "Pause" else "Start", tint = c.accentInk, modifier = Modifier.size(26.dp))
                    }
                    if (cur != null) {
                        Box(
                            Modifier.size(40.dp).pressable({ repo.stopStudy() }).clip(CircleShape).background(c.line),
                            contentAlignment = Alignment.Center,
                        ) { Icon(StopIcon, "Beenden", tint = c.text, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        // Was gerade läuft – antippen ändert Art oder Fach.
        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(if (!readOnly && cur != null) Modifier.clickable { picker = false } else Modifier)
                .background(c.line.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(StudyLook.icon(kind), null, tint = StudyLook.color(kind), modifier = Modifier.size(16.dp))
            Text(StudyKind.label(kind), style = T.label, color = c.text)
            if (subject != null) {
                Text("·", style = T.label, color = c.sub)
                SubjectIcon(subject, 14.dp)
                Text(subject.name, style = T.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            }
            Text(
                when {
                    running -> "· läuft"
                    cur != null -> "· pausiert"
                    else -> "· bereit"
                },
                style = T.small,
                color = c.sub,
            )
        }
    }

    picker?.let { start ->
        StartSheet(
            initialKind = kind,
            initialSubject = subjectId,
            subjects = subjects,
            start = start,
            onDismiss = { picker = null },
        ) { k, s -> repo.startStudy(k, s, start) }
    }
}

/** Auswahl vor dem Start: was du machst (Hausaufgaben, Referat, Lernen) und in welchem Fach. */
@Composable
private fun StartSheet(
    initialKind: String,
    initialSubject: String?,
    subjects: List<de.schulplaner.data.Subject>,
    start: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var subjectId by remember { mutableStateOf(initialSubject) }
    de.schulplaner.ui.components.AppSheet(onDismiss = onDismiss) { close ->
        Text(if (start) "Lernsession starten" else "Session ändern", style = T.title.copy(fontSize = T.section.fontSize * 1.4f), color = colors.text)
        Segmented(
            StudyKind.all.map { it to StudyKind.label(it) },
            kind,
            { kind = it },
            icons = StudyKind.all.map { StudyLook.icon(it) },
        )
        Text(StudyKind.label(kind), style = T.label, color = colors.sub, modifier = Modifier.padding(start = 4.dp))
        de.schulplaner.ui.components.SubjectPicker(subjects, subjectId, { subjectId = it })
        de.schulplaner.ui.components.PrimaryButton(
            if (start) "Start" else "Übernehmen",
            {
                onConfirm(kind, subjectId)
                close()
            },
            Modifier.fillMaxWidth().height(52.dp),
            icon = if (start) PlayIcon else null,
        )
    }
}

// Gefüllte Symbole für die Stoppuhr-Knöpfe.
private fun filled(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(androidx.compose.ui.graphics.vector.addPathNodes(it), fill = androidx.compose.ui.graphics.SolidColor(Color.Black))
        }
    }.build()

val PlayIcon by lazy { filled("play", "M8.5 5.2v13.6a1 1 0 0 0 1.5.9l10.6-6.8a1 1 0 0 0 0-1.7L10 4.3a1 1 0 0 0-1.5.9z") }
val PauseIcon by lazy { filled("pause", Ic.rect(6f, 5f, 4f, 14f, 1.4f), Ic.rect(14f, 5f, 4f, 14f, 1.4f)) }
val StopIcon by lazy { filled("stop", Ic.rect(6.5f, 6.5f, 11f, 11f, 2.2f)) }
