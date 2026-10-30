package de.schulplaner.ui.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.LessonOverride
import de.schulplaner.data.LessonSlot
import de.schulplaner.data.SubjectNote
import de.schulplaner.data.WeekType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.Field
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.Stepper
import de.schulplaner.ui.components.SubjectPicker
import de.schulplaner.ui.components.TaskRow
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import de.schulplaner.ui.today.SubjectBadge
import java.time.LocalDate

/** Antippen einer Stunde: HA eintragen, Entfall/Raum/Vertretung für diesen Tag, Notizen zum Fach. */
@Composable
fun LessonSheet(lesson: Lesson, onDismiss: () -> Unit, onAddTask: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val notes by repo.notes.collectAsStateWithLifecycle()
    val epoch = lesson.date.toEpochDay()
    val base = lesson.override ?: LessonOverride(id = LessonOverride.idFor(lesson.slot.id, epoch), slotId = lesson.slot.id, date = epoch)
    var override by remember { mutableStateOf(base) }
    var newNote by remember { mutableStateOf("") }
    val subjectId = lesson.slot.subjectId

    AppSheet(onDismiss = onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SubjectBadge(lesson.subject, 50)
            Column(Modifier.weight(1f)) {
                Text(lesson.subject?.name ?: "?", style = T.title.copy(fontSize = T.section.fontSize * 1.3f), color = c.text)
                val parts = listOf(Fmt.dayDate(lesson.date), "${lesson.periodLabel} Std", lesson.room.takeIf { it.isNotBlank() }?.let { "R $it" })
                Text(parts.filterNotNull().joinToString(" · "), style = T.small.copy(fontSize = T.label.fontSize), color = c.sub)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Ic.Bookmark, "HA", c.accent, c.accentSoft, Modifier.weight(1f), onAddTask)
            ActionTile(
                Ic.XCircle,
                if (override.cancelled) "Findet statt" else "Entfall",
                c.warn,
                c.warnSoft,
                Modifier.weight(1f),
            ) {
                override = override.copy(cancelled = !override.cancelled)
                repo.saveOverride(override)
            }
        }

        FormLabel("Nur an diesem Tag")
        Field(override.room, { override = override.copy(room = it) }, "Anderer Raum")
        Field(override.note, { override = override.copy(note = it) }, "Vertretung / Hinweis")
        if (override.room != base.room || override.note != base.note) {
            PrimaryButton("Übernehmen", {
                repo.saveOverride(override)
                close()
            }, Modifier.fillMaxWidth())
        }

        val due = tasks.filter { it.dueDate == epoch && it.subjectId == subjectId }
        if (due.isNotEmpty()) {
            FormLabel("Bis zu dieser Stunde")
            due.forEach { t ->
                TaskRow(t.title, "HA", null, t.done, false, { repo.setTaskDone(t, !t.done) }, {})
            }
        }

        FormLabel("Notizen zum Fach")
        notes.filter { it.subjectId == subjectId }.forEach { n ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n.text, style = T.bodyRegular, color = c.text, modifier = Modifier.weight(1f))
                SoftIconButton(Ic.Close, "Notiz löschen", { repo.deleteNote(n) }, tint = c.sub, background = Color.Transparent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(newNote, { newNote = it }, "z. B. Buch mitbringen", Modifier.weight(1f))
            SoftIconButton(Ic.Plus, "Notiz hinzufügen", {
                if (newNote.isNotBlank()) {
                    repo.saveNote(SubjectNote(subjectId = subjectId, text = newNote.trim()))
                    newNote = ""
                }
            })
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    val animatedBg by animateColorAsState(bg, tween(200), label = "tile")
    Column(
        modifier
            .height(76.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(animatedBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = T.label, color = tint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Stunde im Wochenplan anlegen oder ändern. */
@Composable
fun SlotEditSheet(initial: LessonSlot, isNew: Boolean, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    var slot by remember { mutableStateOf(initial) }

    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            title = if (isNew) "Neue Stunde" else "Stunde",
            onSave = if (slot.subjectId.isNotBlank()) {
                {
                    repo.saveSlot(slot)
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    repo.deleteSlot(slot)
                    close()
                }
            } else {
                null
            },
        )
        if (subjects.isEmpty()) {
            Text("Lege zuerst Fächer in den Einstellungen an.", style = T.body, color = c.sub)
            return@AppSheet
        }
        FormLabel("Fach")
        SubjectPicker(subjects, slot.subjectId, { id -> if (id != null) slot = slot.copy(subjectId = id) }, allowNone = false)

        FormLabel("Tag")
        Segmented((1..5).map { it to Fmt.dayShort(it) }, slot.dayOfWeek, { slot = slot.copy(dayOfWeek = it) })

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FormLabel("Ab Stunde")
                val p = schedule.periodByNumber[slot.period]
                Text(p?.let { Fmt.minutes(it.startMinute) + " Uhr" } ?: "", style = T.small, color = c.sub, modifier = Modifier.padding(start = 4.dp))
            }
            Stepper(
                "${slot.period}.",
                onMinus = { slot = slot.copy(period = (slot.period - 1).coerceAtLeast(1)) },
                onPlus = { slot = slot.copy(period = (slot.period + 1).coerceAtMost(12)) },
            )
        }

        FormLabel("Länge")
        Segmented(listOf(1 to "Einzel", 2 to "Doppel", 3 to "3 Std"), slot.length, { slot = slot.copy(length = it) })

        FormLabel("Wann")
        Segmented(
            listOf(WeekType.ALL to "Jede Woche", WeekType.A to "A-Woche", WeekType.B to "B-Woche"),
            slot.weekType,
            { slot = slot.copy(weekType = it) },
        )

        val subject = subjects.firstOrNull { it.id == slot.subjectId }
        Field(slot.room, { slot = slot.copy(room = it) }, if (subject?.room?.isNotBlank() == true) "Raum (sonst ${subject?.room})" else "Raum")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(subjectColor(subject?.color)))
            Text(
                "Tipp: Fächer, die nur alle zwei Wochen sind, als A- oder B-Woche eintragen.",
                style = T.small,
                color = c.sub,
            )
        }
    }
}

@Suppress("unused")
private fun LocalDate.isWeekday() = dayOfWeek.value <= 5
