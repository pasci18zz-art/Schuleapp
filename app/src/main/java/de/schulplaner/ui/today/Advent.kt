package de.schulplaner.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Motive, die hinter den Türchen gezeichnet werden. */
enum class Motif { STAR, HEART, SNOWFLAKE, GIFT, CUP, TREE, MOON, CANDLE, SUN }

class AdventGift(val motif: Motif, val title: String, val text: String)

/** Inhalte der Türchen – bewusst nicht in der Oberfläche aufgelistet, damit es eine Überraschung bleibt. */
private val gifts = listOf(
    AdventGift(Motif.STAR, "Los geht's!", "Der Dezember ist da. Ab heute gibt's jeden Morgen eine Kleinigkeit für dich. Fang den Monat mit etwas Leichtem an: Mach heute nur das Wichtigste – und dann Feierabend."),
    AdventGift(Motif.CUP, "Kakao-Pause", "Heute Abend: heißer Kakao (oder Tee), Decke, Lieblingsserie. Ohne schlechtes Gewissen. Das ist offiziell Teil des Lernplans."),
    AdventGift(Motif.SNOWFLAKE, "Wusstest du?", "Keine zwei Schneeflocken sind gleich – aber fast alle haben sechs Arme, weil sich Wassermoleküle im Eis im Sechseck anordnen. Ein bisschen Chemie zum Staunen."),
    AdventGift(Motif.HEART, "Ein Kompliment", "Du gibst nicht auf, auch wenn's anstrengend wird. Das ist eine Stärke, die man nicht in Noten messen kann – aber sie bringt dich am weitesten."),
    AdventGift(Motif.SUN, "Mini-Mission", "Schreib heute drei Dinge auf, die gut gelaufen sind. Egal wie klein. Morgen wirst du dich darüber freuen."),
    AdventGift(Motif.GIFT, "Nikolaus-Vorabend", "Morgen ist Nikolaus! Stell die Schuhe raus – vielleicht wartet ja was drin. Und falls nicht: Hier ist ein virtueller Schokoweihnachtsmann. 🍫"),
    AdventGift(Motif.STAR, "Nikolaus", "Heute gönnst du dir eine Lern-Pause mehr als sonst. Nikolaus hat's erlaubt."),
    AdventGift(Motif.TREE, "Lach mal", "Warum hat der Tannenbaum keine Prüfungsangst? Er hat genug Zweige zum Festhalten. 🎄 – Okay, schlecht. Aber du hast gegrinst, oder?"),
    AdventGift(Motif.MOON, "Schlaf ist Lernen", "Im Schlaf sortiert dein Gehirn, was du tagsüber gelernt hast. Heute also mit gutem Gewissen früher ins Bett."),
    AdventGift(Motif.CANDLE, "Halbzeit bald", "Zehn Türchen offen! Zünde heute eine Kerze an (oder mach Lichterkette an) und atme dreimal tief durch."),
    AdventGift(Motif.HEART, "Für jemanden", "Schreib heute einer Person eine Nachricht, die dir wichtig ist – einfach so. Das macht zwei Leute glücklich."),
    AdventGift(Motif.SUN, "Erinnerung", "Du bist mehr als deine Noten. Was du draufhast, zeigt sich auch darin, wie du mit anderen umgehst und was du dir selbst beibringst."),
    AdventGift(Motif.SNOWFLAKE, "Wintermusik", "Mach heute beim Lernen eine ruhige Winter-Playlist an. Lo-Fi, Klavier, Weihnachtsjazz – was dir gefällt."),
    AdventGift(Motif.STAR, "Wusstest du?", "Der hellste „Stern“ am Abendhimmel im Winter ist oft gar keiner: Es ist die Venus oder der Jupiter. Schau heute Abend mal raus!"),
    AdventGift(Motif.GIFT, "Mini-Geschenk an dich", "Such dir heute eine Sache aus, auf die du dich freust – Lieblingsessen, Spaziergang, Zocken – und plan sie fest ein."),
    AdventGift(Motif.CUP, "Plätzchen-Tag", "Perfekter Tag für Plätzchen. Oder zumindest dafür, welche zu essen. Wissenschaftlich bewiesen: Zimt riecht nach guter Laune."),
    AdventGift(Motif.TREE, "Fast geschafft", "Noch eine Woche bis Weihnachten. Die letzten Schultage schaffst du mit links – und dann ist erst mal Pause."),
    AdventGift(Motif.HEART, "Stolz-Moment", "Denk an ein Thema, das du am Anfang des Halbjahres nicht konntest – und jetzt schon. Genau das ist Fortschritt."),
    AdventGift(Motif.MOON, "Gemütlich", "Heute Abend kein Bildschirm für eine halbe Stunde: Buch, Musik, Malen oder einfach nichts tun. Dein Kopf sagt danke."),
    AdventGift(Motif.SUN, "Lach mal", "Was macht ein Mathebuch an Weihnachten? Es hat viele Probleme – aber zum Glück auch Lösungen hinten drin."),
    AdventGift(Motif.SNOWFLAKE, "Wintersonnenwende", "Heute ist (fast) der kürzeste Tag des Jahres. Ab jetzt wird es jeden Tag ein bisschen heller. Gute Nachricht, oder?"),
    AdventGift(Motif.CANDLE, "Ferien!", "Die Ferien sind da oder ganz nah. Lass die Schulsachen mal liegen – du hast dir die Pause richtig verdient."),
    AdventGift(Motif.GIFT, "Fast da", "Morgen ist Heiligabend. Nimm dir heute Zeit für die Leute, die dir wichtig sind – und für dich selbst."),
    AdventGift(Motif.STAR, "Frohe Weihnachten!", "Danke, dass du dich das ganze Jahr so reingehängt hast. Genieß die Feiertage, iss zu viel Plätzchen und lass es dir richtig gut gehen. 🎄✨"),
)

