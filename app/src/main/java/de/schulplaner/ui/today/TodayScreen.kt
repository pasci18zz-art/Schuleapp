package de.schulplaner.ui.today

import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import de.schulplaner.ui.components.HeroCard
import de.schulplaner.ui.components.appear
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Exam
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.Subject
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.GradeCalc
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.TermStats
import de.schulplaner.domain.label
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.Tabs
import de.schulplaner.data.Holiday
import de.schulplaner.data.HolidayKind
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.EmptyHint
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.ProgressBar
import de.schulplaner.ui.components.ProgressRing
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.tasks.TaskDefaults
import de.schulplaner.ui.tasks.TaskLine
import de.schulplaner.ui.tasks.kindOf
import de.schulplaner.ui.tasks.stepPercent
import de.schulplaner.data.TaskKind
import de.schulplaner.ui.components.HolidayLook
import de.schulplaner.ui.components.LessonMark
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.SubjectIcons
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.examUrgency
import de.schulplaner.ui.components.taskUrgency
import de.schulplaner.ui.components.urgencyColor
import de.schulplaner.ui.tasks.TaskEditSheet
import de.schulplaner.ui.theme.T
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private class TodayState(
    val now: LocalDateTime,
    val day: LocalDate,
    val lessons: List<Lesson>,
    val current: Lesson?,
    val next: Lesson?,
    val weekLabel: String?,
    val holidayName: String?,
    val holiday: Holiday?,
    val openTasks: List<Task>,
    val doneVisible: List<Task>,
    val tasks: List<Task>,
    val exams: List<Exam>,
    val topics: List<ExamTopic>,
    val subjects: Map<String, Subject>,
    val kinds: List<TaskKind>,
)

@Composable
fun TodayScreen() {
    val nav = LocalNavigator.current
    val repo = LocalContext.current.app.repo
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val exams by repo.exams.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val kinds by repo.taskKinds.collectAsStateWithLifecycle()
    val now = rememberNow()
    val today = now.toLocalDate()

    val day = Schedule.displayDay(now, schedule)
    val lessons = Schedule.lessonsOn(day, schedule)
    val isToday = day == today
    val current = if (isToday) Schedule.currentLesson(now, schedule) else null
    val next = if (isToday) Schedule.nextLessonToday(now, schedule) else lessons.firstOrNull { !it.cancelled }
    val todayEpoch = today.toEpochDay()
    val openTasks = tasks
        .filter { !it.done }
        .sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    // Erledigte bleiben ausgegraut sichtbar, bis ihr Fälligkeitstag vorbei ist.
    val doneVisible = tasks.filter { it.done && (it.dueDate ?: todayEpoch) >= todayEpoch }
        .sortedBy { it.dueDate ?: Long.MAX_VALUE }
    val upcomingExams = exams.filter { it.date >= todayEpoch }.sortedBy { it.date }
    val wt = Schedule.weekTypeOf(day, schedule)

    val state = TodayState(
        now = now, day = day, lessons = lessons, current = current, next = next,
        weekLabel = wt?.let { "$it-Woche" },
        holidayName = Schedule.holidayOn(today, schedule)?.name,
        holiday = Schedule.holidayOn(today, schedule),
        openTasks = openTasks, doneVisible = doneVisible, tasks = tasks, exams = upcomingExams, topics = topics,
        subjects = schedule.subjects,
        kinds = kinds,
    )

    var editTask by remember { mutableStateOf<Pair<Task, Boolean>?>(null) }
    val onAddTask: () -> Unit = { editTask = TaskDefaults.quick(LocalDateTime.now(), schedule) to true }
    val onLessonClick: (Lesson) -> Unit = { l ->
        editTask = TaskDefaults.forSubject(l.slot.subjectId, l.date, schedule) to true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenPadding(if (wide) 28 else 16)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = "Heute",
                overline = Fmt.full(today),
                chip = state.weekLabel,
                modifier = Modifier.appear(0),
            ) {
                SkyBadge(now.hour)
            }

            Box(Modifier.appear(1)) { FerienTile(schedule.holidays, today) }

            if (schedule.slots.isEmpty()) {
                GlassCard {
                    EmptyHint(
                        Ic.Calendar,
                        "Trag zuerst deine Fächer und deinen Stundenplan ein.",
                        action = "Loslegen",
                        onAction = { if (schedule.subjects.isEmpty()) nav.open(Routes.SET_SUBJECTS) else nav.tab(Tabs.TIMETABLE) },
                    )
                }
            }

            if (wide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).appear(2), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NowTile(state)
                        LessonsTile(state, onLessonClick)
                        AppShortcutsRow()
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().appear(3), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TasksTile(state, max = 8, onOpen = { nav.tab(Tabs.TASKS) }, onEdit = { editTask = it to false }, onAdd = onAddTask)
                    }
                    Column(Modifier.weight(1f).appear(4), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExamsListTile(state, onOpen = { nav.open(Routes.exam(it.id)) })
                        GradesTile(onOpen = { nav.tab(Tabs.GRADES) })
                    }
                }
            } else {
                NowTile(state, Modifier.appear(2))
                Box(Modifier.appear(3)) { LessonsTile(state, onLessonClick) }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).appear(4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        TasksTile(state, max = 2, compact = true, onOpen = { nav.tab(Tabs.TASKS) }, onEdit = { editTask = it to false }, onAdd = onAddTask)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        ExamTile(state, onOpen = { nav.open(Routes.exam(it.id)) }, onEmpty = { nav.tab(Tabs.EXAMS) })
                    }
                }
                AppShortcutsRow(Modifier.appear(5))
                Box(Modifier.appear(6)) { GradesTile(onOpen = { nav.tab(Tabs.GRADES) }) }
            }
        }
    }

    editTask?.let { (task, isNew) ->
        TaskEditSheet(task, isNew, onDismiss = { editTask = null })
    }
}

