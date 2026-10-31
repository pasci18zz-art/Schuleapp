package de.schulplaner.ui.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
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
import de.schulplaner.ui.components.LocalUndo
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
    val undo = LocalUndo.current
    val tasks by repo.tasks.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val kinds by repo.taskKinds.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val today = rememberNow().toLocalDate()

    var status by rememberSaveable { mutableStateOf(Status.OPEN) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<Pair<Task, Boolean>?>(null) }
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current

    val subjectMap = subjects.associateBy { it.id }
    val todayEpoch = today.toEpochDay()
    // Erledigte bleiben ausgegraut in "Offen", bis ihr Fälligkeitstag vorbei ist.
    fun stillVisible(t: Task) = t.done && (t.dueDate ?: (t.doneAt?.let { LocalDate.ofEpochDay(it / 86_400_000L).toEpochDay() } ?: 0L)) >= todayEpoch
    // Gelöschte Art: Filter zurücksetzen.
    if (type != null && kinds.none { it.id == type }) type = null
    val visible = tasks.filter { t ->
        (type == null || t.type == type) &&
            (subjectFilter == null || t.subjectId == subjectFilter) &&
            when (status) {
                Status.OPEN -> !t.done || stillVisible(t)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Alle" plus ein Symbol pro Aufgabenart.
                    de.schulplaner.ui.components.MiniSegmented(
                        listOf<Pair<String?, String>>(null to "Alle") + kinds.map { it.id to it.name },
                        type,
                        { type = it },
                        Modifier.weight(1f),
                        icons = listOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) + kinds.map { de.schulplaner.ui.components.KindIcons.of(it.icon) },
                    )
                    SubjectFilter(subjects, subjectFilter) { subjectFilter = it }
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
                            androidx.compose.runtime.key(t.id) {
                            TaskLine(
                                task = t,
                                subject = t.subjectId?.let { subjectMap[it] },
                                kind = kindOf(t.type, kinds),
                                percent = stepPercent(t.id, topics),
                                today = today,
                                onToggle = {
                                    repo.setTaskDone(t, !t.done)
                                    if (!t.done) undo.show("Erledigt") { repo.setTaskDone(t, false) }
                                },
                                onClick = { edit = t to false },
                            )
                            }
                        }
                    }
                }
            }
        }

        // Plus-Knopf: Fach und Datum aus der aktuellen Stunde, Art aus dem gewählten Filter.
        if (!readOnly) Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 88.dp)
                .size(60.dp)
                .pressable({ edit = TaskDefaults.quick(LocalDateTime.now(), schedule).copy(type = type ?: TaskType.HOMEWORK) to true })
                .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = c.accent, spotColor = c.accent)
                .clip(RoundedCornerShape(22.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Plus, "Aufgabe hinzufügen", tint = c.accentInk, modifier = Modifier.size(26.dp))
        }
    }

    edit?.let { (task, isNew) ->
        if (readOnly) TaskViewSheet(task, onDismiss = { edit = null }) else TaskEditSheet(task, isNew, onDismiss = { edit = null })
    }
}

private fun groupTasks(tasks: List<Task>, today: LocalDate, status: Status): List<Pair<String, List<Task>>> {
    val t = today.toEpochDay()
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong()).toEpochDay()
    if (status == Status.DONE) {
        return listOf("Erledigt" to tasks.sortedByDescending { it.doneAt ?: 0 }).filter { it.second.isNotEmpty() }
    }
    // Offene zuerst, erledigte ans Ende der jeweiligen Gruppe.
    val sorted = tasks.sortedWith(compareBy<Task> { it.done }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
    val buckets = linkedMapOf<String, MutableList<Task>>(
        "Überfällig" to mutableListOf(), "Heute" to mutableListOf(), "Morgen" to mutableListOf(),
        "Diese Woche" to mutableListOf(), "Später" to mutableListOf(), "Ohne Datum" to mutableListOf(), "Erledigt" to mutableListOf(),
    )
    sorted.forEach { task ->
        val d = task.dueDate
        val key = when {
            task.done && d != null && d < t -> "Erledigt"
            d == null -> "Ohne Datum"
            d < t -> "Überfällig"
            d == t -> "Heute"
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

/** "Fach"-Knopf mit aufklappendem Menü zum Filtern. */
@Composable
private fun SubjectFilter(subjects: List<de.schulplaner.data.Subject>, selectedId: String?, onSelect: (String?) -> Unit) {
    val c = colors
    var open by remember { mutableStateOf(false) }
    val selected = subjects.firstOrNull { it.id == selectedId }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "dd")
    Box {
        Row(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected != null) c.accentSoft else c.line)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected != null) de.schulplaner.ui.components.SubjectIcon(selected, 16.dp)
            Text(
                selected?.let { it.shortName.ifBlank { it.name } } ?: "Fach",
                style = T.label,
                color = if (selected != null) c.accent else c.text,
                maxLines = 1,
            )
            Icon(Ic.ChevronDown, null, tint = c.sub, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rot })
        }
        androidx.compose.material3.MaterialTheme(
            shapes = androidx.compose.material3.MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(18.dp)),
        ) {
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.widthIn(min = 220.dp).heightIn(max = 420.dp),
                containerColor = c.sheetGlass,
                shadowElevation = 8.dp,
            ) {
                FilterItem("Alle Fächer", null, selectedId == null) {
                    onSelect(null)
                    open = false
                }
                subjects.forEach { s ->
                    FilterItem(s.name, subjectColor(s.color), selectedId == s.id, s) {
                        onSelect(s.id)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterItem(label: String, dot: androidx.compose.ui.graphics.Color?, selected: Boolean, subject: de.schulplaner.data.Subject? = null, onClick: () -> Unit) {
    val c = colors
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, style = T.body, color = if (selected) c.accent else c.text) },
        leadingIcon = {
            when {
                subject != null -> de.schulplaner.ui.components.SubjectIcon(subject, 18.dp)
                dot != null -> SubjectDot(dot, 10.dp)
                else -> Icon(Ic.Layers, null, tint = c.sub, modifier = Modifier.size(16.dp))
            }
        },
        trailingIcon = if (selected) {
            { Icon(Ic.Check, null, tint = c.accent, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        onClick = onClick,
    )
}
