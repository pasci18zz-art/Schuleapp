package de.schulplaner.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.schulplaner.data.ExamTopic
import de.schulplaner.ui.theme.T

/** Griff zum Verschieben (zwei mal drei Punkte). */
val GripIcon by lazy {
    Ic.icon(
        "grip",
        Ic.circle(9f, 6f, 0.6f), Ic.circle(15f, 6f, 0.6f),
        Ic.circle(9f, 12f, 0.6f), Ic.circle(15f, 12f, 0.6f),
        Ic.circle(9f, 18f, 0.6f), Ic.circle(15f, 18f, 0.6f),
        stroke = 2.4f,
    )
}

/**
 * Abhakbare Unterpunkte (Aufgaben) bzw. Themen (Klausuren):
 * antippen zum Bearbeiten, am Griff ziehen zum Umsortieren, Enter fügt hinzu und bleibt im Feld.
 */
@Composable
fun StepsEditor(
    steps: List<ExamTopic>,
    newText: String,
    onNewText: (String) -> Unit,
    onAdd: (String) -> Unit,
    onToggle: (ExamTopic) -> Unit,
    onRename: (ExamTopic, String) -> Unit,
    onRemove: (ExamTopic) -> Unit,
    onReorder: (List<ExamTopic>) -> Unit,
    placeholder: String,
    framed: Boolean = true,
) {
    val c = colors
    // Lokale Reihenfolge während des Ziehens; übernommen wird erst beim Loslassen.
    var order by remember { mutableStateOf(steps) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<String, Int>() }
    if (draggingId == null && order != steps) order = steps
    val currentOrder by rememberUpdatedState(order)
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val addFocus = remember { FocusRequester() }

    val content: @Composable () -> Unit = {
        order.forEach { st ->
            key(st.id) {
                val dragging = draggingId == st.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[st.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .then(if (dragging) Modifier.graphicsLayer { scaleX = 1.02f; scaleY = 1.02f } else Modifier)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckCircle(st.done, { onToggle(st) }, square = true, size = 22.dp, checkedColor = c.good)
                    if (editingId == st.id) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        InlineTextField(
                            editText,
                            { editText = it },
                            "Text",
                            Modifier.weight(1f).focusRequester(focus),
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                if (editText.isNotBlank() && editText != st.text) onRename(st, editText.trim())
                                editingId = null
                            },
                        )
                    } else {
                        Text(
                            st.text,
                            style = T.body.copy(textDecoration = if (st.done) TextDecoration.LineThrough else null),
                            color = if (st.done) c.sub else c.text,
                            modifier = Modifier.weight(1f).pressable({
                                editingId = st.id
                                editText = st.text
                            }),
                        )
                    }
                    SoftIconButton(Ic.Close, "Entfernen", { onRemove(st) }, tint = c.sub, background = Color.Transparent)
                    Box(
                        Modifier
                            .size(34.dp)
                            .pointerInput(st.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = st.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        onReorder(currentOrder)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = currentOrder
                                        val i = list.indexOfFirst { it.id == st.id }
                                        val h = heights[st.id] ?: return@detectDragGestures
                                        if (i < 0) return@detectDragGestures
                                        val self = list[i]
                                        if (dragOffset > h / 2f && i < list.lastIndex) {
                                            val next = list[i + 1]
                                            order = list.toMutableList().also { it[i] = next; it[i + 1] = self }
                                            dragOffset -= heights[next.id] ?: h
                                        } else if (dragOffset < -h / 2f && i > 0) {
                                            val prev = list[i - 1]
                                            order = list.toMutableList().also { it[i] = prev; it[i - 1] = self }
                                            dragOffset += heights[prev.id] ?: h
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(GripIcon, "Verschieben", tint = c.sub, modifier = Modifier.size(18.dp))
                    }
                }
                FormDivider()
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val add = {
                if (newText.isNotBlank()) {
                    onAdd(newText.trim())
                    onNewText("")
                }
            }
            InlineTextField(
                newText, onNewText,
                if (steps.isEmpty()) placeholder else "Weiterer Punkt",
                Modifier.weight(1f).focusRequester(addFocus),
                imeAction = ImeAction.Done,
                onImeAction = add,
            )
            SoftIconButton(Ic.Plus, "Hinzufügen", add)
        }
    }
    if (framed) FormGroup { content() } else androidx.compose.foundation.layout.Column { content() }
}
