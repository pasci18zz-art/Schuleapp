package de.schulplaner.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.SettingKeys
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.theme.AppFont
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

// Farben angelehnt an "Ultra Strawberry": helles Erdbeer-Pink bis kräftiges Beerenrot.
private val Blush = Color(0xFFFFB3C8)
private val Berry = Color(0xFFFF3D74)
private val Strawberry = Color(0xFFE0123F)
private val DeepBerry = Color(0xFF8A0A2C)
private val Cream = Color(0xFFFFF4F7)

/**
 * Monster-Zähler (ca. 3×2): Erdbeer-Look mit Dosen-Glanz und Kratzern, großes Krallen-Logo (eigene Zeichnung).
 * Tippen = +1 (Kachel wackelt, Krallen reißen auf, Funken sprühen, Zahl springt hoch), lange drücken = −1.
 */
@Composable
fun MonsterTile(modifier: Modifier = Modifier) {
    val repo = LocalContext.current.app.repo
    val settings by repo.settings.collectAsStateWithLifecycle()
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    fun countOn(day: Long) = settings[SettingKeys.MONSTER_PREFIX + day]?.toIntOrNull() ?: 0
    val count = countOn(today)
    val monday = de.schulplaner.domain.Schedule.mondayOf(todayDate).toEpochDay()
    val weekDays = (0 until 7).map { monday + it }
    val week = weekDays.filter { it <= today }.sumOf { countOn(it) }

    // Im Dunkelmodus gedämpfte Beerentöne statt knalligem Pink.
    val dark = de.schulplaner.ui.components.colors.isDark
    val bg = if (dark) listOf(Color(0xFF6E3049), Color(0xFF7E1C3E), Color(0xFF5E0D27), Color(0xFF2A0611)) else listOf(Blush, Berry, Strawberry, DeepBerry)
    val glowColor = if (dark) Color(0xFF8E1F45) else Berry
    var editDay by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val slash = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val t = rememberInfiniteTransition(label = "monster")
    val shimmer by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "glow")

    fun hit(delta: Int) {
        repo.addMonster(delta)
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = if (delta > 0) 460 else 260
                    (if (delta > 0) -5f else -2f) at 60
                    (if (delta > 0) 5f else 2f) at 140
                    -3f at 220
                    2f at 300
                    0f at durationMillis
                },
            )
        }
        if (delta > 0) {
            scope.launch {
                slash.snapTo(0f)
                slash.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            }
            scope.launch {
                flash.snapTo(0.85f)
                flash.animateTo(0f, tween(650))
            }
            scope.launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            scope.launch {
                pop.snapTo(1.35f)
                pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
            }
        }
    }

    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(188.dp)
            .graphicsLayer { rotationZ = shake.value }
            .pressable({ hit(1) }, onLongClick = { if (count > 0) hit(-1) })
            .shadow(if (dark) 6.dp else 16.dp, shape, ambientColor = glowColor, spotColor = glowColor)
            .clip(shape)
            .background(Brush.linearGradient(bg, start = Offset.Zero, end = Offset(900f, 700f)))
            .drawBehind { canTexture(shimmer, if (dark) 0.45f else 1f) }
            .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), shape),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Großes Krallen-Logo mit Leuchten.
            Canvas(
                Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        val s = 1f + flash.value * 0.12f
                        scaleX = s
                        scaleY = s
                    },
            ) { drawLogo(slash.value, glow, flash.value) }
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.55f, stiffness = 480f)) { if (up) it else -it } + fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                            (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(140)))
                    },
                    label = "monster-count",
                ) { n ->
                    Text(
                        "$n",
                        style = TextStyle(
                            fontFamily = AppFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 64.sp,
                            lineHeight = 64.sp,
                            shadow = Shadow(DeepBerry.copy(alpha = 0.6f), Offset(0f, 6f), 14f),
                        ),
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                    )
                }
                Text(if (count == 1) "Dose heute" else "Dosen heute", style = T.smallBold.copy(fontSize = 13.sp), color = Cream.copy(alpha = 0.9f))
                // Kleine Wochenleiste Mo–So.
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    val max = weekDays.maxOf { countOn(it) }.coerceAtLeast(3)
                    weekDays.forEach { d ->
                        val n = countOn(d)
                        // Tag antippen = Anzahl korrigieren.
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .then(if (d <= today) Modifier.clickable { editDay = d } else Modifier)
                                .padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .width(12.dp)
                                    .height((4 + 26f * n / max).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (d == today) Color.White else Color.White.copy(alpha = if (d > today) 0.18f else 0.5f)),
                            )
                            Text(
                                Fmt.dayShort(LocalDate.ofEpochDay(d)).take(1),
                                style = T.small.copy(fontSize = 9.sp),
                                color = Cream.copy(alpha = if (d == today) 1f else 0.7f),
                            )
                        }
                    }
                    Text("  $week diese Woche", style = T.smallBold, color = Cream, modifier = Modifier.padding(bottom = 14.dp))
                }
            }
        }
        editDay?.let { d ->
            var value by remember(d) { androidx.compose.runtime.mutableIntStateOf(countOn(d)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editDay = null },
                title = { Text(Fmt.full(LocalDate.ofEpochDay(d))) },
                text = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        de.schulplaner.ui.components.Stepper("$value", onMinus = { value = (value - 1).coerceAtLeast(0) }, onPlus = { value++ })
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        repo.setMonster(d, value)
                        editDay = null
                    }) { Text("Speichern") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { editDay = null }) { Text("Abbrechen") } },
            )
        }
        // Funken und Erdbeer-Tropfen, die beim Tippen aus dem Logo spritzen, plus weißer Blitz.
        Canvas(Modifier.fillMaxSize()) {
            if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value * 0.35f))
            val p = burst.value
            if (p < 1f) {
                val origin = Offset(18.dp.toPx() + 62.dp.toPx(), size.height / 2)
                for (i in 0 until 18) {
                    val ang = Math.toRadians(i * 20.0 + (i % 3) * 7.0)
                    val dist = (60 + (i % 4) * 26).dp.toPx() * p
                    val pos = origin + Offset((cos(ang) * dist).toFloat(), (sin(ang) * dist).toFloat() + 30.dp.toPx() * p * p)
                    val r = (3 + i % 3 * 1.6f).dp.toPx() * (1f - p * 0.5f)
                    drawCircle(if (i % 2 == 0) Color.White else Blush, r, pos, alpha = 1f - p)
                }
            }
        }
    }
}

