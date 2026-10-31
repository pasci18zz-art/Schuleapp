package de.schulplaner.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/** Stoppuhr für Lernsessions: Art wählen, starten/pausieren, beenden. */
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
        Segmented(
            StudyKind.all.map { it to StudyKind.label(it) },
            kind,
            { if (!readOnly) repo.setStudyKind(it) },
            Modifier.padding(top = 12.dp),
            icons = StudyKind.all.map { StudyLook.icon(it) },
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(
                    StudyLook.clock(total),
                    style = T.bigNumber.copy(fontSize = 40.sp, fontFeatureSettings = "tnum"),
                    color = if (cur == null) c.sub else c.text,
                    maxLines = 1,
                )
            }
            if (cur != null && !readOnly) {
                Box(
                    Modifier.size(44.dp).pressable({ repo.stopStudy() }).clip(CircleShape).background(c.line),
                    contentAlignment = Alignment.Center,
                ) { Icon(StopIcon, "Beenden", tint = c.text, modifier = Modifier.size(18.dp)) }
                Box(Modifier.size(10.dp))
            }
            if (!readOnly) Box(
                Modifier
                    .size(56.dp)
                    .pressable({ repo.toggleStudy() })
                    .shadow(10.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (running) PauseIcon else PlayIcon, if (running) "Pause" else "Start", tint = c.accentInk, modifier = Modifier.size(24.dp))
            }
        }
        if (!readOnly) {
            SubjectChooser(subject, subjects, onPick = { repo.setStudySubject(it) })
        } else if (subject != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubjectIcon(subject, 15.dp)
                Text(subject.name, style = T.label, color = c.sub)
            }
        }
    }
}

@Composable
private fun SubjectChooser(subject: de.schulplaner.data.Subject?, subjects: List<de.schulplaner.data.Subject>, onPick: (String?) -> Unit) {
    val c = colors
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.line.copy(alpha = 0.6f)).clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (subject != null) SubjectIcon(subject, 15.dp)
            Text(subject?.name ?: "Fach (optional)", style = T.label, color = if (subject != null) c.text else c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
            Icon(Ic.ChevronDown, null, tint = c.sub, modifier = Modifier.size(13.dp))
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = c.sheetGlass,
            shadowElevation = 8.dp,
        ) {
            MenuEntry(Ic.Layers, "Ohne Fach") { open = false; onPick(null) }
            subjects.forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(s.name, style = T.body, color = c.text) },
                    leadingIcon = { SubjectIcon(s, 18.dp) },
                    onClick = { open = false; onPick(s.id) },
                )
            }
        }
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
