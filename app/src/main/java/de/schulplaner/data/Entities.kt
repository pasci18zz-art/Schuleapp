package de.schulplaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Alle Tabellen haben dieselben Sync-Felder:
 *  - id: UUID als Text, damit zwei Geräte unabhängig Einträge anlegen können
 *  - updatedAt: Zeitpunkt der letzten Änderung (ms), "neueste Änderung gewinnt"
 *  - deleted: Löschen ist nur eine Markierung, damit das andere Gerät es mitbekommt
 *  - dirty: noch nicht hochgeladen (nur lokal)
 */

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val shortName: String = "",
    val color: Long = 0xFF5B7FDB,
    val teacher: String = "",
    val room: String = "",
    val isLk: Boolean = false,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** Notizbuch-Knopf: "" = keiner, "app" = Samsung Notes öffnen, "intent:…" = Verknüpfung, "http…" = Link */
    val notesLink: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Halbjahr, z. B. "Q1". Datumswerte sind LocalDate.toEpochDay(). */
@Entity(tableName = "terms")
data class Term(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

object CategoryKind {
    const val WRITTEN = "WRITTEN"
    const val ORAL = "ORAL"
    const val OTHER = "OTHER"
}

/** Bewertungskategorie eines Fachs in einem Halbjahr, z. B. "Schriftlich 50 %". */
@Entity(tableName = "grade_categories")
data class GradeCategory(
    @PrimaryKey val id: String = newId(),
    val subjectId: String = "",
    val termId: String = "",
    val name: String = "",
    val kind: String = CategoryKind.OTHER,
    val weight: Double = 0.0,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "grades")
data class Grade(
    @PrimaryKey val id: String = newId(),
    val subjectId: String = "",
    val termId: String = "",
    val categoryId: String = "",
    val points: Int = 10,
    val title: String = "",
    val reason: String = "",
    val date: Long = 0,
    val weight: Double = 1.0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Pro Fach und Halbjahr: optional die echte Zeugnisnote, die den berechneten Wert ersetzt. */
@Entity(tableName = "subject_terms")
data class SubjectTerm(
    @PrimaryKey val id: String,
    val subjectId: String,
    val termId: String,
    val overridePoints: Int? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
) {
    companion object {
        fun idFor(subjectId: String, termId: String) = "${subjectId}_$termId"
    }
}

object WeekType {
    const val ALL = "ALL"
    const val A = "A"
    const val B = "B"
}

/** Eine Stunde im Wochenplan. length = Anzahl 45-Minuten-Blöcke (Doppelstunde = 2). */
@Entity(tableName = "lesson_slots")
data class LessonSlot(
    @PrimaryKey val id: String = newId(),
    val dayOfWeek: Int = 1,
    val period: Int = 1,
    val length: Int = 2,
    val subjectId: String = "",
    val room: String = "",
    val weekType: String = WeekType.ALL,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Uhrzeiten einer Stunde, Minuten seit Mitternacht. id = "p<nummer>". */
@Entity(tableName = "period_times")
data class PeriodTime(
    @PrimaryKey val id: String,
    val number: Int,
    val startMinute: Int,
    val endMinute: Int,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
) {
    companion object {
        fun idFor(number: Int) = "p$number"
    }
}

/** Änderung für genau einen Termin einer Stunde (Entfall, Raum, Vertretung). */
@Entity(tableName = "lesson_overrides")
data class LessonOverride(
    @PrimaryKey val id: String,
    val slotId: String,
    val date: Long,
    val cancelled: Boolean = false,
    val room: String = "",
    val note: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
) {
    companion object {
        fun idFor(slotId: String, date: Long) = "${slotId}_$date"
    }
}

object TaskType {
    const val HOMEWORK = "HA"
    const val EXTRA = "EXTRA"
    const val OTHER = "OTHER"

    fun label(type: String) = when (type) {
        HOMEWORK -> "HA"
        EXTRA -> "Zusatz"
        else -> "Sonst."
    }
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = newId(),
    val title: String = "",
    val description: String = "",
    val subjectId: String? = null,
    val type: String = TaskType.HOMEWORK,
    val dueDate: Long? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

object ExamType {
    val all = listOf("Klausur", "Test", "Referat", "Sonstiges")
}

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey val id: String = newId(),
    val subjectId: String = "",
    val date: Long = 0,
    val period: Int? = null,
    val title: String = "",
    val type: String = "Klausur",
    val topicsText: String = "",
    val gradeId: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "exam_topics")
data class ExamTopic(
    @PrimaryKey val id: String = newId(),
    val examId: String = "",
    val text: String = "",
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

object HolidayKind {
    const val FERIEN = "FERIEN"
    const val FEIERTAG = "FEIERTAG"
    const val FREI = "FREI"
}

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val kind: String = HolidayKind.FREI,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "subject_notes")
data class SubjectNote(
    @PrimaryKey val id: String = newId(),
    val subjectId: String = "",
    val text: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

object OwnerType {
    const val EXAM = "exam"
    const val GRADE = "grade"
    const val TASK = "task"
}

/** Datei (Foto, PDF …), liegt unter filesDir/attachments/<id>.<ext>. */
@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val ownerType: String = "",
    val ownerId: String = "",
    val fileName: String = "",
    val displayName: String = "",
    val mimeType: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Einstellungen, die zwischen den Geräten synchronisiert werden (A/B-Woche usw.). */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val id: String,
    val value: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

object SettingKeys {
    const val AB_ENABLED = "ab_enabled"
    /** Montag (epochDay) einer Woche, die eine A-Woche ist. */
    const val AB_REFERENCE = "ab_reference_monday"
    const val SEEDED = "seeded"
    const val HESSEN_HOLIDAYS = "hessen_ferien_v1"
}