@Composable
private fun NowTile(s: TodayState, modifier: Modifier = Modifier) {
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.78f)
    val lesson = s.current ?: s.next
    HeroCard(modifier.fillMaxWidth()) {
        when {
            s.holidayName != null && s.lessons.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(s.holiday?.let { HolidayLook.icon(it) } ?: Ic.Palm, null, tint = white, modifier = Modifier.size(30.dp))
                    Column {
                        Text("FERIEN", style = T.overline, color = soft)
                        Text(s.holidayName, style = T.title.copy(fontSize = T.section.fontSize * 1.3f), color = white)
                    }
                }
            }
            lesson == null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Ic.Sun, null, tint = white, modifier = Modifier.size(30.dp))
                    Column {
                        Text("GESCHAFFT", style = T.overline, color = soft)
                        Text("Heute kein Unterricht mehr", style = T.body, color = white)
                    }
                }
            }
            else -> {
                val isNow = s.current != null
                val isToday = s.day == s.now.toLocalDate()
                val label = when {
                    isNow -> "Jetzt · ${lesson.periodLabel} Std"
                    isToday -> "Als Nächstes · ${lesson.periodLabel} Std"
                    else -> "${Fmt.dayShort(s.day)} · ${lesson.periodLabel} Std"
                }
                val right = when {
                    isNow && lesson.endDateTime != null -> "noch ${Duration.between(s.now, lesson.endDateTime).toMinutes() + 1} min"
                    isToday && lesson.startDateTime != null -> {
                        val m = Duration.between(s.now, lesson.startDateTime).toMinutes() + 1
                        if (m <= 60) "in $m min" else "um ${lesson.start?.let { Fmt.time(it) }}"
                    }
                    else -> lesson.start?.let { Fmt.time(it) } ?: ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label.uppercase(), style = T.overline, color = soft, modifier = Modifier.weight(1f))
                    Text(
                        right,
                        style = T.smallBold,
                        color = white,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(subjectColor(lesson.subject?.color)).align(Alignment.TopEnd).padding(2.dp))
                        val icon = SubjectIcons.of(lesson.subject)
                        if (icon != null) {
                            Icon(icon, lesson.subject?.name, tint = white, modifier = Modifier.size(26.dp))
                        } else {
                            Text(lesson.subject?.label ?: "?", style = T.label.copy(fontSize = T.body.fontSize * 1.05f), color = white, maxLines = 1)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(lesson.subject?.name ?: "?", style = T.title.copy(fontSize = T.section.fontSize * 1.3f), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 3.dp)) {
                            if (lesson.room.isNotBlank()) IconText(Ic.Pin, "R ${lesson.room}", soft)
                            val teacher = lesson.subject?.teacher.orEmpty()
                            if (teacher.isNotBlank()) IconText(Ic.User, teacher, soft)
                        }
                    }
                }
                if (lesson.note.isNotBlank()) {
                    Text(lesson.note, style = T.smallBold, color = white, modifier = Modifier.padding(top = 8.dp))
                }
                if (isNow) {
                    val total = Duration.between(lesson.startDateTime, lesson.endDateTime).seconds.toFloat()
                    val done = Duration.between(lesson.startDateTime, s.now).seconds.toFloat()
                    val p by animateFloatAsState(if (total > 0) (done / total).coerceIn(0f, 1f) else 0f, tween(800), label = "now")
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(p).height(6.dp).clip(RoundedCornerShape(3.dp)).background(white))
                    }
                }
            }
        }
    }
}

