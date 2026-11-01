package de.schulplaner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SchulDao {
    // Fächer
    @Query("SELECT * FROM subjects WHERE deleted = 0 ORDER BY sortOrder, name")
    fun subjects(): Flow<List<Subject>>

    @Upsert
    suspend fun upsertSubject(item: Subject)

    // Halbjahre
    @Query("SELECT * FROM terms WHERE deleted = 0 ORDER BY sortOrder, startDate")
    fun terms(): Flow<List<Term>>

    @Upsert
    suspend fun upsertTerm(item: Term)

    // Bewertung
    @Query("SELECT * FROM grade_categories WHERE deleted = 0 ORDER BY sortOrder")
    fun categories(): Flow<List<GradeCategory>>

    @Query("SELECT * FROM grade_categories WHERE deleted = 0 AND subjectId = :subjectId AND termId = :termId ORDER BY sortOrder")
    suspend fun categoriesFor(subjectId: String, termId: String): List<GradeCategory>

    @Query("SELECT * FROM grade_categories WHERE deleted = 0 AND subjectId = :subjectId ORDER BY sortOrder")
    suspend fun categoriesForSubject(subjectId: String): List<GradeCategory>

    @Upsert
    suspend fun upsertCategory(item: GradeCategory)

    @Query("SELECT * FROM grades WHERE deleted = 0 ORDER BY date, title")
    fun grades(): Flow<List<Grade>>

    @Query("SELECT * FROM grades WHERE id = :id")
    suspend fun grade(id: String): Grade?

    @Upsert
    suspend fun upsertGrade(item: Grade)

    @Query("SELECT * FROM subject_terms WHERE deleted = 0")
    fun subjectTerms(): Flow<List<SubjectTerm>>

    @Upsert
    suspend fun upsertSubjectTerm(item: SubjectTerm)

    // Stundenplan
    @Query("SELECT * FROM lesson_slots WHERE deleted = 0")
    fun slots(): Flow<List<LessonSlot>>

    @Upsert
    suspend fun upsertSlot(item: LessonSlot)

    @Query("SELECT * FROM period_times WHERE deleted = 0 ORDER BY number")
    fun periods(): Flow<List<PeriodTime>>

    @Upsert
    suspend fun upsertPeriod(item: PeriodTime)

    @Query("SELECT * FROM lesson_overrides WHERE deleted = 0")
    fun overrides(): Flow<List<LessonOverride>>

    @Upsert
    suspend fun upsertOverride(item: LessonOverride)

    @Query("SELECT * FROM holidays WHERE deleted = 0 ORDER BY startDate")
    fun holidays(): Flow<List<Holiday>>

    @Upsert
    suspend fun upsertHoliday(item: Holiday)

    // Aufgaben
    @Query("SELECT * FROM tasks WHERE deleted = 0")
    fun tasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): Task?

    @Upsert
    suspend fun upsertTask(item: Task)

    // Klausuren
    @Query("SELECT * FROM exams WHERE deleted = 0 ORDER BY date")
    fun exams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun exam(id: String): Exam?

    @Upsert
    suspend fun upsertExam(item: Exam)

    @Query("SELECT * FROM exam_topics WHERE deleted = 0 ORDER BY sortOrder")
    fun topics(): Flow<List<ExamTopic>>

    @Upsert
    suspend fun upsertTopic(item: ExamTopic)

    @Query("SELECT * FROM exam_topics")
    suspend fun allTopics(): List<ExamTopic>

    // Notizen
    @Query("SELECT * FROM subject_notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun notes(): Flow<List<SubjectNote>>

    @Upsert
    suspend fun upsertNote(item: SubjectNote)

    // Anhänge
    @Query("SELECT * FROM attachments WHERE deleted = 0 ORDER BY createdAt")
    fun attachments(): Flow<List<Attachment>>

    @Upsert
    suspend fun upsertAttachment(item: Attachment)

    @Query("SELECT * FROM attachments WHERE ownerId = :ownerId")
    suspend fun attachmentsIncludingDeleted(ownerId: String): List<Attachment>

    @Query("SELECT * FROM holidays WHERE id = :id")
    suspend fun holiday(id: String): Holiday?

    // Einstellungen
    @Query("SELECT * FROM settings WHERE deleted = 0")
    fun settings(): Flow<List<Setting>>

    @Query("SELECT * FROM settings WHERE id = :key")
    suspend fun setting(key: String): Setting?

    @Upsert
    suspend fun upsertSetting(item: Setting)

    // Lernsessions
    @Query("SELECT * FROM study_sessions WHERE deleted = 0 ORDER BY startedAt DESC")
    fun sessions(): Flow<List<StudySession>>

    @Upsert
    suspend fun upsertSession(item: StudySession)

    @Query("SELECT * FROM study_sessions WHERE deleted = 0 AND finished = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun currentSession(): StudySession?

    @Query("SELECT * FROM study_sessions WHERE deleted = 0 AND finished = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastSession(): StudySession?

    // ---------- Sync ----------

    @Query("SELECT * FROM subjects WHERE dirty = 1")
    suspend fun syncDirtySubject(): List<Subject>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun syncGetSubject(id: String): Subject?

    @Query("UPDATE subjects SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanSubject(id: String, updatedAt: Long)

    @Query("SELECT * FROM subjects WHERE deleted = 0")
    suspend fun syncAllSubject(): List<Subject>

    @Query("SELECT * FROM terms WHERE dirty = 1")
    suspend fun syncDirtyTerm(): List<Term>

    @Query("SELECT * FROM terms WHERE id = :id")
    suspend fun syncGetTerm(id: String): Term?

    @Query("UPDATE terms SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanTerm(id: String, updatedAt: Long)

    @Query("SELECT * FROM terms WHERE deleted = 0")
    suspend fun syncAllTerm(): List<Term>

    @Query("SELECT * FROM grade_categories WHERE dirty = 1")
    suspend fun syncDirtyGradeCategory(): List<GradeCategory>

    @Query("SELECT * FROM grade_categories WHERE id = :id")
    suspend fun syncGetGradeCategory(id: String): GradeCategory?

    @Query("UPDATE grade_categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanGradeCategory(id: String, updatedAt: Long)

    @Query("SELECT * FROM grade_categories WHERE deleted = 0")
    suspend fun syncAllGradeCategory(): List<GradeCategory>

    @Query("SELECT * FROM grades WHERE dirty = 1")
    suspend fun syncDirtyGrade(): List<Grade>

    @Query("SELECT * FROM grades WHERE id = :id")
    suspend fun syncGetGrade(id: String): Grade?

    @Query("UPDATE grades SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanGrade(id: String, updatedAt: Long)

    @Query("SELECT * FROM grades WHERE deleted = 0")
    suspend fun syncAllGrade(): List<Grade>

    @Query("SELECT * FROM subject_terms WHERE dirty = 1")
    suspend fun syncDirtySubjectTerm(): List<SubjectTerm>

    @Query("SELECT * FROM subject_terms WHERE id = :id")
    suspend fun syncGetSubjectTerm(id: String): SubjectTerm?

    @Query("UPDATE subject_terms SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanSubjectTerm(id: String, updatedAt: Long)

    @Query("SELECT * FROM subject_terms WHERE deleted = 0")
    suspend fun syncAllSubjectTerm(): List<SubjectTerm>

    @Query("SELECT * FROM lesson_slots WHERE dirty = 1")
    suspend fun syncDirtyLessonSlot(): List<LessonSlot>

    @Query("SELECT * FROM lesson_slots WHERE id = :id")
    suspend fun syncGetLessonSlot(id: String): LessonSlot?

    @Query("UPDATE lesson_slots SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanLessonSlot(id: String, updatedAt: Long)

    @Query("SELECT * FROM lesson_slots WHERE deleted = 0")
    suspend fun syncAllLessonSlot(): List<LessonSlot>

    @Query("SELECT * FROM period_times WHERE dirty = 1")
    suspend fun syncDirtyPeriodTime(): List<PeriodTime>

    @Query("SELECT * FROM period_times WHERE id = :id")
    suspend fun syncGetPeriodTime(id: String): PeriodTime?

    @Query("UPDATE period_times SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanPeriodTime(id: String, updatedAt: Long)

    @Query("SELECT * FROM period_times WHERE deleted = 0")
    suspend fun syncAllPeriodTime(): List<PeriodTime>

    @Query("SELECT * FROM lesson_overrides WHERE dirty = 1")
    suspend fun syncDirtyLessonOverride(): List<LessonOverride>

    @Query("SELECT * FROM lesson_overrides WHERE id = :id")
    suspend fun syncGetLessonOverride(id: String): LessonOverride?

    @Query("UPDATE lesson_overrides SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanLessonOverride(id: String, updatedAt: Long)

    @Query("SELECT * FROM lesson_overrides WHERE deleted = 0")
    suspend fun syncAllLessonOverride(): List<LessonOverride>

    @Query("SELECT * FROM holidays WHERE dirty = 1")
    suspend fun syncDirtyHoliday(): List<Holiday>

    @Query("SELECT * FROM holidays WHERE id = :id")
    suspend fun syncGetHoliday(id: String): Holiday?

    @Query("UPDATE holidays SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanHoliday(id: String, updatedAt: Long)

    @Query("SELECT * FROM holidays WHERE deleted = 0")
    suspend fun syncAllHoliday(): List<Holiday>

    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun syncDirtyTask(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun syncGetTask(id: String): Task?

    @Query("UPDATE tasks SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanTask(id: String, updatedAt: Long)

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun syncAllTask(): List<Task>

    @Query("SELECT * FROM exams WHERE dirty = 1")
    suspend fun syncDirtyExam(): List<Exam>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun syncGetExam(id: String): Exam?

    @Query("UPDATE exams SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanExam(id: String, updatedAt: Long)

    @Query("SELECT * FROM exams WHERE deleted = 0")
    suspend fun syncAllExam(): List<Exam>

    @Query("SELECT * FROM exam_topics WHERE dirty = 1")
    suspend fun syncDirtyExamTopic(): List<ExamTopic>

    @Query("SELECT * FROM exam_topics WHERE id = :id")
    suspend fun syncGetExamTopic(id: String): ExamTopic?

    @Query("UPDATE exam_topics SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanExamTopic(id: String, updatedAt: Long)

    @Query("SELECT * FROM exam_topics WHERE deleted = 0")
    suspend fun syncAllExamTopic(): List<ExamTopic>

    @Query("SELECT * FROM subject_notes WHERE dirty = 1")
    suspend fun syncDirtySubjectNote(): List<SubjectNote>

    @Query("SELECT * FROM subject_notes WHERE id = :id")
    suspend fun syncGetSubjectNote(id: String): SubjectNote?

    @Query("UPDATE subject_notes SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanSubjectNote(id: String, updatedAt: Long)

    @Query("SELECT * FROM subject_notes WHERE deleted = 0")
    suspend fun syncAllSubjectNote(): List<SubjectNote>

    @Query("SELECT * FROM attachments WHERE dirty = 1")
    suspend fun syncDirtyAttachment(): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun syncGetAttachment(id: String): Attachment?

    @Query("UPDATE attachments SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanAttachment(id: String, updatedAt: Long)

    @Query("SELECT * FROM attachments WHERE deleted = 0")
    suspend fun syncAllAttachment(): List<Attachment>

    @Query("SELECT * FROM settings WHERE dirty = 1")
    suspend fun syncDirtySetting(): List<Setting>

    @Query("SELECT * FROM settings WHERE id = :id")
    suspend fun syncGetSetting(id: String): Setting?

    @Query("UPDATE settings SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanSetting(id: String, updatedAt: Long)

    @Query("SELECT * FROM settings WHERE deleted = 0")
    suspend fun syncAllSetting(): List<Setting>

    @Query("SELECT * FROM study_sessions WHERE dirty = 1")
    suspend fun syncDirtyStudySession(): List<StudySession>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun syncGetStudySession(id: String): StudySession?

    @Query("UPDATE study_sessions SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun syncCleanStudySession(id: String, updatedAt: Long)
}