/** Vorgeschmack am 2. November 2026 – genau ein Türchen. */
private val preview = AdventGift(
    Motif.HEART,
    "Für dich – heute ganz besonders",
    "Du hast in letzter Zeit richtig viel gestemmt: Q1, Klausuren, Referate – und nebenbei baust du dir einfach deine eigene App. " +
        "Das ist nicht selbstverständlich.\n\nHeute darfst du stolz auf dich sein. Gönn dir heute Abend eine Sache, die nur dir guttut – " +
        "ohne To-do, ohne Plan. Du hast es dir verdient. 💛\n\nUnd: Im Dezember warten hier 24 weitere Türchen auf dich.",
)
private val PREVIEW_DATE: LocalDate = LocalDate.of(2026, 11, 2)

/** Gemischte Reihenfolge der Türchen wie bei einem echten Kalender. */
private val doorOrder = listOf(7, 15, 2, 22, 11, 19, 4, 13, 24, 9, 17, 1, 20, 6, 14, 3, 23, 10, 12, 18, 5, 21, 8, 16)

fun adventVisible(d: LocalDate): Boolean = (d.month == Month.DECEMBER && d.dayOfMonth <= 24) || d == PREVIEW_DATE

private val Wine = Color(0xFF8E1C2B)
private val Pine = Color(0xFF1F5A3D)
private val Gold = Color(0xFFF2C14E)

