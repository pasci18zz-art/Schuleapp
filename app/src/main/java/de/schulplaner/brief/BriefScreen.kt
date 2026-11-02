package de.schulplaner.brief

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Repository
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.Tabs
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LessonMark
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.examUrgency
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.components.urgencyColor
import de.schulplaner.ui.theme.T
import java.time.Duration
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** Ein kleiner Satz für den Tag – wechselt täglich. */
private val dailyLines = listOf(
    "Ein Schritt nach dem anderen – du musst heute nicht alles schaffen.",
    "Kleine Fortschritte sind auch Fortschritte.",
    "Du hast schon so viele Tage geschafft. Der hier wird auch was.",
    "Erst die wichtigste Sache, dann der Rest.",
    "Pausen gehören zum Lernen dazu.",
    "Heute ist ein guter Tag, um stolz auf dich zu sein.",
    "Konzentration auf das, was du ändern kannst.",
    "Mach's dir leicht: anfangen ist der schwerste Teil.",
    "Du bist mehr als deine Noten.",
    "Trink genug Wasser. Ernsthaft.",
    "Fehler sind Daten, keine Urteile.",
    "Gut ist besser als perfekt und nie fertig.",
    "Atme kurz durch – du hast das.",
    "Was du heute lernst, macht dein Zukunfts-Ich glücklich.",
    "Sei nett zu dir, auch wenn's mal nicht läuft.",
    "Heute zählt: dranbleiben, nicht durchziehen bis zum Umfallen.",
    "Ein Häkchen nach dem anderen.",
    "Neugier schlägt Druck.",
    "Auch kleine Siege feiern!",
    "Du kannst mehr, als du gerade denkst.",
)

