package de.schulplaner.ui.grades

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.CategoryKind
import de.schulplaner.data.Grade
import de.schulplaner.data.GradeCategory
import de.schulplaner.data.OwnerType
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.ChoiceChip
import de.schulplaner.ui.components.ConfirmDialog
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.FormValueRow
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.MiniSegmented
import androidx.compose.foundation.layout.Box
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.PickerRow
import de.schulplaner.ui.components.PointsPicker
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.Stepper
import de.schulplaner.ui.components.SubjectPicker
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.LocalDate
import java.util.Locale

/** Note eintragen: Punkte, Begründung, Datum, Kategorie, Gewicht und Anhänge. */
@Composable
fun GradeEditSheet(initial: Grade, isNew: Boolean, subjectSelectable: Boolean, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val allCategories by repo.categories.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    var grade by remember { mutableStateOf(initial) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }
    val undo = LocalUndo.current
    val confetti = de.schulplaner.ui.components.LocalConfetti.current

    LaunchedEffect(grade.subjectId, grade.termId) {
        if (grade.subjectId.isNotBlank() && grade.termId.isNotBlank()) {
            val cats = repo.ensureCategories(grade.subjectId, grade.termId)
            if (cats.none { it.id == grade.categoryId }) {
                grade = grade.copy(categoryId = cats.firstOrNull()?.id ?: "")
            }
        }
    }
    val categories = allCategories.filter { it.subjectId == grade.subjectId && it.termId == grade.termId }

    AppSheet(onDismiss = {
        if (isNew && !saved[0]) repo.discardAttachments(grade.id)
        onDismiss()
    }) { close ->
        SheetHeader(
            title = if (isNew) "Neue Note" else "Note",
            onSave = if (grade.subjectId.isNotBlank() && grade.categoryId.isNotBlank()) {
                {
                    saved[0] = true
                    repo.saveGrade(grade.copy(title = grade.title.trim(), reason = grade.reason.trim()))
                    if (grade.points >= 13) confetti.celebrate()
                    close()
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
        if (subjectSelectable) {
            SubjectPicker(subjects, grade.subjectId.ifBlank { null }, { id -> if (id != null) grade = grade.copy(subjectId = id, categoryId = "") }, allowNone = false)
        }
        PointsPicker(grade.points) { grade = grade.copy(points = it) }
        FormGroup {
            if (categories.isNotEmpty()) {
                FormRow("Kategorie") {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            ChoiceChip("${cat.name} · ${cat.weight.toInt()} %", grade.categoryId == cat.id, { grade = grade.copy(categoryId = cat.id) })
                        }
                    }
                }
            }
            FormTextRow("Titel", grade.title, { grade = grade.copy(title = it) }, placeholder = "z. B. 1. Klausur")
            FormTextRow("Begründung", grade.reason, { grade = grade.copy(reason = it) }, placeholder = "optional", singleLine = false)
            FormValueRow("Datum", Fmt.dayDate(LocalDate.ofEpochDay(grade.date)), { showDate = true })
            FormRow("Zählt", divider = false) {
                Text("innerhalb der Kategorie", style = T.small, color = c.sub, modifier = Modifier.weight(1f))
                Stepper(
                    formatFactor(grade.weight),
                    onMinus = { grade = grade.copy(weight = (grade.weight - 0.5).coerceAtLeast(0.5)) },
                    onPlus = { grade = grade.copy(weight = (grade.weight + 0.5).coerceAtMost(5.0)) },
                )
            }
        }
        AttachmentStrip(OwnerType.GRADE, grade.id, attachments.filter { it.ownerId == grade.id })
    }

    if (showDate) {
        DateDialog(LocalDate.ofEpochDay(grade.date), onDismiss = { showDate = false }) { d ->
            grade = grade.copy(date = d.toEpochDay())
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Note löschen?", "Die Note und ihre Anhänge werden entfernt.", onDismiss = { confirmDelete = false }) {
            saved[0] = true
            val original = grade
            repo.deleteGrade(original)
            undo.show("Gelöscht") { repo.restoreGrade(original) }
            onDismiss()
        }
    }
}

fun formatFactor(w: Double): String =
    if (w == w.toInt().toDouble()) "${w.toInt()}×" else String.format(Locale.GERMAN, "%.1f×", w)

/** Bewertungskategorie anlegen/ändern (Name, Art, Gewichtung). */
@Composable
fun CategoryEditSheet(initial: GradeCategory, isNew: Boolean, onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repo
    var cat by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            title = if (isNew) "Neue Kategorie" else "Kategorie",
            onSave = if (cat.name.isNotBlank()) {
                {
                    repo.saveCategory(cat.copy(name = cat.name.trim()))
                    close()
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
        FormGroup {
            FormTextRow("Name", cat.name, { cat = cat.copy(name = it) }, placeholder = "z. B. Facharbeit")
            FormRow("Art") {
                MiniSegmented(
                    listOf(CategoryKind.WRITTEN to "Schriftl.", CategoryKind.ORAL to "Mündl.", CategoryKind.OTHER to "Sonst."),
                    cat.kind,
                    { cat = cat.copy(kind = it) },
                    Modifier.weight(1f),
                )
            }
            FormRow("Gewichtung", divider = false) {
                Box(Modifier.weight(1f))
                Stepper(
                    "${cat.weight.toInt()} %",
                    onMinus = { cat = cat.copy(weight = (cat.weight - 5).coerceAtLeast(0.0)) },
                    onPlus = { cat = cat.copy(weight = (cat.weight + 5).coerceAtMost(100.0)) },
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Kategorie löschen?", "Alle Noten in \"${cat.name}\" werden mit gelöscht.", onDismiss = { confirmDelete = false }) {
            repo.deleteCategory(cat)
            onDismiss()
        }
    }
}
