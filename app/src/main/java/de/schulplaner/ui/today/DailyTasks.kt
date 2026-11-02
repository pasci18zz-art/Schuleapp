package de.schulplaner.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.DailyPin
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.Subject
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalReadOnly
import de.schulplaner.ui.components.LocalTileEdit
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.SubjectIcon
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ein Eintrag der Tagesaufgaben, so wie er heute angezeigt wird. */
class DailyItem(
    val pin: DailyPin,
    val task: Task,
    val step: ExamTopic?,
    val done: Boolean,
    /** Gesetzt, wenn der Eintrag von einem früheren Tag übrig geblieben ist. */
    val carriedFrom: Long?,
)

/** "Mo" für die nächsten Tage, sonst "12.11.". */
private fun dueShort(epoch: Long, today: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return if (epoch in today..today + 6) Fmt.dayShort(d) else Fmt.dateShort(d)
}

private fun dayOf(millis: Long?): Long? =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }

/**
 * Welche Tagesaufgaben heute zu sehen sind: alles für heute Angepinnte, dazu Unerledigtes von früheren Tagen
 * ("von gestern"). Was an einem früheren Tag angepinnt und heute erledigt wurde, bleibt bis heute Abend durchgestrichen stehen.
 */
fun dailyItems(pins: List<DailyPin>, tasks: List<Task>, topics: List<ExamTopic>, today: Long): List<DailyItem> {
    val taskById = tasks.associateBy { it.id }
    val topicById = topics.associateBy { it.id }
    return pins.sortedBy { it.day }.mapNotNull { pin ->
        if (pin.day > today) return@mapNotNull null
        val task = taskById[pin.taskId] ?: return@mapNotNull null
        val step = pin.stepId?.let { topicById[it] ?: return@mapNotNull null }
        val done = task.done || step?.done == true
        val doneToday = if (step != null && !task.done) dayOf(step.updatedAt) == today else dayOf(task.doneAt) == today
        val show = pin.day == today || !done || doneToday
        if (!show) null else DailyItem(pin, task, step, done, pin.day.takeIf { it < today })
    }
}

