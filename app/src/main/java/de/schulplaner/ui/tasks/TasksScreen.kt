package de.schulplaner.ui.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Task
import de.schulplaner.data.TaskType
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.ChoiceChip
import de.schulplaner.ui.components.EmptyHint
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.SectionTitle
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.TaskRow
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate
import java.time.LocalDateTime

private enum class Status { OPEN, ALL, DONE }

@Composable
fun TasksScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val today = rememberNow().toLocalDate()

    var status by rememberSaveable { mutableStateOf(Status.OPEN) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<Pair<Task, Boolean>?>(null) }

    val subjectMap = subjects.associateBy { it.id }
    val visible = tasks.filter { t ->
        (type == null || t.type == type) &&
            (subjectFilter == null || t.subjectId == subjectFilter) &&
            when (status) {
                Status.OPEN -> !t.done
                Status.DONE -> t.done
                Status.ALL -> true
            }
    }
    val groups = groupTasks(visible, today, status)
    val openCount = tasks.count { !it.done }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader(title = "Aufgaben", overline = "$openCount offen")
            }
            item {
                Segmented(
                    listOf(Status.OPEN to "Offen", Status.ALL to "Alle", Status.DONE to "Erledigt"),
                    status,
                    { status = it },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item { ChoiceChip("Alle", type == null, { type = null }) }
                    items(listOf(TaskType.HOMEWORK, TaskType.EXTRA, TaskType.OTHER)) { t ->
                        ChoiceChip(TaskType.label(t), type == t, { type = if (type == t) null else t })
                    }
                    items(subjects.filter { s -> tasks.any { it.subjectId == s.id } }, key = { it.id }) { s ->
                        ChoiceChip(s.name, subjectFilter == s.id, { subjectFilter = if (subjectFilter == s.id) null else s.id }, leading = { SubjectDot(subjectColor(s.color), 9.dp) })
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    GlassCard {
                        EmptyHint(Ic.Tasks, if (status == Status.DONE) "Noch nichts erledigt" else "Nichts zu tun")
                    }
                }
            }
            groups.forEach { (label, list) ->
                item(key = "h_$label") {
                    SectionTitle(label, color = if (label == "Überfällig") c.warn else c.sub)
                }
                item(key = "g_$label") {
                    GlassCard(Modifier.animateContentSize(), shape = RoundedCornerShape(24.dp), padding = PaddingValues(4.dp)) {
                        list.forEach { t ->
                            val subject = t.subjectId?.let { subjectMap[it] }
                            val due = t.dueDate?.let { LocalDate.ofEpochDay(it) }
                            val overdue = due != null && due.isBefore(today) && !t.done
                            TaskRow(
                                title = t.title,
                                subtitle = listOfNotNull(subject?.name ?: "Ohne Fach", due?.let { Fmt.due(today, it) }).joinToString(" · "),
                                dotColor = subject?.let { subjectColor(it.color) },
                                done = t.done,
                                overdue = overdue,
                                onToggle = { repo.setTaskDone(t, !t.done) },
                                onClick = { edit = t to false },
                                trailing = { Pill(TaskType.label(t.type), c.line, c.sub) },
                            )
                        }
                    }
                }
            }
        }

        // Plus-Knopf: neue HA, Fach und Datum werden aus der aktuellen Stunde vorgeschlagen.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 88.dp)
                .size(60.dp)
                .pressable({ edit = TaskDefaults.quick(LocalDateTime.now(), schedule) to true })
                .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = c.accent, spotColor = c.accent)
                .clip(RoundedCornerShape(22.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Plus, "Aufgabe hinzufügen", tint = c.accentInk, modifier = Modifier.size(26.dp))
        }
    }

    edit?.let { (task, isNew) ->
        TaskEditSheet(task, isNew, onDismiss = { edit = null })
    }
}

private fun groupTasks(tasks: List<Task>, today: LocalDate, status: Status): List<Pair<String, List<Task>>> {
    val t = today.toEpochDay()
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong()).toEpochDay()
    val sorted = tasks.sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    if (status == Status.DONE) {
        return listOf("Erledigt" to tasks.sortedByDescending { it.doneAt ?: 0 }).filter { it.second.isNotEmpty() }
    }
    val buckets = linkedMapOf<String, MutableList<Task>>(
        "Überfällig" to mutableListOf(), "Heute" to mutableListOf(), "Morgen" to mutableListOf(),
        "Diese Woche" to mutableListOf(), "Später" to mutableListOf(), "Ohne Datum" to mutableListOf(),
    )
    sorted.forEach { task ->
        val d = task.dueDate
        val key = when {
            d == null -> "Ohne Datum"
            d < t && !task.done -> "Überfällig"
            d <= t -> "Heute"
            d == t + 1 -> "Morgen"
            d <= endOfWeek -> "Diese Woche"
            else -> "Später"
        }
        buckets.getValue(key).add(task)
    }
    return buckets.filter { it.value.isNotEmpty() }.map { it.key to it.value }
}

@Suppress("unused")
@Composable
private fun RowSpacer() = Row(Modifier.padding(2.dp)) {}
