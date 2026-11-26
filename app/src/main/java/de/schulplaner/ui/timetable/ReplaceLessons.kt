package de.schulplaner.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.schulplaner.app
import de.schulplaner.data.Event
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.ColorRow
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.InlineTextField
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/** Eine Stunde im Auswahlmodus: leicht umrandet, gewählt kräftig mit Haken. */
@Composable
internal fun PickCell(period: Int, selected: Boolean, onClick: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .offset(y = (RowH + Gap) * (period - 1))
            .fillMaxWidth()
            .height(RowH)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.32f) else c.text.copy(alpha = 0.04f))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (selected) {
            Box(
                Modifier.padding(4.dp).size(18.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Check, null, tint = c.accentInk, modifier = Modifier.size(12.dp)) }
        }
    }
}

/** Leiste unten im Auswahlmodus: Anzahl, Abbrechen, Ersetzen. */
@Composable
internal fun ReplaceBar(count: Int, onCancel: () -> Unit, onReplace: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(shape)
                .background(c.sheetGlass)
                .border(1.dp, c.cardBorder, shape)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Ic.Close,
                "Abbrechen",
                tint = c.sub,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onCancel).padding(10.dp),
            )
            Text(
                when (count) {
                    0 -> "Stunden antippen …"
                    1 -> "1 Stunde gewählt"
                    else -> "$count Stunden gewählt"
                },
                style = T.body,
                color = if (count == 0) c.sub else c.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            PrimaryButton("Ersetzen", onReplace, icon = Ic.Swap, enabled = count > 0)
        }
    }
}

/** Zusammenhängende Stunden je Tag zu Blöcken zusammenfassen (1,2,3,5 → 1–3 und 5). */
internal fun pickedBlocks(picked: Set<Pair<Long, Int>>): List<Triple<Long, Int, Int>> =
    picked.groupBy({ it.first }, { it.second }).toSortedMap().flatMap { (day, periods) ->
        val sorted = periods.sorted()
        val out = mutableListOf<Triple<Long, Int, Int>>()
        var start = sorted.first()
        var prev = start
        for (p in sorted.drop(1)) {
            if (p != prev + 1) {
                out += Triple(day, start, prev)
                start = p
            }
            prev = p
        }
        out += Triple(day, start, prev)
        out
    }

/** Was kommt statt des Unterrichts? Titel, Farbe, Zeit, Raum – gilt für alle gewählten Stunden. */
@Composable
internal fun ReplaceSheet(picked: Set<Pair<Long, Int>>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    var template by remember { mutableStateOf(Event(title = "")) }
    val blocks = remember(picked) { pickedBlocks(picked) }
    val done = remember { booleanArrayOf(false) }

    AppSheet(onDismiss = { if (done[0]) onDone() else onDismiss() }, confirmDiscard = !done[0] && template.title.isNotBlank()) { close ->
        SheetHeader(
            title = "Stunden ersetzen",
            saveLabel = "Ersetzen",
            onSave = if (template.title.isNotBlank()) {
                {
                    val created = blocks.map { (day, from, to) ->
                        template.copy(id = de.schulplaner.data.newId(), title = template.title.trim(), date = day, firstPeriod = from, lastPeriod = to)
                    }
                    created.forEach { repo.saveEvent(it) }
                    undo.show(if (created.size == 1) "Stunden ersetzt" else "${created.size} Blöcke ersetzt") {
                        created.forEach { repo.deleteEvent(it) }
                    }
                    done[0] = true
                    close()
                }
            } else {
                null
            },
        )
        InlineTextField(template.title, { template = template.copy(title = it) }, "Was ist stattdessen? (z. B. Projekttag)", Modifier.fillMaxWidth().padding(horizontal = 4.dp), big = true)

        // Übersicht der gewählten Stunden.
        Text(
            blocks.joinToString("  ·  ") { (day, from, to) ->
                Fmt.dayShort(LocalDate.ofEpochDay(day)) + " " + if (from == to) "$from." else "$from.–$to."
            },
            style = T.small,
            color = c.sub,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        FormLabel("Farbe")
        ColorRow(template.color, { template = template.copy(color = it) })

        FormGroup {
            FormTextRow("Uhrzeit", template.timeText, { template = template.copy(timeText = it) }, placeholder = "z. B. 8:00–13:30")
            FormTextRow("Raum / Ort", template.room, { template = template.copy(room = it) }, placeholder = "optional")
            FormTextRow("Notiz", template.note, { template = template.copy(note = it) }, placeholder = "optional", singleLine = false, divider = false)
        }
    }
}