@Composable
fun BriefScreen() {
    val c = colors
    val nav = LocalNavigator.current
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val exams by repo.exams.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val kinds by repo.taskKinds.collectAsStateWithLifecycle()
    val pins by repo.dailyPins.collectAsStateWithLifecycle()
    val now = rememberNow()
    val today = now.toLocalDate()
    val snap = Repository.Snapshot(schedule, tasks, kinds, emptyList(), settings, exams, topics)
    val b = MorningBrief.build(snap, today, pins.count { it.day == today.toEpochDay() })
    val real = b.lessons.filter { !it.cancelled }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Ic.ChevronLeft, "Zurück", { nav.back() })
            Text("Morgenbrief", style = T.section, color = c.sub, modifier = Modifier.padding(start = 12.dp))
        }

        // Sonnenaufgang-Karte mit Begrüßung.
        val greet = when (now.hour) {
            in 4..10 -> "Guten Morgen!"
            in 11..17 -> "Hallo!"
            else -> "Guten Abend!"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFB36B), Color(0xFFFF7E8A), Color(0xFF8E6BE8)))),
        ) {
            Sunrise(Modifier.matchParentSize())
            Column(Modifier.padding(22.dp)) {
                Text(Fmt.full(today).uppercase(), style = T.overline, color = Color.White.copy(alpha = 0.85f))
                Text(greet, style = T.hero, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                val summary = when {
                    b.schoolDay && b.firstStart != null && b.lastEnd != null ->
                        "${real.size} ${if (real.size == 1) "Stunde" else "Stunden"} · ${Fmt.time(b.firstStart.toLocalTime())} – ${Fmt.time(b.lastEnd.toLocalTime())}"
                    b.schoolDay -> "${real.size} Stunden heute"
                    else -> "Heute ist kein Unterricht"
                }
                Text(summary, style = T.body, color = Color.White, modifier = Modifier.padding(top = 6.dp))
                if (b.firstStart != null && b.firstStart.isAfter(now)) {
                    val m = Duration.between(now, b.firstStart).toMinutes() + 1
                    val label = if (m >= 60) "Unterricht beginnt in ${m / 60} h ${m % 60} min" else "Unterricht beginnt in $m min"
                    Text(
                        label,
                        style = T.smallBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (b.lessons.isNotEmpty()) {
            Section(Ic.Calendar, "Dein Stundenplan") {
                b.lessons.forEach { l ->
                    val dayEpoch = today.toEpochDay()
                    val ha = tasks.any { !it.done && it.dueDate == dayEpoch && it.subjectId == l.slot.subjectId }
                    val exam = exams.any { it.date == dayEpoch && it.subjectId == l.slot.subjectId }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(l.start?.let { Fmt.time(it) } ?: l.periodLabel, style = T.smallBold, color = c.sub, modifier = Modifier.width(44.dp))
                        LessonMark(subjectColor(l.subject?.color), l.length)
                        Column(Modifier.weight(1f)) {
                            Text(
                                l.subject?.name ?: "?",
                                style = T.body.copy(textDecoration = if (l.cancelled) TextDecoration.LineThrough else null),
                                color = if (l.cancelled) c.sub else c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val info = listOfNotNull(l.periodLabel + " Std", l.room.takeIf { it.isNotBlank() }?.let { "R $it" }).joinToString(" · ")
                            Text(info, style = T.small, color = c.sub)
                        }
                        if (exam) Pill("Klausur", c.warnSoft, c.warn)
                        if (ha) Pill("HA")
                        if (l.cancelled) Pill("Entfall", c.warnSoft, c.warn)
                        else if (l.override?.room?.isNotBlank() == true) Pill("Raum neu", c.accentSoft, c.accent)
                    }
                }
            }
        }

        Section(Ic.Tasks, "Wichtigste Aufgaben", action = if (b.openCount > b.topTasks.size) "alle ${b.openCount}" else null, onAction = { nav.tab(Tabs.TASKS) }) {
            if (b.topTasks.isEmpty()) Text("Nichts offen – genieß es!", style = T.body, color = c.sub)
            b.topTasks.forEach { t ->
                val subject = t.subjectId?.let { schedule.subjects[it] }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CheckCircle(t.done, {
                        repo.setTaskDone(t, !t.done)
                        if (!t.done) undo.show("Erledigt") { repo.setTaskDone(t, false) }
                    }, size = 24.dp)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = T.body, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (subject != null) SubjectIcon(subject, 13.dp)
                            val due = t.dueDate?.let { d ->
                                when (d - today.toEpochDay()) {
                                    in Long.MIN_VALUE..-1L -> "überfällig"
                                    0L -> "heute fällig"
                                    1L -> "morgen fällig"
                                    else -> "fällig ${Fmt.dayDate(LocalDate.ofEpochDay(d))}"
                                }
                            }
                            Text(listOfNotNull(subject?.name, due).joinToString(" · "), style = T.small, color = if (due == "überfällig") c.warn else c.sub, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (b.exams.isNotEmpty()) {
            Section(Sym.Pen, "Klausuren in Sicht") {
                b.exams.forEach { e ->
                    val days = e.date - today.toEpochDay()
                    val urgent = urgencyColor(examUrgency(days))
                    val subject = schedule.subjects[e.subjectId]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.open(Routes.exam(e.id)) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(urgent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("$days", style = T.section, color = urgent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(subject?.name ?: e.title, style = T.body, color = c.text, maxLines = 1)
                            Text("${Fmt.dayDate(LocalDate.ofEpochDay(e.date))} · ${Fmt.inDays(days)}", style = T.small, color = c.sub)
                        }
                    }
                }
            }
        }

        if (b.specials.isNotEmpty()) {
            Section(Ic.Sparkle, "Besonderes") {
                b.specials.forEach { s ->
                    val (icon, tint) = when (s.kind) {
                        Special.Kind.CANCELLED -> Ic.XCircle to c.warn
                        Special.Kind.ROOM -> Ic.Pin to c.accent
                        Special.Kind.NOTE -> Ic.Note to c.accent
                        Special.Kind.HOLIDAY -> Ic.Palm to c.good
                        Special.Kind.LERNZETTEL -> Ic.Book to c.warn
                        Special.Kind.EXAM_TODAY -> Sym.Pen to c.exam
                        Special.Kind.PINNED -> Ic.Pushpin to c.accent
                    }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                        }
                        Text(s.text, style = T.body, color = c.text, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Text("FÜR HEUTE", style = T.overline, color = c.accent)
            Text(dailyLines[(today.dayOfYear - 1) % dailyLines.size], style = T.body.copy(fontSize = T.body.fontSize * 1.1f), color = c.text, modifier = Modifier.padding(top = 6.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.accent)
                .clickable {
                    nav.back()
                    nav.tab(Tabs.TODAY)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Los geht's", style = T.section, color = c.accentInk)
        }
        if (readOnly) Text("Ansichtsmodus", style = T.small, color = c.sub)
    }
}

@Composable
private fun Section(
    icon: ImageVector,
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = colors
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Text(title, style = T.section, color = c.text, modifier = Modifier.weight(1f))
            if (action != null) {
                Text(action, style = T.label, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        content()
    }
}

/** Aufgehende Sonne mit Strahlen in der Begrüßungskarte. */
@Composable
private fun Sunrise(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "sunrise")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "spin")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "rise")
    Canvas(modifier) {
        val d = density
        val cx = size.width - 64 * d
        val cy = size.height - 10 * d - rise * 8 * d
        drawCircle(Brush.radialGradient(listOf(Color(0x99FFF1B8), Color.Transparent), Offset(cx, cy), 140 * d), 140 * d, Offset(cx, cy))
        rotate(spin, Offset(cx, cy)) {
            for (i in 0 until 14) {
                val a = Math.toRadians(i * 360.0 / 14)
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(cx + (cos(a) * 52 * d).toFloat(), cy + (sin(a) * 52 * d).toFloat()),
                    Offset(cx + (cos(a) * 74 * d).toFloat(), cy + (sin(a) * 74 * d).toFloat()),
                    4 * d,
                    StrokeCap.Round,
                )
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C7), Color(0xFFFFC548)), Offset(cx - 10 * d, cy - 10 * d), 50 * d), 42 * d, Offset(cx, cy))
    }
}
