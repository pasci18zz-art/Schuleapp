package de.schulplaner.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.OwnerType
import de.schulplaner.data.Task
import de.schulplaner.data.TaskType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.ScheduleData
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.AttachButton
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.ChoiceChip
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.FormDivider
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.InlineTextField
import de.schulplaner.ui.components.KindIcons
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.StepsEditor
import de.schulplaner.ui.components.SubjectPicker
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
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

private enum class DueMode { NEXT, SECOND, CUSTOM }

/**
 * Aufgabe anlegen/bearbeiten. Fällig: nächste oder übernächste Stunde des Fachs oder ein freies Datum;
 * wechselt man das Fach, wandert das Datum mit. Unterpunkte sind optional und zeigen den Fortschritt.
 */
@Composable
fun TaskEditSheet(initial: Task, isNew: Boolean, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    val kinds by repo.taskKinds.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    fun nextDates(subjectId: String?): List<LocalDate> =
        subjectId?.let { id -> Schedule.nextOccurrences(id, today, schedule, 2).map { it.date } } ?: emptyList()

    var task by remember { mutableStateOf(initial) }
    var mode by remember {
        val dates = nextDates(initial.subjectId).map { it.toEpochDay() }
        mutableStateOf(
            when {
                initial.dueDate == null -> DueMode.CUSTOM
                initial.dueDate == dates.getOrNull(0) -> DueMode.NEXT
                initial.dueDate == dates.getOrNull(1) -> DueMode.SECOND
                else -> DueMode.CUSTOM
            },
        )
    }
    var steps by remember { mutableStateOf(repo.topics.value.filter { it.examId == initial.id }.sortedBy { it.sortOrder }) }
    var newStep by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var askFinish by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }

    val undo = LocalUndo.current
    val initialSteps = remember { steps }
    AppSheet(
        onDismiss = {
            if (isNew && !saved[0]) repo.discardAttachments(task.id)
            onDismiss()
        },
        confirmDiscard = !saved[0] && (task != initial || steps != initialSteps || newStep.isNotBlank()),
    ) { close ->
        SheetHeader(
            title = if (isNew) "Neue Aufgabe" else "Aufgabe",
            onSave = if (task.title.isNotBlank()) {
                {
                    saved[0] = true
                    repo.saveTask(task.copy(title = task.title.trim()))
                    // Ein angefangener Unterpunkt wird mitgespeichert.
                    val all = if (newStep.isNotBlank()) steps + ExamTopic(examId = task.id, text = newStep.trim()) else steps
                    repo.saveSteps(task.id, all)
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    saved[0] = true
                    val original = task
                    repo.deleteTask(original)
                    undo.show("Gelöscht") { repo.restoreTask(original) }
                    close()
                }
            } else {
                null
            },
            extra = { AttachButton(OwnerType.TASK, task.id) },
        )
        InlineTextField(task.title, { task = task.copy(title = it) }, "Was ist zu tun?", Modifier.fillMaxWidth().padding(horizontal = 4.dp), big = true)

        SubjectPicker(subjects, task.subjectId, { id ->
            task = task.copy(subjectId = id)
            val dates = nextDates(id)
            when {
                mode == DueMode.NEXT && dates.isNotEmpty() -> task = task.copy(dueDate = dates[0].toEpochDay())
                mode == DueMode.SECOND && dates.size > 1 -> task = task.copy(dueDate = dates[1].toEpochDay())
                mode != DueMode.CUSTOM -> mode = DueMode.CUSTOM
            }
        })

        FormGroup {
            FormRow("Art") {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { k ->
                        val selected = task.type == k.id
                        ChoiceChip(k.name, selected, { task = task.copy(type = k.id) }, leading = {
                            Icon(KindIcons.of(k.icon), null, tint = if (selected) c.accent else c.sub, modifier = Modifier.size(16.dp))
                        })
                    }
                }
            }
            FormRow("Fällig") {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val dates = nextDates(task.subjectId)
                    dates.getOrNull(0)?.let { d ->
                        ChoiceChip("Nächste · ${Fmt.dayDate(d)}", mode == DueMode.NEXT, {
                            mode = DueMode.NEXT
                            task = task.copy(dueDate = d.toEpochDay())
                        })
                    }
                    dates.getOrNull(1)?.let { d ->
                        ChoiceChip("Übernächste · ${Fmt.dayDate(d)}", mode == DueMode.SECOND, {
                            mode = DueMode.SECOND
                            task = task.copy(dueDate = d.toEpochDay())
                        })
                    }
                    val customLabel = if (mode == DueMode.CUSTOM && task.dueDate != null) Fmt.dayDate(LocalDate.ofEpochDay(task.dueDate!!)) else "Datum …"
                    ChoiceChip(customLabel, mode == DueMode.CUSTOM && task.dueDate != null, { showDate = true }, leading = {
                        Icon(Ic.CalendarPlain, null, tint = c.sub, modifier = Modifier.size(15.dp))
                    })
                }
            }
            FormTextRow("Notizen", task.description, { task = task.copy(description = it) }, placeholder = "optional", singleLine = false, divider = false)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormLabel("Unterpunkte")
                if (steps.isNotEmpty()) {
                    val p = steps.count { it.done } * 100 / steps.size
                    Text("  $p %", style = T.label, color = if (p == 100) c.good else c.accent)
                }
            }
            StepsEditor(
                steps = steps,
                newText = newStep,
                onNewText = { newStep = it },
                onAdd = { text -> steps = steps + ExamTopic(examId = task.id, text = text) },
                onToggle = { st ->
                    steps = steps.map { if (it.id == st.id) it.copy(done = !it.done) else it }
                    // Letzten Unterpunkt abgehakt: fragen, ob die ganze Aufgabe erledigt ist.
                    if (!task.done && steps.isNotEmpty() && steps.all { it.done }) askFinish = true
                },
                onRename = { st, text -> steps = steps.map { if (it.id == st.id) it.copy(text = text) else it } },
                onRemove = { st -> steps = steps.filter { it.id != st.id } },
                onReorder = { steps = it },
                placeholder = "Unterpunkt hinzufügen (optional)",
            )
        }

        AttachmentStrip(attachments.filter { it.ownerId == task.id })

        if (askFinish) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askFinish = false },
                title = { Text("Aufgabe abschließen?") },
                text = { Text("Alle Unterpunkte sind erledigt.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        askFinish = false
                        saved[0] = true
                        repo.saveTask(task.copy(title = task.title.trim(), done = true, doneAt = System.currentTimeMillis()))
                        repo.saveSteps(task.id, steps)
                        undo.show("Erledigt") { repo.setTaskDone(task, false) }
                        close()
                    }) { Text("Ja") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askFinish = false }) { Text("Nein") } },
            )
        }
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
