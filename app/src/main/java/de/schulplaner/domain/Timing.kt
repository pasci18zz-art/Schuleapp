package de.schulplaner.domain

import de.schulplaner.data.Exam
import de.schulplaner.data.Task
import java.time.LocalDate
import java.time.LocalDateTime

/** Wann ist etwas "vorbei"? Klausuren und Referate enden mit ihrer Stunde, sonst um Mitternacht. */
object Timing {
    /**
     * Ende der Stunde des Fachs an diesem Tag: mit [period] die (Doppel-)Stunde, die diese Stunde enthält,
     * sonst die letzte Stunde des Fachs; gibt es keine, das Ende des Tages.
     */
    fun subjectEnd(subjectId: String?, date: LocalDate, data: ScheduleData, period: Int? = null): LocalDateTime {
        val lessons = Schedule.plannedLessons(date, data).filter { it.slot.subjectId == subjectId }
        if (period != null) {
            val hit = lessons.filter { period in it.slot.period until it.slot.period + it.slot.length }
            hit.mapNotNull { it.endDateTime }.maxOrNull()?.let { return it }
            data.periodByNumber[period]?.let { return date.atTime(it.endMinute / 60, it.endMinute % 60) }
        }
        return lessons.mapNotNull { it.endDateTime }.maxOrNull() ?: date.plusDays(1).atStartOfDay()
    }

    fun examEnd(e: Exam, data: ScheduleData): LocalDateTime = subjectEnd(e.subjectId, LocalDate.ofEpochDay(e.date), data, e.period)

    fun examOver(e: Exam, now: LocalDateTime, data: ScheduleData): Boolean = !now.isBefore(examEnd(e, data))

    /** Aufgabe (z. B. Referat) ist vorbei, wenn die Stunde am Fälligkeitstag vorbei ist. */
    fun taskOver(t: Task, now: LocalDateTime, data: ScheduleData): Boolean {
        val due = t.dueDate ?: return false
        return !now.isBefore(subjectEnd(t.subjectId, LocalDate.ofEpochDay(due), data))
    }

    /** Stunde, bis zu der eine Aufgabe fällig ist: eingetragene Stunde, sonst die erste Stunde des Fachs an dem Tag. */
    fun taskPeriod(t: Task, data: ScheduleData): Int? {
        t.duePeriod?.let { return it }
        val due = t.dueDate ?: return null
        return Schedule.lessonsOn(LocalDate.ofEpochDay(due), data).filter { it.slot.subjectId == t.subjectId }.minOfOrNull { it.firstPeriod }
    }

    /** Gehört die Aufgabe in diese (Doppel-)Stunde? Nur in den Block, bis zu dem sie fällig ist. */
    fun taskInLesson(t: Task, l: Lesson, data: ScheduleData): Boolean {
        if (t.dueDate != l.date.toEpochDay() || t.subjectId != l.slot.subjectId) return false
        val p = taskPeriod(t, data) ?: return true
        return p in l.firstPeriod..l.lastPeriod
    }

    /** Stunden zählen: Doppelstunden zählen doppelt, Entfall zählt nicht. */
    fun periodCount(lessons: List<Lesson>): Int {
        var n = 0
        for (l in lessons) {
            if (l.isFree) continue
            for (p in l.firstPeriod..l.lastPeriod) if (!l.cancelledAt(p)) n++
        }
        return n
    }
}
