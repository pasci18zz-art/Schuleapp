package de.schulplaner.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import de.schulplaner.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Heute-Widget: aktuelle/nächste Stunde und Aufgaben. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, goAsync())
}

/** Lern-Widget: Stoppuhr mit Art-Auswahl. */
class StudyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, goAsync())
}

private fun refresh(context: Context, pending: android.content.BroadcastReceiver.PendingResult) {
    val ctx = context.applicationContext
    ctx.app.appScope.launch(Dispatchers.IO) {
        try {
            Widgets.render(ctx, ctx.app.repo.snapshot())
        } finally {
            pending.finish()
        }
    }
}
