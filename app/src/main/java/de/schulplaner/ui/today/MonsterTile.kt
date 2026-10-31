package de.schulplaner.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.theme.AppFont
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Neon = Color(0xFF9BFF1F)
private val NeonDeep = Color(0xFF5BD000)

/**
 * Monster-Zähler: schwarze Kachel mit neongrünen Krallen (eigene Zeichnung im Monster-Stil).
 * Tippen = +1 (Krallen reißen auf, Zahl rollt hoch), lange drücken = −1.
 */
@Composable
fun MonsterTile(modifier: Modifier = Modifier) {
    val repo = LocalContext.current.app.repo
    val settings by repo.settings.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    fun countOn(day: Long) = settings[SettingKeys.MONSTER_PREFIX + day]?.toIntOrNull() ?: 0
    val count = countOn(today)
    val monday = de.schulplaner.domain.Schedule.mondayOf(LocalDate.now()).toEpochDay()
    val week = (monday..today).sumOf { countOn(it) }

    val scope = rememberCoroutineScope()
    val slash = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "monster")
    val breathe by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "glow")

    fun hit(delta: Int) {
        repo.addMonster(delta)
        if (delta > 0) {
            scope.launch {
                slash.snapTo(0f)
                slash.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(700))
            }
        }
    }

    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .pressable({ hit(1) }, onLongClick = { if (count > 0) hit(-1) })
            .shadow(14.dp, shape, ambientColor = Neon.copy(alpha = 0.6f), spotColor = Neon.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF151A12), Color(0xFF050605))))
            .drawBehind {
                // Grünes Leuchten von links, beim Tippen kurz heller.
                val a = (breathe * 0.35f + flash.value * 0.5f).coerceAtMost(1f)
                drawCircle(Brush.radialGradient(listOf(Neon.copy(alpha = a), Color.Transparent), Offset(size.height * 0.6f, size.height * 0.5f), size.height * 1.3f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Neon.copy(alpha = 0.55f), Neon.copy(alpha = 0.1f))), shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(
            Modifier
                .size(58.dp)
                .graphicsLayer {
                    val s = 1f + flash.value * 0.12f
                    scaleX = s
                    scaleY = s
                },
        ) { drawClaws(slash.value) }
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 500f)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(180)) { if (up) -it else it } + fadeOut(tween(150)))
                },
                label = "monster-count",
            ) { n ->
                Text(
                    "$n",
                    style = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 46.sp, lineHeight = 48.sp),
                    color = Neon,
                )
            }
            Text(if (count == 1) "Dose heute" else "Dosen heute", style = T.smallBold, color = Color.White.copy(alpha = 0.7f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$week", style = T.section.copy(fontSize = 22.sp), color = Color.White)
            Text("diese Woche", style = T.small, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

/** Drei gezackte Krallen-Kratzer; [progress] 0→1 lässt sie von oben nach unten aufreißen. */
private fun DrawScope.drawClaws(progress: Float) {
    val w = size.width
    val h = size.height
    fun claw(cx: Float, top: Float, angle: Float, delay: Float) {
        val p = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (p <= 0f) return
        val path = Path().apply {
            // Grob gezackter, nach unten spitz zulaufender Streifen (Breite ~12 % der Kachel).
            val half = w * 0.065f
            val pts = listOf(
                -1.0f to 0.00f, -1.15f to 0.16f, -0.65f to 0.20f, -0.95f to 0.40f, -0.45f to 0.45f,
                -0.65f to 0.66f, -0.20f to 0.71f, -0.15f to 0.92f, 0.05f to 1.0f,
                0.25f to 0.92f, 0.35f to 0.71f, 0.75f to 0.66f, 0.55f to 0.45f, 1.0f to 0.40f,
                0.80f to 0.20f, 1.2f to 0.16f, 1.0f to 0.0f,
            )
            val len = h - top
            pts.forEachIndexed { i, (dx, dy) ->
                val x = cx + dx * half
                val y = top + dy * len
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        rotate(angle, Offset(cx, h)) {
            clipRect(top = 0f, bottom = top + (h - top) * p) {
                drawPath(path, Brush.verticalGradient(listOf(Neon, NeonDeep), startY = top, endY = h))
            }
        }
    }
    claw(w * 0.2f, h * 0.06f, -7f, 0f)
    claw(w * 0.5f, 0f, 0f, 0.12f)
    claw(w * 0.8f, h * 0.06f, 7f, 0.24f)
}
