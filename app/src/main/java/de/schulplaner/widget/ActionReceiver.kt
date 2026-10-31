package de.schulplaner.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.schulplaner.app
import de.schulplaner.study.StudyNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Knöpfe aus Widgets und der Lern-Mitteilung (Start/Pause, Stopp, Art wechseln, Aufgabe abhaken). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val repo = ctx.app.repo
        val pending = goAsync()
        ctx.app.appScope.launch(Dispatchers.IO) {
            try {
                when (intent.action) {
                    ACTION_TOGGLE -> repo.toggleStudyNow()
                    ACTION_STOP -> repo.stopStudyNow()
                    ACTION_KIND -> intent.getStringExtra(EXTRA_KIND)?.let { repo.setStudyKindNow(it) }
                    ACTION_TASK_DONE -> intent.getStringExtra(EXTRA_ID)?.let { repo.completeTaskNow(it) }
                    ACTION_TASK_FILTER -> Widgets.setFilter(ctx, intent.getIntExtra(EXTRA_WIDGET, 0), intent.getStringExtra(EXTRA_KIND))
                    ACTION_REFRESH -> Unit
                }
                val snap = repo.snapshot()
                StudyNotifier.show(ctx, if (ctx.app.prefs.readOnly.value) null else snap.current, snap.schedule.subjects)
                Widgets.render(ctx, snap)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "de.schulplaner.action.STUDY_TOGGLE"
        const val ACTION_STOP = "de.schulplaner.action.STUDY_STOP"
        const val ACTION_KIND = "de.schulplaner.action.STUDY_KIND"
        const val ACTION_TASK_DONE = "de.schulplaner.action.TASK_DONE"
        const val ACTION_REFRESH = "de.schulplaner.action.REFRESH"
        const val ACTION_TASK_FILTER = "de.schulplaner.action.TASK_FILTER"
        const val EXTRA_WIDGET = "widget"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"

        fun intent(ctx: Context, action: String) = Intent(ctx, ActionReceiver::class.java).setAction(action)

        fun pending(ctx: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code, intent(ctx, action).apply(extras),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
