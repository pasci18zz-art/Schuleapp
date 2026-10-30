package de.schulplaner.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
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
import de.schulplaner.data.OwnerType
import de.schulplaner.data.Task
import de.schulplaner.data.TaskType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.ScheduleData
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.ChoiceChip
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.Field
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SubjectPicker
import java.time.LocalDate
import java.time.LocalDateTime

object TaskDefaults {
    /** Neue HA für die aktuelle (oder gerade beendete) Stunde, fällig zur nächsten Stunde des Fachs. */
    fun quick(now: LocalDateTime, data: ScheduleData): Task {
        val subjectId = Schedule.currentOrLastLesson(now, data)?.slot?.subjectId
        return forSubject(subjectId, now.toLocalDate(), data)
    }

    /** Neue HA für ein Fach, fällig zur nächsten Stunde nach [from]. */
    fun forSubject(subjectId: String?, from: LocalDate, data: ScheduleData): Task {
        val due = subjectId?.let { Schedule.nextOccurrence(it, from, data)?.date }
        return Task(subjectId = subjectId, dueDate = due?.toEpochDay(), type = TaskType.HOMEWORK)
    }
}

private enum class DueMode { NEXT, TOMORROW, CUSTOM, NONE }

/**
 * Aufgabe anlegen/bearbeiten. Bei neuen Aufgaben mit Fach ist "Nächste Stunde" vorausgewählt;
 * wechselt man das Fach, wandert das Datum mit.
 */
@Composable
fun TaskEditSheet(initial: Task, isNew: Boolean, onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    fun nextDate(subjectId: String?): LocalDate? =
        subjectId?.let { Schedule.nextOccurrence(it, today, schedule)?.date }

    var task by remember { mutableStateOf(initial) }
    var mode by remember {
        mutableStateOf(
            when {
                initial.dueDate == null -> DueMode.NONE
                isNew && initial.subjectId != null && initial.dueDate == nextDate(initial.subjectId)?.toEpochDay() -> DueMode.NEXT
                initial.dueDate == today.plusDays(1).toEpochDay() -> DueMode.TOMORROW
                else -> DueMode.CUSTOM
            },
        )
    }
    var showDate by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }

    AppSheet(onDismiss = {
        if (isNew && !saved[0]) repo.discardAttachments(task.id)
        onDismiss()
    }) { close ->
        SheetHeader(
            title = if (isNew) "Neue Aufgabe" else "Aufgabe",
            onSave = if (task.title.isNotBlank()) {
                {
                    saved[0] = true
                    repo.saveTask(task.copy(title = task.title.trim()))
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    saved[0] = true
                    repo.deleteTask(task)
                    close()
                }
            } else {
                null
            },
        )
        Field(task.title, { task = task.copy(title = it) }, "Was ist zu tun?")

        FormLabel("Fach")
        SubjectPicker(subjects, task.subjectId, { id ->
            task = task.copy(subjectId = id)
            if (mode == DueMode.NEXT) {
                val d = nextDate(id)
                task = task.copy(dueDate = d?.toEpochDay())
                if (d == null) mode = DueMode.NONE
            }
        })

        FormLabel("Art")
        Segmented(
            listOf(TaskType.HOMEWORK to "HA", TaskType.EXTRA to "Zusatz", TaskType.OTHER to "Sonstiges"),
            task.type,
            { task = task.copy(type = it) },
        )

        FormLabel("Fällig")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = nextDate(task.subjectId)
            if (next != null) {
                ChoiceChip("Nächste Stunde · ${Fmt.dayDate(next)}", mode == DueMode.NEXT, {
                    mode = DueMode.NEXT
                    task = task.copy(dueDate = next.toEpochDay())
                })
            }
            ChoiceChip("Morgen", mode == DueMode.TOMORROW, {
                mode = DueMode.TOMORROW
                task = task.copy(dueDate = today.plusDays(1).toEpochDay())
            })
            val customLabel = if (mode == DueMode.CUSTOM && task.dueDate != null) {
                Fmt.dayDate(LocalDate.ofEpochDay(task.dueDate!!))
            } else {
                "Datum …"
            }
            ChoiceChip(customLabel, mode == DueMode.CUSTOM, { showDate = true })
            ChoiceChip("Ohne", mode == DueMode.NONE, {
                mode = DueMode.NONE
                task = task.copy(dueDate = null)
            })
        }

        Field(task.description, { task = task.copy(description = it) }, "Notizen", singleLine = false, minLines = 2)

        FormLabel("Anhänge")
        AttachmentStrip(OwnerType.TASK, task.id, attachments.filter { it.ownerId == task.id })
    }

    if (showDate) {
        DateDialog(
            initial = task.dueDate?.let { LocalDate.ofEpochDay(it) } ?: today.plusDays(1),
            onDismiss = { showDate = false },
        ) { d ->
            mode = DueMode.CUSTOM
            task = task.copy(dueDate = d.toEpochDay())
        }
    }
}
