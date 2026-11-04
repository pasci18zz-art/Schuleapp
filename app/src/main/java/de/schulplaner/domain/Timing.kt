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

    /** Stunden zählen: Doppelstunden zählen doppelt, Entfall zählt nicht. */
    fun periodCount(lessons: List<Lesson>): Int = lessons.filter { !it.cancelled && !it.isFree }.sumOf { it.length }
}
