package de.schulplaner.study

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.data.StudyKind
import de.schulplaner.data.StudySession
import de.schulplaner.data.Subject
import de.schulplaner.widget.ActionReceiver

/**
 * Mitteilung für die laufende Lernsession mit mitlaufender Uhr, Pause und Stopp.
 * Sie erfüllt die Regeln für Android-16-"Live Updates", damit Samsung sie in der Now Bar zeigen kann.
 */
object StudyNotifier {
    private const val CHANNEL = "study"
    private const val ID = 4711

    fun show(ctx: Context, session: StudySession?, subjects: Map<String, Subject>) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (session == null || session.finished) {
            nm.cancel(ID)
            return
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Lernzeit", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Laufende Lernsession mit Stoppuhr"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        val now = System.currentTimeMillis()
        val total = session.totalMs(now)
        val running = session.isRunning
        val subject = session.subjectId?.let { subjects[it] }
        val title = StudyKind.label(session.kind) + (subject?.let { " · ${it.name}" } ?: "")

        val open = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "study")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = Notification.Action.Builder(
            Icon.createWithResource(ctx, if (running) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            if (running) "Pause" else "Weiter",
            ActionReceiver.pending(ctx, ActionReceiver.ACTION_TOGGLE, 11),
        ).build()
        val stop = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_w_stop),
            "Beenden",
            ActionReceiver.pending(ctx, ActionReceiver.ACTION_STOP, 12),
        ).build()

        val builder = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_study)
            .setContentTitle(title)
            .setContentText(if (running) "Läuft" else "Pausiert bei ${format(total)}")
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF4F5BD5.toInt())
            .setContentIntent(open)
            .addAction(toggle)
            .addAction(stop)
            .setShowWhen(running)
            .setWhen(now - total)
            .setUsesChronometer(running)
        // Live Update anfragen (Android 16; ältere Versionen ignorieren die Angaben).
        builder.addExtras(
            Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                if (!running) putString("android.shortCriticalText", "Pause")
            },
        )
        nm.notify(ID, builder.build())
    }

    /** "1:05 h" oder "23 min". */
    fun format(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60}:${(min % 60).toString().padStart(2, '0')} h" else "$min min"
    }
}
