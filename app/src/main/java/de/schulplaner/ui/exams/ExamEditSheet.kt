package de.schulplaner.ui.exams

import androidx.compose.material3.Text
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
import de.schulplaner.ui.components.Field
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
        FormLabel("Fach")
        SubjectPicker(subjects, exam.subjectId, { id ->
            if (id != null) {
                var e = exam.copy(subjectId = id)
                if (isNew) {
                    Schedule.nextOccurrence(id, LocalDate.now(), schedule)?.let { e = e.copy(date = it.date.toEpochDay()) }
                }
                exam = withAutoPeriod(e)
            }
        }, allowNone = false)

        FormLabel("Art")
        Segmented(ExamType.all.map { it to (if (it == "Sonstiges") "Sonst." else it) }, exam.type, { exam = exam.copy(type = it) })

        Field(exam.title, { exam = exam.copy(title = it) }, "Thema (z. B. Genetik)")

        val date = LocalDate.ofEpochDay(exam.date)
        PickerRow(Ic.CalendarPlain, "Datum", Fmt.dayDate(date), { showDate = true })
        PickerRow(Ic.Clock, "Stunde", exam.period?.let { "$it. Std" } ?: "–", {
            exam = exam.copy(period = ((exam.period ?: 0) % 10) + 1)
        })
        Text("Die Stunde wird aus dem Stundenplan übernommen; antippen zum Ändern.", style = T.small, color = c.sub)
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
