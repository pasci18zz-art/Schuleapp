package de.schulplaner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Subject::class, Term::class, GradeCategory::class, Grade::class, SubjectTerm::class,
        LessonSlot::class, PeriodTime::class, LessonOverride::class, Holiday::class,
        Task::class, Exam::class, ExamTopic::class, SubjectNote::class, Attachment::class,
        Setting::class, StudySession::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SchulDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schulplaner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** Version 2: Notizbuch-Link pro Fach, Art der freien Tage. Bestehende Daten bleiben erhalten. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN notesLink TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE holidays ADD COLUMN kind TEXT NOT NULL DEFAULT 'FREI'")
            }
        }

        /** Version 3: Symbol pro Fach. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 4: Lernsessions (Stoppuhr). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `study_sessions` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `subjectId` TEXT, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `elapsedMs` INTEGER NOT NULL, `runningSince` INTEGER, " +
                        "`finished` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
