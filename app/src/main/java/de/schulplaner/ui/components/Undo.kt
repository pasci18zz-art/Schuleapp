package de.schulplaner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.delay

class UndoEvent(val id: Long, val message: String, val undo: () -> Unit)

/** "Rückgängig"-Leiste: zeigt kurz eine Meldung mit Knopf. */
class UndoState {
    var current by mutableStateOf<UndoEvent?>(null)
        private set
    private var counter = 0L

    fun show(message: String, undo: () -> Unit) {
        current = UndoEvent(++counter, message, undo)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalUndo = staticCompositionLocalOf { UndoState() }

@Composable
fun UndoBar(state: UndoState, modifier: Modifier = Modifier) {
    val c = colors
    val event = state.current
    // Letzte Meldung merken, damit sie beim Ausblenden noch sichtbar bleibt.
    val last = remember { arrayOfNulls<UndoEvent>(1) }
    if (event != null) last[0] = event
    LaunchedEffect(event?.id) {
        val e = event ?: return@LaunchedEffect
        delay(4500)
        state.dismiss(e.id)
    }
    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val e = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.snackbar)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(e.message, style = T.label.copy(fontSize = T.body.fontSize * 0.93f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "Rückgängig",
                style = T.label.copy(fontSize = T.body.fontSize * 0.93f),
                color = Color(0xFFAEB6FF),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        e.undo()
                        state.dismiss(e.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
