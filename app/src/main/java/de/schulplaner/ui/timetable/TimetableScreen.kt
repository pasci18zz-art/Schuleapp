package de.schulplaner.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.LessonSlot
import de.schulplaner.data.Task
import de.schulplaner.data.WeekType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.label
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.tasks.TaskDefaults
import de.schulplaner.ui.tasks.TaskEditSheet
import de.schulplaner.ui.theme.T

private val RowH = 56.dp
private val Gap = 4.dp
private val HeaderH = 42.dp
private val TimeW = 32.dp

@Composable
fun TimetableScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val exams by repo.exams.collectAsStateWithLifecycle()
    val now = rememberNow()
    val today = now.toLocalDate()

    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var openLesson by remember { mutableStateOf<Lesson?>(null) }
    var editSlot by remember { mutableStateOf<Pair<LessonSlot, Boolean>?>(null) }
    var newTask by remember { mutableStateOf<Task?>(null) }

    val monday = Schedule.mondayOf(today).plusWeeks(weekOffset.toLong())
    val wt = Schedule.weekTypeOf(monday, schedule)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = Fmt.weekRange(monday),
            overline = if (editMode) "Bearbeiten" else "KW ${Fmt.weekOfYear(monday)}",
            chip = wt?.let { "$it-Woche" },
        ) {
            GlassIconButton(Ic.ChevronLeft, "Vorherige Woche", { weekOffset-- })
            GlassIconButton(Ic.ChevronRight, "Nächste Woche", { weekOffset++ })
            GlassIconButton(if (editMode) Ic.Check else Ic.Pencil, if (editMode) "Fertig" else "Stundenplan bearbeiten", { editMode = !editMode }, filled = editMode)
        }

        if (schedule.subjects.isEmpty()) {
            GlassCard {
                Text("Lege zuerst unter Einstellungen → Fächer deine Fächer an.", style = T.body, color = c.sub)
            }
        } else if (schedule.slots.isEmpty() && !editMode) {
            GlassCard(onClick = { editMode = true }) {
                Text("Noch kein Stundenplan. Tippe auf den Stift oben (oder hier), dann auf ein freies Feld.", style = T.body, color = c.sub)
            }
        }

        AnimatedContent(
            targetState = weekOffset,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(200)))
            },
            label = "week",
        ) { offset ->
            val mon = Schedule.mondayOf(today).plusWeeks(offset.toLong())
            WeekGrid(
                monday = mon,
                todayEpoch = today.toEpochDay(),
                editMode = editMode,
                maxPeriod = schedule.maxPeriod,
                lessonsFor = { d -> if (editMode) Schedule.plannedLessons(d, schedule) else Schedule.lessonsOn(d, schedule) },
                holidayFor = { d -> Schedule.holidayOn(d, schedule)?.name },
                hasHa = { l -> tasks.any { !it.done && it.dueDate == l.date.toEpochDay() && it.subjectId == l.slot.subjectId } },
                hasExam = { l -> exams.any { it.date == l.date.toEpochDay() && it.subjectId == l.slot.subjectId } },
                periodStart = { p -> schedule.periodByNumber[p]?.let { Fmt.minutes(it.startMinute) } ?: "" },
                onLesson = { l ->
                    if (editMode) editSlot = l.slot to false else openLesson = l
                },
                onEmpty = { day, period ->
                    editSlot = LessonSlot(
                        dayOfWeek = day,
                        period = period,
                        length = 2,
                        weekType = WeekType.ALL,
                        subjectId = schedule.subjects.values.firstOrNull()?.id ?: "",
                    ) to true
                },
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Legend(Ic.Bookmark, "HA", c.accent)
            Box(Modifier.width(18.dp))
            Legend(Ic.Alert, "Klausur", c.warn)
            Box(Modifier.width(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(3.dp)).stripes(c.sub, 1.5.dp))
                Text("Entfall", style = T.smallBold, color = c.sub)
            }
        }
    }

    openLesson?.let { l ->
        LessonSheet(
            lesson = l,
            onDismiss = { openLesson = null },
            onAddTask = {
                openLesson = null
                newTask = TaskDefaults.forSubject(l.slot.subjectId, l.date, schedule)
            },
        )
    }
    editSlot?.let { (slot, isNew) ->
        SlotEditSheet(slot, isNew, onDismiss = { editSlot = null })
    }
    newTask?.let { t ->
        TaskEditSheet(t, isNew = true, onDismiss = { newTask = null })
    }
}

