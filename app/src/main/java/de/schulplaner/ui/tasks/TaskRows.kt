package de.schulplaner.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.Subject
import de.schulplaner.data.Task
import de.schulplaner.data.TaskKind
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.label
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.KindIcons
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.LocalDate

/** Art einer Aufgabe; unbekannte (z. B. gelöschte) Arten gelten als "Sonstiges". */
fun kindOf(type: String, kinds: List<TaskKind>): TaskKind =
    kinds.firstOrNull { it.id == type } ?: TaskKind(type, "Sonstiges", "dots")

/** Fortschritt der Unterpunkte in Prozent, null wenn es keine gibt. */
fun stepPercent(taskId: String, topics: List<ExamTopic>): Int? {
    val steps = topics.filter { it.examId == taskId }
    if (steps.isEmpty()) return null
    return steps.count { it.done } * 100 / steps.size
}

/** "Heute", "Morgen", sonst "Di 3.11." */
fun dueLabel(today: LocalDate, date: LocalDate): String = when (Fmt.daysUntil(today, date)) {
    0L -> "Heute"
    1L -> "Morgen"
    else -> "${Fmt.dayShort(date)} ${Fmt.dateShort(date)}"
}

/**
 * Eine Aufgabe in einer Liste: links abhaken, Titel mit Fach (und Fortschritt),
 * rechts Fälligkeitsdatum und das Symbol der Aufgabenart.
 */
@Composable
fun TaskLine(
    task: Task,
    subject: Subject?,
    kind: TaskKind,
    percent: Int?,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val c = colors
    val due = task.dueDate?.let { LocalDate.ofEpochDay(it) }
    val overdue = !task.done && due != null && due.isBefore(today)
    val titleColor by animateColorAsState(if (task.done) c.sub else c.text, tween(200), label = "title")
    val alpha by animateFloatAsState(if (task.done) 0.55f else 1f, tween(250), label = "done-alpha")
    val dueColor = when {
        task.done -> c.sub
        overdue -> c.warn
        due == today -> c.accent
        else -> c.sub
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .graphicsLayer { this.alpha = alpha }
            .padding(end = if (compact) 4.dp else 12.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(task.done, onToggle, ringColor = if (overdue) c.warn else c.sub, size = if (compact) 20.dp else 26.dp)
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = (if (compact) T.label else T.body).copy(textDecoration = if (task.done) TextDecoration.LineThrough else null),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (subject != null && !compact) SubjectIcon(subject, 14.dp)
                val name = if (compact) subject?.label else subject?.name
                val small = if (compact) T.small.copy(fontSize = T.small.fontSize * 0.92f) else T.small
                Text(name ?: "Ohne Fach", style = small, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (percent != null) Text("· $percent %", style = small.copy(fontWeight = T.smallBold.fontWeight), color = if (percent == 100) c.good else c.accent, maxLines = 1)
                if (compact && due != null) Text("· ${dueLabel(today, due)}", style = small, color = dueColor, maxLines = 1)
            }
        }
        if (!compact && due != null) {
            Text(dueLabel(today, due), style = T.smallBold, color = dueColor, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 10.dp))
        }
        Icon(KindIcons.of(kind.icon), kind.name, tint = c.sub, modifier = Modifier.size(if (compact) 16.dp else 19.dp))
    }
}
