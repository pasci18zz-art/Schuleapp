package de.schulplaner.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schulplaner.data.Exam
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.ScheduleData
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.HolidayLook
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.AppFont
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime

// ---------------- Wochenübersicht ----------------

/**
 * Die Woche auf einen Blick: Mo–Fr als Spalten, Stunden als Balken in Fachfarbe
 * (Doppelstunden doppelt hoch), Klausuren rot umrandet, darunter HA-Anzahl und Klausur-Symbol.
 */
@Composable
fun WeekOverview(now: LocalDateTime, schedule: ScheduleData, tasks: List<Task>, exams: List<Exam>, modifier: Modifier = Modifier) {
    val c = colors
    val today = now.toLocalDate()
    val monday = Schedule.weekToShow(now, schedule)
    val next = monday.isAfter(Schedule.mondayOf(today))
    val maxP = schedule.maxPeriod.coerceAtLeast(6)
    GlassCard(modifier.fillMaxSize(), padding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 22.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (next) "Nächste Woche" else "Diese Woche", style = T.section, color = c.text)
            Text("KW ${Fmt.weekOfYear(monday)} · ${Fmt.weekRange(monday)}", style = T.small, color = c.sub, modifier = Modifier.weight(1f), maxLines = 1)
            Schedule.weekTypeOf(monday, schedule)?.let { Pill("$it-Woche") }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 5) {
                val date = monday.plusDays(i.toLong())
                val epoch = date.toEpochDay()
                val isToday = date == today
                val holiday = Schedule.holidayOn(date, schedule)
                val lessons = Schedule.lessonsOn(date, schedule)
                val dayExams = exams.filter { it.date == epoch }
                val ha = tasks.count { !it.done && it.dueDate == epoch }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) c.accentSoft.copy(alpha = 0.8f) else Color.Transparent)
                        .padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Fmt.dayShort(date), style = T.smallBold, color = if (isToday) c.accent else c.sub)
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text("${date.dayOfMonth}", style = T.smallBold, color = if (isToday) c.accentInk else c.text)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                        if (holiday != null) {
                            val tint = HolidayLook.color(holiday)
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(HolidayLook.icon(holiday), holiday.name, tint = tint, modifier = Modifier.size(20.dp)) }
                        } else {
                            val examColor = c.exam
                            Canvas(Modifier.fillMaxSize()) {
                                val unit = size.height / maxP
                                val gap = 2.dp.toPx()
                                lessons.forEach { l ->
                                    val top = (l.firstPeriod - 1) * unit
                                    val h = l.length * unit - gap
                                    val col = subjectColor(l.subject?.color)
                                    drawRoundRect(
                                        col.copy(alpha = if (l.cancelled) 0.25f else 0.85f),
                                        topLeft = Offset(0f, top),
                                        size = Size(size.width, h),
                                        cornerRadius = CornerRadius(4.dp.toPx()),
                                    )
                                    if (dayExams.any { it.subjectId == l.slot.subjectId }) {
                                        drawRoundRect(
                                            examColor,
                                            topLeft = Offset(0f, top),
                                            size = Size(size.width, h),
                                            cornerRadius = CornerRadius(4.dp.toPx()),
                                            style = Stroke(2.dp.toPx()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.height(18.dp).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (ha > 0) {
                            Icon(Sym.House, null, tint = c.sub, modifier = Modifier.size(11.dp))
                            Text("$ha", style = T.smallBold.copy(fontSize = 10.sp), color = c.sub)
                        }
                        if (dayExams.isNotEmpty()) Icon(Sym.Pen, "Klausur", tint = c.exam, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

// ---------------- Flip-Uhr ----------------

/** Uhrzeit, die sich genau zum Minutenwechsel erneuert. */
@Composable
private fun rememberMinute(): LocalTime =
    produceState(LocalTime.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 30)
            value = LocalTime.now()
        }
    }.value

/** Große Klappzahlen-Uhr: Beim Minutenwechsel fällt die obere Hälfte von oben nach unten. */
@Composable
fun FlipClockCard(subtitle: String, modifier: Modifier = Modifier) {
    val time = rememberMinute()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2C33), Color(0xFF15161A))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(time.hour.toString().padStart(2, '0'), Modifier.weight(1f).widthIn(max = 170.dp).height(132.dp))
            FlipCard(time.minute.toString().padStart(2, '0'), Modifier.weight(1f).widthIn(max = 170.dp).height(132.dp))
        }
        Text(
            subtitle,
            style = T.label,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private val flipStyle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 92.sp, lineHeight = 92.sp)
private val cardBrush = Brush.verticalGradient(listOf(Color(0xFF3A3C44), Color(0xFF1E1F24)))

/** Eine Klappkarte mit zwei Ziffern. */
@Composable
fun FlipCard(value: String, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(value) }
    var old by remember { mutableStateOf(value) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) {
            old = shown
            shown = value
            p.snapTo(0f)
            p.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val f = p.value
    Box(modifier) {
        // Hinten oben schon die neue Zahl, unten noch die alte – bis die Klappe darüber gefallen ist.
        FlipHalf(shown, top = true)
        FlipHalf(if (f < 1f) old else shown, top = false)
        if (f < 0.5f) {
            FlipHalf(old, top = true, angle = -180f * f)
        } else if (f < 1f) {
            FlipHalf(shown, top = false, angle = 180f * (1f - f))
        }
        // Scharnier in der Mitte.
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color.Black.copy(alpha = 0.6f)))
    }
}

@Composable
private fun BoxScope.FlipHalf(text: String, top: Boolean, angle: Float = 0f) {
    val shade = (kotlin.math.abs(angle) / 90f).coerceIn(0f, 1f) * 0.55f
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                rotationX = angle
                cameraDistance = 16f * density
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .drawWithContent {
                val half = size.height / 2
                clipRect(top = if (top) 0f else half, bottom = if (top) half else size.height) {
                    this@drawWithContent.drawContent()
                    if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(cardBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = flipStyle, color = Color(0xFFF4F4F6), maxLines = 1)
    }
}