/** Kleines lebendiges Symbol oben rechts: Sonne am Tag, Mond mit Sternen am Abend. */
@Composable
private fun SkyBadge(hour: Int) {
    val day = hour in 6..17
    val t = rememberInfiniteTransition(label = "sky")
    val spin = t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val twinkle = t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "twinkle")
    val bob = t.animateFloat(-2f, 2f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "bob")
    val brush = if (day) {
        Brush.linearGradient(listOf(Color(0xFFFFD36E), Color(0xFFFF9F43)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF4F5BD5), Color(0xFF2B2F6B)))
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            if (day) {
                rotate(spin.value) {
                    for (i in 0 until 8) {
                        val ang = Math.toRadians(i * 45.0)
                        val r1 = size.minDimension * 0.27f
                        val r2 = size.minDimension * 0.37f
                        drawLine(
                            Color.White,
                            Offset(cx + (cos(ang) * r1).toFloat(), cy + (sin(ang) * r1).toFloat()),
                            Offset(cx + (cos(ang) * r2).toFloat(), cy + (sin(ang) * r2).toFloat()),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                drawCircle(Color.White, size.minDimension * 0.17f, Offset(cx, cy))
            } else {
                val y = cy + bob.value.dp.toPx() * 0.5f
                drawCircle(Color(0xFFFFF3C4), size.minDimension * 0.2f, Offset(cx - 2.dp.toPx(), y))
                drawCircle(Color(0xFF3A4090), size.minDimension * 0.18f, Offset(cx + 3.dp.toPx(), y - 3.dp.toPx()))
                drawCircle(Color.White.copy(alpha = twinkle.value), 1.4.dp.toPx(), Offset(size.width * 0.75f, size.height * 0.3f))
                drawCircle(Color.White.copy(alpha = 1.3f - twinkle.value), 1.1.dp.toPx(), Offset(size.width * 0.28f, size.height * 0.25f))
                drawCircle(Color.White.copy(alpha = twinkle.value * 0.8f), 1.dp.toPx(), Offset(size.width * 0.7f, size.height * 0.75f))
            }
        }
    }
}

