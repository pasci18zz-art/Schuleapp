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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Benachrichtigung für die Eltern-Geräte (Ansichtsmodus): Neuer Elternbrief oder neue Note.
 * Alle ~15 Minuten wird im Hintergrund synchronisiert und verglichen, was schon gemeldet wurde.
 */
object ParentAlerts {
    private const val WORK = "eltern_check"
    const val CHANNEL = "eltern"
    private const val PREFS = "eltern_alerts"
    private const val RED = 0xFFE53935.toInt()
    private val mutex = Mutex()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ParentAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        // Beim nächsten Einschalten des Ansichtsmodus wieder bei null anfangen.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Vergleicht den aktuellen Stand mit dem schon Gemeldeten und benachrichtigt über Neues. */
    suspend fun check(context: Context) = mutex.withLock {
        val app = context.app
        if (!app.prefs.readOnly.value) return@withLock
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snap = app.repo.alertSnapshot()
        val now = System.currentTimeMillis()
        // Beim ersten Mal nur merken, was schon da ist – sonst käme eine Flut alter Noten.
        var since = sp.getLong("since", 0L)
        if (since == 0L) {
            since = now
            sp.edit().putLong("since", now).apply()
        }
        val seenLetters = sp.getStringSet("letters", emptySet())!!.toMutableSet()
        val seenGrades = sp.getStringSet("grades", emptySet())!!.toMutableSet()

        val newLetters = snap.letters.filter { !it.deleted && it.id !in seenLetters && maxOf(it.createdAt, it.updatedAt) > since }
        val newGrades = snap.grades.filter { !it.deleted && it.id !in seenGrades && it.updatedAt > since }

        // Alles Bestehende als gesehen merken (auch Altes von vor dem Start).
        seenLetters += snap.letters.map { it.id }
        seenGrades += snap.grades.map { it.id }
        sp.edit().putStringSet("letters", seenLetters).putStringSet("grades", seenGrades).apply()

        val subjects = snap.subjects.associateBy { it.id }
        val categories = snap.categories.associateBy { it.id }
        newLetters.filter { !it.done }.forEach { l ->
            val text = if (l.title.isBlank() || l.title == "Elternbrief") "Es gibt einen neuen Elternbrief." else "Neuer Elternbrief: ${l.title}"
            notify(context, l.id.hashCode(), "Neuer Elternbrief", text)
        }
        if (newGrades.size > 3) {
            val lines = newGrades.map { g -> gradeLine(g.points, subjects[g.subjectId]?.name, categories[g.categoryId]?.name, g.title) }
            notify(context, "grades".hashCode(), "${newGrades.size} neue Noten", lines.joinToString("\n"))
        } else {
            newGrades.forEach { g ->
                val subject = subjects[g.subjectId]?.name ?: "Note"
                notify(context, g.id.hashCode(), "Neue Note in $subject", gradeLine(g.points, null, categories[g.categoryId]?.name, g.title))
            }
        }
    }

    private fun gradeLine(points: Int, subject: String?, category: String?, title: String): String =
        listOfNotNull(
            subject,
            "$points ${if (points == 1) "Punkt" else "Punkte"}",
            category?.ifBlank { null },
            title.ifBlank { null },
        ).joinToString(" · ")

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Für Eltern", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Neue Elternbriefe und Noten (Ansichtsmodus)"
                    enableLights(true)
                    lightColor = RED
                },
            )
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_ha)
            .setColor(RED)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class ParentAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ctx.app.prefs.readOnly.value) return Result.success()
        runCatching { ctx.app.sync.syncNow() }
        runCatching { ParentAlerts.check(ctx) }
        return Result.success()
    }
}