/** Ganz oben auf der Startseite: Was heute dran ist – farbig hervorgehoben. */
@Composable
fun DailyTile(
    items: List<DailyItem>,
    subjects: Map<String, Subject>,
    today: LocalDate,
    onOpen: (Task) -> Unit,
    onPick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val readOnly = LocalReadOnly.current
    if (items.isEmpty() && readOnly) return
    val doneCount = items.count { it.done }
    val shape = RoundedCornerShape(24.dp)
    val warm = Color(0xFFFF9A62)
    val cool = c.accent
    val tileEdit = LocalTileEdit.current
    Column(
        modifier
            .pressable(null, tileEdit)
            .clip(shape)
            .background(c.card)
            .background(Brush.linearGradient(listOf(warm.copy(alpha = if (c.isDark) 0.26f else 0.22f), cool.copy(alpha = if (c.isDark) 0.26f else 0.18f))))
            .border(1.5.dp, Brush.linearGradient(listOf(warm.copy(alpha = 0.7f), cool.copy(alpha = 0.6f))), shape)
            .animateContentSize()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(warm, cool))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Pushpin, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Tagesaufgaben", style = T.section, color = c.text)
                Text(
                    when {
                        items.isEmpty() -> "Was willst du heute schaffen?"
                        doneCount == items.size -> "Alles geschafft – stark!"
                        else -> "$doneCount von ${items.size} erledigt"
                    },
                    style = T.small,
                    color = c.sub,
                )
            }
            if (onPick != null) SoftIconButton(Ic.Plus, "Für heute auswählen", onPick)
        }
        if (items.isNotEmpty()) {
            val p by animateFloatAsState(doneCount.toFloat() / items.size, tween(500), label = "daily")
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line)) {
                Box(Modifier.fillMaxWidth(p).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(warm, cool))))
            }
        }
        items.forEach { item ->
            val subject = item.task.subjectId?.let { subjects[it] }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(item.task) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CheckCircle(item.done, {
                    if (item.step != null) {
                        repo.saveTopic(item.step.copy(done = !item.step.done))
                    } else {
                        repo.setTaskDone(item.task, !item.task.done)
                        if (!item.task.done) undo.show("Erledigt") { repo.setTaskDone(item.task, false) }
                    }
                }, size = 24.dp, square = item.step != null, checkedColor = c.good)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.step?.text ?: item.task.title,
                        style = T.body.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                        color = if (item.done) c.sub else c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (subject != null) SubjectIcon(subject, 13.dp)
                        val info = listOfNotNull(
                            if (item.step != null) item.task.title else subject?.name,
                            item.task.dueDate?.let { "fällig ${dueShort(it, today.toEpochDay())}" },
                        ).joinToString(" · ")
                        Text(info, style = T.small, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                item.carriedFrom?.let { from ->
                    val label = if (from == today.toEpochDay() - 1) "von gestern" else "seit ${Fmt.dayShort(LocalDate.ofEpochDay(from))}"
                    Text(
                        label,
                        style = T.smallBold,
                        color = warm,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(warm.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (!readOnly) {
                    Icon(
                        Ic.Close,
                        "Lösen",
                        tint = c.sub,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable { repo.unpin(item.pin.taskId, item.pin.stepId) }.padding(5.dp),
                    )
                }
            }
        }
    }
}

/** Auswahl "Für heute": offene Aufgaben und ihre Unterpunkte antippen, um sie anzupinnen. */
@Composable
fun DailyPickerSheet(onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val pins by repo.dailyPins.collectAsStateWithLifecycle()
    val bySubject = subjects.associateBy { it.id }
    val open = tasks.filter { !it.done }.sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    fun pinned(taskId: String, stepId: String?) = pins.any { it.taskId == taskId && it.stepId == stepId }

    AppSheet(onDismiss = onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Für heute", style = T.title.copy(fontSize = T.section.fontSize * 1.4f), color = c.text)
                Text("Ganze Aufgaben oder einzelne Unterpunkte anpinnen", style = T.small, color = c.sub)
            }
            Text(
                "Fertig",
                style = T.label,
                color = c.accentInk,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).clickable { close() }.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
        if (open.isEmpty()) Text("Keine offenen Aufgaben.", style = T.body, color = c.sub)
        open.forEach { t ->
            val subject = t.subjectId?.let { bySubject[it] }
            val steps = topics.filter { it.examId == t.id && !it.done }.sortedBy { it.sortOrder }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(vertical = 4.dp),
            ) {
                PickRow(
                    text = t.title,
                    info = listOfNotNull(subject?.name, t.dueDate?.let { "fällig ${dueShort(it, LocalDate.now().toEpochDay())}" }).joinToString(" · "),
                    color = subjectColor(subject?.color),
                    pinned = pinned(t.id, null),
                    indent = false,
                ) { repo.togglePin(t.id, null) }
                steps.forEach { st ->
                    PickRow(st.text, null, subjectColor(subject?.color), pinned(t.id, st.id), indent = true) { repo.togglePin(t.id, st.id) }
                }
            }
        }
    }
}

@Composable
private fun PickRow(text: String, info: String?, color: Color, pinned: Boolean, indent: Boolean, onToggle: () -> Unit) {
    val c = colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = if (indent) 34.dp else 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(if (indent) 6.dp else 9.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(text, style = if (indent) T.bodyRegular else T.body, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!info.isNullOrBlank()) Text(info, style = T.small, color = c.sub, maxLines = 1)
        }
        PinMark(pinned)
    }
}

@Composable
private fun PinMark(pinned: Boolean) {
    val c = colors
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(if (pinned) c.accent else c.line),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ic.Pushpin, if (pinned) "Angepinnt" else "Anpinnen", tint = if (pinned) c.accentInk else c.sub, modifier = Modifier.size(17.dp))
    }
}

/** Knopf "📌 Für heute" für Aufgaben-Sheets. */
@Composable
fun PinTodayButton(pinned: Boolean, onToggle: () -> Unit) {
    val c = colors
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (pinned) c.accent else c.accentSoft)
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Ic.Pushpin, null, tint = if (pinned) c.accentInk else c.accent, modifier = Modifier.size(16.dp))
        Text("Heute", style = T.label, color = if (pinned) c.accentInk else c.accent)
    }
}

/** Kleines Pin-Symbol neben einem Unterpunkt. */
@Composable
fun PinStepButton(pinned: Boolean, onToggle: () -> Unit) {
    val c = colors
    Icon(
        Ic.Pushpin,
        if (pinned) "Für heute gelöst" else "Für heute anpinnen",
        tint = if (pinned) c.accent else c.sub.copy(alpha = 0.6f),
        modifier = Modifier.size(30.dp).clip(CircleShape).background(if (pinned) c.accentSoft else Color.Transparent).clickable(onClick = onToggle).padding(6.dp),
    )
}
