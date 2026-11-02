package de.schulplaner.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Mood
import de.schulplaner.data.SettingKeys
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.InlineTextField
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import kotlin.math.hypot

/** Die fünf Stimmungen, von furchtbar (rot) bis super (hellgrün). */
object MoodLook {
    val colors = listOf(Color(0xFFE5484D), Color(0xFFF2843B), Color(0xFFF5C542), Color(0xFFA8D84E), Color(0xFF5FDB8F))
    val labels = listOf("Furchtbar", "Schlecht", "Geht so", "Gut", "Super")
    fun color(level: Int) = colors[(level - 1).coerceIn(0, 4)]
    fun label(level: Int) = labels[(level - 1).coerceIn(0, 4)]
}

/** Zeichnet ein Gesicht zur Stimmung: Mund, Augen und Bäckchen ändern sich mit der Stufe. */
fun DrawScope.moodFace(level: Int, center: Offset, radius: Float, fill: Color = MoodLook.color(level), blink: Float = 0f) {
    drawCircle(
        Brush.radialGradient(listOf(fill.copy(alpha = 1f).lighten(0.25f), fill), Offset(center.x - radius * 0.3f, center.y - radius * 0.35f), radius * 1.3f),
        radius,
        center,
    )
    val ink = Color(0xFF3B2A20).copy(alpha = 0.85f)
    val eyeY = center.y - radius * 0.18f
    val eyeDx = radius * 0.33f
    val eyeH = radius * 0.13f * (1f - blink * 0.85f)
    for (side in listOf(-1f, 1f)) {
        val ex = center.x + side * eyeDx
        if (level == 5) {
            // Glücklich zusammengekniffene Augen.
            drawArc(ink, 200f, 140f, false, Offset(ex - radius * 0.13f, eyeY - radius * 0.08f), Size(radius * 0.26f, radius * 0.2f), style = Stroke(radius * 0.08f, cap = StrokeCap.Round))
        } else {
            drawOval(ink, Offset(ex - radius * 0.075f, eyeY - eyeH), Size(radius * 0.15f, eyeH * 2))
        }
        if (level == 1) {
            // Traurige Augenbrauen.
            drawLine(ink, Offset(ex - side * radius * 0.16f, eyeY - radius * 0.32f), Offset(ex + side * radius * 0.08f, eyeY - radius * 0.22f), radius * 0.07f, StrokeCap.Round)
        }
    }
    if (level >= 4) {
        drawCircle(Color(0x66FF6F61), radius * 0.12f, Offset(center.x - radius * 0.55f, center.y + radius * 0.12f))
        drawCircle(Color(0x66FF6F61), radius * 0.12f, Offset(center.x + radius * 0.55f, center.y + radius * 0.12f))
    }
    val my = center.y + radius * 0.32f
    val mw = radius * 0.62f
    val stroke = Stroke(radius * 0.09f, cap = StrokeCap.Round)
    when (level) {
        1 -> drawArc(ink, 200f, 140f, false, Offset(center.x - mw / 2, my - radius * 0.02f), Size(mw, radius * 0.4f), style = stroke)
        2 -> drawArc(ink, 215f, 110f, false, Offset(center.x - mw / 2, my + radius * 0.02f), Size(mw, radius * 0.28f), style = stroke)
        3 -> drawLine(ink, Offset(center.x - mw * 0.38f, my + radius * 0.06f), Offset(center.x + mw * 0.38f, my + radius * 0.06f), radius * 0.09f, StrokeCap.Round)
        4 -> drawArc(ink, 25f, 130f, false, Offset(center.x - mw / 2, my - radius * 0.22f), Size(mw, radius * 0.36f), style = stroke)
        else -> {
            val p = Path().apply {
                moveTo(center.x - mw * 0.55f, my - radius * 0.08f)
                quadraticBezierTo(center.x, my + radius * 0.55f, center.x + mw * 0.55f, my - radius * 0.08f)
                close()
            }
            drawPath(p, ink)
            drawCircle(Color(0xFFFF8A8A), radius * 0.12f, Offset(center.x, my + radius * 0.12f))
        }
    }
}

private fun Color.lighten(f: Float) = Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha)

/** Gemeinsamer Zustand: Wo der Stimmungs-Tracker gerade offen ist und aus welchem Punkt (Sonne/Mond) er wächst. */
class MoodHost {
    var visible by mutableStateOf(false)
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set

    fun open(at: Offset) {
        origin = at
        visible = true
    }

    fun close() {
        visible = false
    }
}

val LocalMoodHost = androidx.compose.runtime.staticCompositionLocalOf { MoodHost() }

/** Sanft anfahren, lange ausrollen. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Wachsender Kreis ab [origin]. Als Layer-Ausschnitt gezeichnet: Der Inhalt wird nur einmal aufgenommen und dann nur noch beschnitten. */
private class RevealShape(private val origin: Offset, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val far = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y))
        return Outline.Generic(Path().apply { addOval(Rect(origin, far * progress)) })
    }
}

