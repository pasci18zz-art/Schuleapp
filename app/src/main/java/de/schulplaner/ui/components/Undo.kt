package de.schulplaner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
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
        delay(2600)
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
                .widthIn(max = 360.dp)
                .padding(horizontal = 18.dp)
                .shadow(if (c.isDark) 0.dp else 8.dp, RoundedCornerShape(50), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(50))
                .background(if (c.isDark) Color(0xFF2E323C) else Color.White)
                .border(1.dp, c.cardBorder, RoundedCornerShape(50))
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Check, null, tint = c.good, modifier = Modifier.size(16.dp))
            Text(
                e.message,
                style = T.label,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            )
            Text(
                "Rückgängig",
                style = T.label,
                color = c.accent,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        e.undo()
                        state.dismiss(e.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}
