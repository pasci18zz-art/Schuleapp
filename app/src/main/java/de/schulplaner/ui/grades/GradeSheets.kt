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
import de.schulplaner.ui.components.Field
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
            FormLabel("Fach")
            SubjectPicker(subjects, grade.subjectId.ifBlank { null }, { id -> if (id != null) grade = grade.copy(subjectId = id, categoryId = "") }, allowNone = false)
        }
        if (categories.isNotEmpty()) {
            FormLabel("Kategorie")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    ChoiceChip("${cat.name} · ${cat.weight.toInt()} %", grade.categoryId == cat.id, { grade = grade.copy(categoryId = cat.id) })
                }
            }
        }
        FormLabel("Punkte")
        PointsPicker(grade.points) { grade = grade.copy(points = it) }
        Field(grade.title, { grade = grade.copy(title = it) }, "Titel (z. B. 1. Klausur, Mitarbeit Okt.)")
        Field(grade.reason, { grade = grade.copy(reason = it) }, "Begründung", singleLine = false, minLines = 2)
        PickerRow(Ic.CalendarPlain, "Datum", Fmt.dayDate(LocalDate.ofEpochDay(grade.date)), { showDate = true })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FormLabel("Zählt")
                Text("innerhalb der Kategorie", style = T.small, color = c.sub, modifier = Modifier)
            }
            Stepper(
                formatFactor(grade.weight),
                onMinus = { grade = grade.copy(weight = (grade.weight - 0.5).coerceAtLeast(0.5)) },
                onPlus = { grade = grade.copy(weight = (grade.weight + 0.5).coerceAtMost(5.0)) },
            )
        }
        FormLabel("Anhänge")
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
            repo.deleteGrade(grade)
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
        Field(cat.name, { cat = cat.copy(name = it) }, "Name (z. B. Facharbeit)")
        FormLabel("Art")
        Segmented(
            listOf(CategoryKind.WRITTEN to "Schriftlich", CategoryKind.ORAL to "Mündlich", CategoryKind.OTHER to "Sonstiges"),
            cat.kind,
            { cat = cat.copy(kind = it) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { FormLabel("Gewichtung") }
            Stepper(
                "${cat.weight.toInt()} %",
                onMinus = { cat = cat.copy(weight = (cat.weight - 5).coerceAtLeast(0.0)) },
                onPlus = { cat = cat.copy(weight = (cat.weight + 5).coerceAtMost(100.0)) },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Kategorie löschen?", "Alle Noten in \"${cat.name}\" werden mit gelöscht.", onDismiss = { confirmDelete = false }) {
            repo.deleteCategory(cat)
            onDismiss()
        }
    }
}
