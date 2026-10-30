package de.schulplaner.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import de.schulplaner.data.Exam
import de.schulplaner.data.HolidayKind
import de.schulplaner.data.LessonSlot
import de.schulplaner.data.Subject
import de.schulplaner.data.Task
import de.schulplaner.data.WeekType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.ScheduleData
import de.schulplaner.domain.label
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.HolidayLook
import de.schulplaner.ui.components.KindIcons
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.tasks.kindOf
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.tasks.TaskDefaults
import de.schulplaner.ui.tasks.TaskEditSheet
import de.schulplaner.ui.tasks.TaskViewSheet
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val RowH = 58.dp
private val Gap = 4.dp
private val HeaderH = 42.dp
private val TimeW = 32.dp
private const val PAGES = 2001
private const val CENTER = 1000
private val monthNames = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

@Composable
fun TimetableScreen() {
    val c = colors
    val nav = LocalNavigator.current
    val repo = LocalContext.current.app.repo
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val exams by repo.exams.collectAsStateWithLifecycle()
    val today = rememberNow().toLocalDate()
    val scope = rememberCoroutineScope()

    var monthMode by rememberSaveable { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var openLesson by remember { mutableStateOf<Lesson?>(null) }
    var editSlot by remember { mutableStateOf<Pair<LessonSlot, Boolean>?>(null) }
    var newTask by remember { mutableStateOf<Task?>(null) }
    var viewTask by remember { mutableStateOf<Task?>(null) }

    val weekPager = rememberPagerState(initialPage = CENTER) { PAGES }
    val monthPager = rememberPagerState(initialPage = CENTER) { PAGES }
    val baseMonday = Schedule.mondayOf(today)
    val baseMonth = YearMonth.from(today)
    val monday = baseMonday.plusWeeks((weekPager.currentPage - CENTER).toLong())
    val month = baseMonth.plusMonths((monthPager.currentPage - CENTER).toLong())

    val openTasks = tasks.filter { !it.done }
    val haFor: (Lesson) -> List<Task> = { l -> openTasks.filter { it.dueDate == l.date.toEpochDay() && it.subjectId == l.slot.subjectId } }
    val examFor: (Lesson) -> Boolean = { l -> exams.any { it.date == l.date.toEpochDay() && it.subjectId == l.slot.subjectId } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding(if (wide) 28 else 16)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (monthMode) {
                ScreenHeader(title = monthNames[month.monthValue - 1], overline = "${month.year}") {
                    GlassIconButton(Ic.ChevronLeft, "Vorheriger Monat", { scope.launch { monthPager.animateScrollToPage(monthPager.currentPage - 1) } })
                    GlassIconButton(Ic.ChevronRight, "Nächster Monat", { scope.launch { monthPager.animateScrollToPage(monthPager.currentPage + 1) } })
                }
            } else {
                ScreenHeader(
                    title = Fmt.weekRange(monday),
                    overline = if (editMode) "Bearbeiten" else "KW ${Fmt.weekOfYear(monday)}",
                    chip = Schedule.weekTypeOf(monday, schedule)?.let { "$it-Woche" },
                ) {
                    GlassIconButton(Ic.ChevronLeft, "Vorherige Woche", { scope.launch { weekPager.animateScrollToPage(weekPager.currentPage - 1) } })
                    GlassIconButton(Ic.ChevronRight, "Nächste Woche", { scope.launch { weekPager.animateScrollToPage(weekPager.currentPage + 1) } })
                    GlassIconButton(if (editMode) Ic.Check else Ic.Pencil, if (editMode) "Fertig" else "Stundenplan bearbeiten", { editMode = !editMode }, filled = editMode)
                }
            }

            Segmented(listOf(false to "Woche", true to "Monat"), monthMode, {
                monthMode = it
                if (it) editMode = false
            }, Modifier.then(if (wide) Modifier.width(320.dp) else Modifier))

            if (!monthMode) {
                if (schedule.subjects.isEmpty()) {
                    GlassCard(onClick = { nav.open(Routes.SET_SUBJECTS) }) {
                        Text("Lege zuerst deine Fächer an (antippen).", style = T.body, color = c.sub)
                    }
                } else if (schedule.slots.isEmpty() && !editMode) {
                    GlassCard(onClick = { editMode = true }) {
                        Text("Noch kein Stundenplan. Tippe auf den Stift oben (oder hier), dann auf ein freies Feld.", style = T.body, color = c.sub)
                    }
                }
                val rows = schedule.maxPeriod
                val gridH = 24.dp + HeaderH + RowH * rows + Gap * (rows - 1)
                // Wischen auf dem Raster wechselt die Woche, außerhalb den Tab.
                HorizontalPager(weekPager, Modifier.fillMaxWidth().height(gridH), pageSpacing = 16.dp, key = { it }) { page ->
                    WeekGrid(
                        monday = baseMonday.plusWeeks((page - CENTER).toLong()),
                        todayEpoch = today.toEpochDay(),
                        editMode = editMode,
                        maxPeriod = rows,
                        wide = wide,
                        schedule = schedule,
                        haFor = haFor,
                        examFor = examFor,
                        onLesson = { l -> if (editMode) editSlot = l.slot to false else openLesson = l },
                        onTask = { viewTask = it },
                        onEmpty = { day, period ->
                            editSlot = LessonSlot(
                                dayOfWeek = day, period = period, length = 2, weekType = WeekType.ALL,
                                subjectId = schedule.subjects.values.firstOrNull()?.id ?: "",
                            ) to true
                        },
                    )
                }
                Legend()
            } else {
                val cellH = if (wide) 112.dp else 60.dp
                val monthH = 20.dp + 26.dp + cellH * 6 + 3.dp * 5
                val day = LocalDate.ofEpochDay(selectedDay)
                val panel: @Composable (Modifier) -> Unit = { m ->
                    DayPanel(day, schedule, tasks, exams, m, onTask = { viewTask = it }, onExam = { nav.open(Routes.exam(it.id)) })
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalPager(monthPager, Modifier.weight(1f).height(monthH), pageSpacing = 16.dp, key = { it }) { page ->
                            MonthGrid(baseMonth.plusMonths((page - CENTER).toLong()), today, selectedDay, cellH, true, schedule, tasks, exams) { selectedDay = it }
                        }
                        panel(Modifier.width(300.dp))
                    }
                } else {
                    HorizontalPager(monthPager, Modifier.fillMaxWidth().height(monthH), pageSpacing = 16.dp, key = { it }) { page ->
                        MonthGrid(baseMonth.plusMonths((page - CENTER).toLong()), today, selectedDay, cellH, false, schedule, tasks, exams) { selectedDay = it }
                    }
                    panel(Modifier.fillMaxWidth())
                }
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
            onViewTask = {
                openLesson = null
                viewTask = it
            },
        )
    }
    editSlot?.let { (slot, isNew) -> SlotEditSheet(slot, isNew, onDismiss = { editSlot = null }) }
    newTask?.let { t -> TaskEditSheet(t, isNew = true, onDismiss = { newTask = null }) }
    viewTask?.let { t -> TaskViewSheet(t, onDismiss = { viewTask = null }) }
}

