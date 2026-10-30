package de.schulplaner.ui.exams

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.app
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.OwnerType
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.GradeCalc
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.AttachmentStrip
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.Field
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.PointsPicker
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.ProgressRing
import de.schulplaner.ui.components.SecondaryButton
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.pressable
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate

@Composable
fun ExamDetailScreen(id: String) {
    val nav = LocalNavigator.current
    val confetti = de.schulplaner.ui.components.LocalConfetti.current
    val c = colors
    val repo = LocalContext.current.app.repo
    val exams by repo.exams.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val allTopics by repo.topics.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()
    val grades by repo.grades.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val today = rememberNow().toLocalDate()

    val exam = exams.firstOrNull { it.id == id }
    var editing by remember { mutableStateOf(false) }
    var resultSheet by remember { mutableStateOf(false) }
    var newTopic by remember { mutableStateOf("") }
    var notes by remember(exam?.id) { mutableStateOf(exam?.topicsText ?: "") }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(exam?.topicsText) {
        if (exam != null && notes.isBlank()) notes = exam.topicsText
    }
    if (exam == null) {
        if (deleted) LaunchedEffect(Unit) { nav.back() }
        return
    }

    val subject = subjects.firstOrNull { it.id == exam.subjectId }
    val color = subjectColor(subject?.color)
    val topics = allTopics.filter { it.examId == exam.id }
    val doneCount = topics.count { it.done }
    val progress = if (topics.isEmpty()) 0f else doneCount.toFloat() / topics.size
    val date = LocalDate.ofEpochDay(exam.date)
    val days = Fmt.daysUntil(today, date)
    val grade = exam.gradeId?.let { gid -> grades.firstOrNull { it.id == gid } }
    val room = exam.period?.let { p ->
        schedule.slots.firstOrNull { it.subjectId == exam.subjectId && it.dayOfWeek == date.dayOfWeek.value && p in it.period until it.period + it.length }
    }?.room?.ifBlank { subject?.room } ?: subject?.room

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Ic.ChevronLeft, "Zurück", { nav.back() })
            Box(Modifier.weight(1f))
            GlassIconButton(Ic.Pencil, "Bearbeiten", { editing = true })
        }

        Column(Modifier.padding(horizontal = 6.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = c.tint)).padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                de.schulplaner.ui.components.SubjectIcon(subject, 15.dp)
                Text(listOfNotNull(subject?.name, if (subject?.isLk == true) "LK" else null).joinToString(" · "), style = T.smallBold, color = c.text)
            }
            Text(exam.title.ifBlank { exam.type }, style = T.title.copy(fontSize = T.hero.fontSize * 0.85f), color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Meta(Ic.CalendarPlain, Fmt.dayMonth(date))
                exam.period?.let { Meta(Ic.Clock, "$it. Std") }
                if (!room.isNullOrBlank()) Meta(Ic.Pin, "R $room")
            }
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Icon(Ic.Hourglass, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Box(Modifier.weight(1f))
                if (grade != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${grade.points}", style = T.bigNumber.copy(fontSize = T.bigNumber.fontSize * 0.88f), color = c.text)
                        Text("P · ${GradeCalc.note(grade.points.toDouble())}", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 6.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (days >= 0) "$days" else "–", style = T.bigNumber.copy(fontSize = T.bigNumber.fontSize * 0.88f), color = c.text)
                        Text(if (days == 1L) "Tag" else "Tage", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressRing(progress, color, size = 72.dp) {
                        Text(if (topics.isEmpty()) "–" else "${(progress * 100).toInt()}%", style = T.section, color = c.text)
                    }
                    Column {
                        Text("$doneCount/${topics.size}", style = T.section, color = c.text)
                        Text("Themen", style = T.small, color = c.sub)
                    }
                }
            }
        }

        GlassCard(padding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp)) {
            Text("Lernplan", style = T.section, color = c.text, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
            topics.forEach { t -> TopicRow(t, onToggle = { repo.saveTopic(t.copy(done = !t.done)) }, onDelete = { repo.deleteTopic(t) }) }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(newTopic, { newTopic = it }, "Neues Thema", Modifier.weight(1f))
                SoftIconButton(Ic.Plus, "Thema hinzufügen", {
                    if (newTopic.isNotBlank()) {
                        repo.saveTopic(ExamTopic(examId = exam.id, text = newTopic.trim(), sortOrder = (topics.maxOfOrNull { it.sortOrder } ?: 0) + 1))
                        newTopic = ""
                    }
                })
            }
        }

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Themen & Fotos", style = T.section, color = c.text, modifier = Modifier.weight(1f))
                de.schulplaner.ui.components.AttachButton(OwnerType.EXAM, exam.id)
            }
            val files = attachments.filter { it.ownerId == exam.id }
            AttachmentStrip(files)
            if (files.isNotEmpty()) Box(Modifier.height(12.dp))
            Field(notes, { notes = it }, "Themen / Notizen (z. B. Buch S. 140–188)", singleLine = false, minLines = 3)
            if (notes != exam.topicsText) {
                PrimaryButton("Speichern", { repo.saveExam(exam.copy(topicsText = notes)) }, Modifier.padding(top = 10.dp).fillMaxWidth())
            }
        }

        SecondaryButton(
            if (grade == null) "Ergebnis eintragen" else "Ergebnis ändern",
            { resultSheet = true },
            Modifier.fillMaxWidth(),
            icon = Ic.Grades,
        )
    }

    if (editing) {
        ExamEditSheet(exam, isNew = false, onDismiss = { editing = false }, onDeleted = { deleted = true })
    }
    if (resultSheet) {
        var points by remember { mutableStateOf(grade?.points ?: 10) }
        AppSheet(onDismiss = { resultSheet = false }) { close ->
            SheetHeader("Ergebnis", onSave = {
                repo.setExamResult(exam, points)
                if (points >= 13) confetti.celebrate()
                close()
            }, onDelete = if (grade != null) {
                {
                    repo.setExamResult(exam, null)
                    close()
                }
            } else {
                null
            })
            PointsPicker(points) { points = it }
            Text("Wird automatisch als schriftliche Note im Noten-Tab eingetragen.", style = T.small, color = c.sub)
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = c.sub, modifier = Modifier.size(15.dp))
        Text(text, style = T.label, color = c.sub)
    }
}

@Composable
private fun TopicRow(t: ExamTopic, onToggle: () -> Unit, onDelete: () -> Unit) {
    val c = colors
    val textColor by animateColorAsState(if (t.done) c.sub else c.text, tween(200), label = "topic")
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable(onToggle, onLongClick = { confirm = true }).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(t.done, onToggle, square = true, size = 24.dp, checkedColor = c.good)
        Text(
            t.text,
            style = T.body.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null),
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
    if (confirm) {
        de.schulplaner.ui.components.ConfirmDialog("Thema löschen?", "\"${t.text}\"", onDismiss = { confirm = false }, onConfirm = onDelete)
    }
}
