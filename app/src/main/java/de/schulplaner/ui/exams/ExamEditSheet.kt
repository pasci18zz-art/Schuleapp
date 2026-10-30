package de.schulplaner.ui.exams

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Exam
import de.schulplaner.data.ExamType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.ConfirmDialog
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.FormValueRow
import de.schulplaner.ui.components.MiniSegmented
import de.schulplaner.ui.components.Stepper
import androidx.compose.ui.Modifier
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.PickerRow
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SubjectPicker
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/** Klausur anlegen/ändern. Die Stunde wird automatisch aus dem Stundenplan übernommen. */
@Composable
fun ExamEditSheet(initial: Exam, isNew: Boolean, onDismiss: () -> Unit, onSaved: (Exam) -> Unit = {}, onDeleted: () -> Unit = {}) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    var exam by remember { mutableStateOf(initial) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun withAutoPeriod(e: Exam): Exam {
        val lesson = Schedule.lessonsOn(LocalDate.ofEpochDay(e.date), schedule).firstOrNull { it.slot.subjectId == e.subjectId }
        return e.copy(period = lesson?.firstPeriod)
    }

    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            title = if (isNew) "Neue Klausur" else "Klausur",
            onSave = if (exam.subjectId.isNotBlank()) {
                {
                    repo.saveExam(exam)
                    close()
                    onSaved(exam)
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                { confirmDelete = true }
            } else {
                null
            },
        )
        if (subjects.isEmpty()) {
            Text("Lege zuerst Fächer in den Einstellungen an.", style = T.body, color = c.sub)
            return@AppSheet
        }
        SubjectPicker(subjects, exam.subjectId, { id ->
            if (id != null) {
                var e = exam.copy(subjectId = id)
                if (isNew) {
                    Schedule.nextOccurrence(id, LocalDate.now(), schedule)?.let { e = e.copy(date = it.date.toEpochDay()) }
                }
                exam = withAutoPeriod(e)
            }
        }, allowNone = false)

        val date = LocalDate.ofEpochDay(exam.date)
        FormGroup {
            FormRow("Art") {
                MiniSegmented(ExamType.all.map { it to (if (it == "Sonstiges") "Sonst." else it) }, exam.type, { exam = exam.copy(type = it) }, Modifier.weight(1f))
            }
            FormTextRow("Thema", exam.title, { exam = exam.copy(title = it) }, placeholder = "z. B. Genetik", divider = false)
        }

        // Termin: zuerst die nächsten Stunden des Fachs, sonst frei im Kalender.
        val lessons = remember(exam.subjectId, schedule) {
            Schedule.nextOccurrences(exam.subjectId, LocalDate.now().minusDays(1), schedule, 6)
        }
        val fromList = lessons.any { it.date.toEpochDay() == exam.date }
        FormLabel("Termin")
        FormGroup {
            lessons.forEach { l ->
                val selected = l.date.toEpochDay() == exam.date
                FormRow(null, onClick = { exam = exam.copy(date = l.date.toEpochDay(), period = l.firstPeriod) }) {
                    Text(Fmt.dayDate(l.date), style = T.body, color = if (selected) c.accent else c.text, modifier = Modifier.weight(1f))
                    Text("${l.periodLabel} Std", style = T.small, color = c.sub)
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        if (selected) Icon(Ic.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
            FormRow(null, onClick = { showDate = true }, divider = false) {
                Icon(Ic.CalendarPlain, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(
                    if (fromList) "Anderes Datum im Kalender …" else Fmt.dayDate(date),
                    style = T.body,
                    color = if (!fromList) c.accent else c.text,
                    modifier = Modifier.weight(1f),
                )
                if (!fromList) Icon(Ic.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
            }
        }
        if (!fromList) {
            FormGroup {
                FormRow("Stunde", divider = false) {
                    Text(exam.period?.let { "$it. Std" } ?: "–", style = T.body, color = c.text, modifier = Modifier.weight(1f))
                    Stepper(
                        exam.period?.let { "$it." } ?: "–",
                        onMinus = { exam = exam.copy(period = ((exam.period ?: 2) - 1).coerceAtLeast(1)) },
                        onPlus = { exam = exam.copy(period = ((exam.period ?: 0) + 1).coerceAtMost(12)) },
                    )
                }
            }
        }
    }

    if (showDate) {
        DateDialog(LocalDate.ofEpochDay(exam.date), onDismiss = { showDate = false }) { d ->
            exam = withAutoPeriod(exam.copy(date = d.toEpochDay()))
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Klausur löschen?", "Themen, Lernplan und Fotos werden mit gelöscht.", onDismiss = { confirmDelete = false }) {
            repo.deleteExam(exam)
            onDismiss()
            onDeleted()
        }
    }
}
