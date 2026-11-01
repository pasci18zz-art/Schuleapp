package de.schulplaner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFF4F5BD5), Color(0xFF8A5CE0), Color(0xFFFFB020), Color(0xFF3BD873),
    Color(0xFFFF6B8B), Color(0xFF5AB2FF), Color(0xFFFF8A4C),
)

class ConfettiBurst(val id: Long, val center: Offset?, val big: Boolean)

/** Konfetti für die ganze App: kleine Explosion an einer Stelle oder Regen über den ganzen Bildschirm. */
class ConfettiState {
    val bursts = mutableStateListOf<ConfettiBurst>()
    private var counter = 0L

    /** Kleines Konfetti an einer Stelle (z. B. beim Abhaken). */
    fun pop(center: Offset) {
        if (bursts.size > 8) bursts.removeAt(0)
        bursts.add(ConfettiBurst(++counter, center, big = false))
    }

    /** Großes Konfetti über den ganzen Bildschirm (z. B. ab 13 Punkten). */
    fun celebrate() {
        if (bursts.size > 8) bursts.removeAt(0)
        bursts.add(ConfettiBurst(++counter, null, big = true))
    }

    fun remove(b: ConfettiBurst) {
        bursts.remove(b)
    }
}

val LocalConfetti = staticCompositionLocalOf { ConfettiState() }

private class Particle(
    val x0: Float, val y0: Float, val vx: Float, val vy: Float,
    val size: Float, val rot0: Float, val spin: Float, val color: Color, val round: Boolean, val wobble: Float,
)

@Composable
fun ConfettiOverlay(state: ConfettiState) {
    state.bursts.toList().forEach { b ->
        key(b.id) { BurstLayer(b) { state.remove(b) } }
    }
}

@Composable
private fun BurstLayer(burst: ConfettiBurst, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val duration = if (burst.big) 3200 else 900
    val particles = remember {
        val r = Random(burst.id)
        if (burst.big) {
            // Zwei "Konfettikanonen" unten links und rechts.
            List(160) { i ->
                val left = i % 2 == 0
                Particle(
                    x0 = if (left) -0.02f else 1.02f,
                    y0 = 1.02f,
                    vx = (if (left) 1f else -1f) * (0.15f + r.nextFloat() * 0.75f),
                    vy = -(1.9f + r.nextFloat() * 1.0f),
                    size = 5f + r.nextFloat() * 7f,
                    rot0 = r.nextFloat() * 360f,
                    spin = (r.nextFloat() - 0.5f) * 1400f,
                    color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                    round = r.nextFloat() < 0.3f,
                    wobble = r.nextFloat() * 6f,
                )
            }
        } else {
            List(22) { i ->
                val angle = (i / 22f) * 2 * PI.toFloat() + r.nextFloat() * 0.3f
                val speed = 0.5f + r.nextFloat() * 0.6f
                Particle(
                    x0 = 0f, y0 = 0f,
                    vx = cos(angle) * speed, vy = sin(angle) * speed - 0.35f,
                    size = 3.5f + r.nextFloat() * 4f,
                    rot0 = r.nextFloat() * 360f,
                    spin = (r.nextFloat() - 0.5f) * 900f,
                    color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                    round = r.nextFloat() < 0.5f,
                    wobble = 0f,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        if (burst.big) delay(250) // Sheet erst schließen lassen
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val alpha = if (p > 0.75f) (1f - p) / 0.25f else 1f
        val d = density
        particles.forEach { pt ->
            val pos: Offset
            if (burst.big) {
                val x = (pt.x0 + pt.vx * p + sin(p * 10f + pt.wobble) * 0.015f) * size.width
                val y = (pt.y0 + pt.vy * p + 2.2f * p * p) * size.height
                pos = Offset(x, y)
            } else {
                val c = burst.center ?: return@forEach
                val reach = 70f * d
                val x = c.x + pt.vx * reach * (1f - (1f - p) * (1f - p)) * 1.2f
                val y = c.y + (pt.vy * reach * p + 90f * d * p * p)
                pos = Offset(x, y)
            }
            val s = pt.size * d
            val col = pt.color.copy(alpha = alpha)
            if (pt.round) {
                drawCircle(col, s / 2, pos)
            } else {
                rotate(pt.rot0 + pt.spin * p, pos) {
                    drawRect(col, Offset(pos.x - s / 2, pos.y - s * 0.3f), Size(s, s * 0.6f))
                }
            }
        }
    }
}
