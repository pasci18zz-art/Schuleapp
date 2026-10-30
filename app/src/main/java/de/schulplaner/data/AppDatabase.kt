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
        Setting::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SchulDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schulplaner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
