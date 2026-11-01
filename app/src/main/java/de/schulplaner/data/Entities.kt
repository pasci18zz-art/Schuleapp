package de.schulplaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
@Serializable
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
    /** Früher Notizbuch-Knopf pro Fach – wird nicht mehr genutzt, bleibt aber für bestehende Datenbanken. */
    val notesLink: String = "",
    /** Symbol des Fachs (Schlüssel aus SubjectIcons), leer = automatisch nach dem Namen. */
    val icon: String = "",
    /** Geplante Klausuren bzw. mündliche Noten pro Halbjahr (für den Notenziel-Rechner). */
    val plannedExams: Int = 2,
    val plannedOral: Int = 3,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

/** Halbjahr, z. B. "Q1". Datumswerte sind LocalDate.toEpochDay(). */
@Entity(tableName = "terms")
@Serializable
data class Term(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

object CategoryKind {
    const val WRITTEN = "WRITTEN"
    const val ORAL = "ORAL"
    const val OTHER = "OTHER"
}

/** Bewertungskategorie eines Fachs in einem Halbjahr, z. B. "Schriftlich 50 %". */
@Entity(tableName = "grade_categories")
@Serializable
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
    @Transient val dirty: Boolean = true,
)

@Entity(tableName = "grades")
@Serializable
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
    @Transient val dirty: Boolean = true,
)

/** Pro Fach und Halbjahr: optional die echte Zeugnisnote, die den berechneten Wert ersetzt. */
@Entity(tableName = "subject_terms")
@Serializable
data class SubjectTerm(
    @PrimaryKey val id: String,
    val subjectId: String,
    val termId: String,
    val overridePoints: Int? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
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

/**
 * Eine Stunde im Wochenplan. length = Anzahl 45-Minuten-Blöcke (Doppelstunde = 2).
 * Freistunden haben subjectId = [FREE].
 */
@Entity(tableName = "lesson_slots")
@Serializable
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
    @Transient val dirty: Boolean = true,
) {
    val isFree: Boolean get() = subjectId == FREE

    companion object {
        const val FREE = "__frei"
    }
}

/** Uhrzeiten einer Stunde, Minuten seit Mitternacht. id = "p<nummer>". */
@Entity(tableName = "period_times")
@Serializable
data class PeriodTime(
    @PrimaryKey val id: String,
    val number: Int,
    val startMinute: Int,
    val endMinute: Int,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
) {
    companion object {
        fun idFor(number: Int) = "p$number"
    }
}

/** Änderung für genau einen Termin einer Stunde (Entfall, Raum, Vertretung). */
@Entity(tableName = "lesson_overrides")
@Serializable
data class LessonOverride(
    @PrimaryKey val id: String,
    val slotId: String,
    val date: Long,
    val cancelled: Boolean = false,
    val room: String = "",
    val note: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
) {
    companion object {
        fun idFor(slotId: String, date: Long) = "${slotId}_$date"
    }
}

object TaskType {
    const val HOMEWORK = "HA"
    const val EXTRA = "EXTRA"
    const val OTHER = "OTHER"
    const val TALK = "REFERAT"
}

/** Aufgabenart (selbst einstellbar, wird als Einstellung synchronisiert). */
@Serializable
data class TaskKind(
    val id: String = newId(),
    val name: String = "",
    /** Schlüssel aus KindIcons. */
    val icon: String = "dots",
) {
    companion object {
        val defaults = listOf(
            TaskKind(TaskType.HOMEWORK, "Hausaufgabe", "house"),
            TaskKind(TaskType.TALK, "Referat", "presentation"),
            TaskKind(TaskType.EXTRA, "Zusatz", "star"),
            TaskKind(TaskType.OTHER, "Sonstiges", "dots"),
        )
    }
}

@Entity(tableName = "tasks")
@Serializable
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
    @Transient val dirty: Boolean = true,
)

object ExamType {
    val all = listOf("Klausur", "Test", "Referat", "Sonstiges")
}

@Entity(tableName = "exams")
@Serializable
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
    @Transient val dirty: Boolean = true,
)

