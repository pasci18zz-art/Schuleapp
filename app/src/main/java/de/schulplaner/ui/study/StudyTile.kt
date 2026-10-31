package de.schulplaner.ui.study

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
import de.schulplaner.domain.label
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
 * Lernzeit-Kachel mit drei Seiten (von rechts nach links wischen): Uhr (Standard), Heute, Woche.
 * Play startet sofort mit der zuletzt gewählten Art und dem Fach; lange drücken = neu auswählen.
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
    val zone = java.time.ZoneId.systemDefault()
    val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val monday = weekStart()
    val weekList = sessions.filter { it.startedAt >= monday }
    val todayList = sessions.filter { it.startedAt >= todayStart }
    val week = weekList.sumOf { it.totalMs(now) }
    val todayTotal = todayList.sumOf { it.totalMs(now) }
    // null = zu, true = neue Session starten, false = laufende ändern
    var picker by remember { mutableStateOf<Boolean?>(null) }
    val pager = androidx.compose.foundation.pager.rememberPagerState { 3 }

    GlassCard(modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Clock, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text(
                when (pager.currentPage) {
                    0 -> "Lernzeit"
                    1 -> "Heute gelernt"
                    else -> "Diese Woche"
                },
                style = T.section,
                color = c.text,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            when (pager.currentPage) {
                // Was Play startet bzw. was läuft – klein, als Symbole.
                0 -> {
                    Icon(StudyLook.icon(kind), StudyKind.label(kind), tint = StudyLook.color(kind), modifier = Modifier.size(16.dp))
                    if (subject != null) {
                        Box(Modifier.width(6.dp))
                        SubjectIcon(subject, 15.dp)
                        Text(" ${subject.label}", style = T.smallBold, color = c.sub, maxLines = 1)
                    }
                }
                1 -> Text(StudyLook.short(todayTotal), style = T.smallBold, color = c.sub)
                else -> Text(StudyLook.short(week), style = T.smallBold, color = c.sub)
            }
            if (onOpen != null) Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(16.dp))
        }
        androidx.compose.foundation.pager.HorizontalPager(
            pager,
            Modifier.fillMaxWidth().height(132.dp).padding(top = 10.dp),
            pageSpacing = 16.dp,
            key = { it },
        ) { page ->
            when (page) {
                0 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    FlipTimer(total, Modifier.weight(1f), height = 92.dp, dim = cur == null)
                    if (!readOnly) {
                        Column(Modifier.padding(start = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .size(62.dp)
                                    .pressable(
                                        {
                                            if (cur == null) repo.startStudy(kind, subjectId, start = true) else repo.toggleStudy()
                                        },
                                        onLongClick = { picker = cur == null },
                                    )
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
                1 -> DaySummary(todayList, now, subjects, Modifier.fillMaxSize())
                else -> WeekMini(weekList, now, subjects, Modifier.fillMaxSize())
            }
        }
        // Seiten-Punkte.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                val active = pager.currentPage == i
                val w by androidx.compose.animation.core.animateDpAsState(if (active) 16.dp else 6.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = w, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) c.accent else c.sub.copy(alpha = 0.3f)),
                )
            }
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

/** Heute: Summe und Aufteilung nach Fach (Muster je Art). */
@Composable
private fun DaySummary(list: List<StudySession>, now: Long, subjects: List<de.schulplaner.data.Subject>, modifier: Modifier) {
    val c = colors
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Heute noch nichts gelernt", style = T.body, color = c.sub) }
        return
    }
    val bySubject = list.groupBy { it.subjectId }.map { (id, l) -> id to l.sumOf { it.totalMs(now) } }.sortedByDescending { it.second }
    val max = bySubject.maxOf { it.second }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bySubject.take(4).forEach { (id, ms) ->
            val s = subjects.firstOrNull { it.id == id }
            val col = s?.let { de.schulplaner.ui.components.subjectColor(it.color) } ?: NoSubject
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectIcon(s, 16.dp)
                Text(s?.label ?: "Ohne Fach", style = T.label, color = c.text, modifier = Modifier.width(64.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Balken mit den Mustern der Arten (HA voll, Lernen gestreift, Referat kariert).
                val parts = list.filter { it.subjectId == id }.groupBy { it.kind }.map { (k, l) -> k to l.sumOf { it.totalMs(now) } }
                androidx.compose.foundation.Canvas(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(5.dp))) {
                    drawRect(c.line.copy(alpha = 0.6f))
                    var x = 0f
                    parts.forEach { (k, v) ->
                        val wpx = size.width * (v.toFloat() / max)
                        patternRect(k, col, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(wpx, size.height))
                        x += wpx
                    }
                }
                Text(StudyLook.short(ms), style = T.smallBold, color = c.sub, modifier = Modifier.width(52.dp), maxLines = 1)
            }
        }
        if (bySubject.size > 4) Text("+${bySubject.size - 4} weitere", style = T.small, color = c.sub)
    }
}

/** Woche: Balken Mo–So in Fachfarbe mit Mustern je Art. */
@Composable
private fun WeekMini(list: List<StudySession>, now: Long, subjects: List<de.schulplaner.data.Subject>, modifier: Modifier) {
    val c = colors
    val zone = java.time.ZoneId.systemDefault()
    val monday = de.schulplaner.domain.Schedule.mondayOf(LocalDate.now())
    val colorOf = subjects.associate { it.id to de.schulplaner.ui.components.subjectColor(it.color) }
    val perDay = (0 until 7).map { i ->
        val d = monday.plusDays(i.toLong())
        list.filter { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == d }
            .groupBy { it.subjectId to it.kind }
            .map { (k, l) -> Triple(k.first, k.second, l.sumOf { it.totalMs(now) }) }
    }
    val max = perDay.maxOf { day -> day.sumOf { it.third } }.coerceAtLeast(30 * 60_000L)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perDay.forEach { day ->
                androidx.compose.foundation.Canvas(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                    drawRect(c.line.copy(alpha = 0.6f))
                    var bottom = size.height
                    day.forEach { (sid, k, ms) ->
                        val hpx = size.height * (ms.toFloat() / max)
                        patternRect(k, sid?.let { colorOf[it] } ?: NoSubject, androidx.compose.ui.geometry.Offset(0f, bottom - hpx), androidx.compose.ui.geometry.Size(size.width, hpx))
                        bottom -= hpx
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until 7).forEach { i ->
                val d = monday.plusDays(i.toLong())
                Text(
                    de.schulplaner.domain.Fmt.dayShort(d),
                    style = T.smallBold.copy(fontSize = T.small.fontSize * 0.9f),
                    color = if (d == LocalDate.now()) c.accent else c.sub,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