@Composable
private fun Legend(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Text(text, style = T.smallBold, color = colors.sub)
    }
}

private fun Modifier.stripes(color: Color, width: Dp): Modifier = drawBehind {
    val w = width.toPx()
    val step = w * 3.5f
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = w)
            x += step
        }
    }
}

@Composable
private fun WeekGrid(
    monday: java.time.LocalDate,
    todayEpoch: Long,
    editMode: Boolean,
    maxPeriod: Int,
    lessonsFor: (java.time.LocalDate) -> List<Lesson>,
    holidayFor: (java.time.LocalDate) -> String?,
    hasHa: (Lesson) -> Boolean,
    hasExam: (Lesson) -> Boolean,
    periodStart: (Int) -> String,
    onLesson: (Lesson) -> Unit,
    onEmpty: (Int, Int) -> Unit,
) {
    val c = colors
    val bodyH = RowH * maxPeriod + Gap * (maxPeriod - 1)
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            // Zeitspalte
            Column(Modifier.width(TimeW)) {
                Box(Modifier.height(HeaderH))
                Box(Modifier.height(bodyH)) {
                    for (p in 1..maxPeriod) {
                        Column(
                            Modifier.offset(y = (RowH + Gap) * (p - 1)).height(RowH).width(TimeW),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$p", style = T.label, color = c.text)
                            Text(periodStart(p), style = T.small.copy(fontSize = T.small.fontSize * 0.82f), color = c.sub, maxLines = 1)
                        }
                    }
                }
            }
            for (i in 0 until 5) {
                val date = monday.plusDays(i.toLong())
                val isToday = date.toEpochDay() == todayEpoch
                val lessons = lessonsFor(date)
                val holiday = holidayFor(date)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) c.accentSoft else Color.Transparent),
                ) {
                    Column(Modifier.fillMaxWidth().height(HeaderH), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(Fmt.dayShort(date), style = T.smallBold, color = c.sub)
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${date.dayOfMonth}", style = T.smallBold, color = if (isToday) c.accentInk else c.text)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(bodyH)) {
                        if (holiday != null && !editMode) {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(c.line),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Ferien", style = T.smallBold, color = c.sub, textAlign = TextAlign.Center)
                            }
                        } else {
                            if (editMode) {
                                val covered = lessons.flatMap { it.firstPeriod..it.lastPeriod }.toSet()
                                for (p in 1..maxPeriod) {
                                    if (p !in covered) {
                                        Box(
                                            Modifier
                                                .offset(y = (RowH + Gap) * (p - 1))
                                                .fillMaxWidth()
                                                .height(RowH)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.5.dp, c.line, RoundedCornerShape(12.dp))
                                                .clickable { onEmpty(date.dayOfWeek.value, p) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Ic.Plus, "Stunde hinzufügen", tint = c.sub, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            lessons.forEach { l ->
                                LessonCell(l, editMode, hasHa(l), hasExam(l)) { onLesson(l) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCell(l: Lesson, editMode: Boolean, ha: Boolean, exam: Boolean, onClick: () -> Unit) {
    val c = colors
    val color = subjectColor(l.subject?.color)
    val len = l.lastPeriod - l.firstPeriod + 1
    val tinted = color.copy(alpha = c.tint)
    Box(
        Modifier
            .offset(y = (RowH + Gap) * (l.firstPeriod - 1))
            .fillMaxWidth()
            .height(RowH * len + Gap * (len - 1))
            .clip(RoundedCornerShape(12.dp))
            .then(if (l.cancelled) Modifier.stripes(tinted, 6.dp) else Modifier.background(tinted))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                l.subject?.label ?: "?",
                style = T.label.copy(textDecoration = if (l.cancelled) TextDecoration.LineThrough else null),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Column {
                if (editMode && l.slot.weekType != WeekType.ALL) {
                    Text("nur ${l.slot.weekType}", style = T.smallBold.copy(fontSize = T.small.fontSize * 0.8f), color = c.accent, maxLines = 1)
                }
                Text(l.room, style = T.smallBold.copy(fontSize = T.small.fontSize * 0.82f), color = c.sub, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        if (!editMode && (ha || exam)) {
            Icon(
                if (exam) Ic.Alert else Ic.Bookmark,
                if (exam) "Klausur" else "Hausaufgabe",
                tint = if (exam) c.warn else c.accent,
                modifier = Modifier.align(Alignment.TopEnd).size(13.dp),
            )
        }
    }
}
