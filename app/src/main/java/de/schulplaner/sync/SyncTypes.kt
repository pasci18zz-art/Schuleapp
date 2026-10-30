package de.schulplaner.sync

import de.schulplaner.data.*
import kotlinx.serialization.KSerializer

/** Beschreibt, wie eine Tabelle synchronisiert wird. */
class SyncType<T : Any>(
    val name: String,
    val serializer: KSerializer<T>,
    val dirty: suspend () -> List<T>,
    val get: suspend (String) -> T?,
    val upsert: suspend (T) -> Unit,
    val clean: suspend (String, Long) -> Unit,
    val id: (T) -> String,
    val updatedAt: (T) -> Long,
    val deleted: (T) -> Boolean,
    val asClean: (T) -> T,
)

/** Alle synchronisierten Tabellen (generiert nach demselben Muster). */
fun syncTypes(dao: SchulDao): List<SyncType<*>> = listOf(
    SyncType(
        "subject", Subject.serializer(),
        { dao.syncDirtySubject() }, { dao.syncGetSubject(it) }, { dao.upsertSubject(it) }, { i, u -> dao.syncCleanSubject(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "term", Term.serializer(),
        { dao.syncDirtyTerm() }, { dao.syncGetTerm(it) }, { dao.upsertTerm(it) }, { i, u -> dao.syncCleanTerm(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "category", GradeCategory.serializer(),
        { dao.syncDirtyGradeCategory() }, { dao.syncGetGradeCategory(it) }, { dao.upsertCategory(it) }, { i, u -> dao.syncCleanGradeCategory(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "grade", Grade.serializer(),
        { dao.syncDirtyGrade() }, { dao.syncGetGrade(it) }, { dao.upsertGrade(it) }, { i, u -> dao.syncCleanGrade(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "subject_term", SubjectTerm.serializer(),
        { dao.syncDirtySubjectTerm() }, { dao.syncGetSubjectTerm(it) }, { dao.upsertSubjectTerm(it) }, { i, u -> dao.syncCleanSubjectTerm(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "slot", LessonSlot.serializer(),
        { dao.syncDirtyLessonSlot() }, { dao.syncGetLessonSlot(it) }, { dao.upsertSlot(it) }, { i, u -> dao.syncCleanLessonSlot(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "period", PeriodTime.serializer(),
        { dao.syncDirtyPeriodTime() }, { dao.syncGetPeriodTime(it) }, { dao.upsertPeriod(it) }, { i, u -> dao.syncCleanPeriodTime(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "override", LessonOverride.serializer(),
        { dao.syncDirtyLessonOverride() }, { dao.syncGetLessonOverride(it) }, { dao.upsertOverride(it) }, { i, u -> dao.syncCleanLessonOverride(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "holiday", Holiday.serializer(),
        { dao.syncDirtyHoliday() }, { dao.syncGetHoliday(it) }, { dao.upsertHoliday(it) }, { i, u -> dao.syncCleanHoliday(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "task", Task.serializer(),
        { dao.syncDirtyTask() }, { dao.syncGetTask(it) }, { dao.upsertTask(it) }, { i, u -> dao.syncCleanTask(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "exam", Exam.serializer(),
        { dao.syncDirtyExam() }, { dao.syncGetExam(it) }, { dao.upsertExam(it) }, { i, u -> dao.syncCleanExam(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "topic", ExamTopic.serializer(),
        { dao.syncDirtyExamTopic() }, { dao.syncGetExamTopic(it) }, { dao.upsertTopic(it) }, { i, u -> dao.syncCleanExamTopic(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "note", SubjectNote.serializer(),
        { dao.syncDirtySubjectNote() }, { dao.syncGetSubjectNote(it) }, { dao.upsertNote(it) }, { i, u -> dao.syncCleanSubjectNote(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "attachment", Attachment.serializer(),
        { dao.syncDirtyAttachment() }, { dao.syncGetAttachment(it) }, { dao.upsertAttachment(it) }, { i, u -> dao.syncCleanAttachment(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
    SyncType(
        "setting", Setting.serializer(),
        { dao.syncDirtySetting() }, { dao.syncGetSetting(it) }, { dao.upsertSetting(it) }, { i, u -> dao.syncCleanSetting(i, u) },
        { it.id }, { it.updatedAt }, { it.deleted }, { it.copy(dirty = false) },
    ),
)