/**
 * Versteckter Stimmungs-Tracker (langes Drücken auf Sonne/Mond). Wächst als Kreis aus der Sonne heraus.
 * Liegt direkt in der App (kein eigenes Fenster) und ist im Ansichtsmodus nicht vorhanden.
 */
@Composable
fun MoodOverlay(origin: Offset, onClose: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // Der erste Aufbau ist schwer: zwei Bilder abwarten, damit er nicht in die Animation fällt (das war das Ruckeln).
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        reveal.animateTo(1f, tween(640, easing = Emphasized))
    }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                reveal.animateTo(0f, tween(380, easing = EmphasizedIn))
                onClose()
            }
        }
    }
    BackHandler(onBack = close)
    // Die schwere Monatsübersicht erst aufbauen, wenn der Kreis schon groß ist.
    val ready by remember { derivedStateOf { reveal.value > 0.4f } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = RevealShape(origin, reveal.value)
                clip = true
                alpha = (reveal.value * 5f).coerceAtMost(1f)
            }
            // Berührungen nicht an die Seite dahinter durchlassen.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        MoodContent(close, ready)
    }
}

/** Blinzeln: kurz vor Ende jedes Durchlaufs schließen sich die Augen (0 = offen, 1 = zu). */
private fun blinkOf(phase: Float): Float = if (phase > 0.95f) (1f - kotlin.math.abs(phase - 0.975f) / 0.025f).coerceIn(0f, 1f) else 0f