/** Thema einer Klausur oder Unterpunkt einer Aufgabe (examId = Klausur- bzw. Aufgaben-ID). */
@Entity(tableName = "exam_topics")
@Serializable
data class ExamTopic(
    @PrimaryKey val id: String = newId(),
    val examId: String = "",
    val text: String = "",
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

/** Arten einer Lernsession (fest): Hausaufgaben, Referat, Lernen. */
object StudyKind {
    const val HOMEWORK = "HA"
    const val TALK = "REFERAT"
    const val LEARN = "LERNEN"
    val all = listOf(HOMEWORK, TALK, LEARN)

    fun label(kind: String) = when (kind) {
        HOMEWORK -> "Hausaufgaben"
        TALK -> "Referat"
        else -> "Lernen"
    }
}

/**
 * Lernsession (Stoppuhr). Die Zeit ergibt sich aus [elapsedMs] plus der laufenden Strecke seit [runningSince].
 * Höchstens eine Session ist nicht [finished] – das ist die aktuelle.
 */
@Entity(tableName = "study_sessions")
@Serializable
data class StudySession(
    @PrimaryKey val id: String = newId(),
    val kind: String = StudyKind.LEARN,
    val subjectId: String? = null,
    /** Beginn (ms seit 1970). */
    val startedAt: Long = 0,
    /** Ende (ms), nur bei beendeten Sessions. */
    val endedAt: Long? = null,
    /** Bisher gezählte Zeit ohne die laufende Strecke. */
    val elapsedMs: Long = 0,
    /** Läuft gerade seit (ms), null = pausiert oder beendet. */
    val runningSince: Long? = null,
    val finished: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
) {
    fun totalMs(now: Long = System.currentTimeMillis()): Long = elapsedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    val isRunning: Boolean get() = runningSince != null && !finished
}

object HolidayKind {
    const val FERIEN = "FERIEN"
    const val FEIERTAG = "FEIERTAG"
    const val FREI = "FREI"
}

@Entity(tableName = "holidays")
@Serializable
data class Holiday(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val kind: String = HolidayKind.FREI,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

@Entity(tableName = "subject_notes")
@Serializable
data class SubjectNote(
    @PrimaryKey val id: String = newId(),
    val subjectId: String = "",
    val text: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

object OwnerType {
    const val EXAM = "exam"
    const val GRADE = "grade"
    const val TASK = "task"
}

/** Datei (Foto, PDF …), liegt unter filesDir/attachments/<id>.<ext>. */
@Entity(tableName = "attachments")
@Serializable
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
    @Transient val dirty: Boolean = true,
)

/** Einstellungen, die zwischen den Geräten synchronisiert werden (A/B-Woche usw.). */
@Entity(tableName = "settings")
@Serializable
data class Setting(
    @PrimaryKey val id: String,
    val value: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @Transient val dirty: Boolean = true,
)

object SettingKeys {
    const val AB_ENABLED = "ab_enabled"
    /** Montag (epochDay) einer Woche, die eine A-Woche ist. */
    const val AB_REFERENCE = "ab_reference_monday"
    const val SEEDED = "seeded"
    const val HESSEN_HOLIDAYS = "hessen_ferien_v1"
    /** Liste der Aufgabenarten als JSON. */
    const val TASK_KINDS = "task_kinds"
    /** Vorauswahl der Stoppuhr (Art, Fach), wenn gerade keine Session läuft. */
    const val STUDY_KIND = "study_kind"
    const val STUDY_SUBJECT = "study_subject"
    /** Lernzettel-Aufgabe beim Anlegen einer Klausur ("false" = aus) und ihre Aufgabenart. */
    const val LZ_ENABLED = "lz_enabled"
    const val LZ_KIND = "lz_kind"
    /** Notenziel (Punkte) pro Fach und Halbjahr: "goal_<fach>_<halbjahr>". */
    const val GOAL_PREFIX = "goal_"
    /** Abweichende Planung pro Fach und Halbjahr: "plan_<fach>_<halbjahr>" = "klausuren,muendlich". */
    const val PLAN_PREFIX = "plan_"
    /** Monster-Zähler: ein Eintrag pro Tag, "monster_<epochDay>" = Anzahl. */
    const val MONSTER_PREFIX = "monster_"
}
