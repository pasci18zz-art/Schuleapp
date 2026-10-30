package de.schulplaner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.data.Repository
import de.schulplaner.data.SettingKeys
import de.schulplaner.data.StudyKind
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.label
import de.schulplaner.quick.QuickAddActivity
import de.schulplaner.study.StudyNotifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Baut die Widgets (RemoteViews) aus einer Momentaufnahme der Daten. */
object Widgets {
    private const val ACCENT = 0xFF4F5BD5.toInt()

    fun render(ctx: Context, snap: Repository.Snapshot) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val todayIds = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
        val studyIds = mgr.getAppWidgetIds(ComponentName(ctx, StudyWidget::class.java))
        if (todayIds.isNotEmpty()) {
            val views = RemoteViews(
                mapOf(
                    SizeF(110f, 100f) to today(ctx, snap, R.layout.widget_today_small, withTasks = false),
                    SizeF(240f, 100f) to today(ctx, snap, R.layout.widget_today_wide, withTasks = true),
                ),
            )
            todayIds.forEach { mgr.updateAppWidget(it, views) }
            scheduleNext(ctx, snap)
        }
        if (studyIds.isNotEmpty()) {
            val views = RemoteViews(
                mapOf(
                    SizeF(100f, 40f) to study(ctx, snap, R.layout.widget_study_compact),
                    SizeF(100f, 100f) to study(ctx, snap, R.layout.widget_study_square),
                    SizeF(190f, 100f) to study(ctx, snap, R.layout.widget_study_full),
                ),
            )
            studyIds.forEach { mgr.updateAppWidget(it, views) }
        }
    }

    // ---------------- Heute ----------------

    private fun openApp(ctx: Context, code: Int, route: String? = null): PendingIntent =
        PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).apply {
                if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun today(ctx: Context, snap: Repository.Snapshot, layout: Int, withTasks: Boolean): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val now = LocalDateTime.now()
        val data = snap.schedule
        val today = now.toLocalDate()
        val current = Schedule.currentLesson(now, data)
        val upcoming = Schedule.lessonsOn(today, data).filter { it.startDateTime?.isAfter(now) == true }.sortedBy { it.firstPeriod }

        v.setOnClickPendingIntent(R.id.now_card, openApp(ctx, 20))
        v.setOnClickPendingIntent(R.id.next_card, openApp(ctx, 21))

        when {
            current != null -> {
                main(v, current, "JETZT", current.endDateTime, "noch %s")
                second(v, upcoming.firstOrNull(), "DANACH", countdown = true, endOfDay = current)
            }
            upcoming.isNotEmpty() -> {
                main(v, upcoming[0], "ALS NÄCHSTES", upcoming[0].startDateTime, "in %s")
                second(v, upcoming.getOrNull(1), "DANACH", countdown = false, endOfDay = upcoming[0])
            }
            else -> {
                val holiday = Schedule.holidayOn(today, data)
                v.setTextViewText(R.id.now_label, if (holiday != null) "FREI" else "HEUTE")
                v.setTextViewText(R.id.now_subject, holiday?.name ?: if (Schedule.lessonsOn(today, data).isEmpty()) "Kein Unterricht" else "Geschafft!")
                v.setViewVisibility(R.id.now_timer, View.GONE)
                v.setTextViewText(R.id.now_info, "")
                tint(v, R.id.now_bg, ACCENT, 150)
                val nextDay = Schedule.nextSchoolDay(today.plusDays(1), data)
                val first = nextDay?.let { d -> Schedule.lessonsOn(d, data).firstOrNull { !it.cancelled } }
                if (first != null) {
                    val day = if (first.date == today.plusDays(1)) "MORGEN" else Fmt.dayShort(first.date).uppercase()
                    v.setTextViewText(R.id.next_label, "$day · ${first.periodLabel} STD · ${first.start?.let { Fmt.time(it) } ?: ""}")
                    v.setTextViewText(R.id.next_subject, first.subject?.name ?: "?")
                    tint(v, R.id.next_bar, color(first), 255)
                } else {
                    v.setTextViewText(R.id.next_label, "DANACH")
                    v.setTextViewText(R.id.next_subject, "Nichts geplant")
                    tint(v, R.id.next_bar, 0x66FFFFFF, 255)
                }
                v.setViewVisibility(R.id.next_timer, View.GONE)
            }
        }

        if (withTasks) tasks(ctx, v, snap, today)
        return v
    }

    private fun color(l: Lesson?): Int = (l?.subject?.color ?: 0xFF7C8A9E).toInt()

    /** Farbfilter auf eine weiße Fläche (Fachfarbe), [alpha] 0–255. */
    private fun tint(v: RemoteViews, id: Int, color: Int, alpha: Int) {
        v.setInt(id, "setColorFilter", color or (0xFF shl 24))
        v.setInt(id, "setImageAlpha", alpha)
    }

    /** Live-Countdown bis [target] (läuft im Widget von selbst weiter). */
    private fun countdown(v: RemoteViews, id: Int, target: LocalDateTime?, format: String) {
        if (target == null) {
            v.setViewVisibility(id, View.GONE)
            return
        }
        val ms = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        v.setViewVisibility(id, View.VISIBLE)
        v.setChronometer(id, SystemClock.elapsedRealtime() + ms, format, true)
        v.setChronometerCountDown(id, true)
    }

    private fun main(v: RemoteViews, l: Lesson, label: String, target: LocalDateTime?, format: String) {
        val extra = if (l.cancelled) " · ENTFALL" else ""
        v.setTextViewText(R.id.now_label, "$label · ${l.periodLabel} STD$extra")
        v.setTextViewText(R.id.now_subject, l.subject?.name ?: "?")
        v.setTextViewText(R.id.now_info, l.room.takeIf { it.isNotBlank() }?.let { "R $it" } ?: "")
        tint(v, R.id.now_bg, color(l), 165)
        countdown(v, R.id.now_timer, target, format)
    }

    private fun second(v: RemoteViews, l: Lesson?, label: String, countdown: Boolean, endOfDay: Lesson) {
        if (l == null) {
            v.setTextViewText(R.id.next_label, label)
            v.setTextViewText(R.id.next_subject, "Schluss um ${endOfDay.end?.let { Fmt.time(it) } ?: "–"}")
            tint(v, R.id.next_bar, 0x66FFFFFF, 255)
            v.setViewVisibility(R.id.next_timer, View.GONE)
            return
        }
        val extra = if (l.cancelled) " · ENTFALL" else ""
        v.setTextViewText(R.id.next_label, "$label · ${l.periodLabel} STD · ${l.start?.let { Fmt.time(it) } ?: ""}$extra")
        v.setTextViewText(R.id.next_subject, l.subject?.name ?: "?")
        tint(v, R.id.next_bar, color(l), 255)
        if (countdown) countdown(v, R.id.next_timer, l.startDateTime, "in %s") else v.setViewVisibility(R.id.next_timer, View.GONE)
    }

    private fun tasks(ctx: Context, v: RemoteViews, snap: Repository.Snapshot, today: LocalDate) {
        val limit = today.plusDays(1).toEpochDay()
        val open = snap.tasks
            .filter { !it.done && (it.dueDate == null || it.dueDate <= limit) }
            .sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
            .take(20)
        val allOpen = snap.tasks.count { !it.done }
        v.setTextViewText(R.id.tasks_count, "$allOpen")
        // Je mehr offen, desto wärmer die Zahl (wie in der App).
        v.setTextColor(R.id.tasks_count, urgency(allOpen))
        v.setOnClickPendingIntent(R.id.tasks_title, openApp(ctx, 22, "tasks"))
        v.setOnClickPendingIntent(
            R.id.tasks_add,
            PendingIntent.getActivity(
                ctx, 23,
                Intent(ctx, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
        open.forEach { t ->
            val item = RemoteViews(ctx.packageName, R.layout.widget_task_item)
            val subject = t.subjectId?.let { snap.schedule.subjects[it] }
            val due = t.dueDate?.let { LocalDate.ofEpochDay(it) }
            item.setTextViewText(R.id.item_title, t.title)
            val dueText = due?.let {
                when (Fmt.daysUntil(today, it)) {
                    0L -> "heute"
                    1L -> "morgen"
                    else -> if (it.isBefore(today)) "überfällig" else Fmt.dayDate(it)
                }
            }
            item.setTextViewText(R.id.item_meta, listOfNotNull(subject?.label ?: "Ohne Fach", dueText).joinToString(" · "))
            if (due != null && due.isBefore(today)) item.setTextColor(R.id.item_meta, 0xFFFF9E7A.toInt())
            val c = (subject?.color ?: 0xFF9AA1AF).toInt()
            item.setInt(R.id.item_dot, "setColorFilter", c)
            item.setInt(R.id.item_check, "setColorFilter", c)
            item.setOnClickFillInIntent(R.id.item_check, Intent().putExtra(ActionReceiver.EXTRA_ID, t.id))
            items.addItem(t.id.hashCode().toLong(), item)
        }
        v.setRemoteAdapter(R.id.tasks_list, items.build())
        v.setEmptyView(R.id.tasks_list, R.id.tasks_empty)
        v.setPendingIntentTemplate(
            R.id.tasks_list,
            PendingIntent.getBroadcast(
                ctx, 24, ActionReceiver.intent(ctx, ActionReceiver.ACTION_TASK_DONE),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    private fun urgency(open: Int): Int = when {
        open == 0 -> 0xFFFFFFFF.toInt()
        open <= 2 -> 0xFF7FD6A0.toInt()
        open <= 4 -> 0xFFFFD166.toInt()
        open <= 6 -> 0xFFFFA05C.toInt()
        else -> 0xFFFF6B6B.toInt()
    }

    /** Aktualisiert das Heute-Widget beim nächsten Stundenwechsel (Beginn/Ende) bzw. um Mitternacht. */
    private fun scheduleNext(ctx: Context, snap: Repository.Snapshot) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val times = Schedule.lessonsOn(today, snap.schedule).flatMap { listOfNotNull(it.startDateTime, it.endDateTime) }
        val next = (times + today.plusDays(1).atStartOfDay().plusMinutes(1)).filter { it.isAfter(now) }.minOrNull() ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = ActionReceiver.pending(ctx, ActionReceiver.ACTION_REFRESH, 30)
        // Nicht weckend: Ist der Bildschirm aus, kommt die Aktualisierung beim Einschalten.
        am.setWindow(AlarmManager.RTC, at, 20_000, pi)
    }

    // ---------------- Lernen ----------------

    private val kindIds = listOf(
        Triple(StudyKind.HOMEWORK, R.id.kind_ha, R.drawable.ic_w_house),
        Triple(StudyKind.TALK, R.id.kind_ref, R.drawable.ic_w_presentation),
        Triple(StudyKind.LEARN, R.id.kind_learn, R.drawable.ic_w_pen),
    )

    private fun study(ctx: Context, snap: Repository.Snapshot, layout: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val cur = snap.current
        val kind = cur?.kind ?: snap.settings[SettingKeys.STUDY_KIND]?.takeIf { it in StudyKind.all } ?: StudyKind.LEARN
        val now = System.currentTimeMillis()
        val total = cur?.totalMs(now) ?: 0L
        val running = cur?.isRunning == true

        v.setChronometer(R.id.study_timer, SystemClock.elapsedRealtime() - total, null, running)
        v.setImageViewResource(R.id.study_toggle, if (running) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        v.setOnClickPendingIntent(R.id.study_toggle, ActionReceiver.pending(ctx, ActionReceiver.ACTION_TOGGLE, 40))
        v.setViewVisibility(R.id.study_stop, if (cur != null) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.study_stop, ActionReceiver.pending(ctx, ActionReceiver.ACTION_STOP, 41))
        v.setOnClickPendingIntent(R.id.study_timer, openApp(ctx, 42, "study"))

        when (layout) {
            R.layout.widget_study_compact -> {
                // Antippen wechselt zur nächsten Art.
                val i = kindIds.indexOfFirst { it.first == kind }.coerceAtLeast(0)
                v.setImageViewResource(R.id.study_kind, kindIds[i].third)
                val next = kindIds[(i + 1) % kindIds.size].first
                v.setOnClickPendingIntent(
                    R.id.study_kind,
                    ActionReceiver.pending(ctx, ActionReceiver.ACTION_KIND, 43) { putExtra(ActionReceiver.EXTRA_KIND, next) },
                )
            }
            else -> {
                kindIds.forEachIndexed { i, (k, id, _) ->
                    val selected = k == kind
                    // Gewählte Art: helle Glas-Markierung, die anderen gedimmt.
                    v.setInt(bgId(id), "setImageAlpha", if (selected) 60 else 0)
                    v.setInt(iconId(id), "setImageAlpha", if (selected) 255 else 140)
                    v.setOnClickPendingIntent(
                        id,
                        ActionReceiver.pending(ctx, ActionReceiver.ACTION_KIND, 44 + i) { putExtra(ActionReceiver.EXTRA_KIND, k) },
                    )
                }
            }
        }
        if (layout == R.layout.widget_study_full) {
            val subject = (cur?.subjectId ?: snap.settings[SettingKeys.STUDY_SUBJECT])?.let { snap.schedule.subjects[it] }
            val week = weekTotal(snap, now)
            v.setTextViewText(
                R.id.study_info,
                listOfNotNull(subject?.name, "Diese Woche: ${StudyNotifier.format(week)}").joinToString(" · "),
            )
            v.setOnClickPendingIntent(R.id.study_info, openApp(ctx, 47, "study"))
        }
        return v
    }

    private fun bgId(id: Int) = when (id) {
        R.id.kind_ha -> R.id.kind_ha_bg
        R.id.kind_ref -> R.id.kind_ref_bg
        else -> R.id.kind_learn_bg
    }

    private fun iconId(id: Int) = when (id) {
        R.id.kind_ha -> R.id.kind_ha_icon
        R.id.kind_ref -> R.id.kind_ref_icon
        else -> R.id.kind_learn_icon
    }

    /** Lernzeit der aktuellen Woche (Mo–So) inklusive laufender Session. */
    fun weekTotal(snap: Repository.Snapshot, now: Long): Long {
        val zone = ZoneId.systemDefault()
        val monday = Schedule.mondayOf(LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli()
        return snap.sessions.filter { it.startedAt >= monday }.sumOf { it.totalMs(now) }
    }
}
