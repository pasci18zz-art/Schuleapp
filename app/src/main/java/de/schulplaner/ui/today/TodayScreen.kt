package de.schulplaner.ui.today

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.sp
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
import de.schulplaner.ui.study.StudyTile
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
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current
    val onAddTask: (() -> Unit)? = if (readOnly) null else ({ editTask = TaskDefaults.quick(LocalDateTime.now(), schedule) to true })
    val onLessonClick: (Lesson) -> Unit = { l ->
        if (!readOnly) editTask = TaskDefaults.forSubject(l.slot.subjectId, l.date, schedule) to true
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
            // Sonne bzw. Mond oben rechts in der Ecke – nur gezeichnet, nimmt keinen Platz weg.
            Box(Modifier.appear(0)) {
                SkyArt(now.hour, Modifier.matchParentSize())
                ScreenHeader(
                    title = "Heute",
                    overline = Fmt.full(today),
                    chip = state.weekLabel,
                )
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
                // Spalten oben bündig; jede Kachel so hoch wie ihr Inhalt.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).appear(2), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NowTile(state)
                        LessonsTile(state, schedule, onLessonClick)
                        if (!readOnly) MonsterTile()
                        AppShortcutsRow()
                    }
                    Column(Modifier.weight(1f).appear(3), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TasksTile(state, max = 8, onOpen = { nav.tab(Tabs.TASKS) }, onEdit = { editTask = it to false }, onAdd = onAddTask, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f).appear(4), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StudyTile(onOpen = { nav.open(Routes.STUDY) })
                        ExamsListTile(state, onOpen = { nav.open(Routes.exam(it.id)) })
                        GradesTile(onOpen = { nav.tab(Tabs.GRADES) })
                    }
                }
            } else {
                NowTile(state, Modifier.appear(2))
                // Handy: Aufgaben und Klausur direkt unter der Jetzt-Karte, dann der Stundenplan.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).appear(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        TasksTile(state, max = 2, compact = true, onOpen = { nav.tab(Tabs.TASKS) }, onEdit = { editTask = it to false }, onAdd = onAddTask)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        ExamTile(state, onOpen = { nav.open(Routes.exam(it.id)) }, onEmpty = { nav.tab(Tabs.EXAMS) })
                    }
                }
                Box(Modifier.appear(4)) { LessonsTile(state, schedule, onLessonClick) }
                StudyTile(Modifier.appear(5), onOpen = { nav.open(Routes.STUDY) })
                // Im Ansichtsmodus ausgeblendet.
                if (!readOnly) MonsterTile(Modifier.appear(5))
                AppShortcutsRow(Modifier.appear(6))
                Box(Modifier.appear(7)) { GradesTile(onOpen = { nav.tab(Tabs.GRADES) }) }
            }
        }
    }

    editTask?.let { (task, isNew) ->
        if (readOnly) {
            de.schulplaner.ui.tasks.TaskViewSheet(task, onDismiss = { editTask = null })
        } else {
            TaskEditSheet(task, isNew, onDismiss = { editTask = null })
        }
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

/**
 * Verspielte Sonne (tagsüber, mit Gesicht, Strahlen und vorbeiziehender Wolke) bzw. schlafender Mond
 * mit funkelnden Sternen (abends) – oben rechts, mit weichem Leuchten, das in den Hintergrund übergeht.
 */
@Composable
private fun SkyArt(hour: Int, modifier: Modifier = Modifier) {
    val day = hour in 6..17
    val t = rememberInfiniteTransition(label = "sky")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "pulse")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "twinkle")
    Canvas(modifier) {
        val d = density
        val cx = size.width - 46 * d
        val cy = 34 * d
        if (day) {
            // Warmes Leuchten, das sanft ausläuft.
            drawCircle(Brush.radialGradient(listOf(Color(0x80FFD36E), Color(0x00FFD36E)), Offset(cx, cy), 120 * d * pulse), 120 * d * pulse, Offset(cx, cy))
            rotate(spin, Offset(cx, cy)) {
                for (i in 0 until 12) {
                    val ang = Math.toRadians(i * 30.0)
                    val r1 = 31 * d
                    val r2 = (if (i % 2 == 0) 46 else 39) * d
                    drawLine(
                        Color(0xFFFFC23D),
                        Offset(cx + (cos(ang) * r1).toFloat(), cy + (sin(ang) * r1).toFloat()),
                        Offset(cx + (cos(ang) * r2).toFloat(), cy + (sin(ang) * r2).toFloat()),
                        strokeWidth = 4.5f * d,
                        cap = StrokeCap.Round,
                    )
                }
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A3), Color(0xFFFFB73A)), Offset(cx - 6 * d, cy - 6 * d), 30 * d), 26 * d, Offset(cx, cy))
            // Gesicht.
            val face = Color(0xFF8A4B00)
            drawArc(face, 200f, 140f, false, Offset(cx - 12 * d, cy - 7 * d), androidx.compose.ui.geometry.Size(8 * d, 7 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * d, cap = StrokeCap.Round))
            drawArc(face, 200f, 140f, false, Offset(cx + 4 * d, cy - 7 * d), androidx.compose.ui.geometry.Size(8 * d, 7 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * d, cap = StrokeCap.Round))
            drawArc(face, 20f, 140f, false, Offset(cx - 8 * d, cy - 1 * d), androidx.compose.ui.geometry.Size(16 * d, 11 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f * d, cap = StrokeCap.Round))
            drawCircle(Color(0x66FF8A65), 3.2f * d, Offset(cx - 14 * d, cy + 4 * d))
            drawCircle(Color(0x66FF8A65), 3.2f * d, Offset(cx + 14 * d, cy + 4 * d))
            // Wolke, die langsam vorbeizieht.
            val wx = cx - 70 * d + drift * 50 * d
            val wy = cy + 26 * d
            val cloud = Color.White.copy(alpha = 0.92f)
            drawCircle(cloud, 9 * d, Offset(wx, wy))
            drawCircle(cloud, 12 * d, Offset(wx + 12 * d, wy - 5 * d))
            drawCircle(cloud, 8 * d, Offset(wx + 25 * d, wy))
            drawRoundRect(cloud, Offset(wx - 4 * d, wy - 2 * d), androidx.compose.ui.geometry.Size(33 * d, 10 * d), androidx.compose.ui.geometry.CornerRadius(5 * d))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0x668E99FF), Color(0x008E99FF)), Offset(cx, cy), 120 * d * pulse), 120 * d * pulse, Offset(cx, cy))
            // Sichelmond mit schlafendem Gesicht.
            val moon = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), 27 * d))
            }
            val cut = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(cx + 13 * d, cy - 9 * d), 23 * d))
            }
            val crescent = androidx.compose.ui.graphics.Path().apply {
                op(moon, cut, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            rotate(-12f + drift * 8f, Offset(cx, cy)) {
                drawPath(crescent, Brush.radialGradient(listOf(Color(0xFFFFF6CF), Color(0xFFFFD873)), Offset(cx - 8 * d, cy), 34 * d))
                drawArc(
                    Color(0xFF8A6A1E), 20f, 140f, false, Offset(cx - 18 * d, cy - 4 * d), androidx.compose.ui.geometry.Size(9 * d, 6 * d),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f * d, cap = StrokeCap.Round),
                )
                drawCircle(Color(0x55FF8A65), 3f * d, Offset(cx - 16 * d, cy + 8 * d))
            }
            // Funkelnde Sterne (vierzackig).
            val stars = listOf(Triple(-62f, -8f, 5f), Triple(-38f, 30f, 3.5f), Triple(-80f, 22f, 3f), Triple(18f, 34f, 4f), Triple(-20f, -20f, 3f), Triple(-100f, -2f, 2.5f))
            stars.forEachIndexed { i, (dx, dy, r) ->
                val a = if (i % 2 == 0) twinkle else 1f - twinkle
                val p = Offset(cx + dx * d, cy + dy * d)
                val rr = r * d * (0.7f + a * 0.5f)
                val star = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p.x, p.y - rr * 2)
                    quadraticBezierTo(p.x, p.y, p.x + rr * 2, p.y)
                    quadraticBezierTo(p.x, p.y, p.x, p.y + rr * 2)
                    quadraticBezierTo(p.x, p.y, p.x - rr * 2, p.y)
                    quadraticBezierTo(p.x, p.y, p.x, p.y - rr * 2)
                    close()
                }
                drawPath(star, Color(0xFFFFF3C4).copy(alpha = 0.35f + 0.65f * a))
            }
        }
    }
}

