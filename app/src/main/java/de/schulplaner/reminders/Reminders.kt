package de.schulplaner.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.app
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Erinnerung am Vorabend: offene HA und Klausuren für morgen. */
object Reminders {
    private const val WORK = "vorabend_erinnerung"
    const val CHANNEL = "erinnerungen"

    suspend fun scheduleFromPrefs(context: Context, replace: Boolean) {
        val (on, minute) = context.app.prefs.reminderSnapshot()
        if (on) schedule(context, minute, replace) else cancel(context)
    }

    fun schedule(context: Context, minuteOfDay: Int, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Hausaufgaben und Klausuren für morgen"
                },
            )
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = ctx.app.repo
        // Kurz warten, bis die Datenbank geladen ist.
        withTimeoutOrNull(5000) { repo.scheduleData.first { it.periods.isNotEmpty() } }
        withTimeoutOrNull(2000) { repo.tasks.first { true } }

        val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
        val subjects = repo.subjects.value.associateBy { it.id }
        val tasks = repo.tasks.value.filter { !it.done && it.dueDate == tomorrow }
        val exams = repo.exams.value.filter { it.date == tomorrow }

        if (tasks.isNotEmpty() || exams.isNotEmpty()) {
            val lines = exams.map { "Klausur: ${subjects[it.subjectId]?.name ?: ""} ${it.title}".trim() } +
                tasks.map { t -> listOfNotNull(subjects[t.subjectId]?.name, t.title).joinToString(": ") }
            val title = when {
                exams.isNotEmpty() && tasks.isEmpty() -> "Morgen: Klausur"
                tasks.size == 1 -> "Noch 1 Aufgabe für morgen"
                else -> "Noch ${tasks.size} Aufgaben für morgen"
            }
            notify(ctx, title, lines)
        }
        Reminders.scheduleFromPrefs(ctx, replace = true)
        return Result.success()
    }

    private fun notify(ctx: Context, title: String, lines: List<String>) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        Reminders.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_ha)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }
}