/** Adventskalender oben auf der Startseite (1.–24. Dezember), eingeklappt mit dem Türchen von heute. */
@Composable
fun AdventCard(today: LocalDate, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.app.repo
    val settings by repo.settings.collectAsStateWithLifecycle()
    val isPreview = today == PREVIEW_DATE
    val year = today.year
    fun doorKey(n: Int) = if (isPreview) "advent_preview_$year" else "advent_${year}_$n"
    fun opened(n: Int) = settings[doorKey(n)] == "1"
    var expanded by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Int?>(null) }
    val todayDoor = if (isPreview) 0 else today.dayOfMonth
    val openedCount = if (isPreview) 0 else (1..24).count { opened(it) }

    val t = rememberInfiniteTransition(label = "advent")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "twinkle")

    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF6B1424), Color(0xFF8E1C2B), Color(0xFF1F4D36))))
            .border(1.5.dp, Gold.copy(alpha = 0.55f), shape)
            .animateContentSize(),
    ) {
        Box {
            // Kleine funkelnde Sterne im Hintergrund.
            Canvas(Modifier.matchParentSize()) {
                for (i in 0 until 12) {
                    val x = size.width * ((i * 0.37f + 0.11f) % 1f)
                    val y = size.height * ((i * 0.53f + 0.2f) % 1f)
                    val a = (sin((twinkle + i * 0.13f) * 2 * PI).toFloat() + 1f) / 2f
                    drawCircle(Color.White.copy(alpha = 0.15f + 0.45f * a), (1.2f + (i % 3)) * density, Offset(x, y))
                }
            }
            // Ist das Türchen von heute schon offen, bleibt nur eine schmale Zeile – sie steht nicht im Weg.
            val slim = !isPreview && opened(todayDoor) && !expanded
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPreview) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = if (slim) 9.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (slim) {
                    Text("Adventskalender", style = T.label, color = Gold)
                    Text("$openedCount von 24 geöffnet", style = T.small, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                    Icon(Ic.ChevronDown, "Alle Türchen zeigen", tint = Gold, modifier = Modifier.size(20.dp))
                } else {
                Column(Modifier.weight(1f)) {
                    Text(if (isPreview) "KLEINER VORGESCHMACK" else "ADVENTSKALENDER", style = T.overline, color = Gold)
                    Text(
                        when {
                            isPreview -> "Ein Türchen nur für heute"
                            !opened(todayDoor) -> "Türchen $todayDoor wartet auf dich!"
                            else -> "$openedCount von 24 geöffnet"
                        },
                        style = T.section,
                        color = Color.White,
                    )
                    if (!isPreview) {
                        Text(if (expanded) "Tippen zum Einklappen" else "Tippen für alle Türchen", style = T.small, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Door(
                    number = if (isPreview) 2 else todayDoor,
                    opened = opened(todayDoor),
                    available = true,
                    glow = glow,
                    big = true,
                    motif = if (isPreview) preview.motif else gifts[todayDoor - 1].motif,
                ) { open = todayDoor }
                }
            }
        }
        if (expanded && !isPreview) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                val cols = if (maxWidth > 560.dp) 8 else 6
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    doorOrder.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { n ->
                                Box(Modifier.weight(1f)) {
                                    Door(
                                        number = n,
                                        opened = opened(n),
                                        available = n <= today.dayOfMonth,
                                        glow = if (n == todayDoor && !opened(n)) glow else 0f,
                                        big = false,
                                        motif = gifts[n - 1].motif,
                                    ) { open = n }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { n ->
        val gift = if (isPreview) preview else gifts[n - 1]
        DoorDialog(
            number = if (isPreview) 2 else n,
            gift = gift,
            alreadyOpen = opened(n),
            onOpened = { if (!opened(n)) repo.setSetting(doorKey(n), "1") },
            onClose = { open = null },
        )
    }
}

@Composable
private fun Door(number: Int, opened: Boolean, available: Boolean, glow: Float, big: Boolean, motif: Motif, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(if (big) 18.dp else 12.dp)
    Box(
        Modifier
            .then(if (big) Modifier.size(54.dp) else Modifier.fillMaxWidth().aspectRatio(0.82f))
            .graphicsLayer { translationX = shake.value }
            .clip(shape)
            .background(
                if (opened) Brush.linearGradient(listOf(Color(0xFFFFF4D6), Color(0xFFFFE2A8)))
                else Brush.linearGradient(listOf(if (number % 2 == 0) Wine else Pine, (if (number % 2 == 0) Wine else Pine).copy(alpha = 0.75f))),
            )
            .border(if (glow > 0f) 2.dp else 1.dp, Gold.copy(alpha = if (glow > 0f) 0.5f + 0.5f * glow else 0.45f), shape)
            .clickable {
                if (available) {
                    onClick()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        shake.animateTo(0f, keyframes { durationMillis = 360; -10f at 60; 10f at 140; -6f at 220; 4f at 300 })
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (opened) {
            Canvas(Modifier.fillMaxSize(0.62f)) { drawMotif(motif, center, size.minDimension / 2) }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                // Goldene Verzierung in den Ecken.
                val r = 3 * density
                drawCircle(Gold.copy(alpha = 0.7f), r, Offset(r * 2.4f, r * 2.4f))
                drawCircle(Gold.copy(alpha = 0.7f), r, Offset(size.width - r * 2.4f, size.height - r * 2.4f))
                if (glow > 0f) drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f * glow), Color.Transparent), center, size.maxDimension), size.maxDimension, center)
            }
            Text(
                "$number",
                style = T.section.copy(fontSize = if (big) 26.sp else 17.sp),
                color = if (available) Color.White else Color.White.copy(alpha = 0.45f),
            )
            if (!available) {
                Icon(Ic.Clock, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(10.dp))
            }
        }
    }
}

/** Das große Türchen: zwei Flügel schwingen auf, dahinter leuchtet die Überraschung. */
@Composable
private fun DoorDialog(number: Int, gift: AdventGift, alreadyOpen: Boolean, onOpened: () -> Unit, onClose: () -> Unit) {
    val p = remember { Animatable(if (alreadyOpen) 1f else 0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (!alreadyOpen) {
            kotlinx.coroutines.delay(250)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            p.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
            onOpened()
        }
    }
    val t = rememberInfiniteTransition(label = "gift")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val float by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "float")

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(enabled = p.value >= 1f, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF8E7), Color(0xFFFFE9C2))))
                    // Tippen auf die Karte selbst schließt nichts (nur daneben).
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
            ) {
                // Inhalt hinter der Tür.
                val reveal = ((p.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp)
                        .graphicsLayer {
                            alpha = reveal
                            scaleX = 0.85f + 0.15f * reveal
                            scaleY = 0.85f + 0.15f * reveal
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TÜRCHEN $number", style = T.overline, color = Wine)
                    Canvas(Modifier.padding(vertical = 14.dp).size(140.dp).graphicsLayer { translationY = float * 6 * density }) {
                        val r = size.minDimension / 2
                        drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f), Color.Transparent), center, r), r, center)
                        rotate(spin, center) {
                            for (i in 0 until 12) {
                                val a = i * PI / 6
                                drawLine(
                                    Gold.copy(alpha = 0.5f),
                                    Offset(center.x + (cos(a) * r * 0.62f).toFloat(), center.y + (sin(a) * r * 0.62f).toFloat()),
                                    Offset(center.x + (cos(a) * r * 0.9f).toFloat(), center.y + (sin(a) * r * 0.9f).toFloat()),
                                    3 * density,
                                    StrokeCap.Round,
                                )
                            }
                        }
                        drawMotif(gift.motif, center, r * 0.5f)
                        // Funken, die nach außen fliegen.
                        for (i in 0 until 10) {
                            val a = i * 2 * PI / 10 + spin / 90
                            val d = r * (0.5f + 0.5f * reveal)
                            drawCircle(listOf(Gold, Color(0xFFFF7A8A), Color.White)[i % 3].copy(alpha = (1f - reveal * 0.6f)), 3 * density, Offset(center.x + (cos(a) * d).toFloat(), center.y + (sin(a) * d).toFloat()))
                        }
                    }
                    Text(gift.title, style = T.title.copy(fontSize = 24.sp), color = Color(0xFF3A1A14), textAlign = TextAlign.Center)
                    Text(gift.text, style = T.bodyRegular.copy(fontSize = 16.sp, lineHeight = 23.sp), color = Color(0xFF4A2E25), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "Schließen",
                        style = T.label,
                        color = Color.White,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Wine)
                            .clickable(onClick = onClose)
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
                // Zwei Türflügel, die aufschwingen.
                if (p.value < 1f) {
                    val angle = 115f * p.value
                    Row(Modifier.matchParentSize()) {
                        DoorWing(Modifier.weight(1f).fillMaxSize(), left = true, angle = angle, number = number)
                        DoorWing(Modifier.weight(1f).fillMaxSize(), left = false, angle = angle, number = number)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoorWing(modifier: Modifier, left: Boolean, angle: Float, number: Int) {
    Box(
        modifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                rotationY = if (left) -angle else angle
                cameraDistance = 14f * density
                alpha = if (angle > 100f) (115f - angle) / 15f else 1f
            }
            .background(Brush.verticalGradient(listOf(Wine, Color(0xFF6B1424))))
            .border(1.dp, Gold.copy(alpha = 0.6f)),
        contentAlignment = if (left) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Goldene Ranke und Knauf.
            val inset = 14 * density
            drawRoundRect(Gold.copy(alpha = 0.5f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), androidx.compose.ui.geometry.CornerRadius(10 * density), style = Stroke(2 * density))
            val kx = if (left) size.width - 12 * density else 12 * density
            drawCircle(Gold, 5 * density, Offset(kx, size.height / 2))
        }
        Text(
            if (left) "${number / 10}".takeIf { number >= 10 } ?: "" else "${number % 10}".takeIf { number >= 10 } ?: "$number",
            style = T.hero.copy(fontSize = 54.sp),
            color = Gold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** Zeichnet ein Motiv (Stern, Herz, Schneeflocke …) um [c] mit Radius [r]. */
fun DrawScope.drawMotif(m: Motif, c: Offset, r: Float) {
    when (m) {
        Motif.STAR -> {
            val p = Path()
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val rr = if (i % 2 == 0) r else r * 0.45f
                val x = c.x + (cos(a) * rr).toFloat()
                val y = c.y + (sin(a) * rr).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, Brush.radialGradient(listOf(Color(0xFFFFF1A8), Gold), c, r))
        }
        Motif.HEART -> {
            val p = Path().apply {
                moveTo(c.x, c.y + r * 0.85f)
                cubicTo(c.x - r * 1.3f, c.y - r * 0.1f, c.x - r * 0.6f, c.y - r * 1.05f, c.x, c.y - r * 0.4f)
                cubicTo(c.x + r * 0.6f, c.y - r * 1.05f, c.x + r * 1.3f, c.y - r * 0.1f, c.x, c.y + r * 0.85f)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color(0xFFFF8A9A), Color(0xFFE5484D)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f))
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.14f, Offset(c.x - r * 0.42f, c.y - r * 0.38f))
        }
        Motif.SNOWFLAKE -> {
            val col = Color(0xFF5AA9FF)
            for (k in 0 until 6) {
                val a = k * PI / 3
                val e = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                drawLine(col, c, e, r * 0.13f, StrokeCap.Round)
                val m2 = Offset(c.x + (cos(a) * r * 0.6f).toFloat(), c.y + (sin(a) * r * 0.6f).toFloat())
                for (s in listOf(-1, 1)) {
                    val b = a + s * PI / 4
                    drawLine(col, m2, Offset(m2.x + (cos(b) * r * 0.32f).toFloat(), m2.y + (sin(b) * r * 0.32f).toFloat()), r * 0.1f, StrokeCap.Round)
                }
            }
        }
        Motif.GIFT -> {
            drawRoundRect(Color(0xFFE5484D), Offset(c.x - r * 0.8f, c.y - r * 0.35f), Size(r * 1.6f, r * 1.15f), androidx.compose.ui.geometry.CornerRadius(r * 0.12f))
            drawRoundRect(Color(0xFFFF6B6B), Offset(c.x - r * 0.9f, c.y - r * 0.6f), Size(r * 1.8f, r * 0.35f), androidx.compose.ui.geometry.CornerRadius(r * 0.1f))
            drawRect(Gold, Offset(c.x - r * 0.12f, c.y - r * 0.6f), Size(r * 0.24f, r * 1.4f))
            drawOval(Gold, Offset(c.x - r * 0.55f, c.y - r * 0.95f), Size(r * 0.5f, r * 0.38f), style = Stroke(r * 0.12f))
            drawOval(Gold, Offset(c.x + r * 0.05f, c.y - r * 0.95f), Size(r * 0.5f, r * 0.38f), style = Stroke(r * 0.12f))
        }
        Motif.CUP -> {
            drawRoundRect(Color(0xFFB0643A), Offset(c.x - r * 0.6f, c.y - r * 0.3f), Size(r * 1.1f, r * 1.0f), androidx.compose.ui.geometry.CornerRadius(r * 0.2f))
            drawArc(Color(0xFFB0643A), -90f, 180f, false, Offset(c.x + r * 0.3f, c.y - r * 0.1f), Size(r * 0.5f, r * 0.5f), style = Stroke(r * 0.12f))
            drawOval(Color(0xFF6B3A1F), Offset(c.x - r * 0.52f, c.y - r * 0.38f), Size(r * 0.94f, r * 0.22f))
            for (k in 0 until 3) {
                val x = c.x - r * 0.3f + k * r * 0.3f
                drawArc(Color(0xFFBBBBBB), 180f, 180f, false, Offset(x - r * 0.08f, c.y - r * 0.95f), Size(r * 0.16f, r * 0.4f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
        }
        Motif.TREE -> {
            for (k in 0 until 3) {
                val top = c.y - r + k * r * 0.45f
                val half = r * (0.45f + k * 0.22f)
                val p = Path().apply {
                    moveTo(c.x, top)
                    lineTo(c.x + half, top + r * 0.7f)
                    lineTo(c.x - half, top + r * 0.7f)
                    close()
                }
                drawPath(p, Color(0xFF2E8B57))
            }
            drawRect(Color(0xFF8B5A2B), Offset(c.x - r * 0.12f, c.y + r * 0.6f), Size(r * 0.24f, r * 0.35f))
            listOf(Offset(-0.25f, -0.1f), Offset(0.3f, 0.25f), Offset(-0.35f, 0.45f), Offset(0.1f, -0.45f)).forEachIndexed { i, o ->
                drawCircle(listOf(Color(0xFFE5484D), Gold, Color(0xFF5AA9FF), Color.White)[i], r * 0.09f, Offset(c.x + o.x * r, c.y + o.y * r))
            }
        }
        Motif.MOON -> {
            val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r * 0.85f)) }
            val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.42f, c.y - r * 0.3f), r * 0.72f)) }
            val crescent = Path().apply { op(moon, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
            drawPath(crescent, Brush.radialGradient(listOf(Color(0xFFFFF6CF), Color(0xFFFFD873)), c, r))
            drawCircle(Gold, r * 0.08f, Offset(c.x + r * 0.7f, c.y - r * 0.75f))
            drawCircle(Gold, r * 0.06f, Offset(c.x + r * 0.9f, c.y + r * 0.2f))
        }
        Motif.CANDLE -> {
            drawRoundRect(Color(0xFFF7F0E1), Offset(c.x - r * 0.25f, c.y - r * 0.2f), Size(r * 0.5f, r * 1.1f), androidx.compose.ui.geometry.CornerRadius(r * 0.08f))
            drawLine(Color(0xFF3B3B3B), Offset(c.x, c.y - r * 0.2f), Offset(c.x, c.y - r * 0.35f), r * 0.06f)
            val flame = Path().apply {
                moveTo(c.x, c.y - r * 0.95f)
                quadraticBezierTo(c.x + r * 0.3f, c.y - r * 0.5f, c.x, c.y - r * 0.32f)
                quadraticBezierTo(c.x - r * 0.3f, c.y - r * 0.5f, c.x, c.y - r * 0.95f)
                close()
            }
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFC94D), Color.Transparent), Offset(c.x, c.y - r * 0.55f), r * 0.7f), r * 0.7f, Offset(c.x, c.y - r * 0.55f))
            drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF8C3A)), c.y - r * 0.95f, c.y - r * 0.32f))
        }
        Motif.SUN -> {
            for (i in 0 until 12) {
                val a = i * PI / 6
                drawLine(
                    Color(0xFFFFB73A),
                    Offset(c.x + (cos(a) * r * 0.62f).toFloat(), c.y + (sin(a) * r * 0.62f).toFloat()),
                    Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
                    r * 0.13f,
                    StrokeCap.Round,
                )
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A3), Color(0xFFFFB73A)), c, r * 0.55f), r * 0.5f, c)
        }
    }
}