@Composable
private fun MoodContent(onClose: () -> Unit, showMonth: Boolean) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val settings by repo.settings.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }
    var day by remember { mutableLongStateOf(today.toEpochDay()) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    fun moodOf(d: Long) = Mood.decode(settings[SettingKeys.MOOD_PREFIX + d])
    val saved = moodOf(day)
    var note by remember(day) { mutableStateOf(saved?.note.orEmpty()) }
    val level = saved?.level ?: 0
    val tint by animateColorAsState(if (level > 0) MoodLook.color(level) else c.accent, tween(500), label = "mood-tint")
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Notiz beim Verlassen des Tages bzw. beim Schließen sichern.
    fun saveNote() {
        val m = moodOf(day) ?: return
        if (m.note != note.trim()) repo.setMood(day, m.copy(note = note.trim()))
    }
    val closeAll = {
        saveNote()
        onClose()
    }

    val t = rememberInfiniteTransition(label = "mood-bg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    // Als State (ohne "by") und nur beim Zeichnen gelesen – sonst würde die ganze Seite in jedem Bild neu berechnet.
    val blinkPhase = t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "blink")

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .drawWithContent {
                val w = size.width
                val h = size.height
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent), Offset(w * (0.2f + 0.5f * drift), h * 0.12f), w * 0.9f), w * 0.9f, Offset(w * (0.2f + 0.5f * drift), h * 0.12f))
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.3f), Color.Transparent), Offset(w * (0.9f - 0.4f * drift), h * 0.7f), w * 0.8f), w * 0.8f, Offset(w * (0.9f - 0.4f * drift), h * 0.7f))
                drawContent()
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("STIMMUNG", style = T.overline, color = c.sub)
                        Text("Nur für dich", style = T.small, color = c.sub)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c.card).clickable(onClick = closeAll),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.Close, "Schließen", tint = c.text, modifier = Modifier.size(20.dp)) }
                }

                val date = LocalDate.ofEpochDay(day)
                Text(
                    if (day == today.toEpochDay()) "Wie geht's dir heute?" else "Wie war dein ${Fmt.dayLong(date.dayOfWeek.value)}?",
                    style = T.title,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(Fmt.full(date), style = T.body, color = c.sub, modifier = Modifier.padding(top = 2.dp))

                // Großes Gesicht.
                Canvas(
                    Modifier
                        .padding(top = 18.dp)
                        .size(128.dp)
                        .graphicsLayer {
                            scaleX = bounce.value
                            scaleY = bounce.value
                        },
                ) {
                    val r = size.minDimension / 2
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent), center, r * 1.0f), r, center)
                    if (level > 0) {
                        moodFace(level, center, r * 0.8f, blink = blinkOf(blinkPhase.value))
                    } else {
                        drawCircle(c.line, r * 0.8f, center)
                        drawCircle(c.sub.copy(alpha = 0.5f), r * 0.8f, center, style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                    }
                }
                Text(
                    if (level > 0) MoodLook.label(level) else "Tippe ein Gesicht an",
                    style = T.section.copy(fontSize = T.section.fontSize * 1.2f),
                    color = if (level > 0) tint else c.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // Auswahl auf einem Verlauf von Rot nach Hellgrün.
                Box(Modifier.padding(top = 18.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(MoodLook.colors.map { it.copy(alpha = 0.55f) })),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (1..5).forEach { lv ->
                            val selected = lv == level
                            val sc = remember { Animatable(1f) }
                            LaunchedEffect(selected) { sc.animateTo(if (selected) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
                            Canvas(
                                Modifier
                                    .size(56.dp)
                                    .graphicsLayer {
                                        scaleX = sc.value
                                        scaleY = sc.value
                                    }
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        repo.setMood(day, Mood(lv, note.trim()))
                                        scope.launch {
                                            bounce.snapTo(0.7f)
                                            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
                                        }
                                    },
                            ) {
                                val r = size.minDimension / 2
                                if (selected) drawCircle(Color.White.copy(alpha = if (c.isDark) 0.25f else 0.9f), r, center)
                                moodFace(lv, center, r * (if (selected) 0.84f else 0.76f), fill = if (level == 0 || selected) MoodLook.color(lv) else MoodLook.color(lv).copy(alpha = 0.55f))
                            }
                        }
                    }
                }

                // Notiz.
                Box(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.card)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    InlineTextField(
                        note,
                        { note = it },
                        if (level > 0) "Was war heute los? (optional)" else "Erst ein Gesicht wählen, dann Notiz",
                        Modifier.fillMaxWidth(),
                        singleLine = false,
                    )
                }
                if (level > 0 && note.trim() != saved?.note.orEmpty()) {
                    Text(
                        "Notiz speichern",
                        style = T.label,
                        color = c.accentInk,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tint)
                            .clickable { saveNote() }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }

                if (showMonth) {
                    MoodMonth(
                        month = month,
                        today = today,
                        selected = day,
                        moodOf = { moodOf(it) },
                        onMonth = { month = it },
                        onPick = { d ->
                            saveNote()
                            day = d
                        },
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Monatsübersicht: Wochen als Zeilen, Mo–So als Spalten. */
@Composable
private fun MoodMonth(
    month: YearMonth,
    today: LocalDate,
    selected: Long,
    moodOf: (Long) -> Mood?,
    onMonth: (YearMonth) -> Unit,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val last = month.atEndOfMonth()
    val weeks = ((last.toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    val monthNames = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    val levels = (1..month.lengthOfMonth()).mapNotNull { moodOf(month.atDay(it).toEpochDay())?.level }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Ic.ChevronLeft, "Vorheriger Monat", tint = c.sub,
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable { onMonth(month.minusMonths(1)) }.padding(7.dp),
            )
            Text("${monthNames[month.monthValue - 1]} ${month.year}", style = T.section, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            val canNext = month < YearMonth.from(today)
            Icon(
                Ic.ChevronRight, "Nächster Monat", tint = if (canNext) c.sub else c.sub.copy(alpha = 0.25f),
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(enabled = canNext) { onMonth(month.plusMonths(1)) }.padding(7.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Text("KW", style = T.small, color = c.sub, modifier = Modifier.width(30.dp))
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it, style = T.smallBold, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat(weeks) { w ->
            val monday = start.plusDays(w * 7L)
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = T.small, color = c.sub.copy(alpha = 0.7f), modifier = Modifier.width(30.dp))
                (0 until 7).forEach { i ->
                    val date = monday.plusDays(i.toLong())
                    val epoch = date.toEpochDay()
                    val inMonth = date.month == month.month
                    val future = date.isAfter(today)
                    val mood = if (inMonth) moodOf(epoch) else null
                    Box(Modifier.weight(1f).padding(horizontal = 2.5.dp), contentAlignment = Alignment.Center) {
                        if (inMonth) {
                            val isSel = epoch == selected
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(mood?.let { MoodLook.color(it.level) } ?: c.line.copy(alpha = if (future) 0.35f else 1f))
                                    .then(if (isSel) Modifier.border(2.5.dp, c.text, RoundedCornerShape(11.dp)) else Modifier)
                                    .clickable(enabled = !future) { onPick(epoch) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (mood != null) {
                                    Canvas(Modifier.fillMaxSize(0.62f)) { moodFace(mood.level, center, size.minDimension / 2) }
                                } else {
                                    Text(
                                        "${date.dayOfMonth}",
                                        style = T.smallBold,
                                        color = if (date == today) c.accent else c.sub.copy(alpha = if (future) 0.4f else 1f),
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
        }
        if (levels.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                (5 downTo 1).forEach { lv ->
                    val n = levels.count { it == lv }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MoodLook.color(lv).copy(alpha = 0.18f)).padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(16.dp)) { moodFace(lv, center, size.minDimension / 2) }
                        Text(" $n", style = T.smallBold, color = c.text)
                    }
                }
            }
            val avg = levels.average()
            Text(
                "Ø ${MoodLook.label(kotlin.math.round(avg).toInt())} · ${levels.size} Tage eingetragen",
                style = T.small,
                color = c.sub,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