@Composable
private fun Legend() {
    val c = colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 10.dp).freeCell(c.sub, c.line, 3.dp))
        Text(" Frei", style = T.smallBold, color = c.sub)
        Box(Modifier.width(18.dp))
        Box(Modifier.size(14.dp, 10.dp).border(1.5.dp, c.exam, RoundedCornerShape(3.dp)))
        Text(" Klausur", style = T.smallBold, color = c.sub)
        Box(Modifier.width(18.dp))
        Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(3.dp)).stripes(c.sub, 1.5.dp))
        Text(" Entfall", style = T.smallBold, color = c.sub)
    }
}

/** Freistunde: leicht grau mit gestricheltem Rand. */
private fun Modifier.freeCell(line: Color, fill: Color, radius: Dp): Modifier = drawBehind {
    val r = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
    drawRoundRect(fill.copy(alpha = 0.55f), cornerRadius = r)
    val w = 1.2.dp.toPx()
    drawRoundRect(
        line.copy(alpha = 0.45f),
        topLeft = Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
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
    monday: LocalDate,
    todayEpoch: Long,
    editMode: Boolean,
    maxPeriod: Int,
    wide: Boolean,
    schedule: ScheduleData,
    haFor: (Lesson) -> List<Task>,
    examFor: (Lesson) -> Boolean,
    onLesson: (Lesson) -> Unit,
    onTask: (Task) -> Unit,
    onEmpty: (Int, Int) -> Unit,
) {
    val c = colors
    val bodyH = RowH * maxPeriod + Gap * (maxPeriod - 1)
    GlassCard(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
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
                            Text(schedule.periodByNumber[p]?.let { Fmt.minutes(it.startMinute) } ?: "", style = T.small.copy(fontSize = T.small.fontSize * 0.8f), color = c.sub, maxLines = 1)
                        }
                    }
                }
            }
            for (i in 0 until 5) {
                val date = monday.plusDays(i.toLong())
                val isToday = date.toEpochDay() == todayEpoch
                val lessons = if (editMode) Schedule.plannedLessons(date, schedule, withFree = true) else Schedule.lessonsOn(date, schedule, withFree = true)
                val holiday = Schedule.holidayOn(date, schedule)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (isToday) c.accentSoft else Color.Transparent)) {
                    Column(Modifier.fillMaxWidth().height(HeaderH), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(Fmt.dayShort(date), style = T.smallBold, color = c.sub)
                        Box(Modifier.size(22.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                            Text("${date.dayOfMonth}", style = T.smallBold, color = if (isToday) c.accentInk else c.text)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(bodyH)) {
                        if (holiday != null && !editMode) {
                            val tint = HolidayLook.color(holiday)
                            Column(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)).padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(HolidayLook.icon(holiday), null, tint = tint, modifier = Modifier.size(22.dp))
                                Text(holiday.name, style = T.smallBold.copy(fontSize = T.small.fontSize * 0.85f), color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        } else {
                            if (editMode) {
                                val covered = lessons.flatMap { it.firstPeriod..it.lastPeriod }.toSet()
                                for (p in 1..maxPeriod) {
                                    if (p !in covered) {
                                        Box(
                                            Modifier.offset(y = (RowH + Gap) * (p - 1)).fillMaxWidth().height(RowH)
                                                .clip(RoundedCornerShape(12.dp)).border(1.5.dp, c.line, RoundedCornerShape(12.dp))
                                                .clickable { onEmpty(date.dayOfWeek.value, p) },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Ic.Plus, "Stunde hinzufügen", tint = c.sub, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }
                            lessons.forEach { l ->
                                if (l.isFree) {
                                    FreeCell(l, editMode) { onLesson(l) }
                                } else {
                                    LessonCell(l, editMode, wide, if (editMode) emptyList() else haFor(l), !editMode && examFor(l), { onLesson(l) }, onTask)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCell(l: Lesson, editMode: Boolean, wide: Boolean, tasks: List<Task>, exam: Boolean, onClick: () -> Unit, onTask: (Task) -> Unit) {
    val c = colors
    val color = subjectColor(l.subject?.color)
    val len = l.lastPeriod - l.firstPeriod + 1
    val tinted = color.copy(alpha = c.tint)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .offset(y = (RowH + Gap) * (l.firstPeriod - 1))
            .fillMaxWidth()
            .height(RowH * len + Gap * (len - 1))
            .clip(shape)
            .then(if (l.cancelled) Modifier.stripes(tinted, 6.dp) else Modifier.background(tinted))
            .then(if (exam) Modifier.border(1.5.dp, c.exam, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (wide) l.subject?.name ?: "?" else l.subject?.label ?: "?",
            style = T.label.copy(fontSize = T.label.fontSize * 0.95f, textDecoration = if (l.cancelled) TextDecoration.LineThrough else null),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val roomText = listOfNotNull(l.room.ifBlank { null }, if (editMode && l.slot.weekType != WeekType.ALL) "nur ${l.slot.weekType}" else null).joinToString(" · ")
        if (roomText.isNotBlank()) Text(roomText, style = T.smallBold.copy(fontSize = T.small.fontSize * 0.8f), color = c.sub, maxLines = 1, overflow = TextOverflow.Clip)
        if (exam) Text("Klausur", style = T.smallBold.copy(fontSize = T.small.fontSize * 0.82f), color = c.exam, maxLines = 1)
        // HA direkt als Text unter dem Fach (antippen zeigt sie an).
        val maxTasks = if (len >= 2) 2 else 1
        tasks.take(maxTasks).forEach { t ->
            Text(
                "• " + t.title,
                style = T.small.copy(fontSize = T.small.fontSize * 0.82f, lineHeight = T.small.fontSize * 0.98f),
                color = c.text,
                maxLines = if (len >= 2 && tasks.size == 1) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onTask(t) },
            )
        }
    }
}

/** Freistunde im Raster: grau und gestrichelt, ohne Text. */
@Composable
private fun FreeCell(l: Lesson, editMode: Boolean, onClick: () -> Unit) {
    val c = colors
    val len = l.lastPeriod - l.firstPeriod + 1
    Box(
        Modifier
            .offset(y = (RowH + Gap) * (l.firstPeriod - 1))
            .fillMaxWidth()
            .height(RowH * len + Gap * (len - 1))
            .clip(RoundedCornerShape(12.dp))
            .freeCell(c.sub, c.line, 12.dp)
            .then(if (editMode) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** Monatskalender: Fächerfarben, Klausuren rot umrandet, Ferien hinterlegt. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDay: Long,
    cellH: Dp,
    rich: Boolean,
    schedule: ScheduleData,
    tasks: List<Task>,
    exams: List<Exam>,
    onSelect: (Long) -> Unit,
) {
    val c = colors
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    GlassCard(padding = PaddingValues(10.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, d ->
                Text(d, style = T.smallBold, color = if (i == 6) c.exam else c.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0 until 6) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in 0 until 7) {
                        val date = start.plusDays((w * 7 + d).toLong())
                        val epoch = date.toEpochDay()
                        val inMonth = date.month == month.month
                        val holiday = Schedule.holidayOn(date, schedule)
                        val dayExams = exams.filter { it.date == epoch }
                        val dayTasks = tasks.filter { it.dueDate == epoch }.sortedBy { it.done }
                        val selected = epoch == selectedDay
                        val isToday = date == today
                        Column(
                            Modifier
                                .weight(1f)
                                .height(cellH)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        selected -> c.accentSoft
                                        holiday?.kind == HolidayKind.FERIEN -> c.line
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable { onSelect(epoch) }
                                .alpha(if (inMonth) 1f else 0.35f)
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = T.label,
                                    color = when {
                                        isToday -> c.accentInk
                                        d == 6 || holiday?.kind == HolidayKind.FEIERTAG -> c.exam
                                        d == 5 -> c.sub
                                        else -> c.text
                                    },
                                )
                            }
                            if (rich) {
                                if (holiday != null && (holiday.kind == HolidayKind.FEIERTAG || epoch == holiday.startDate)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(HolidayLook.icon(holiday), null, tint = HolidayLook.color(holiday), modifier = Modifier.size(13.dp))
                                        Text(
                                            holiday.name,
                                            style = T.small.copy(fontSize = T.small.fontSize * 0.8f),
                                            color = if (holiday.kind == HolidayKind.FEIERTAG) c.exam else c.sub,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val items = dayExams.map { e -> Triple(schedule.subjects[e.subjectId], "Klausur ${schedule.subjects[e.subjectId]?.label ?: ""}".trim(), true) } +
                                    dayTasks.map { t -> Triple(t.subjectId?.let { schedule.subjects[it] }, t.title, false) }
                                items.take(3).forEach { (s, text, isExam) ->
                                    EventPill(s, text, isExam, dayTasks.firstOrNull { it.title == text }?.done == true)
                                }
                                if (items.size > 3) Text("+${items.size - 3}", style = T.smallBold.copy(fontSize = T.small.fontSize * 0.8f), color = c.sub)
                            } else {
                                val bars = dayExams.map { schedule.subjects[it.subjectId]?.color to true } +
                                    dayTasks.map { t -> t.subjectId?.let { schedule.subjects[it] }?.color to false }
                                if (bars.isEmpty() && holiday != null && inMonth) {
                                    Icon(HolidayLook.icon(holiday), null, tint = HolidayLook.color(holiday).copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                                }
                                bars.take(3).forEach { (argb, isExam) ->
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                                            .background(subjectColor(argb))
                                            .then(if (isExam) Modifier.border(1.dp, c.exam, RoundedCornerShape(3.dp)) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventPill(subject: Subject?, text: String, exam: Boolean, done: Boolean) {
    val c = colors
    val color = subjectColor(subject?.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = c.tint))
            .then(if (exam) Modifier.border(1.2.dp, c.exam, RoundedCornerShape(5.dp)) else Modifier)
            .alpha(if (done) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(16.dp).background(color))
        Text(
            text,
            style = T.smallBold.copy(fontSize = T.small.fontSize * 0.82f, textDecoration = if (done) TextDecoration.LineThrough else null),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Was am gewählten Tag ansteht. */
@Composable
private fun DayPanel(
    day: LocalDate,
    schedule: ScheduleData,
    tasks: List<Task>,
    exams: List<Exam>,
    modifier: Modifier,
    onTask: (Task) -> Unit,
    onExam: (Exam) -> Unit,
) {
    val c = colors
    val epoch = day.toEpochDay()
    val holiday = Schedule.holidayOn(day, schedule)
    val dayExams = exams.filter { it.date == epoch }
    val dayTasks = tasks.filter { it.dueDate == epoch }.sortedBy { it.done }
    val lessons = Schedule.lessonsOn(day, schedule)
    val kinds by LocalContext.current.app.repo.taskKinds.collectAsStateWithLifecycle()
    GlassCard(modifier, padding = PaddingValues(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
        Text(Fmt.full(day), style = T.section, color = c.text, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
        if (holiday != null) {
            Row(Modifier.padding(start = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(HolidayLook.icon(holiday), null, tint = HolidayLook.color(holiday), modifier = Modifier.size(18.dp))
                Text(holiday.name, style = T.label, color = if (holiday.kind == HolidayKind.FEIERTAG) c.exam else c.sub)
            }
        }
        dayExams.forEach { e ->
            val s = schedule.subjects[e.subjectId]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, c.exam, RoundedCornerShape(14.dp))
                    .clickable { onExam(e) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SubjectIcon(s, 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(e.type, s?.name, e.title.ifBlank { null }).joinToString(" · "), style = T.body, color = c.text, maxLines = 2)
                    e.period?.let { Text("$it. Std", style = T.small, color = c.sub) }
                }
                Icon(Sym.Pen, "Klausur", tint = c.exam, modifier = Modifier.size(18.dp))
            }
        }
        dayTasks.forEach { t ->
            val s = t.subjectId?.let { schedule.subjects[it] }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onTask(t) }.alpha(if (t.done) 0.5f else 1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SubjectIcon(s, 18.dp)
                val kind = kindOf(t.type, kinds)
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.body.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null), color = c.text, maxLines = 2)
                    Text(listOfNotNull(s?.name, kind.name).joinToString(" · "), style = T.small, color = c.sub)
                }
                Icon(KindIcons.of(kind.icon), kind.name, tint = c.sub, modifier = Modifier.size(18.dp))
            }
        }
        if (dayExams.isEmpty() && dayTasks.isEmpty()) {
            Text(
                if (lessons.isEmpty()) "Nichts eingetragen" else "${lessons.size} Stunden, nichts fällig",
                style = T.small,
                color = c.sub,
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
            )
        }
    }
}
