package de.schulplaner.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.KindIcons
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.ProgressBar
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/** Aufgabe ansehen (aus Stundenplan und Kalender). Unterpunkte lassen sich direkt abhaken. */
@Composable
fun TaskViewSheet(task: Task, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    val kinds by repo.taskKinds.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val subject = subjects.firstOrNull { it.id == task.subjectId }
    val kind = kindOf(task.type, kinds)
    val files = attachments.filter { it.ownerId == task.id }
    val steps = topics.filter { it.examId == task.id }.sortedBy { it.sortOrder }
    val today = LocalDate.now()
    var askFinish by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current
    val undo = de.schulplaner.ui.components.LocalUndo.current

    AppSheet(onDismiss = onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subject != null) SubjectIcon(subject, 18.dp)
            Text(subject?.name ?: "Ohne Fach", style = T.label, color = c.sub, modifier = Modifier.weight(1f))
            Icon(KindIcons.of(kind.icon), null, tint = c.sub, modifier = Modifier.size(18.dp))
            Text(kind.name, style = T.label, color = c.sub)
        }
        Text(
            task.title,
            style = T.title.copy(fontSize = T.section.fontSize * 1.4f, textDecoration = if (task.done) TextDecoration.LineThrough else null),
            color = if (task.done) c.sub else c.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            task.dueDate?.let { Pill("fällig ${Fmt.dayDate(LocalDate.ofEpochDay(it))}", c.accentSoft, c.accent) }
            if (task.done) Pill("erledigt", c.line, c.sub)
            else if (task.dueDate != null && task.dueDate < today.toEpochDay()) Pill("überfällig", c.warnSoft, c.warn)
        }
        if (task.description.isNotBlank()) {
            Text(task.description, style = T.bodyRegular, color = c.text, modifier = Modifier.padding(top = 4.dp))
        }
        if (steps.isNotEmpty()) {
            val p = steps.count { it.done } * 100 / steps.size
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unterpunkte", style = T.label, color = c.sub, modifier = Modifier.weight(1f))
                    Text("$p %", style = T.label, color = if (p == 100) c.good else c.accent)
                }
                ProgressBar(p / 100f, subjectColor(subject?.color))
                FormGroup {
                    steps.forEach { st ->
                        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(st.done, {
                                repo.saveTopic(st.copy(done = !st.done))
                                // Letzter Unterpunkt abgehakt: Aufgabe abschließen?
                                if (!task.done && !st.done && !readOnly && steps.all { it.done || it.id == st.id }) askFinish = true
                            }, square = true, size = 22.dp, checkedColor = c.good)
                            Text(
                                st.text,
                                style = T.body.copy(textDecoration = if (st.done) TextDecoration.LineThrough else null),
                                color = if (st.done) c.sub else c.text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Anhänge", style = T.label, color = c.sub)
                AttachmentStrip(files, editable = false)
            }
        }
        if (askFinish) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askFinish = false },
                title = { Text("Aufgabe abschließen?") },
                text = { Text("Alle Unterpunkte sind erledigt.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        askFinish = false
                        repo.setTaskDone(task, true)
                        undo.show("Erledigt") { repo.setTaskDone(task, false) }
                        close()
                    }) { Text("Ja") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askFinish = false }) { Text("Nein") } },
            )
        }
    }
}
