package de.schulplaner.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text, der nur an Leerzeichen umbricht: Ein Wort, das allein nicht in die Zeile passt,
 * wird mit "…" gekürzt statt mitten im Wort getrennt (keine einzelnen Buchstaben in neuen Zeilen).
 */
@Composable
fun WordSafeText(text: String, style: TextStyle, color: Color, maxLines: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val fitted = remember(text, maxW, style) {
            if (maxW <= 0) text else text.split(' ').joinToString(" ") { fitWord(measurer, it, style, maxW) }
        }
        Text(fitted, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

private fun fitWord(measurer: TextMeasurer, word: String, style: TextStyle, maxW: Int): String {
    if (word.isEmpty() || measurer.measure(word, style, softWrap = false).size.width <= maxW) return word
    var end = word.length - 1
    while (end > 1 && measurer.measure(word.take(end) + "…", style, softWrap = false).size.width > maxW) end--
    return word.take(end) + "…"
}
