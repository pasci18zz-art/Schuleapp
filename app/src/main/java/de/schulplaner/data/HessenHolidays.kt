package de.schulplaner.data

import java.time.LocalDate

/**
 * Schulferien und gesetzliche Feiertage in Hessen.
 * Ferientermine laut Ferienkalender (Hessisches Kultusministerium / KMK), Stand Oktober 2026.
 * Feiertage werden berechnet. Die IDs sind fest, damit beide Geräte beim Sync dieselben Einträge haben.
 */
object HessenHolidays {
    private data class F(val key: String, val name: String, val start: String, val end: String)

    private val ferien = listOf(
        F("2026_herbst", "Herbstferien", "2026-10-05", "2026-10-17"),
        F("2026_weihnachten", "Weihnachtsferien", "2026-12-23", "2027-01-12"),
        F("2027_ostern", "Osterferien", "2027-03-22", "2027-04-02"),
        F("2027_sommer", "Sommerferien", "2027-06-28", "2027-08-06"),
        F("2027_herbst", "Herbstferien", "2027-10-04", "2027-10-16"),
        F("2027_weihnachten", "Weihnachtsferien", "2027-12-23", "2028-01-11"),
        F("2028_ostern", "Osterferien", "2028-04-03", "2028-04-14"),
        F("2028_sommer", "Sommerferien", "2028-07-03", "2028-08-11"),
        F("2028_herbst", "Herbstferien", "2028-10-09", "2028-10-20"),
        F("2028_weihnachten", "Weihnachtsferien", "2028-12-27", "2029-01-12"),
    )

    /** Ostersonntag (Gaußsche Osterformel, gregorianisch). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private fun feiertage(year: Int): List<Pair<String, LocalDate>> {
        val e = easter(year)
        return listOf(
            "Neujahr" to LocalDate.of(year, 1, 1),
            "Karfreitag" to e.minusDays(2),
            "Ostermontag" to e.plusDays(1),
            "Tag der Arbeit" to LocalDate.of(year, 5, 1),
            "Christi Himmelfahrt" to e.plusDays(39),
            "Pfingstmontag" to e.plusDays(50),
            "Fronleichnam" to e.plusDays(60),
            "Tag der Deutschen Einheit" to LocalDate.of(year, 10, 3),
            "1. Weihnachtstag" to LocalDate.of(year, 12, 25),
            "2. Weihnachtstag" to LocalDate.of(year, 12, 26),
        )
    }

    fun all(now: Long): List<Holiday> {
        val list = ferien.map {
            Holiday(
                id = "hessen_${it.key}",
                name = it.name,
                startDate = LocalDate.parse(it.start).toEpochDay(),
                endDate = LocalDate.parse(it.end).toEpochDay(),
                kind = HolidayKind.FERIEN,
                updatedAt = now,
            )
        }
        val days = (2026..2028).flatMap { y ->
            feiertage(y).map { (name, d) ->
                Holiday(
                    id = "hessen_ft_$d",
                    name = name,
                    startDate = d.toEpochDay(),
                    endDate = d.toEpochDay(),
                    kind = HolidayKind.FEIERTAG,
                    updatedAt = now,
                )
            }
        }
        return list + days
    }
}