@Composable
private fun LessonsTile(s: TodayState, onLessonClick: (Lesson) -> Unit) {
    val c = colors
    if (s.lessons.isEmpty()) return
    val isToday = s.day == s.now.toLocalDate()
    val title = when {
        isToday -> "Stunden"
        s.day == s.now.toLocalDate().plusDays(1) -> "Morgen"
        else -> Fmt.dayMonth(s.day)
    }
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)) {
        Text(title, style = T.section, color = c.text, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        s.lessons.forEach { l ->
            val past = isToday && l.endDateTime?.let { !s.now.isBefore(it) } == true
            val now = s.current?.slot?.id == l.slot.id
            val dayEpoch = l.date.toEpochDay()
            val ha = s.tasks.any { !it.done && it.dueDate == dayEpoch && it.subjectId == l.slot.subjectId }
            val exam = s.exams.any { it.date == dayEpoch && it.subjectId == l.slot.subjectId }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (now) c.accentSoft else Color.Transparent)
                    .clickable { onLessonClick(l) }
                    .alpha(if (past) 0.5f else 1f)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(l.periodLabel, style = T.smallBold, color = c.sub, modifier = Modifier.width(36.dp))
                LessonMark(subjectColor(l.subject?.color), l.length)
                Column(Modifier.weight(1f)) {
                    Text(
                        l.subject?.name ?: "?",
                        style = T.body.copy(textDecoration = if (l.cancelled) TextDecoration.LineThrough else null),
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val time = listOfNotNull(l.start?.let { Fmt.time(it) }, l.end?.let { Fmt.time(it) }).joinToString(" – ")
                    Text(listOf(time, l.room.takeIf { it.isNotBlank() }?.let { "R $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), style = T.small, color = c.sub)
                }
                if (exam) Pill("Klausur", c.warnSoft, c.warn)
                if (ha) Pill("HA")
                if (l.cancelled) Pill("Entfall", c.warnSoft, c.warn)
            }
        }
    }
}

@Composable
private fun TasksTile(s: TodayState, max: Int, compact: Boolean = false, onOpen: () -> Unit, onEdit: (Task) -> Unit, onAdd: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val today = s.now.toLocalDate()
    val relevant = s.openTasks.filter { it.dueDate == null || it.dueDate <= s.day.toEpochDay() + 1 }
    val open = (if (compact) relevant else s.openTasks).take(max)
    val done = s.doneVisible.filter { !compact || it.dueDate == null || it.dueDate <= s.day.toEpochDay() + 1 }
        .take((max - open.size).coerceAtLeast(0))
    // Je mehr offen ist, desto wärmer/röter wird die Zahl.
    val urgent = urgencyColor(taskUrgency(s.openTasks.size))
    GlassCard(Modifier.fillMaxSize(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Tasks, "Aufgaben", tint = c.accent, modifier = Modifier.size(22.dp))
                if (!compact) Text("Aufgaben", style = T.section, color = c.text, modifier = Modifier.padding(start = 10.dp))
                Box(Modifier.weight(1f))
                Text("${s.openTasks.size}", style = T.title.copy(fontSize = T.section.fontSize * 1.6f), color = if (s.openTasks.isEmpty()) c.text else urgent)
            }
            Box(Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(c.accentSoft).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Ic.Plus, "Aufgabe hinzufügen", tint = c.accent, modifier = Modifier.size(16.dp))
            }
        }
        if (open.isEmpty() && done.isEmpty()) {
            Text("Alles erledigt", style = T.small, color = c.sub, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
        }
        (open + done).forEach { t ->
            TaskLine(
                task = t,
                subject = t.subjectId?.let { s.subjects[it] },
                kind = kindOf(t.type, s.kinds),
                percent = stepPercent(t.id, s.topics),
                today = today,
                onToggle = {
                    repo.setTaskDone(t, !t.done)
                    if (!t.done) undo.show("Erledigt") { repo.setTaskDone(t, false) }
                },
                onClick = { onEdit(t) },
                compact = compact,
            )
        }
    }
}

/** Countdown bis zu den nächsten Ferien (bzw. "noch X Tage Ferien"). */
@Composable
private fun FerienTile(holidays: List<Holiday>, today: LocalDate) {
    val c = colors
    val t = today.toEpochDay()
    val ferien = holidays.filter { it.kind == HolidayKind.FERIEN || it.name.contains("ferien", ignoreCase = true) }
    val current = ferien.firstOrNull { t in it.startDate..it.endDate }
    val next = ferien.filter { it.startDate > t }.minByOrNull { it.startDate }
    val h = current ?: next ?: return
    val days = if (current != null) h.endDate - t + 1 else h.startDate - t
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tint = HolidayLook.color(h)
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = if (c.isDark) 0.22f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HolidayLook.icon(h), null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(h.name, style = T.body, color = c.text)
                Text(
                    if (current != null) "bis ${Fmt.dayMonth(LocalDate.ofEpochDay(h.endDate))}" else "ab ${Fmt.dayMonth(LocalDate.ofEpochDay(h.startDate))}",
                    style = T.small,
                    color = c.sub,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$days", style = T.title.copy(fontSize = T.section.fontSize * 1.4f), color = c.text)
                Text(if (current != null) "Tage frei" else if (days == 1L) "Tag" else "Tage", style = T.small, color = c.sub)
            }
        }
    }
}

