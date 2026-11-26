package de.schulplaner.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.AppFont

/** Lernzeit als Klappzahlen-Uhr: Minuten und Sekunden (ab einer Stunde zusätzlich Stunden). */
@Composable
fun FlipTimer(totalMs: Long, modifier: Modifier = Modifier, height: Dp = 96.dp, dim: Boolean = false) {
    val sec = totalMs / 1000
    val h = sec / 3600
    val m = (sec / 60) % 60
    val s = sec % 60
    val font = (height.value * 0.66f).sp
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val cardMod = Modifier.weight(1f).widthIn(max = 120.dp).height(height)
        if (h > 0) FlipCard("$h", cardMod, font, dim)
        FlipCard(m.toString().padStart(2, '0'), cardMod, font, dim)
        FlipCard(s.toString().padStart(2, '0'), cardMod, font, dim)
    }
}

/**
 * Eine Klappkarte im hellen Design: Bei neuem Wert fällt die obere Hälfte nach unten
 * und gibt die neue Zahl frei (wie bei einer Klappzahlen-Uhr).
 */
@Composable
fun FlipCard(value: String, modifier: Modifier = Modifier, fontSize: TextUnit = 64.sp, dim: Boolean = false) {
    val c = colors
    var shown by remember { mutableStateOf(value) }
    var old by remember { mutableStateOf(value) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) {
            old = shown
            shown = value
            p.snapTo(0f)
            p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val f = p.value
    val dark = c.isDark
    val cardBrush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF3A3D47), Color(0xFF2A2C34)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEFF5)))
    }
    val digit = if (dark) Color(0xFFF2F3F7) else Color(0xFF1C1F27)
    val style = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, lineHeight = fontSize)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .shadow(if (dark) 0.dp else 6.dp, shape, ambientColor = Color(0x33101828), spotColor = Color(0x33101828))
            .clip(shape)
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E5EC), shape),
    ) {
        val textColor = if (dim) digit.copy(alpha = 0.45f) else digit
        // Hinten oben schon die neue Zahl, unten noch die alte – bis die Klappe darüber gefallen ist.
        FlipHalf(shown, top = true, brush = cardBrush, style = style, color = textColor)
        FlipHalf(if (f < 1f) old else shown, top = false, brush = cardBrush, style = style, color = textColor)
        if (f < 0.5f) {
            FlipHalf(old, top = true, angle = -180f * f, brush = cardBrush, style = style, color = textColor)
        } else if (f < 1f) {
            FlipHalf(shown, top = false, angle = 180f * (1f - f), brush = cardBrush, style = style, color = textColor)
        }
        // Scharnier in der Mitte.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(if (dark) Color.Black.copy(alpha = 0.5f) else Color(0xFFD3D7E0)),
        )
    }
}

@Composable
private fun BoxScope.FlipHalf(text: String, top: Boolean, brush: Brush, style: TextStyle, color: Color, angle: Float = 0f) {
    val shade = (kotlin.math.abs(angle) / 90f).coerceIn(0f, 1f) * 0.3f
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
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color, maxLines = 1)
    }
}
