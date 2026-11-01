package de.schulplaner.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Deutsche Datums- und Zeitformate. */
object Fmt {
    private val dayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val dayLong = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val monthShort = listOf("Jan", "Feb", "März", "Apr", "Mai", "Juni", "Juli", "Aug", "Sep", "Okt", "Nov", "Dez")
    private val longFormat = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

    fun dayShort(date: LocalDate) = dayShort[date.dayOfWeek.value - 1]
    fun dayShort(dayOfWeek: Int) = dayShort[dayOfWeek - 1]
    fun dayLong(dayOfWeek: Int) = dayLong[dayOfWeek - 1]

    /** "30.10." */
    fun dateShort(date: LocalDate) = "${date.dayOfMonth}.${date.monthValue}."

    /** "Fr, 30.10." */
    fun dayDate(date: LocalDate) = "${dayShort(date)}, ${dateShort(date)}"

    /** "Fr, 30. Okt" */
    fun dayMonth(date: LocalDate) = "${dayShort(date)}, ${date.dayOfMonth}. ${monthShort[date.monthValue - 1]}"

    /** "Freitag, 30. Oktober" */
    fun full(date: LocalDate) = "${dayLong[date.dayOfWeek.value - 1]}, ${date.format(longFormat)}"

    /** "26.–30. Okt" */
    fun weekRange(monday: LocalDate): String {
        val friday = monday.plusDays(4)
        return if (monday.month == friday.month) {
            "${monday.dayOfMonth}.–${friday.dayOfMonth}. ${monthShort[friday.monthValue - 1]}"
        } else {
            "${monday.dayOfMonth}. ${monthShort[monday.monthValue - 1]} – ${friday.dayOfMonth}. ${monthShort[friday.monthValue - 1]}"
        }
    }

    fun time(t: LocalTime) = "${t.hour}:${t.minute.toString().padStart(2, '0')}"
    fun minutes(m: Int) = "${m / 60}:${(m % 60).toString().padStart(2, '0')}"

    fun daysUntil(today: LocalDate, date: LocalDate) = ChronoUnit.DAYS.between(today, date)

    /** Fälligkeit kurz: "überfällig", "heute", "morgen", "Mo", "12.11." */
    fun due(today: LocalDate, date: LocalDate): String {
        val d = daysUntil(today, date)
        return when {
            d < 0 -> "überfällig"
            d == 0L -> "heute"
            d == 1L -> "morgen"
            d < 7 -> dayShort(date)
            else -> dateShort(date)
        }
    }

    fun inDays(days: Long) = when (days) {
        0L -> "heute"
        1L -> "morgen"
        else -> "in $days Tagen"
    }

    fun weekOfYear(date: LocalDate): Int = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

/** Kurzname eines Fachs, sonst der volle Name. */
val de.schulplaner.data.Subject.label: String get() = shortName.ifBlank { name }
