package de.schulplaner.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import java.time.LocalDate
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Design-Themen: Standard oder eine Jahreszeit mit eigenen Farben und kleinen Animationen. */
enum class Season(val key: String, val label: String) {
    STANDARD("standard", "Standard"),
    SPRING("fruehling", "Frühling"),
    SUMMER("sommer", "Sommer"),
    AUTUMN("herbst", "Herbst"),
    WINTER("winter", "Winter"),
    XMAS("weihnachten", "Weihnachten"),
    ;

    companion object {
        const val AUTO = "saisonal"

        /** Welche Jahreszeit gerade ist (Weihnachten: 1.–26. Dezember). */
        fun forDate(d: LocalDate): Season = when (d.month) {
            Month.DECEMBER -> if (d.dayOfMonth <= 26) XMAS else WINTER
            Month.JANUARY, Month.FEBRUARY -> WINTER
            Month.MARCH, Month.APRIL, Month.MAY -> SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> SUMMER
            else -> AUTUMN
        }

        fun resolve(choice: String, date: LocalDate = LocalDate.now()): Season =
            if (choice == AUTO) forDate(date) else entries.firstOrNull { it.key == choice } ?: STANDARD
    }
}

val LocalSeason = staticCompositionLocalOf { Season.STANDARD }

/** Hintergrund-Farbflächen passend zum Thema. */
fun SchulColors.forSeason(s: Season): SchulColors {
    val d = isDark
    return when (s) {
        Season.STANDARD -> this
        Season.SPRING -> copy(
            orb1 = Color(if (d) 0x80C2507EL else 0xB8FF9EC7L),
            orb2 = Color(if (d) 0x5E5E9E3AL else 0xA8A7E07AL),
            orb3 = Color(if (d) 0x524A86C8L else 0x8C8FD3FFL),
        )
        Season.SUMMER -> copy(
            orb1 = Color(if (d) 0x80C78A1FL else 0xC2FFC94DL),
            orb2 = Color(if (d) 0x70C2553AL else 0xB8FF8A65L),
            orb3 = Color(if (d) 0x5221A09BL else 0x9945D6D0L),
        )
        Season.AUTUMN -> copy(
            orb1 = Color(if (d) 0x80B8641EL else 0xC2FFA94DL),
            orb2 = Color(if (d) 0x70A83E22L else 0xB8E0603AL),
            orb3 = Color(if (d) 0x52907A2AL else 0x80C9A640L),
        )
        Season.WINTER -> copy(
            orb1 = Color(if (d) 0x803A6EC8L else 0xC28EC5FFL),
            orb2 = Color(if (d) 0x605F4FB8L else 0xA8B9A6FFL),
            orb3 = Color(if (d) 0x52368FA8L else 0x80BFEFFFL),
        )
        Season.XMAS -> copy(
            orb1 = Color(if (d) 0x80A8282EL else 0xB0E5484DL),
            orb2 = Color(if (d) 0x701E7A44L else 0x9E3FAE6BL),
            orb3 = Color(if (d) 0x60B08A1EL else 0x99FFC94DL),
        )
    }
}

private fun rnd(i: Int, salt: Int): Float {
    var x = (i * 374761393 + salt * 668265263)
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 0xFFFFFF.toFloat()
}

/**
 * Zeichnet die Teilchen des Themas: fallende Blätter, Schnee, Blüten oder Funkeln.
 * [t] läuft in einer Minute von 0 bis 1; alle Bewegungen sind so gewählt, dass der Übergang nahtlos ist.
 * [front] = wenige, größere Teilchen vor den Inhalten.
 */
