package de.schulplaner.ui.exams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.ExamTopic
import de.schulplaner.ui.components.StepsEditor

/**
 * Fragen zu einer Klausur: gleiche Liste in der Klausur und im Lernzettel (synchronisiert).
 * Gespeichert wie Unterpunkte unter der ID "q_<Klausur>"; abhaken = Frage geklärt.
 */
object Questions {
    fun ownerId(examId: String) = "q_$examId"

    /** Klausur-ID zu einer Lernzettel-Aufgabe ("lz_<Klausur>"), sonst null. */
    fun examOfTask(taskId: String): String? = taskId.removePrefix("lz_").takeIf { taskId.startsWith("lz_") && it.isNotBlank() }
}

/** Anzahl offener Fragen (für die Beschriftung im Umschalter). */
@Composable
fun openQuestionCount(examId: String): Int {
    val topics by LocalContext.current.app.repo.topics.collectAsStateWithLifecycle()
    val owner = Questions.ownerId(examId)
    return topics.count { it.examId == owner && !it.done }
}

@Composable
fun QuestionsEditor(examId: String, framed: Boolean = false) {
    val repo = LocalContext.current.app.repo
    val topics by repo.topics.collectAsStateWithLifecycle()
    val owner = Questions.ownerId(examId)
    val questions = topics.filter { it.examId == owner }.sortedBy { it.sortOrder }
    var newText by rememberSaveable(examId) { mutableStateOf("") }
    StepsEditor(
        steps = questions,
        newText = newText,
        onNewText = { newText = it },
        onAdd = { text -> repo.saveTopic(ExamTopic(examId = owner, text = text, sortOrder = (questions.maxOfOrNull { it.sortOrder } ?: 0) + 1)) },
        onToggle = { q -> repo.saveTopic(q.copy(done = !q.done)) },
        onRename = { q, text -> repo.saveTopic(q.copy(text = text)) },
        onRemove = { q -> repo.deleteTopic(q) },
        onReorder = { list -> list.forEachIndexed { i, q -> if (q.sortOrder != i) repo.saveTopic(q.copy(sortOrder = i)) } },
        placeholder = "Frage eintragen",
        framed = framed,
    )
}

/** Beschriftung für den Fragen-Reiter, z. B. "Fragen (2)". */
fun questionsLabel(open: Int) = if (open > 0) "Fragen ($open)" else "Fragen"
