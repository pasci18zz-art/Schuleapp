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
import androidx.compose.ui.graphics.toArgb
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.data.HolidayKind
import de.schulplaner.data.Repository
import de.schulplaner.data.SettingKeys
import de.schulplaner.data.StudyKind
import de.schulplaner.data.Subject
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import de.schulplaner.domain.label
import de.schulplaner.quick.QuickAddActivity
import de.schulplaner.study.StudyNotifier
import de.schulplaner.ui.components.HolidayLook
import de.schulplaner.ui.components.SubjectIcons
import de.schulplaner.ui.components.examUrgency
import de.schulplaner.ui.components.urgencyColor
import de.schulplaner.ui.tasks.kindOf
import de.schulplaner.ui.tasks.stepPercent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Baut alle Widgets (RemoteViews) aus einer Momentaufnahme der Daten.
 * Look wie die Samsung-Widgets (graues Glas), dazu Elemente aus der App: Umschalter, Fachfarben, Symbole.
 */
object Widgets {
    private const val RED = 0xFFFF6B6B.toInt()
    private const val RED_TEXT = 0xFFFF9A8F.toInt()
    private const val RING = 0x99FFFFFF.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0xB3FFFFFF.toInt()
    private const val ACCENT = 0xFF6F7BFF.toInt()

    fun render(ctx: Context, snap: Repository.Snapshot) {
        val mgr = AppWidgetManager.getInstance(ctx)
        fun ids(cls: Class<*>) = mgr.getAppWidgetIds(ComponentName(ctx, cls))

        val todayIds = ids(TodayWidget::class.java)
        todayIds.forEach { id ->
            mgr.updateAppWidget(
                id,
                RemoteViews(
                    mapOf(
                        SizeF(110f, 100f) to today(ctx, snap, R.layout.widget_today_small, withTasks = false),
                        SizeF(240f, 100f) to today(ctx, snap, R.layout.widget_today_wide, withTasks = true),
                    ),
                ),
            )
        }
        ids(TasksWidget::class.java).forEach { id -> mgr.updateAppWidget(id, tasksWidget(ctx, snap, id)) }
        ids(StudyWidget::class.java).forEach { id ->
            mgr.updateAppWidget(
                id,
                RemoteViews(
                    mapOf(
                        SizeF(100f, 40f) to study(ctx, snap, R.layout.widget_study_compact),
                        SizeF(100f, 100f) to study(ctx, snap, R.layout.widget_study_square),
                        SizeF(190f, 100f) to study(ctx, snap, R.layout.widget_study_full),
                    ),
                ),
            )
        }
        ids(ExamWidget::class.java).forEach { id -> mgr.updateAppWidget(id, exam(ctx, snap)) }
        ids(HolidayWidget::class.java).forEach { id ->
            mgr.updateAppWidget(
                id,
                RemoteViews(
                    mapOf(
                        SizeF(110f, 50f) to holiday(ctx, snap, R.layout.widget_holiday_wide),
                        SizeF(110f, 110f) to holiday(ctx, snap, R.layout.widget_holiday),
                    ),
                ),
            )
        }
        ids(QuickWidget::class.java).forEach { id -> mgr.updateAppWidget(id, quick(ctx)) }
        if (todayIds.isNotEmpty()) scheduleNext(ctx, snap)
    }

    // ---------------- Gemeinsames ----------------

    private fun drawable(ctx: Context, name: String): Int = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

    private fun subjectIcon(ctx: Context, s: Subject?): Int = s?.let { SubjectIcons.keyFor(it) }?.let { drawable(ctx, "ic_s_$it") } ?: 0

    private fun subjectColor(s: Subject?): Int = (s?.color ?: 0xFF9AA1AF).toInt()