/** Dosen-Look: Glanzstreifen, der langsam vorbeizieht, feine Kratzer und Erdbeer-Kerne. */
private fun DrawScope.canTexture(shimmer: Float, strength: Float = 1f) {
    val w = size.width
    val h = size.height
    // Glanz wie auf Metall.
    val x = shimmer * w
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f * strength), Color.Transparent),
            start = Offset(x - w * 0.25f, 0f),
            end = Offset(x + w * 0.05f, h),
        ),
    )
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * strength), Color.Transparent), endY = h * 0.45f))
    // Kratzer (wie von Krallen) rechts oben.
    val scratch = Color.White.copy(alpha = 0.12f)
    for (i in 0 until 4) {
        val sx = w * (0.7f + i * 0.07f)
        drawLine(scratch, Offset(sx, h * 0.05f), Offset(sx - w * 0.12f, h * 0.55f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
    // Erdbeer-Kerne.
    for (i in 0 until 22) {
        val seed = (i * 7919) % 1000 / 1000f
        val px = w * ((seed * 13.7f + i * 0.13f) % 1f)
        val py = h * ((seed * 5.3f + i * 0.29f) % 1f)
        drawOval(Color(0xFFFFE08A).copy(alpha = 0.35f), topLeft = Offset(px, py), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 4.5.dp.toPx()))
    }
}

/**
 * Drei lange, ausgefranste Krallen-Kratzer (eigene Zeichnung im Monster-Stil, kein Original-Logo):
 * oben breit mit gezackten Spitzen, Ränder unregelmäßig eingerissen, unten in dünne Fetzen auslaufend.
 * [progress] 0→1 lässt sie von oben nach unten aufreißen.
 */
private fun DrawScope.drawLogo(progress: Float, glow: Float, flash: Float) {
    val w = size.width
    val h = size.height
    // Feste Pseudo-Zufallswerte, damit die Kratzer immer gleich aussehen.
    fun jitter(seed: Int): Float = (((seed * 7919 + 104729) % 1000) / 1000f) * 2f - 1f
    fun claw(index: Int, cx: Float, top: Float, bottom: Float, angle: Float, half: Float, delay: Float) {
        val p = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (p <= 0f) return
        val len = bottom - top
        val steps = 22
        val path = Path()
        // Obere Kante: drei kleine Zacken.
        path.moveTo(cx - half, top + len * 0.03f)
        path.lineTo(cx - half * 0.45f, top)
        path.lineTo(cx - half * 0.1f, top + len * 0.035f)
        path.lineTo(cx + half * 0.35f, top - len * 0.01f)
        path.lineTo(cx + half, top + len * 0.04f)
        // Rechte Kante nach unten: verjüngt, ausgefranst.
        for (k in 1..steps) {
            val f = k / steps.toFloat()
            val taper = 1f - f * 0.72f
            val rough = jitter(index * 100 + k) * half * 0.28f + (if (k % 3 == 0) half * 0.22f else 0f)
            path.lineTo(cx + half * taper + rough, top + len * f)
        }
        // Unten zwei dünne Fetzen.
        path.lineTo(cx + half * 0.05f, bottom + len * 0.04f)
        path.lineTo(cx - half * 0.12f, bottom - len * 0.05f)
        path.lineTo(cx - half * 0.38f, bottom + len * 0.02f)
        // Linke Kante nach oben.
        for (k in steps downTo 1) {
            val f = k / steps.toFloat()
            val taper = 1f - f * 0.72f
            val rough = jitter(index * 100 + k + 50) * half * 0.28f - (if (k % 4 == 1) half * 0.25f else 0f)
            path.lineTo(cx - half * taper + rough, top + len * f)
        }
        path.close()
        rotate(angle, Offset(cx, bottom)) {
            clipRect(top = 0f, bottom = top + (len * 1.06f) * p) {
                // Leuchten, Füllung, feine dunkle Kante.
                drawPath(path, Color.White.copy(alpha = 0.22f * glow + flash * 0.4f), style = Stroke(11.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, Brush.verticalGradient(listOf(Color.White, Cream, Blush), startY = top, endY = bottom))
                drawPath(path, DeepBerry.copy(alpha = 0.5f), style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
    val half = w * 0.095f
    // Außen lang, Mitte etwas kürzer und tiefer – zusammen ein "M".
    claw(1, w * 0.19f, h * 0.02f, h * 0.96f, -4f, half, 0f)
    claw(2, w * 0.5f, h * 0.12f, h * 0.84f, 0f, half * 0.95f, 0.12f)
    claw(3, w * 0.81f, h * 0.02f, h * 0.96f, 4f, half, 0.24f)
}
