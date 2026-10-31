package de.schulplaner.domain

import de.schulplaner.data.Holiday
import de.schulplaner.data.LessonOverride
import de.schulplaner.data.LessonSlot
import de.schulplaner.data.PeriodTime
import de.schulplaner.data.Subject
import de.schulplaner.data.WeekType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Momentaufnahme aller Daten, die man für Stundenplan-Berechnungen braucht. */
data class ScheduleData(
    val subjects: Map<String, Subject> = emptyMap(),
    val slots: List<LessonSlot> = emptyList(),
    val periods: List<PeriodTime> = emptyList(),
    val overrides: Map<String, LessonOverride> = emptyMap(),
    val holidays: List<Holiday> = emptyList(),
    val abEnabled: Boolean = false,
    val abReference: Long? = null,
) {
    val periodByNumber: Map<Int, PeriodTime> = periods.associateBy { it.number }
    val maxPeriod: Int = maxOf(periods.maxOfOrNull { it.number } ?: 8, slots.maxOfOrNull { it.period + it.length - 1 } ?: 0)
}

/** Eine konkrete Stunde an einem Datum (Doppelstunden sind eine Stunde mit zwei Blöcken). */
data class Lesson(
    val slot: LessonSlot,
    val subject: Subject?,
    val date: LocalDate,
    val firstPeriod: Int,
    val lastPeriod: Int,
    val start: LocalTime?,
    val end: LocalTime?,
    val room: String,
    val cancelled: Boolean,
    val note: String,
    val override: LessonOverride?,
) {
    val periodLabel: String = if (firstPeriod == lastPeriod) "$firstPeriod." else "$firstPeriod.–$lastPeriod."
    val isFree: Boolean get() = slot.isFree
    val length: Int get() = lastPeriod - firstPeriod + 1
    val startDateTime: LocalDateTime? get() = start?.let { date.atTime(it) }
    val endDateTime: LocalDateTime? get() = end?.let { date.atTime(it) }
}

object Schedule {

    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** "A", "B" oder null, wenn A/B-Wochen ausgeschaltet sind. */
    fun weekTypeOf(date: LocalDate, data: ScheduleData): String? {
        if (!data.abEnabled) return null
        val ref = data.abReference ?: return null
        val weeks = Math.floorDiv(mondayOf(date).toEpochDay() - ref, 7L)
        return if (Math.floorMod(weeks, 2L) == 0L) WeekType.A else WeekType.B
    }

    fun holidayOn(date: LocalDate, data: ScheduleData): Holiday? {
        val d = date.toEpochDay()
        return data.holidays.firstOrNull { d in it.startDate..it.endDate }
    }

    fun slotMatches(slot: LessonSlot, weekType: String?): Boolean =
        slot.weekType == WeekType.ALL || weekType == null || slot.weekType == weekType

    /** Stunden laut Plan für ein Datum, ohne Ferien-Prüfung. Freistunden nur mit [withFree]. */
    fun plannedLessons(date: LocalDate, data: ScheduleData, withFree: Boolean = false): List<Lesson> {
        val dow = date.dayOfWeek.value
        if (dow > 5) return emptyList()
        val wt = weekTypeOf(date, data)
        val epoch = date.toEpochDay()
        return data.slots
            .filter { it.dayOfWeek == dow && slotMatches(it, wt) && (withFree || !it.isFree) }
            .sortedBy { it.period }
            .map { slot ->
                val ov = data.overrides[LessonOverride.idFor(slot.id, epoch)]
                val subject = data.subjects[slot.subjectId]
                val last = slot.period + slot.length - 1
                Lesson(
                    slot = slot,
                    subject = subject,
                    date = date,
                    firstPeriod = slot.period,
                    lastPeriod = last,
                    start = data.periodByNumber[slot.period]?.let { LocalTime.of(it.startMinute / 60, it.startMinute % 60) },
                    end = data.periodByNumber[last]?.let { LocalTime.of(it.endMinute / 60, it.endMinute % 60) },
                    room = ov?.room?.takeIf { it.isNotBlank() } ?: slot.room.ifBlank { subject?.room ?: "" },
                    cancelled = ov?.cancelled == true,
                    note = ov?.note ?: "",
                    override = ov,
                )
            }
    }

