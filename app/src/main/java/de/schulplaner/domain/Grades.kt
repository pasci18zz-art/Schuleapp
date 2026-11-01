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

    /** Ergebnis des Notenziel-Rechners. */
    data class Goal(
        val remainingWritten: Int,
        val remainingOral: Int,
        /** Punkte, die jede noch fehlende Note im Schnitt haben müsste; null = nicht erreichbar. */
        val needed: Int?,
        /** Schnitt, wenn alle fehlenden Noten 15 Punkte wären (oder der aktuelle, falls nichts fehlt). */
        val best: Double?,
    ) {
        val nothingLeft: Boolean get() = remainingWritten + remainingOral == 0
    }

    /**
     * Wie viele Punkte braucht man bei den noch fehlenden Klausuren und mündlichen Noten im Schnitt,
     * damit der Fachschnitt mindestens [target] erreicht? Gewichtung schriftlich/mündlich laut Kategorien.
     */
    fun goal(
        categories: List<GradeCategory>,
        grades: List<Grade>,
        plannedWritten: Int,
        plannedOral: Int,
        target: Double,
    ): Goal {
        val written = categories.firstOrNull { it.kind == de.schulplaner.data.CategoryKind.WRITTEN }
        val oral = categories.firstOrNull { it.kind == de.schulplaner.data.CategoryKind.ORAL }
        val countW = if (written == null) 0 else grades.count { g -> categories.any { it.id == g.categoryId && it.kind == de.schulplaner.data.CategoryKind.WRITTEN } }
        val countO = if (oral == null) 0 else grades.count { g -> categories.any { it.id == g.categoryId && it.kind == de.schulplaner.data.CategoryKind.ORAL } }
        val remW = if (written == null) 0 else (plannedWritten - countW).coerceAtLeast(0)
        val remO = if (oral == null) 0 else (plannedOral - countO).coerceAtLeast(0)
        fun avgWith(x: Int): Double? {
            val extra = List(remW) { Grade(categoryId = written!!.id, points = x, weight = 1.0) } +
                List(remO) { Grade(categoryId = oral!!.id, points = x, weight = 1.0) }
            return subjectAverage(categories, grades + extra)
        }
        val best = avgWith(15)
        if (remW + remO == 0) return Goal(0, 0, null, subjectAverage(categories, grades))
        var needed: Int? = null
        for (x in 0..15) {
            val a = avgWith(x) ?: continue
            if (a >= target - 1e-9) {
                needed = x
                break
            }
        }
        return Goal(remW, remO, needed, best)
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

    /** Schnitt eines Fachs nur aus schriftlichen bzw. mündlichen Kategorien ([kind] = CategoryKind). */
    fun kindAverage(termId: String, subjectId: String, kind: String, categories: List<GradeCategory>, grades: List<Grade>): Double? {
        val cats = categories.filter { it.subjectId == subjectId && it.termId == termId && it.kind == kind }
        val gs = grades.filter { it.subjectId == subjectId && it.termId == termId }
        return GradeCalc.subjectAverage(cats, gs)
    }

    /** Halbjahresschnitt nur schriftlich bzw. mündlich (Fächer mit Noten in dieser Art zählen gleich). */
    fun kindTermAverage(termId: String, subjects: List<Subject>, kind: String, categories: List<GradeCategory>, grades: List<Grade>): Double? {
        val values = subjects.filter { !it.archived }.mapNotNull { kindAverage(termId, it.id, kind, categories, grades) }
        return if (values.isEmpty()) null else values.average()
    }

    /** Halbjahresschnitt: alle Fächer mit Ergebnis zählen gleich. */
    fun average(results: List<SubjectResult>): Double? {
        val values = results.mapNotNull { it.value }
        return if (values.isEmpty()) null else values.average()
    }
}
