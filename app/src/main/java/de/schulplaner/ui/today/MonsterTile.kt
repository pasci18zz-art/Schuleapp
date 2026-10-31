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
            .shadow(16.dp, shape, ambientColor = Berry, spotColor = Berry)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Blush, Berry, Strawberry, DeepBerry), start = Offset.Zero, end = Offset(900f, 700f)))
            .drawBehind { canTexture(shimmer) }
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun DrawScope.canTexture(shimmer: Float) {
    val w = size.width
    val h = size.height
    // Glanz wie auf Metall.
    val x = shimmer * w
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
            start = Offset(x - w * 0.25f, 0f),
            end = Offset(x + w * 0.05f, h),
        ),
    )
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.45f))
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

/** Drei kräftige, gezackte Krallen im Monster-Stil; [progress] 0→1 lässt sie von oben aufreißen. */
private fun DrawScope.drawLogo(progress: Float, glow: Float, flash: Float) {
    val w = size.width
    val h = size.height
    val outline = DeepBerry
    fun claw(cx: Float, top: Float, bottom: Float, angle: Float, half: Float, delay: Float) {
        val p = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (p <= 0f) return
        val len = bottom - top
        // Unregelmäßiger, nach unten spitz zulaufender Kratzer mit groben Zacken.
        val pts = listOf(
            -1.00f to 0.00f, -1.25f to 0.10f, -0.70f to 0.14f, -1.05f to 0.30f, -0.55f to 0.36f,
            -0.85f to 0.52f, -0.35f to 0.58f, -0.55f to 0.74f, -0.15f to 0.80f, -0.20f to 0.93f, 0.05f to 1.00f,
            0.30f to 0.90f, 0.30f to 0.80f, 0.70f to 0.74f, 0.50f to 0.58f, 0.95f to 0.52f,
            0.70f to 0.36f, 1.15f to 0.30f, 0.85f to 0.14f, 1.30f to 0.10f, 1.00f to 0.00f,
        )
        val path = Path().apply {
            pts.forEachIndexed { i, (dx, dy) ->
                val px = cx + dx * half * (1f - dy * 0.35f)
                val py = top + dy * len
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        rotate(angle, Offset(cx, bottom)) {
            clipRect(top = 0f, bottom = top + len * p) {
                // Leuchten.
                drawPath(path, Color.White.copy(alpha = 0.25f * glow + flash * 0.4f), style = Stroke(10.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, Brush.verticalGradient(listOf(Color.White, Cream, Blush), startY = top, endY = bottom))
                drawPath(path, outline.copy(alpha = 0.55f), style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
    val half = w * 0.085f
    claw(w * 0.2f, h * 0.10f, h * 0.98f, -9f, half, 0f)
    claw(w * 0.5f, h * 0.0f, h * 0.92f, 0f, half * 1.05f, 0.12f)
    claw(w * 0.8f, h * 0.10f, h * 0.98f, 9f, half, 0.24f)
}