    /** Stunden an einem Datum; leer am Wochenende und in den Ferien. */
    fun lessonsOn(date: LocalDate, data: ScheduleData, withFree: Boolean = false): List<Lesson> =
        if (holidayOn(date, data) != null) emptyList() else plannedLessons(date, data, withFree)

    fun currentLesson(now: LocalDateTime, data: ScheduleData): Lesson? =
        lessonsOn(now.toLocalDate(), data).firstOrNull { l ->
            val s = l.startDateTime
            val e = l.endDateTime
            s != null && e != null && !now.isBefore(s) && now.isBefore(e)
        }

    /** Die laufende Stunde, sonst die zuletzt beendete von heute. */
    fun currentOrLastLesson(now: LocalDateTime, data: ScheduleData): Lesson? {
        currentLesson(now, data)?.let { return it }
        return lessonsOn(now.toLocalDate(), data)
            .filter { it.endDateTime?.let { e -> !now.isBefore(e) } == true }
            .maxByOrNull { it.lastPeriod }
    }

    fun nextLessonToday(now: LocalDateTime, data: ScheduleData): Lesson? =
        lessonsOn(now.toLocalDate(), data)
            .filter { !it.cancelled && it.startDateTime?.isAfter(now) == true }
            .minByOrNull { it.firstPeriod }

    /** Nächster Tag nach [after], an dem das Fach stattfindet (ohne Entfall). */
    fun nextOccurrence(subjectId: String, after: LocalDate, data: ScheduleData, maxDays: Int = 70): Lesson? {
        var d = after.plusDays(1)
        repeat(maxDays) {
            lessonsOn(d, data).firstOrNull { it.slot.subjectId == subjectId && !it.cancelled }?.let { return it }
            d = d.plusDays(1)
        }
        return null
    }

    /** Die nächsten [count] Stunden des Fachs nach [after] (eine pro Tag). */
    fun nextOccurrences(subjectId: String, after: LocalDate, data: ScheduleData, count: Int, maxDays: Int = 120): List<Lesson> {
        val out = mutableListOf<Lesson>()
        var d = after.plusDays(1)
        repeat(maxDays) {
            lessonsOn(d, data).firstOrNull { it.slot.subjectId == subjectId && !it.cancelled }?.let { out += it }
            if (out.size >= count) return out
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Welche Woche der Stundenplan zeigt: ab Unterrichtsende am Freitag (ohne Unterricht ab 13 Uhr)
     * und am Wochenende schon die nächste.
     */
    fun weekToShow(now: LocalDateTime, data: ScheduleData): LocalDate {
        val today = now.toLocalDate()
        val monday = mondayOf(today)
        val dow = today.dayOfWeek.value
        if (dow >= 6) return monday.plusWeeks(1)
        if (dow == 5) {
            val end = lessonsOn(today, data).mapNotNull { it.endDateTime }.maxOrNull() ?: today.atTime(13, 0)
            if (!now.isBefore(end)) return monday.plusWeeks(1)
        }
        return monday
    }

    /** Schultag [offset] Schultage von [from] entfernt (Tage ohne Unterricht werden übersprungen). */
    fun schoolDayAt(from: LocalDate, offset: Int, data: ScheduleData): LocalDate {
        var d = from
        var left = kotlin.math.abs(offset)
        val step = if (offset >= 0) 1L else -1L
        var guard = 0
        while (left > 0 && guard < 400) {
            d = d.plusDays(step)
            guard++
            if (lessonsOn(d, data).isNotEmpty()) left--
        }
        return d
    }

    /** Erster Tag ab [from] (inklusive), an dem Unterricht ist. */
    fun nextSchoolDay(from: LocalDate, data: ScheduleData, maxDays: Int = 60): LocalDate? {
        var d = from
        repeat(maxDays) {
            if (lessonsOn(d, data).isNotEmpty()) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** Welcher Tag auf "Heute" gezeigt wird: heute, solange noch Unterricht läuft, sonst der nächste Schultag. */
    fun displayDay(now: LocalDateTime, data: ScheduleData): LocalDate {
        val today = now.toLocalDate()
        val lessons = lessonsOn(today, data)
        val lastEnd = lessons.mapNotNull { it.endDateTime }.maxOrNull()
        if (lessons.isNotEmpty() && lastEnd != null && now.isBefore(lastEnd)) return today
        return nextSchoolDay(today.plusDays(1), data) ?: today
    }
}
