package de.schulplaner.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Event
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.ColorRow
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.InlineTextField
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.Stepper
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/**
 * Termin anlegen oder ändern: Titel, Farbe, Datum, Uhrzeit als Text, betroffene Stunden (ersetzen den Unterricht), Raum, Notiz.
 */
@Composable
fun EventEditSheet(initial: Event, isNew: Boolean, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var event by remember { mutableStateOf(initial) }
    var showDate by remember { mutableStateOf(false) }
    val maxPeriod = schedule.maxPeriod.coerceAtLeast(2)
    val initialEvent = remember { initial }

    AppSheet(onDismiss = onDismiss, confirmDiscard = event != initialEvent) { close ->
        SheetHeader(
            title = if (isNew) "Neuer Termin" else "Termin",
            onSave = if (event.title.isNotBlank()) {
                {
                    repo.saveEvent(event.copy(title = event.title.trim()))
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    val gone = event
                    repo.deleteEvent(gone)
                    undo.show("Termin gelöscht") { repo.restoreEvent(gone) }
                    close()
                }
            } else {
                null
            },
        )
        InlineTextField(event.title, { event = event.copy(title = it) }, "Was ist los? (z. B. Ausflug, Prüfung)", Modifier.fillMaxWidth().padding(horizontal = 4.dp), big = true)

        FormLabel("Farbe")
        ColorRow(event.color, { event = event.copy(color = it) })

        FormGroup {
            FormRow("Datum", onClick = { showDate = true }) {
                Icon(Ic.CalendarPlain, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(Fmt.dayDate(LocalDate.ofEpochDay(event.date)), style = T.body, color = c.text, modifier = Modifier.weight(1f))
            }
            FormTextRow("Uhrzeit", event.timeText, { event = event.copy(timeText = it) }, placeholder = "z. B. 8:00–13:30")
            FormTextRow("Raum / Ort", event.room, { event = event.copy(room = it) }, placeholder = "optional")
            FormTextRow("Notiz", event.note, { event = event.copy(note = it) }, placeholder = "optional", singleLine = false, divider = false)
        }

        // Stunden: ersetzen den Unterricht an dem Tag.
        FormLabel("Ersetzt den Unterricht in den Stunden")
        FormGroup {
            val has = event.firstPeriod != null
            FormRow("Stunden", divider = has) {
                Text(
                    when {
                        !has -> "keine (nur Termin)"
                        event.lastPeriod == null || event.lastPeriod == event.firstPeriod -> "${event.firstPeriod}. Stunde"
                        else -> "${event.firstPeriod}.–${event.lastPeriod}. Stunde"
                    },
                    style = T.body,
                    color = if (has) c.text else c.sub,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (has) "Entfernen" else "Wählen",
                    style = T.label,
                    color = c.accent,
                    modifier = Modifier
                        .clickable {
                            event = if (has) event.copy(firstPeriod = null, lastPeriod = null) else event.copy(firstPeriod = 1, lastPeriod = 2)
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            if (has) {
                FormRow("Von", divider = true) {
                    Box(Modifier.weight(1f))
                    Stepper(
                        "${event.firstPeriod}.",
                        onMinus = {
                            val f = ((event.firstPeriod ?: 1) - 1).coerceAtLeast(1)
                            event = event.copy(firstPeriod = f)
                        },
                        onPlus = {
                            val f = ((event.firstPeriod ?: 1) + 1).coerceAtMost(maxPeriod)
                            event = event.copy(firstPeriod = f, lastPeriod = maxOf(f, event.lastPeriod ?: f))
                        },
                    )
                }
                FormRow("Bis", divider = false) {
                    Box(Modifier.weight(1f))
                    Stepper(
                        "${event.lastPeriod ?: event.firstPeriod}.",
                        onMinus = {
                            val l = ((event.lastPeriod ?: event.firstPeriod ?: 1) - 1).coerceAtLeast(event.firstPeriod ?: 1)
                            event = event.copy(lastPeriod = l)
                        },
                        onPlus = {
                            val l = ((event.lastPeriod ?: event.firstPeriod ?: 1) + 1).coerceAtMost(maxPeriod)
                            event = event.copy(lastPeriod = l)
                        },
                    )
                }
            }
        }
        if (event.firstPeriod != null) {
            Text(
                "In diesen Stunden steht an dem Tag dein Termin statt des Unterrichts.",
                style = T.small,
                color = c.sub,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (showDate) {
        DateDialog(LocalDate.ofEpochDay(event.date), onDismiss = { showDate = false }) { d ->
            event = event.copy(date = d.toEpochDay())
        }
    }
}