fun DrawScope.seasonParticles(season: Season, t: Float, dark: Boolean, front: Boolean) {
    if (season == Season.STANDARD) return
    val w = size.width
    val h = size.height
    val dp = density
    val count = when {
        front -> if (season == Season.SUMMER) 5 else 7
        season == Season.SUMMER -> 22
        else -> 26
    }
    val salt = if (front) 99 else 7
    for (i in 0 until count) {
        val r1 = rnd(i, salt)
        val r2 = rnd(i, salt + 1)
        val r3 = rnd(i, salt + 2)
        val cycles = 2 + (r2 * 4).toInt() // ganze Zahl → nahtlos
        val phase = (t * cycles + r1) % 1f
        val sway = sin((t * cycles + r3) * 2 * PI).toFloat()
        val scale = (if (front) 1.25f else 0.75f) + r3 * 0.6f
        when (season) {
            Season.WINTER, Season.XMAS -> {
                val x = w * r1 + sway * 22 * dp
                val y = -20 * dp + phase * (h + 40 * dp)
                val r = (2.2f + r2 * 3.2f) * dp * scale
                val a = if (dark) 0.85f else 0.95f
                if (r > 4.2f * dp) snowflake(Offset(x, y), r * 1.6f, 360f * t * cycles * 0.25f, Color.White.copy(alpha = a))
                else {
                    drawCircle(Color.White.copy(alpha = a * 0.35f), r * 1.9f, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
                }
            }
            Season.AUTUMN -> {
                val x = w * r1 + sway * 40 * dp
                val y = -30 * dp + phase * (h + 60 * dp)
                val colors = listOf(Color(0xFFE8833A), Color(0xFFD9542F), Color(0xFFF2B33D), Color(0xFFA8572B), Color(0xFFC9772E))
                leaf(Offset(x, y), 11 * dp * scale, sway * 50f + 360f * t * cycles * 0.5f * (if (i % 2 == 0) 1 else -1), colors[i % colors.size].copy(alpha = if (front) 0.85f else 0.75f))
            }
            Season.SPRING -> {
                val x = w * r1 + sway * 30 * dp
                val y = -20 * dp + phase * (h + 40 * dp)
                val colors = listOf(Color(0xFFFFB3CF), Color(0xFFFFD1E0), Color(0xFFFF8FB8), Color(0xFFFFFFFF))
                blossom(Offset(x, y), 8 * dp * scale, 360f * t * cycles * 0.4f, colors[i % colors.size].copy(alpha = if (front) 0.9f else 0.8f))
            }
            Season.SUMMER -> {
                // Funkeln, das langsam nach oben schwebt und dabei aufblitzt.
                val x = w * r1 + sway * 16 * dp
                val y = h + 20 * dp - phase * (h + 40 * dp)
                val tw = (sin((t * cycles * 3 + r2) * 2 * PI).toFloat() + 1f) / 2f
                val col = if (i % 3 == 0) Color(0xFFFFF3B0) else if (i % 3 == 1) Color(0xFFFFD36E) else Color.White
                sparkle(Offset(x, y), (5f + 6f * tw) * dp * scale * 0.7f, col.copy(alpha = 0.35f + 0.6f * tw))
            }
            Season.STANDARD -> Unit
        }
    }
}

/** Lichterkette oben am Bildschirm (Weihnachten). */
fun DrawScope.fairyLights(t: Float, top: Float) {
    val w = size.width
    val dp = density
    val sag = 16 * dp
    val n = (w / (34 * dp)).toInt().coerceAtLeast(6)
    val wire = Path().apply {
        moveTo(0f, top)
        val seg = w / 3
        for (k in 0 until 3) cubicTo(k * seg + seg * 0.3f, top + sag, k * seg + seg * 0.7f, top + sag, (k + 1) * seg, top)
    }
    drawPath(wire, Color(0xFF2E4A2F).copy(alpha = 0.8f), style = Stroke(1.6f * dp))
    val colors = listOf(Color(0xFFFF4D4D), Color(0xFFFFC94D), Color(0xFF4DD17A), Color(0xFF5AA9FF), Color(0xFFFF7AD1))
    for (i in 0 until n) {
        val x = (i + 0.5f) * w / n
        val seg = w / 3
        val local = (x % seg) / seg
        val y = top + sag * 4 * local * (1 - local) * 0.75f + 6 * dp
        val col = colors[i % colors.size]
        val glow = (sin((t * 12 + i * 0.37f) * 2 * PI).toFloat() + 1f) / 2f
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * glow + 0.15f), Color.Transparent), Offset(x, y), 16 * dp), 16 * dp, Offset(x, y))
        drawRoundRect(Color(0xFF3B3B3B), Offset(x - 2 * dp, y - 6 * dp), Size(4 * dp, 3 * dp))
        drawOval(col.copy(alpha = 0.6f + 0.4f * glow), Offset(x - 3.5f * dp, y - 3 * dp), Size(7 * dp, 10 * dp))
        drawCircle(Color.White.copy(alpha = 0.6f * glow), 1.4f * dp, Offset(x - 1 * dp, y))
    }
}

private fun DrawScope.snowflake(c: Offset, r: Float, angle: Float, color: Color) {
    rotate(angle, c) {
        for (k in 0 until 6) {
            val a = k * PI / 3
            val e = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
            drawLine(color, c, e, r * 0.16f, StrokeCap.Round)
            val m = Offset(c.x + (cos(a) * r * 0.6f).toFloat(), c.y + (sin(a) * r * 0.6f).toFloat())
            for (s in listOf(-1, 1)) {
                val b = a + s * PI / 4
                drawLine(color, m, Offset(m.x + (cos(b) * r * 0.3f).toFloat(), m.y + (sin(b) * r * 0.3f).toFloat()), r * 0.12f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.leaf(c: Offset, r: Float, angle: Float, color: Color) {
    rotate(angle, c) {
        val p = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticBezierTo(c.x + r * 0.95f, c.y - r * 0.2f, c.x, c.y + r)
            quadraticBezierTo(c.x - r * 0.95f, c.y - r * 0.2f, c.x, c.y - r)
            close()
        }
        drawPath(p, color)
        drawLine(color.copy(alpha = 1f).darker(), Offset(c.x, c.y - r * 0.85f), Offset(c.x, c.y + r * 1.35f), r * 0.1f, StrokeCap.Round)
    }
}

private fun DrawScope.blossom(c: Offset, r: Float, angle: Float, color: Color) {
    rotate(angle, c) {
        for (k in 0 until 5) {
            val a = k * 2 * PI / 5
            val pc = Offset(c.x + (cos(a) * r * 0.55f).toFloat(), c.y + (sin(a) * r * 0.55f).toFloat())
            drawCircle(color, r * 0.48f, pc)
        }
        drawCircle(Color(0xFFFFD54F), r * 0.24f, c)
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawCircle(color.copy(alpha = color.alpha * 0.3f), r * 0.9f, c)
    drawPath(p, color)
}

private fun Color.darker() = Color(red * 0.6f, green * 0.6f, blue * 0.6f, alpha)
