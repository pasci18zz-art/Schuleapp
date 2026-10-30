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

    // Einstellungen
    @Query("SELECT * FROM settings WHERE deleted = 0")
    fun settings(): Flow<List<Setting>>

    @Query("SELECT * FROM settings WHERE id = :key")
    suspend fun setting(key: String): Setting?

    @Upsert
    suspend fun upsertSetting(item: Setting)
}
