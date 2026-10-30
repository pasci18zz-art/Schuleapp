package de.schulplaner.domain

import de.schulplaner.data.Grade
import de.schulplaner.data.GradeCategory
import de.schulplaner.data.Subject
import de.schulplaner.data.SubjectTerm
import java.util.Locale
import kotlin.math.roundToInt

object GradeCalc {
    private val NOTES = listOf("6", "5-", "5", "5+", "4-", "4", "4+", "3-", "3", "3+", "2-", "2", "2+", "1-", "1", "1+")

    fun note(points: Double): String = NOTES[points.roundToInt().coerceIn(0, 15)]

    fun format(value: Double): String = String.format(Locale.GERMAN, "%.1f", value)

    /** Gewichteter Mittelwert der Punkte innerhalb einer Kategorie. */
    fun mean(grades: List<Grade>): Double? {
        val w = grades.sumOf { it.weight }
        if (grades.isEmpty() || w <= 0.0) return null
        return grades.sumOf { it.points * it.weight } / w
    }

    /**
     * Fachschnitt: Kategorien mit Noten werden nach ihrer Gewichtung verrechnet.
     * Kategorien ohne Noten zählen (noch) nicht mit.
     */
    fun subjectAverage(categories: List<GradeCategory>, grades: List<Grade>): Double? {
        var sum = 0.0
        var weights = 0.0
        for (c in categories) {
            if (c.weight <= 0.0) continue
            val avg = mean(grades.filter { it.categoryId == c.id }) ?: continue
            sum += avg * c.weight
            weights += c.weight
        }
        return if (weights > 0.0) sum / weights else null
    }

    /**
     * Wie viele Punkte braucht die nächste Note in [categoryId], damit der Schnitt mindestens [target] ist?
     * 0 = schon sicher, null = nicht erreichbar.
     */
    fun neededPoints(
        categories: List<GradeCategory>,
        grades: List<Grade>,
        categoryId: String,
        target: Double,
        weight: Double = 1.0,
    ): Int? {
        for (x in 0..15) {
            val extra = Grade(categoryId = categoryId, points = x, weight = weight)
            val avg = subjectAverage(categories, grades + extra) ?: continue
            if (avg >= target - 1e-9) return x
        }
        return null
    }
}

/** Ergebnis eines Fachs in einem Halbjahr. */
data class SubjectResult(
    val subject: Subject,
    val computed: Double?,
    val override: Int?,
    val gradeCount: Int,
) {
    val value: Double? get() = override?.toDouble() ?: computed
}

object TermStats {
    fun results(
        termId: String,
        subjects: List<Subject>,
        categories: List<GradeCategory>,
        grades: List<Grade>,
        subjectTerms: List<SubjectTerm>,
    ): List<SubjectResult> {
        val overrides = subjectTerms.filter { it.termId == termId }.associateBy { it.subjectId }
        return subjects.filter { !it.archived }.map { s ->
            val cats = categories.filter { it.subjectId == s.id && it.termId == termId }
            val gs = grades.filter { it.subjectId == s.id && it.termId == termId }
            SubjectResult(s, GradeCalc.subjectAverage(cats, gs), overrides[s.id]?.overridePoints, gs.size)
        }
    }

    /** Halbjahresschnitt: alle Fächer mit Ergebnis zählen gleich. */
    fun average(results: List<SubjectResult>): Double? {
        val values = results.mapNotNull { it.value }
        return if (values.isEmpty()) null else values.average()
    }
}