/** Tagesplan: von rechts nach links wischen = nächster Schultag (Wochenenden/Ferien werden übersprungen). */
@Composable
private fun LessonsTile(s: TodayState, schedule: de.schulplaner.domain.ScheduleData, onLessonClick: (Lesson) -> Unit) {
    val c = colors
    if (schedule.slots.isEmpty()) return
    val center = 500
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = center) { 1000 }
    val scope = rememberCoroutineScope()
    val shownDay = Schedule.schoolDayAt(s.day, pager.currentPage - center, schedule)
    val todayDate = s.now.toLocalDate()
    val title = when (shownDay) {
        todayDate -> "Stunden heute"
        todayDate.plusDays(1) -> "Morgen"
        else -> Fmt.dayMonth(shownDay)
    }
    GlassCard(
        Modifier.animateContentSize(),
        padding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.section, color = c.text, modifier = Modifier.weight(1f))
            if (pager.currentPage != center) {
                Text(
                    "Heute",
                    style = T.label,
                    color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { scope.launch { pager.animateScrollToPage(center) } }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Icon(
                Ic.ChevronLeft, "Vorheriger Tag", tint = c.sub,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }.padding(6.dp),
            )
            Icon(
                Ic.ChevronRight, "Nächster Tag", tint = c.sub,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }.padding(6.dp),
            )
        }
        androidx.compose.foundation.pager.HorizontalPager(
            pager,
            Modifier.fillMaxWidth(),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
            key = { it },
        ) { page ->
            val day = Schedule.schoolDayAt(s.day, page - center, schedule)
            val lessons = if (day == s.day) s.lessons else Schedule.lessonsOn(day, schedule)
            val isToday = day == todayDate
            Column {
                if (lessons.isEmpty()) {
                    Text("Kein Unterricht", style = T.small, color = c.sub, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp))
                }
                lessons.forEach { l ->
                    val past = isToday && l.endDateTime?.let { !s.now.isBefore(it) } == true
                    val now = isToday && s.current?.slot?.id == l.slot.id
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
                        Text(l.periodLabel, style = T.smallBold, color = c.sub, maxLines = 1, softWrap = false, modifier = Modifier.width(50.dp))
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
    }
}

@Composable
private fun TasksTile(
    s: TodayState,
    max: Int,
    compact: Boolean = false,
    onOpen: () -> Unit,
    onEdit: (Task) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
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
    GlassCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Tasks, "Aufgaben", tint = c.accent, modifier = Modifier.size(22.dp))
                if (!compact) Text("Aufgaben", style = T.section, color = c.text, modifier = Modifier.padding(start = 10.dp))
                Box(Modifier.weight(1f))
                Text("${s.openTasks.size}", style = T.title.copy(fontSize = T.section.fontSize * 1.6f), color = if (s.openTasks.isEmpty()) c.text else urgent)
            }
            if (onAdd != null) {
                Box(Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(c.accentSoft).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Icon(Ic.Plus, "Aufgabe hinzufügen", tint = c.accent, modifier = Modifier.size(16.dp))
                }
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
