package de.schulplaner.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Exam
import de.schulplaner.data.ExamTopic
import de.schulplaner.data.Grade
import de.schulplaner.data.Subject
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.components.EmptyHint
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.ProgressBar
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.SectionTitle
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.rememberNow
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate

@Composable
fun ExamsScreen() {
    val nav = LocalNavigator.current
    val repo = LocalContext.current.app.repo
    val exams by repo.exams.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val topics by repo.topics.collectAsStateWithLifecycle()
    val grades by repo.grades.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val today = rememberNow().toLocalDate()
    var create by remember { mutableStateOf<Exam?>(null) }

    val subjectMap = subjects.associateBy { it.id }
    val gradeMap = grades.associateBy { it.id }
    val upcoming = exams.filter { it.date >= today.toEpochDay() }.sortedBy { it.date }
    val past = exams.filter { it.date < today.toEpochDay() }.sortedByDescending { it.date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader(title = "Klausuren", overline = "${upcoming.size} anstehend") {
                GlassIconButton(Ic.Plus, "Klausur hinzufügen", {
                    val first = subjects.firstOrNull()
                    val next = first?.let { Schedule.nextOccurrence(it.id, today, schedule) }
                    create = Exam(
                        subjectId = first?.id ?: "",
                        date = (next?.date ?: today.plusDays(7)).toEpochDay(),
                        period = next?.firstPeriod,
                    )
                }, filled = true)
            }
        }
        if (exams.isEmpty()) {
            item {
                GlassCard { EmptyHint(Ic.Exam, "Noch keine Klausuren eingetragen") }
            }
        }
        items(upcoming, key = { it.id }) { e ->
            ExamCard(e, subjectMap[e.subjectId], topics, null, today) { nav.open(Routes.exam(e.id)) }
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("Vergangen") }
            items(past, key = { it.id }) { e ->
                ExamCard(e, subjectMap[e.subjectId], topics, e.gradeId?.let { gradeMap[it] }, today, dim = true) { nav.open(Routes.exam(e.id)) }
            }
        }
    }

    create?.let { e ->
        ExamEditSheet(e, isNew = true, onDismiss = { create = null }, onSaved = { nav.open(Routes.exam(it.id)) })
    }
}

@Composable
private fun ExamCard(e: Exam, subject: Subject?, topics: List<ExamTopic>, grade: Grade?, today: LocalDate, dim: Boolean = false, onClick: () -> Unit) {
    val c = colors
    val date = LocalDate.ofEpochDay(e.date)
    val days = Fmt.daysUntil(today, date)
    val ts = topics.filter { it.examId == e.id }
    val progress = if (ts.isEmpty()) 0f else ts.count { it.done }.toFloat() / ts.size
    val color = subjectColor(subject?.color)
    // Je näher die Klausur, desto röter die Tage-Kachel.
    val urgent = de.schulplaner.ui.components.urgencyColor(de.schulplaner.ui.components.examUrgency(days))
    val boxColor = if (!dim && grade == null) urgent.copy(alpha = 0.2f) else color.copy(alpha = c.tint)
    GlassCard(Modifier.fillMaxWidth().alpha(if (dim) 0.75f else 1f), padding = PaddingValues(14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.size(56.dp).clip(RoundedCornerShape(19.dp)).background(boxColor),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (grade != null) {
                    Text("${grade.points}", style = T.section.copy(fontSize = T.section.fontSize * 1.25f), color = c.text)
                    Text("P", style = T.small, color = c.sub)
                } else if (!dim) {
                    Text("$days", style = T.section.copy(fontSize = T.section.fontSize * 1.25f), color = urgent)
                    Text(if (days == 1L) "Tag" else "Tage", style = T.small, color = c.sub)
                } else {
                    Text("–", style = T.section, color = c.sub)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.schulplaner.ui.components.SubjectIcon(subject, 17.dp)
                    Text(subject?.name ?: "?", style = T.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (e.type != "Klausur") Pill(e.type, c.line, c.sub)
                }
                Text(
                    listOfNotNull(e.title.ifBlank { null }, Fmt.dayDate(date), e.period?.let { "$it. Std" }).joinToString(" · "),
                    style = T.small,
                    color = c.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dim && ts.isNotEmpty()) ProgressBar(progress, color)
            }
        }
    }
}
