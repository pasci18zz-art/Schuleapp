package de.schulplaner.ui.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.StudyKind
import de.schulplaner.data.StudySession
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormValueRow
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.ProgressBar
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.Stepper
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.SubjectPicker
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.TimeDialog
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.theme.T
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Übersicht der Lernzeit: Wochenbalken, Aufteilung nach Art und Fach, alle Sessions zum Bearbeiten. */
@Composable
fun StudyScreen() {
    val c = colors
    val nav = LocalNavigator.current
    val repo = LocalContext.current.app.repo
    val sessions by repo.sessions.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val subjectMap = subjects.associateBy { it.id }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Pair<StudySession, Boolean>?>(null) }
    val zone = ZoneId.systemDefault()
    val running = sessions.any { it.isRunning }
    val now = rememberTicker(running)
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current

    val monday = Schedule.mondayOf(LocalDate.now()).plusWeeks(weekOffset.toLong())
    fun dayOf(s: StudySession): LocalDate = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
    val week = sessions.filter { val d = dayOf(it); !d.isBefore(monday) && d.isBefore(monday.plusDays(7)) }
    val total = week.sumOf { it.totalMs(now) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Ic.ChevronLeft, "Zurück", { nav.back() })
            Box(Modifier.weight(1f))
            if (!readOnly) GlassIconButton(Ic.Plus, "Session nachtragen", {
                val end = System.currentTimeMillis()
                edit = StudySession(startedAt = end - 30 * 60_000, endedAt = end, elapsedMs = 30 * 60_000, finished = true) to true
            }, filled = true)
        }
        ScreenHeader(title = "Lernzeit", overline = if (weekOffset == 0) "Diese Woche" else Fmt.weekRange(monday)) {
            GlassIconButton(Ic.ChevronLeft, "Vorherige Woche", { weekOffset-- })
            GlassIconButton(Ic.ChevronRight, "Nächste Woche", { if (weekOffset < 0) weekOffset++ })
        }

        if (weekOffset == 0) StudyTile()

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(StudyLook.short(total), style = T.bigNumber.copy(fontSize = T.bigNumber.fontSize * 0.8f), color = c.text)
                Text("in ${week.size} Sessions", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 6.dp))
            }
            WeekBars(monday, week, now, Modifier.padding(top = 12.dp))
        }

        if (total > 0) {
            GlassCard(padding = PaddingValues(16.dp)) {
                Text("Nach Art", style = T.section, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
                StudyKind.all.forEach { k ->
                    val ms = week.filter { it.kind == k }.sumOf { it.totalMs(now) }
                    BreakdownRow(
                        icon = { Icon(StudyLook.icon(k), null, tint = StudyLook.color(k), modifier = Modifier.size(20.dp)) },
                        label = StudyKind.label(k),
                        ms = ms,
                        share = ms.toFloat() / total,
                        color = StudyLook.color(k),
                    )
                }
            }
            val bySubject = week.groupBy { it.subjectId }.mapValues { (_, l) -> l.sumOf { it.totalMs(now) } }
                .filter { it.key != null && it.value > 0 }.entries.sortedByDescending { it.value }
            if (bySubject.isNotEmpty()) {
                GlassCard(padding = PaddingValues(16.dp)) {
                    Text("Nach Fach", style = T.section, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
                    bySubject.forEach { (id, ms) ->
                        val s = subjectMap[id]
                        BreakdownRow(
                            icon = { SubjectIcon(s, 20.dp) },
                            label = s?.name ?: "?",
                            ms = ms,
                            share = ms.toFloat() / total,
                            color = de.schulplaner.ui.components.subjectColor(s?.color),
                        )
                    }
                }
            }
        }

        GlassCard(padding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
            Text("Sessions", style = T.section, color = c.text, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            if (week.isEmpty()) {
                Text("Keine Sessions in dieser Woche", style = T.small, color = c.sub, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            }
            week.sortedByDescending { it.startedAt }.forEach { s ->
                val start = Instant.ofEpochMilli(s.startedAt).atZone(zone)
                val endMs = if (s.finished) s.endedAt ?: (s.startedAt + s.elapsedMs) else null
                val time = buildString {
                    append(Fmt.dayDate(start.toLocalDate()))
                    append(" · ")
                    append(Fmt.time(start.toLocalTime()))
                    if (endMs != null) append("–" + Fmt.time(Instant.ofEpochMilli(endMs).atZone(zone).toLocalTime()))
                    if (!s.finished) append(if (s.isRunning) " · läuft" else " · pausiert")
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { edit = s to false }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(StudyLook.color(s.kind).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(StudyLook.icon(s.kind), null, tint = StudyLook.color(s.kind), modifier = Modifier.size(19.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(StudyKind.label(s.kind), s.subjectId?.let { subjectMap[it]?.name }).joinToString(" · "),
                            style = T.body,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(time, style = T.small, color = c.sub, maxLines = 1)
                    }
                    Text(StudyLook.short(s.totalMs(now)), style = T.label, color = c.text)
                }
            }
        }
    }

    if (!readOnly) edit?.let { (s, isNew) -> SessionSheet(s, isNew, onDismiss = { edit = null }) }
}

@Composable
private fun BreakdownRow(icon: @Composable () -> Unit, label: String, ms: Long, share: Float, color: androidx.compose.ui.graphics.Color) {
    val c = colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(label, style = T.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                Text(StudyLook.short(ms), style = T.label, color = c.sub)
            }
            ProgressBar(share, color, height = 5.dp)
        }
    }
}

/** Farbe für Sessions ohne Fach. */
private val NoSubject = androidx.compose.ui.graphics.Color(0xFF7C8CB0)

/** Fläche im Muster der Lern-Art: Hausaufgaben voll, Lernen schräg gestrichelt, Referat kariert. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.patternRect(
    kind: String,
    color: androidx.compose.ui.graphics.Color,
    topLeft: Offset,
    size: Size,
) {
    when (kind) {
        StudyKind.HOMEWORK -> drawRect(color, topLeft, size)
        StudyKind.LEARN -> {
            drawRect(color.copy(alpha = 0.28f), topLeft, size)
            clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
                val step = 5.dp.toPx()
                val w = 2.dp.toPx()
                var x = topLeft.x - size.height
                while (x < topLeft.x + size.width) {
                    drawLine(color, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), strokeWidth = w)
                    x += step
                }
            }
        }
        else -> {
            drawRect(color.copy(alpha = 0.28f), topLeft, size)
            clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
                val cell = 4.dp.toPx()
                var row = 0
                var y = topLeft.y + size.height - cell
                while (y > topLeft.y - cell) {
                    var col = 0
                    var x = topLeft.x
                    while (x < topLeft.x + size.width) {
                        if ((row + col) % 2 == 0) drawRect(color, Offset(x, y), Size(cell, cell))
                        x += cell
                        col++
                    }
                    y -= cell
                    row++
                }
            }
        }
    }
}

/** Balken Mo–So: Fachfarbe, Muster je Art (gestapelt nach Fach und Art). */
@Composable
private fun WeekBars(monday: LocalDate, week: List<StudySession>, now: Long, modifier: Modifier = Modifier) {
    val c = colors
    val zone = ZoneId.systemDefault()
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val colorOf = subjects.associate { it.id to de.schulplaner.ui.components.subjectColor(it.color) }
    // Pro Tag: Abschnitte (Fach, Art, Dauer), sortiert nach Fach, damit gleiche Fächer zusammenstehen.
    val perDay = (0 until 7).map { i ->
        val d = monday.plusDays(i.toLong())
        week.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == d }
            .groupBy { it.subjectId to it.kind }
            .map { (key, list) -> Triple(key.first, key.second, list.sumOf { it.totalMs(now) }) }
            .filter { it.third > 0 }
            .sortedWith(compareBy({ it.first ?: "~" }, { StudyKind.all.indexOf(it.second) }))
    }
    val max = perDay.maxOf { day -> day.sumOf { it.third } }.coerceAtLeast(30 * 60_000L)
    val today = LocalDate.now()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perDay.forEach { day ->
                val sum = day.sumOf { it.third }
                Text(
                    if (sum >= 60_000) StudyLook.short(sum).removeSuffix(" min").removeSuffix(" h") else "",
                    style = T.small.copy(fontSize = T.small.fontSize * 0.85f),
                    color = c.sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perDay.forEach { day ->
                Canvas(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                    drawRect(c.line.copy(alpha = 0.6f))
                    var bottom = size.height
                    day.forEach { (subjectId, kind, ms) ->
                        val h = size.height * (ms.toFloat() / max)
                        val col = subjectId?.let { colorOf[it] } ?: NoSubject
                        patternRect(kind, col, Offset(0f, bottom - h), Size(size.width, h))
                        bottom -= h
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until 7).forEach { i ->
                val d = monday.plusDays(i.toLong())
                Text(
                    Fmt.dayShort(d),
                    style = T.smallBold,
                    color = if (d == today) c.accent else c.sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Legende: Muster je Art (Farbe = Fach).
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StudyKind.all.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Canvas(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))) { patternRect(k, c.sub, Offset.Zero, size) }
                    Text(StudyKind.label(k), style = T.small, color = c.sub)
                }
            }
        }
        Text("Farbe = Fach", style = T.small.copy(fontSize = T.small.fontSize * 0.9f), color = c.sub.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
    }
}

/** Session ändern oder nachtragen: Art, Fach, Tag, Beginn, Dauer. */
@Composable
private fun SessionSheet(initial: StudySession, isNew: Boolean, onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var s by remember { mutableStateOf(initial) }
    // Dauer in Minuten (bei laufender Session der Stand beim Öffnen).
    var minutes by remember { mutableIntStateOf((initial.totalMs() / 60_000).toInt()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickDuration by remember { mutableStateOf(false) }
    val start = Instant.ofEpochMilli(s.startedAt).atZone(zone)

    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            if (isNew) "Session nachtragen" else "Session",
            onSave = if (minutes > 0) {
                {
                    val dur = minutes * 60_000L
                    val t = System.currentTimeMillis()
                    val saved = when {
                        s.finished -> s.copy(elapsedMs = dur, endedAt = s.startedAt + dur)
                        // Laufende Session: korrigierte Zeit übernehmen und von jetzt an weiterzählen.
                        s.runningSince != null -> s.copy(elapsedMs = dur, runningSince = t)
                        else -> s.copy(elapsedMs = dur)
                    }
                    repo.saveSession(saved)
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    val original = s
                    repo.deleteSession(original)
                    undo.show("Gelöscht") { repo.restoreSession(original) }
                    close()
                }
            } else {
                null
            },
        )
        Segmented(
            StudyKind.all.map { it to StudyKind.label(it) },
            s.kind,
            { s = s.copy(kind = it) },
            icons = StudyKind.all.map { StudyLook.icon(it) },
        )
        SubjectPicker(subjects, s.subjectId, { s = s.copy(subjectId = it) })
        FormGroup {
            FormValueRow("Tag", Fmt.dayDate(start.toLocalDate()), { pickDate = true })
            FormValueRow("Beginn", Fmt.time(start.toLocalTime()) + " Uhr", { pickTime = true })
            FormRow("Dauer", divider = false) {
                Text(
                    StudyLook.short(minutes * 60_000L),
                    style = T.body,
                    color = colors.text,
                    modifier = Modifier.weight(1f).clickable { pickDuration = true },
                )
                Stepper(
                    "±5",
                    onMinus = { minutes = (minutes - 5).coerceAtLeast(0) },
                    onPlus = { minutes += 5 },
                )
            }
        }
        Text("Dauer antippen, um sie genau einzugeben (Stunden:Minuten).", style = T.small, color = colors.sub)
    }

    if (pickDate) {
        DateDialog(start.toLocalDate(), onDismiss = { pickDate = false }) { d ->
            s = s.copy(startedAt = d.atTime(start.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
        }
    }
    if (pickTime) {
        TimeDialog(start.hour * 60 + start.minute, onDismiss = { pickTime = false }) { m ->
            s = s.copy(startedAt = start.toLocalDate().atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli())
        }
    }
    if (pickDuration) {
        TimeDialog(minutes.coerceAtMost(23 * 60 + 59), onDismiss = { pickDuration = false }) { m -> minutes = m }
    }
}
