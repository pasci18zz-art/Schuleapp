package de.schulplaner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Subject::class, Term::class, GradeCategory::class, Grade::class, SubjectTerm::class,
        LessonSlot::class, PeriodTime::class, LessonOverride::class, Holiday::class,
        Task::class, Exam::class, ExamTopic::class, SubjectNote::class, Attachment::class,
        Setting::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SchulDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schulplaner.db").build()
    }
}
