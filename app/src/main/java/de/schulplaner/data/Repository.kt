package de.schulplaner.data

import de.schulplaner.domain.ScheduleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Zentrale Stelle für alle Daten. Schreibvorgänge laufen im App-Scope,
 * damit sie nicht abbrechen, wenn ein Bildschirm geschlossen wird.
 */
class Repository(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    val files: AttachmentStore,
) {
    private val dao = db.dao()

    private fun <T> Flow<List<T>>.state(): StateFlow<List<T>> =
        stateIn(scope, SharingStarted.Eagerly, emptyList())

    val subjects = dao.subjects().state()
    val terms = dao.terms().state()
    val categories = dao.categories().state()
    val grades = dao.grades().state()
    val subjectTerms = dao.subjectTerms().state()
    val slots = dao.slots().state()
    val periods = dao.periods().state()
    val overrides = dao.overrides().state()
    val holidays = dao.holidays().state()
    val tasks = dao.tasks().state()
    val exams = dao.exams().state()
    val topics = dao.topics().state()
    val notes = dao.notes().state()
    val attachments = dao.attachments().state()

    val settings: StateFlow<Map<String, String>> = dao.settings()
        .map { list -> list.associate { it.id to it.value } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val scheduleData: StateFlow<ScheduleData> = combine(
        combine(subjects, slots, periods) { s, sl, p -> Triple(s, sl, p) },
        overrides,
        holidays,
        settings,
    ) { (s, sl, p), ov, h, st ->
        ScheduleData(
            subjects = s.associateBy { it.id },
            slots = sl.filter { slot -> s.any { it.id == slot.subjectId } },
            periods = p,
            overrides = ov.associateBy { it.id },
            holidays = h,
            abEnabled = st[SettingKeys.AB_ENABLED] == "true",
            abReference = st[SettingKeys.AB_REFERENCE]?.toLongOrNull(),
        )
    }.stateIn(scope, SharingStarted.Eagerly, ScheduleData())

    private fun now() = System.currentTimeMillis()
    private fun io(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) { block() }
    }

    // ---------- Fächer ----------
    fun saveSubject(s: Subject) = io { dao.upsertSubject(s.copy(updatedAt = now(), dirty = true)) }
    fun deleteSubject(s: Subject) = io {
        dao.upsertSubject(s.copy(deleted = true, updatedAt = now(), dirty = true))
        slots.value.filter { it.subjectId == s.id }.forEach {
            dao.upsertSlot(it.copy(deleted = true, updatedAt = now(), dirty = true))
        }
    }

    // ---------- Halbjahre ----------
    fun saveTerm(t: Term) = io { dao.upsertTerm(t.copy(updatedAt = now(), dirty = true)) }
    fun deleteTerm(t: Term) = io { dao.upsertTerm(t.copy(deleted = true, updatedAt = now(), dirty = true)) }

    fun currentTerm(terms: List<Term>, today: LocalDate = LocalDate.now()): Term? {
        val d = today.toEpochDay()
        return terms.firstOrNull { d in it.startDate..it.endDate }
            ?: terms.filter { it.startDate <= d }.maxByOrNull { it.startDate }
            ?: terms.firstOrNull()
    }

    fun termFor(date: Long): Term? = terms.value.firstOrNull { date in it.startDate..it.endDate }

    // ---------- Noten ----------
    fun saveCategory(c: GradeCategory) = io { dao.upsertCategory(c.copy(updatedAt = now(), dirty = true)) }
    fun deleteCategory(c: GradeCategory) = io {
        dao.upsertCategory(c.copy(deleted = true, updatedAt = now(), dirty = true))
        grades.value.filter { it.categoryId == c.id }.forEach {
            dao.upsertGrade(it.copy(deleted = true, updatedAt = now(), dirty = true))
        }
    }

    fun saveGrade(g: Grade) = io { dao.upsertGrade(g.copy(updatedAt = now(), dirty = true)) }
    fun deleteGrade(g: Grade) = io {
        dao.upsertGrade(g.copy(deleted = true, updatedAt = now(), dirty = true))
        deleteAttachmentsOf(g.id)
    }

    fun setOverride(subjectId: String, termId: String, points: Int?) = io {
        dao.upsertSubjectTerm(
            SubjectTerm(
                id = SubjectTerm.idFor(subjectId, termId),
                subjectId = subjectId,
                termId = termId,
                overridePoints = points,
                updatedAt = now(),
            ),
        )
    }

    /**
     * Sorgt dafür, dass ein Fach in einem Halbjahr Kategorien hat:
     * übernimmt die vom letzten Halbjahr, sonst "Schriftlich 50 % / Mündlich 50 %".
     */
    suspend fun ensureCategories(subjectId: String, termId: String): List<GradeCategory> {
        val existing = dao.categoriesFor(subjectId, termId)
        if (existing.isNotEmpty()) return existing
        val termOrder = terms.value.associate { it.id to it.startDate }
        val currentStart = termOrder[termId] ?: Long.MAX_VALUE
        val previous = dao.categoriesForSubject(subjectId)
            .filter { (termOrder[it.termId] ?: Long.MIN_VALUE) < currentStart }
            .groupBy { it.termId }
            .maxByOrNull { termOrder[it.key] ?: Long.MIN_VALUE }
            ?.value
        val template = previous?.map { it.copy(id = newId(), termId = termId) } ?: listOf(
            GradeCategory(subjectId = subjectId, termId = termId, name = "Schriftlich", kind = CategoryKind.WRITTEN, weight = 50.0, sortOrder = 0),
            GradeCategory(subjectId = subjectId, termId = termId, name = "Mündlich", kind = CategoryKind.ORAL, weight = 50.0, sortOrder = 1),
        )
        template.forEach { dao.upsertCategory(it.copy(updatedAt = now(), dirty = true)) }
        return template
    }

    // ---------- Stundenplan ----------
    fun saveSlot(s: LessonSlot) = io { dao.upsertSlot(s.copy(updatedAt = now(), dirty = true)) }
    fun deleteSlot(s: LessonSlot) = io { dao.upsertSlot(s.copy(deleted = true, updatedAt = now(), dirty = true)) }

    fun savePeriod(p: PeriodTime) = io { dao.upsertPeriod(p.copy(updatedAt = now(), dirty = true)) }
    fun deletePeriod(p: PeriodTime) = io { dao.upsertPeriod(p.copy(deleted = true, updatedAt = now(), dirty = true)) }

    fun saveOverride(o: LessonOverride) = io {
        val empty = !o.cancelled && o.room.isBlank() && o.note.isBlank()
        dao.upsertOverride(o.copy(deleted = empty, updatedAt = now(), dirty = true))
    }

    fun saveHoliday(h: Holiday) = io { dao.upsertHoliday(h.copy(updatedAt = now(), dirty = true)) }
    fun deleteHoliday(h: Holiday) = io { dao.upsertHoliday(h.copy(deleted = true, updatedAt = now(), dirty = true)) }

    // ---------- Aufgaben ----------
    fun saveTask(t: Task) = io {
        val created = if (t.createdAt == 0L) now() else t.createdAt
        dao.upsertTask(t.copy(createdAt = created, updatedAt = now(), dirty = true))
    }

    fun setTaskDone(t: Task, done: Boolean) = saveTask(t.copy(done = done, doneAt = if (done) now() else null))
    fun deleteTask(t: Task) = io {
        dao.upsertTask(t.copy(deleted = true, updatedAt = now(), dirty = true))
        deleteAttachmentsOf(t.id)
    }

    // ---------- Klausuren ----------
    fun saveExam(e: Exam) = io { dao.upsertExam(e.copy(updatedAt = now(), dirty = true)) }
    fun deleteExam(e: Exam) = io {
        dao.upsertExam(e.copy(deleted = true, updatedAt = now(), dirty = true))
        topics.value.filter { it.examId == e.id }.forEach {
            dao.upsertTopic(it.copy(deleted = true, updatedAt = now(), dirty = true))
        }
        deleteAttachmentsOf(e.id)
    }

    fun saveTopic(t: ExamTopic) = io { dao.upsertTopic(t.copy(updatedAt = now(), dirty = true)) }
    fun deleteTopic(t: ExamTopic) = io { dao.upsertTopic(t.copy(deleted = true, updatedAt = now(), dirty = true)) }

    /** Trägt das Ergebnis einer Klausur als schriftliche Note ein (oder aktualisiert sie). */
    fun setExamResult(exam: Exam, points: Int?) = io {
        val existing = exam.gradeId?.let { dao.grade(it) }
        if (points == null) {
            existing?.let { dao.upsertGrade(it.copy(deleted = true, updatedAt = now(), dirty = true)) }
            dao.upsertExam(exam.copy(gradeId = null, updatedAt = now(), dirty = true))
            return@io
        }
        val term = termFor(exam.date) ?: currentTerm(terms.value) ?: return@io
        val cats = ensureCategories(exam.subjectId, term.id)
        val cat = cats.firstOrNull { it.kind == CategoryKind.WRITTEN } ?: cats.first()
        val grade = (existing ?: Grade()).copy(
            subjectId = exam.subjectId,
            termId = term.id,
            categoryId = cat.id,
            points = points,
            title = exam.title.ifBlank { exam.type },
            date = exam.date,
            deleted = false,
            updatedAt = now(),
            dirty = true,
        )
        dao.upsertGrade(grade)
        dao.upsertExam(exam.copy(gradeId = grade.id, updatedAt = now(), dirty = true))
    }

    // ---------- Notizen ----------
    fun saveNote(n: SubjectNote) = io { dao.upsertNote(n.copy(updatedAt = now(), dirty = true)) }
    fun deleteNote(n: SubjectNote) = io { dao.upsertNote(n.copy(deleted = true, updatedAt = now(), dirty = true)) }

    // ---------- Anhänge ----------
    fun addAttachment(a: Attachment) = io { dao.upsertAttachment(a.copy(updatedAt = now(), dirty = true)) }
    fun deleteAttachment(a: Attachment) = io {
        dao.upsertAttachment(a.copy(deleted = true, updatedAt = now(), dirty = true))
        files.fileFor(a).delete()
    }

    /** Für abgebrochene neue Einträge: schon hinzugefügte Fotos wieder entfernen. */
    fun discardAttachments(ownerId: String) = io { deleteAttachmentsOf(ownerId) }

    private suspend fun deleteAttachmentsOf(ownerId: String) {
        attachments.value.filter { it.ownerId == ownerId }.forEach {
            dao.upsertAttachment(it.copy(deleted = true, updatedAt = now(), dirty = true))
            files.fileFor(it).delete()
        }
    }

    // ---------- Einstellungen ----------
    fun setSetting(key: String, value: String) = io {
        dao.upsertSetting(Setting(id = key, value = value, updatedAt = now(), dirty = true))
    }

    /** Legt beim ersten Start Stundenzeiten, Halbjahre und A/B-Woche an. */
    suspend fun seedIfNeeded() {
        if (dao.setting(SettingKeys.SEEDED) != null) return
        val t = now()
        // Typische 45-Minuten-Raster, in den Einstellungen änderbar.
        val times = listOf(
            7 * 60 + 45 to 8 * 60 + 30,
            8 * 60 + 30 to 9 * 60 + 15,
            9 * 60 + 35 to 10 * 60 + 20,
            10 * 60 + 20 to 11 * 60 + 5,
            11 * 60 + 20 to 12 * 60 + 5,
            12 * 60 + 5 to 12 * 60 + 50,
            13 * 60 + 30 to 14 * 60 + 15,
            14 * 60 + 15 to 15 * 60,
        )
        times.forEachIndexed { i, (s, e) ->
            dao.upsertPeriod(PeriodTime(PeriodTime.idFor(i + 1), i + 1, s, e, updatedAt = t))
        }
        // Halbjahre E1–Q4 (Aug–Jan / Feb–Jul), Daten in den Einstellungen anpassbar.
        val today = LocalDate.now()
        val schoolYearStart = if (today.monthValue >= 8) today.year else today.year - 1
        // Wer gerade in Q1 ist, hat die E-Phase im Schuljahr davor gehabt.
        val eStartYear = schoolYearStart - 1
        val names = listOf("E1", "E2", "Q1", "Q2", "Q3", "Q4")
        names.forEachIndexed { i, name ->
            val year = eStartYear + i / 2
            val start = if (i % 2 == 0) LocalDate.of(year, 8, 1) else LocalDate.of(year + 1, 2, 1)
            val end = if (i % 2 == 0) LocalDate.of(year + 1, 1, 31) else LocalDate.of(year + 1, 7, 31)
            dao.upsertTerm(Term(name = name, startDate = start.toEpochDay(), endDate = end.toEpochDay(), sortOrder = i, updatedAt = t))
        }
        val monday = de.schulplaner.domain.Schedule.mondayOf(today).toEpochDay()
        dao.upsertSetting(Setting(SettingKeys.AB_ENABLED, "true", updatedAt = t))
        dao.upsertSetting(Setting(SettingKeys.AB_REFERENCE, monday.toString(), updatedAt = t))
        dao.upsertSetting(Setting(SettingKeys.SEEDED, "true", updatedAt = t))
    }

    /** Wartet, bis die Daten einmal aus der Datenbank geladen wurden. */
    suspend fun awaitLoaded() {
        dao.settings().first()
    }
}