@Composable
private fun ExamTile(s: TodayState, onOpen: (Exam) -> Unit, onEmpty: () -> Unit) {
    val c = colors
    val exam = s.exams.firstOrNull()
    if (exam == null) {
        GlassCard(Modifier.fillMaxSize(), onClick = onEmpty) {
            Icon(Ic.Exam, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("Keine Klausuren in Sicht", style = T.label, color = c.sub, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }
    val subject = s.subjects[exam.subjectId]
    val ts = s.topics.filter { it.examId == exam.id }
    val progress = if (ts.isEmpty()) 0f else ts.count { it.done }.toFloat() / ts.size
    val days = Fmt.daysUntil(s.now.toLocalDate(), LocalDate.ofEpochDay(exam.date))
    GlassCard(Modifier.fillMaxSize(), onClick = { onOpen(exam) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Pen, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f))
            Text(Fmt.dayMonth(LocalDate.ofEpochDay(exam.date)), style = T.smallBold, color = c.sub)
        }
        // Wie auf dem Tablet: Tage bis zur Klausur, je näher desto röter.
        val urgent = urgencyColor(examUrgency(days))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(urgent.copy(alpha = 0.18f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("$days", style = T.section.copy(fontSize = T.section.fontSize * 1.2f), color = urgent)
                Text(if (days == 1L) "Tag" else "Tage", style = T.small.copy(fontSize = T.small.fontSize * 0.85f), color = c.sub)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SubjectIcon(subject, 15.dp)
                    Text(subject?.label ?: "?", style = T.section.copy(fontSize = T.section.fontSize * 1.05f), color = c.text, maxLines = 1)
                }
                Text(if (ts.isEmpty()) Fmt.inDays(days) else "${(progress * 100).toInt()} % gelernt", style = T.small, color = c.sub, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExamsListTile(s: TodayState, onOpen: (Exam) -> Unit) {
    val c = colors
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text("Klausuren", style = T.section, color = c.text)
        if (s.exams.isEmpty()) Text("Keine Klausuren in Sicht", style = T.small, color = c.sub, modifier = Modifier.padding(top = 10.dp))
        s.exams.take(4).forEach { e ->
            val subject = s.subjects[e.subjectId]
            val ts = s.topics.filter { it.examId == e.id }
            val progress = if (ts.isEmpty()) 0f else ts.count { it.done }.toFloat() / ts.size
            val days = Fmt.daysUntil(s.now.toLocalDate(), LocalDate.ofEpochDay(e.date))
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(e) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val urgent = urgencyColor(examUrgency(days))
                Column(
                    Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(urgent.copy(alpha = 0.18f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("$days", style = T.section.copy(fontSize = T.section.fontSize * 1.1f), color = urgent)
                    Text(if (days == 1L) "Tag" else "Tage", style = T.small.copy(fontSize = T.small.fontSize * 0.85f), color = c.sub)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectIcon(subject, 16.dp)
                        Text(subject?.name ?: "?", style = T.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(Fmt.dayDate(LocalDate.ofEpochDay(e.date)), style = T.small, color = c.sub)
                    }
                    ProgressBar(progress, subjectColor(subject?.color))
                }
            }
        }
    }
}

@Composable
private fun GradesTile(onOpen: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val terms by repo.terms.collectAsStateWithLifecycle()
    val categories by repo.categories.collectAsStateWithLifecycle()
    val grades by repo.grades.collectAsStateWithLifecycle()
    val subjectTerms by repo.subjectTerms.collectAsStateWithLifecycle()
    val term = repo.currentTerm(terms) ?: return
    val results = TermStats.results(term.id, subjects, categories, grades, subjectTerms)
    val avg = TermStats.average(results) ?: return
    val best = results.filter { it.value != null }.sortedByDescending { it.value }.take(3)
    GlassCard(onClick = onOpen, padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Grades, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f))
            Text(term.name, style = T.label, color = c.sub)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(GradeCalc.format(avg), style = T.bigNumber, color = c.text)
            Text("P Ø", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text("${results.count { it.value != null }} Fächer", style = T.small, color = c.sub)
        if (best.isNotEmpty()) {
            Text("BESTE FÄCHER", style = T.overline, color = c.accent, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            best.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(c.line).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${i + 1}", style = T.label, color = c.sub)
                    SubjectIcon(r.subject, 16.dp)
                    Text(r.subject.name, style = T.label.copy(fontSize = T.body.fontSize * 0.93f), color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(GradeCalc.format(r.value!!), style = T.label.copy(fontSize = T.body.fontSize * 0.93f), color = c.text)
                }
            }
        }
    }
}

@Composable
fun SubjectBadge(subject: Subject?, sizeDp: Int) {
    val c = colors
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp * 0.34f).dp)).background(subjectColor(subject?.color).copy(alpha = c.tint)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = SubjectIcons.of(subject)
        if (icon != null) {
            Icon(icon, subject?.name, tint = subjectColor(subject?.color), modifier = Modifier.size((sizeDp * 0.5f).dp))
        } else {
            val label = subject?.shortName?.ifBlank { null } ?: subject?.name?.take(2) ?: "?"
            Text(label, style = T.label.copy(fontSize = T.body.fontSize * (if (label.length > 3) 0.8f else 1.05f)), color = c.text, maxLines = 1)
        }
    }
}

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = T.small.copy(fontSize = T.label.fontSize), color = color, maxLines = 1)
    }
}

@Suppress("unused")
@Composable
private fun AddButton(onClick: () -> Unit) = SoftIconButton(Ic.Plus, "Hinzufügen", onClick)