    private fun openApp(ctx: Context, code: Int, route: String? = null): PendingIntent =
        PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).apply {
                if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun quickAdd(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 23,
            Intent(ctx, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Farbfilter auf ein weißes Bild (z. B. Fachfarbe), [alpha] 0–255. */
    private fun tint(v: RemoteViews, id: Int, color: Int, alpha: Int = 255) {
        v.setInt(id, "setColorFilter", color or (0xFF shl 24))
        v.setInt(id, "setImageAlpha", alpha)
    }

    private fun urgency(open: Int): Int = when {
        open == 0 -> WHITE
        open <= 2 -> 0xFF7FD6A0.toInt()
        open <= 4 -> 0xFFFFD166.toInt()
        open <= 6 -> 0xFFFFA05C.toInt()
        else -> 0xFFFF6B6B.toInt()
    }

    // ---------------- Aufgabenlisten ----------------

    private val prefsName = "widgets"

    /** Filter eines Aufgaben-Widgets (Aufgabenart oder null = alle). */
    private fun filterFor(ctx: Context, widgetId: Int): String? =
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("filter_$widgetId", null)?.ifBlank { null }

    fun setFilter(ctx: Context, widgetId: Int, kind: String?) {
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString("filter_$widgetId", kind ?: "").apply()
    }

    /**
     * Alle offenen Aufgaben: zuerst überfällig/heute/morgen (rot), dann ein Strich, dann der Rest nach Datum.
     * Die Liste ist im Widget scrollbar.
     */
    private fun fillTasks(ctx: Context, v: RemoteViews, snap: Repository.Snapshot, today: LocalDate, filter: String?, header: Boolean) {
        val open = snap.tasks.filter { !it.done && (filter == null || it.type == filter) }
        val limit = today.plusDays(1).toEpochDay()
        val urgent = open.filter { it.dueDate != null && it.dueDate <= limit }
            .sortedWith(compareBy<Task> { it.dueDate }.thenBy { it.createdAt })
        val rest = open.filter { it.dueDate == null || it.dueDate > limit }
            .sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })

        if (header) {
            v.setTextViewText(R.id.tasks_count, "${open.size}")
            v.setTextColor(R.id.tasks_count, urgency(open.size))
            v.setOnClickPendingIntent(R.id.tasks_title, openApp(ctx, 22, "tasks"))
        }
        v.setOnClickPendingIntent(R.id.tasks_add, quickAdd(ctx))

        val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
        urgent.forEachIndexed { i, t -> items.addItem(t.id.hashCode().toLong(), row(ctx, snap, t, today, true, i == urgent.lastIndex)) }
        if (urgent.isNotEmpty() && rest.isNotEmpty()) items.addItem(-1L, RemoteViews(ctx.packageName, R.layout.widget_task_sep))
        rest.forEachIndexed { i, t -> items.addItem(t.id.hashCode().toLong(), row(ctx, snap, t, today, false, i == rest.lastIndex)) }
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

    private fun row(ctx: Context, snap: Repository.Snapshot, t: Task, today: LocalDate, urgent: Boolean, last: Boolean): RemoteViews {
        val r = RemoteViews(ctx.packageName, R.layout.widget_task_row)
        val subject = t.subjectId?.let { snap.schedule.subjects[it] }
        val due = t.dueDate?.let { LocalDate.ofEpochDay(it) }
        val dueText = due?.let {
            when {
                it.isBefore(today) -> "überfällig"
                it == today -> "heute"
                it == today.plusDays(1) -> "morgen"
                else -> "${Fmt.dayShort(it)} ${Fmt.dateShort(it)}"
            }
        }
        val pct = stepPercent(t.id, snap.topics)
        r.setTextViewText(R.id.row_title, t.title)
        r.setTextViewText(R.id.row_meta, listOfNotNull(subject?.label ?: "Ohne Fach", dueText).joinToString(" · "))
        // Unterpunkte: Balken mit Fortschritt, bei 100 % nur ein grünes "100 %".
        when {
            pct == null -> Unit
            pct >= 100 -> r.setViewVisibility(R.id.row_pct, View.VISIBLE)
            else -> {
                r.setViewVisibility(R.id.row_pct, View.VISIBLE)
                r.setTextViewText(R.id.row_pct, "$pct %")
                r.setTextColor(R.id.row_pct, DIM)
                r.setViewVisibility(R.id.row_progress, View.VISIBLE)
                r.setProgressBar(R.id.row_progress, 100, pct, false)
            }
        }
        if (urgent) r.setTextColor(R.id.row_meta, RED_TEXT)
        r.setInt(R.id.row_check, "setColorFilter", if (urgent) RED else RING)
        r.setInt(R.id.row_dot, "setColorFilter", subjectColor(subject))
        val kind = kindOf(t.type, snap.kinds)
        r.setImageViewResource(R.id.row_kind, drawable(ctx, "ic_k_${kind.icon}").takeIf { it != 0 } ?: R.drawable.ic_k_dots)
        r.setViewVisibility(R.id.row_line, if (last) View.GONE else View.VISIBLE)
        r.setOnClickFillInIntent(R.id.row_check, Intent().putExtra(ActionReceiver.EXTRA_ID, t.id))
        return r
    }

    private val segIds = listOf(R.id.seg_1, R.id.seg_2, R.id.seg_3, R.id.seg_4, R.id.seg_5)
    private val segIconIds = listOf(R.id.seg_1_icon, R.id.seg_2_icon, R.id.seg_3_icon, R.id.seg_4_icon, R.id.seg_5_icon)

    private fun tasksWidget(ctx: Context, snap: Repository.Snapshot, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_tasks)
        val kinds = snap.kinds.take(5)
        val filter = filterFor(ctx, widgetId)?.takeIf { f -> kinds.any { it.id == f } }
        fun filterIntent(code: Int, kind: String?) = ActionReceiver.pending(ctx, ActionReceiver.ACTION_TASK_FILTER, 1000 + widgetId * 10 + code) {
            putExtra(ActionReceiver.EXTRA_WIDGET, widgetId)
            putExtra(ActionReceiver.EXTRA_KIND, kind ?: "")
        }
        // Umschalter wie in der App: "Alle" plus ein Symbol je Aufgabenart.
        v.setInt(R.id.seg_0, "setBackgroundResource", if (filter == null) R.drawable.widget_seg_on else 0)
        v.setTextColor(R.id.seg_0_label, if (filter == null) WHITE else DIM)
        v.setOnClickPendingIntent(R.id.seg_0, filterIntent(0, null))
        segIds.forEachIndexed { i, id ->
            val k = kinds.getOrNull(i)
            if (k == null) {
                v.setViewVisibility(id, View.GONE)
            } else {
                val on = k.id == filter
                v.setViewVisibility(id, View.VISIBLE)
                v.setInt(id, "setBackgroundResource", if (on) R.drawable.widget_seg_on else 0)
                v.setImageViewResource(segIconIds[i], drawable(ctx, "ic_k_${k.icon}").takeIf { it != 0 } ?: R.drawable.ic_k_dots)
                v.setInt(segIconIds[i], "setImageAlpha", if (on) 255 else 170)
                v.setOnClickPendingIntent(id, filterIntent(i + 1, k.id))
            }
        }
        val openCount = snap.tasks.count { !it.done }
        v.setTextViewText(R.id.seg_0_label, if (openCount > 0) "Alle $openCount" else "Alle")
        fillTasks(ctx, v, snap, LocalDate.now(), filter, header = false)
        return v
    }

    private val allSegIds = listOf(R.id.seg_0) + segIds

    /**
     * Filter wechseln mit Gleit-Effekt: Widgets können nicht selbst animieren, deshalb wandert die
     * blaue Markierung in ein paar schnellen Zwischenbildern (wie die Leiste in der App) zum neuen Eintrag.
     */
    suspend fun switchTaskFilter(ctx: Context, snap: Repository.Snapshot, widgetId: Int, kind: String?) {
        val kinds = snap.kinds.take(5)
        fun indexOf(k: String?) = if (k == null) 0 else kinds.indexOfFirst { it.id == k }.let { if (it < 0) 0 else it + 1 }
        val from = indexOf(filterFor(ctx, widgetId)?.takeIf { f -> kinds.any { it.id == f } })
        val to = indexOf(kind?.ifBlank { null })
        setFilter(ctx, widgetId, kind)
        if (from == to) return
        val mgr = AppWidgetManager.getInstance(ctx)
        val frames = 7
        for (f in 1..frames) {
            val t = f / frames.toFloat()
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            val pos = from + (to - from) * eased
            val rv = RemoteViews(ctx.packageName, R.layout.widget_tasks)
            allSegIds.forEachIndexed { i, id ->
                val a = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                val bg = when {
                    a < 0.17f -> 0
                    a < 0.5f -> R.drawable.widget_seg_on_33
                    a < 0.83f -> R.drawable.widget_seg_on_66
                    else -> R.drawable.widget_seg_on
                }
                rv.setInt(id, "setBackgroundResource", bg)
            }
            rv.setTextColor(R.id.seg_0_label, if (pos < 0.5f) WHITE else DIM)
            mgr.partiallyUpdateAppWidget(widgetId, rv)
            kotlinx.coroutines.delay(38)
        }
    }

    // ---------------- Heute ----------------

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
                main(ctx, v, current, "JETZT", current.endDateTime, "noch %s")
                second(v, upcoming.firstOrNull(), countdown = true, endOfDay = current)
            }
            upcoming.isNotEmpty() -> {
                main(ctx, v, upcoming[0], "ALS NÄCHSTES", upcoming[0].startDateTime, "in %s")
                second(v, upcoming.getOrNull(1), countdown = false, endOfDay = upcoming[0])
            }
            else -> {
                val holiday = Schedule.holidayOn(today, data)
                v.setTextViewText(R.id.now_label, if (holiday != null) "FREI" else "HEUTE")
                v.setTextViewText(R.id.now_subject, holiday?.name ?: if (Schedule.lessonsOn(today, data).isEmpty()) "Kein Unterricht" else "Geschafft!")
                v.setViewVisibility(R.id.now_timer, View.GONE)
                v.setTextViewText(R.id.now_info, "")
                v.setViewVisibility(R.id.now_icon, View.GONE)
                tint(v, R.id.now_bg, ACCENT, 200)
                val nextDay = Schedule.nextSchoolDay(today.plusDays(1), data)
                val first = nextDay?.let { d -> Schedule.lessonsOn(d, data).firstOrNull { !it.cancelled } }
                if (first != null) {
                    val day = if (first.date == today.plusDays(1)) "MORGEN" else Fmt.dayShort(first.date).uppercase()
                    v.setTextViewText(R.id.next_label, "$day · ${first.periodLabel} STD · ${first.start?.let { Fmt.time(it) } ?: ""}")
                    v.setTextViewText(R.id.next_subject, first.subject?.name ?: "?")
                    tint(v, R.id.next_bar, subjectColor(first.subject))
                } else {
                    v.setTextViewText(R.id.next_label, "DANACH")
                    v.setTextViewText(R.id.next_subject, "Nichts geplant")
                    tint(v, R.id.next_bar, 0x66FFFFFF)
                }
                v.setViewVisibility(R.id.next_timer, View.GONE)
            }
        }
        if (withTasks) fillTasks(ctx, v, snap, today, null, header = true)
        return v
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

    private fun main(ctx: Context, v: RemoteViews, l: Lesson, label: String, target: LocalDateTime?, format: String) {
        val extra = if (l.cancelled) " · ENTFALL" else ""
        v.setTextViewText(R.id.now_label, "$label · ${l.periodLabel} STD$extra")
        v.setTextViewText(R.id.now_subject, l.subject?.name ?: "?")
        v.setTextViewText(R.id.now_info, l.room.takeIf { it.isNotBlank() }?.let { "R $it" } ?: "")
        val icon = subjectIcon(ctx, l.subject)
        if (icon != 0) {
            v.setViewVisibility(R.id.now_icon, View.VISIBLE)
            v.setImageViewResource(R.id.now_icon, icon)
        } else {
            v.setViewVisibility(R.id.now_icon, View.GONE)
        }
        tint(v, R.id.now_bg, subjectColor(l.subject), 205)
        countdown(v, R.id.now_timer, target, format)
    }

    private fun second(v: RemoteViews, l: Lesson?, countdown: Boolean, endOfDay: Lesson) {
        if (l == null) {
            v.setTextViewText(R.id.next_label, "DANACH")
            v.setTextViewText(R.id.next_subject, "Schluss um ${endOfDay.end?.let { Fmt.time(it) } ?: "–"}")
            tint(v, R.id.next_bar, 0x66FFFFFF)
            v.setViewVisibility(R.id.next_timer, View.GONE)
            return
        }
        val extra = if (l.cancelled) " · ENTFALL" else ""
        v.setTextViewText(R.id.next_label, "DANACH · ${l.periodLabel} STD · ${l.start?.let { Fmt.time(it) } ?: ""}$extra")
        v.setTextViewText(R.id.next_subject, l.subject?.name ?: "?")
        tint(v, R.id.next_bar, subjectColor(l.subject))
        if (countdown) countdown(v, R.id.next_timer, l.startDateTime, "in %s") else v.setViewVisibility(R.id.next_timer, View.GONE)
    }

    /** Aktualisiert das Heute-Widget beim nächsten Stundenwechsel (Beginn/Ende) bzw. um Mitternacht. */
    private fun scheduleNext(ctx: Context, snap: Repository.Snapshot) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val times = Schedule.lessonsOn(today, snap.schedule).flatMap { listOfNotNull(it.startDateTime, it.endDateTime) }
        val next = (times + today.plusDays(1).atStartOfDay().plusMinutes(1)).filter { it.isAfter(now) }.minOrNull() ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // Nicht weckend: Ist der Bildschirm aus, kommt die Aktualisierung beim Einschalten.
        am.setWindow(AlarmManager.RTC, at, 20_000, ActionReceiver.pending(ctx, ActionReceiver.ACTION_REFRESH, 30))
    }

    // ---------------- Lernzeit ----------------

    private val kindSlots = listOf(
        Triple(StudyKind.HOMEWORK, R.id.kind_ha, R.id.kind_ha_icon),
        Triple(StudyKind.TALK, R.id.kind_ref, R.id.kind_ref_icon),
        Triple(StudyKind.LEARN, R.id.kind_learn, R.id.kind_learn_icon),
    )

    private fun kindIcon(kind: String) = when (kind) {
        StudyKind.HOMEWORK -> R.drawable.ic_k_house
        StudyKind.TALK -> R.drawable.ic_k_presentation
        else -> R.drawable.ic_k_pen
    }

    private fun study(ctx: Context, snap: Repository.Snapshot, layout: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val cur = snap.current
        val kind = cur?.kind ?: snap.settings[SettingKeys.STUDY_KIND]?.takeIf { it in StudyKind.all } ?: StudyKind.LEARN
        val now = System.currentTimeMillis()
        val total = cur?.totalMs(now) ?: 0L
        val running = cur?.isRunning == true
        val subject = (cur?.subjectId ?: snap.settings[SettingKeys.STUDY_SUBJECT])?.let { snap.schedule.subjects[it] }

        v.setChronometer(R.id.study_timer, SystemClock.elapsedRealtime() - total, null, running)
        v.setTextColor(R.id.study_timer, if (cur == null) DIM else WHITE)
        v.setImageViewResource(R.id.study_toggle, if (running) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        v.setOnClickPendingIntent(R.id.study_toggle, ActionReceiver.pending(ctx, ActionReceiver.ACTION_TOGGLE, 40))
        v.setViewVisibility(R.id.study_stop, if (cur != null) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.study_stop, ActionReceiver.pending(ctx, ActionReceiver.ACTION_STOP, 41))
        v.setOnClickPendingIntent(R.id.study_timer, openApp(ctx, 42, "study"))
        val state = when {
            running -> null
            cur != null -> "pausiert"
            else -> "bereit"
        }
        val week = if (layout == R.layout.widget_study_full) "Woche ${StudyNotifier.format(weekTotal(snap, now))}" else null
        v.setTextViewText(R.id.study_info, listOfNotNull(StudyKind.label(kind), subject?.label, state, week).joinToString(" · "))

        if (layout == R.layout.widget_study_compact) {
            // Antippen wechselt zur nächsten Art.
            val i = kindSlots.indexOfFirst { it.first == kind }.coerceAtLeast(0)
            v.setImageViewResource(R.id.study_kind, kindIcon(kind))
            val next = kindSlots[(i + 1) % kindSlots.size].first
            v.setOnClickPendingIntent(
                R.id.study_kind,
                ActionReceiver.pending(ctx, ActionReceiver.ACTION_KIND, 43) { putExtra(ActionReceiver.EXTRA_KIND, next) },
            )
        } else {
            // Glas-Umschalter wie in der App: gewählte Art blau hinterlegt.
            kindSlots.forEachIndexed { i, (k, slot, icon) ->
                val on = k == kind
                v.setInt(slot, "setBackgroundResource", if (on) R.drawable.widget_seg_on else 0)
                v.setInt(icon, "setImageAlpha", if (on) 255 else 170)
                v.setOnClickPendingIntent(
                    slot,
                    ActionReceiver.pending(ctx, ActionReceiver.ACTION_KIND, 44 + i) { putExtra(ActionReceiver.EXTRA_KIND, k) },
                )
            }
        }
        if (layout == R.layout.widget_study_full) v.setOnClickPendingIntent(R.id.study_info, openApp(ctx, 47, "study"))
        return v
    }

    /** Lernzeit der aktuellen Woche (Mo–So) inklusive laufender Session. */
    fun weekTotal(snap: Repository.Snapshot, now: Long): Long {
        val monday = Schedule.mondayOf(LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return snap.sessions.filter { it.startedAt >= monday }.sumOf { it.totalMs(now) }
    }

    // ---------------- Nächste Klausur ----------------

    private fun exam(ctx: Context, snap: Repository.Snapshot): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_exam)
        val today = LocalDate.now()
        val e = snap.exams.filter { it.date >= today.toEpochDay() }.minByOrNull { it.date }
        if (e == null) {
            v.setTextViewText(R.id.exam_date, "")
            v.setTextViewText(R.id.exam_subject, "Keine Klausur")
            v.setTextViewText(R.id.exam_title, "Gerade nichts in Sicht")
            v.setViewVisibility(R.id.exam_icon, View.GONE)
            v.setTextViewText(R.id.exam_days, "–")
            v.setTextColor(R.id.exam_days, WHITE)
            v.setTextViewText(R.id.exam_days_label, "")
            v.setViewVisibility(R.id.exam_progress, View.GONE)
            v.setTextViewText(R.id.exam_info, "")
            v.setOnClickPendingIntent(R.id.exam_subject, openApp(ctx, 50))
            return v
        }
        val subject = snap.schedule.subjects[e.subjectId]
        val date = LocalDate.ofEpochDay(e.date)
        val days = Fmt.daysUntil(today, date)
        v.setTextViewText(R.id.exam_date, Fmt.dayMonth(date))
        v.setTextViewText(R.id.exam_subject, subject?.name ?: "?")
        v.setTextViewText(R.id.exam_title, listOfNotNull(e.type, e.title.ifBlank { null }, e.period?.let { "$it. Std" }).joinToString(" · "))
        val icon = subjectIcon(ctx, subject)
        if (icon != 0) {
            v.setViewVisibility(R.id.exam_icon, View.VISIBLE)
            v.setImageViewResource(R.id.exam_icon, icon)
            v.setInt(R.id.exam_icon, "setColorFilter", subjectColor(subject))
        } else {
            v.setViewVisibility(R.id.exam_icon, View.GONE)
        }
        // Je näher, desto röter (wie in der App).
        v.setTextViewText(R.id.exam_days, if (days == 0L) "Heute" else "$days")
        v.setTextColor(R.id.exam_days, urgencyColor(examUrgency(days)).toArgb())
        v.setTextViewText(R.id.exam_days_label, when (days) { 0L -> ""; 1L -> "Tag"; else -> "Tage" })
        val topics = snap.topics.filter { it.examId == e.id }
        val learned = if (topics.isEmpty()) null else topics.count { it.done } * 100 / topics.size
        v.setViewVisibility(R.id.exam_progress, if (learned != null) View.VISIBLE else View.GONE)
        v.setProgressBar(R.id.exam_progress, 100, learned ?: 0, false)
        val lz = stepPercent("lz_${e.id}", snap.topics)
        v.setTextViewText(
            R.id.exam_info,
            listOfNotNull(learned?.let { "$it % gelernt" }, lz?.let { "Lernzettel $it %" }).joinToString(" · ").ifBlank { "Noch keine Themen" },
        )
        val open = openApp(ctx, 51, "exam/${e.id}")
        v.setOnClickPendingIntent(R.id.exam_subject, open)
        v.setOnClickPendingIntent(R.id.exam_days, open)
        return v
    }

    // ---------------- Ferien ----------------

    private fun holiday(ctx: Context, snap: Repository.Snapshot, layout: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val today = LocalDate.now()
        val t = today.toEpochDay()
        val ferien = snap.schedule.holidays.filter { it.kind == HolidayKind.FERIEN || it.name.contains("ferien", ignoreCase = true) }
        val current = ferien.firstOrNull { t in it.startDate..it.endDate }
        val h = current ?: ferien.filter { it.startDate > t }.minByOrNull { it.startDate }
        v.setOnClickPendingIntent(R.id.hol_days, openApp(ctx, 60))
        if (h == null) {
            v.setTextViewText(R.id.hol_name, "Keine Ferien")
            v.setTextViewText(R.id.hol_date, "in der App eintragen")
            v.setTextViewText(R.id.hol_days, "–")
            v.setTextViewText(R.id.hol_days_label, "")
            tint(v, R.id.hol_bg, ACCENT, 90)
            return v
        }
        val color = HolidayLook.color(h).toArgb()
        tint(v, R.id.hol_bg, color, 95)
        v.setImageViewResource(R.id.hol_icon, drawable(ctx, "ic_h_${HolidayLook.key(h)}").takeIf { it != 0 } ?: R.drawable.ic_h_palm)
        v.setInt(R.id.hol_icon, "setColorFilter", color)
        v.setTextViewText(R.id.hol_name, h.name)
        val days = if (current != null) h.endDate - t + 1 else h.startDate - t
        v.setTextViewText(
            R.id.hol_date,
            if (current != null) "bis ${Fmt.dayMonth(LocalDate.ofEpochDay(h.endDate))}" else "ab ${Fmt.dayMonth(LocalDate.ofEpochDay(h.startDate))}",
        )
        v.setTextViewText(R.id.hol_days, "$days")
        v.setTextViewText(R.id.hol_days_label, if (current != null) "Tage frei" else if (days == 1L) "Tag" else "Tage")
        return v
    }

    // ---------------- Schnell-HA ----------------

    private fun quick(ctx: Context): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_quick).apply { setOnClickPendingIntent(R.id.quick_btn, quickAdd(ctx)) }
}
