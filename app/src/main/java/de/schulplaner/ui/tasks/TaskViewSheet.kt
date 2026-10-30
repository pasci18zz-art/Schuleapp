package de.schulplaner.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.OwnerType
import de.schulplaner.data.Task
import de.schulplaner.data.TaskType
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/** Aufgabe nur ansehen (aus Stundenplan und Kalender). */
@Composable
fun TaskViewSheet(task: Task, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    val subject = subjects.firstOrNull { it.id == task.subjectId }
    val files = attachments.filter { it.ownerId == task.id }
    val today = LocalDate.now()

    AppSheet(onDismiss = onDismiss) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subject != null) SubjectDot(subjectColor(subject.color))
            Text(subject?.name ?: "Ohne Fach", style = T.label, color = c.sub, modifier = Modifier.weight(1f))
        }
        Text(
            task.title,
            style = T.title.copy(fontSize = T.section.fontSize * 1.4f, textDecoration = if (task.done) TextDecoration.LineThrough else null),
            color = if (task.done) c.sub else c.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(TaskType.label(task.type), c.line, c.sub)
            task.dueDate?.let { Pill("fällig ${Fmt.dayDate(LocalDate.ofEpochDay(it))}", c.accentSoft, c.accent) }
            if (task.done) Pill("erledigt", c.line, c.sub)
            else if (task.dueDate != null && task.dueDate < today.toEpochDay()) Pill("überfällig", c.warnSoft, c.warn)
        }
        if (task.description.isNotBlank()) {
            Text(task.description, style = T.bodyRegular, color = c.text, modifier = Modifier.padding(top = 4.dp))
        }
        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Anhänge", style = T.label, color = c.sub)
                AttachmentStrip(OwnerType.TASK, task.id, files, editable = false)
            }
        }
    }
}
