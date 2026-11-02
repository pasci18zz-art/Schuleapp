package de.schulplaner.brief

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.schulplaner.MainActivity
import de.schulplaner.R
import de.schulplaner.app
import de.schulplaner.data.Exam
import de.schulplaner.data.HolidayKind
import de.schulplaner.data.Repository
import de.schulplaner.data.Task
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Lesson
import de.schulplaner.domain.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Was der Morgenbrief für einen Tag zeigt. */
class BriefData(
    val date: LocalDate,
    val lessons: List<Lesson>,
    val schoolDay: Boolean,
    val topTasks: List<Task>,
    val openCount: Int,
    val exams: List<Exam>,
    val specials: List<Special>,
    val firstStart: LocalDateTime?,
    val lastEnd: LocalDateTime?,
) {
    /** Kommt der Brief heute? Schultag – oder freier Tag, an dem noch Aufgaben offen sind. */
    val worthShowing: Boolean get() = schoolDay || openCount > 0
}

/** Besonderheiten des Tages (Entfall, Raumwechsel, Ferien bald …). */
class Special(val kind: Kind, val text: String) {
    enum class Kind { CANCELLED, ROOM, NOTE, HOLIDAY, LERNZETTEL, EXAM_TODAY, PINNED }
}

object MorningBrief {
    const val CHANNEL = "morgenbrief"
    const val ID = 4800
    private const val ACTION_FIRE = "de.schulplaner.BRIEF_FIRE"
    const val ACTION_DISMISS = "de.schulplaner.BRIEF_DISMISS"

    /** Tagesüberblick aus den aktuellen Daten. */
    fun build(snap: Repository.Snapshot, date: LocalDate = LocalDate.now(), pinnedCount: Int = 0): BriefData {
        val data = snap.schedule
        val today = date.toEpochDay()
        val lessons = Schedule.lessonsOn(date, data)
        val holiday = Schedule.holidayOn(date, data)
        val schoolDay = holiday == null && lessons.any { !it.cancelled }
        val open = snap.tasks.filter { !it.done }
        // Wichtigste Aufgaben: zuerst, was heute/morgen fällig (oder überfällig) ist, dann nach Datum.
        val top = open.sortedWith(
            compareBy<Task>({ (it.dueDate ?: Long.MAX_VALUE) > today + 1 }, { it.dueDate ?: Long.MAX_VALUE }, { it.createdAt }),
        ).take(3)
        val exams = snap.exams.filter { it.date in today..today + 14 }.sortedBy { it.date }
        val specials = mutableListOf<Special>()
        val names = data.subjects
        snap.exams.filter { it.date == today }.forEach { e ->
            specials += Special(Special.Kind.EXAM_TODAY, "Heute Klausur: ${names[e.subjectId]?.name ?: e.title}")
        }
        lessons.forEach { l ->
            val name = l.subject?.name ?: "?"
            when {
                l.cancelled -> specials += Special(Special.Kind.CANCELLED, "Entfall: ${l.periodLabel} Std $name")
                l.override?.room?.isNotBlank() == true -> specials += Special(Special.Kind.ROOM, "Raumwechsel: $name in ${l.room}")
            }
            if (l.note.isNotBlank()) specials += Special(Special.Kind.NOTE, "$name: ${l.note}")
        }
        // Ferien in Sicht (innerhalb einer Woche) bzw. heute frei.
        val ferien = data.holidays.filter { it.kind == HolidayKind.FERIEN || it.name.contains("ferien", ignoreCase = true) }
        if (holiday != null) {
            specials += Special(Special.Kind.HOLIDAY, "${holiday.name} – genieß den freien Tag")
        } else {
            ferien.filter { it.startDate in (today + 1)..(today + 7) }.minByOrNull { it.startDate }?.let { h ->
                val d = h.startDate - today
                specials += Special(Special.Kind.HOLIDAY, if (d == 1L) "Morgen beginnen die ${h.name}!" else "Noch $d Tage bis zu den ${h.name}")
            }
        }
        open.filter { it.id.startsWith("lz_") && it.dueDate != null && it.dueDate <= today + 2 }.forEach { t ->
            specials += Special(Special.Kind.LERNZETTEL, "${t.title} bis ${Fmt.dayShort(LocalDate.ofEpochDay(t.dueDate!!))} fertig machen")
        }
        if (pinnedCount > 0) specials += Special(Special.Kind.PINNED, "$pinnedCount Tagesaufgaben angepinnt")
        val real = lessons.filter { !it.cancelled }
        return BriefData(
            date = date,
            lessons = lessons,
            schoolDay = schoolDay,
            topTasks = top,
            openCount = open.size,
            exams = exams,
            specials = specials,
            firstStart = real.mapNotNull { it.startDateTime }.minOrNull(),
            lastEnd = real.mapNotNull { it.endDateTime }.maxOrNull(),
        )
    }

    // ---------- Planung ----------

    private fun firePending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 4801, Intent(ctx, BriefReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun scheduleFromPrefs(ctx: Context) {
        val (on, minute) = ctx.app.prefs.briefSnapshot()
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePending(ctx)
        if (!on || ctx.app.prefs.readOnly.value) {
            am.cancel(pi)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Genau, wenn erlaubt; sonst darf Android um ein paar Minuten verschieben.
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
        }
    }

    // ---------- Mitteilung ----------

    private fun ensureChannel(ctx: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Morgenbrief", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tagesüberblick am Morgen"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    fun post(ctx: Context, b: BriefData, subjectsName: (String?) -> String?) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(ctx, nm)
        val now = LocalDateTime.now()
        val real = b.lessons.filter { !it.cancelled }

        val title = when {
            b.schoolDay -> "Guten Morgen! ${real.size} ${if (real.size == 1) "Stunde" else "Stunden"} heute"
            else -> "Guten Morgen! Heute frei"
        }
        val lines = mutableListOf<String>()
        if (real.isNotEmpty()) {
            val first = real.first()
            lines += "Los geht's ${first.start?.let { Fmt.time(it) } ?: ""} mit ${first.subject?.name ?: "?"}${first.room.takeIf { it.isNotBlank() }?.let { " in $it" } ?: ""}"
            lines += real.joinToString(", ") { it.subject?.name ?: "?" }
        }
        if (b.topTasks.isNotEmpty()) {
            lines += "Aufgaben: " + b.topTasks.joinToString("; ") { t -> listOfNotNull(subjectsName(t.subjectId), t.title).joinToString(": ") }
        }
        b.exams.firstOrNull()?.let { e ->
            val d = e.date - b.date.toEpochDay()
            lines += "Klausur ${subjectsName(e.subjectId) ?: e.title} ${Fmt.inDays(d)}"
        }
        b.specials.filter { it.kind != Special.Kind.PINNED }.take(3).forEach { lines += it.text }

        val open = PendingIntent.getActivity(
            ctx, 4802,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "brief")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            ctx, 4803, Intent(ctx, BriefReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_study)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: if (b.openCount > 0) "${b.openCount} Aufgaben offen" else "")
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setCategory(Notification.CATEGORY_EVENT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFFF9A62.toInt())
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_w_stop), "Ausblenden", dismiss).build())
        // Verschwindet zu Unterrichtsbeginn (an freien Tagen mittags).
        val until = b.firstStart?.takeIf { it.isAfter(now) } ?: b.date.atTime(12, 0)
        val ms = java.time.Duration.between(now, until).toMillis()
        if (ms > 0) builder.setTimeoutAfter(ms)
        if (b.firstStart != null && b.firstStart.isAfter(now)) {
            // In der Now Bar läuft der Countdown bis zur ersten Stunde.
            builder.setShowWhen(true)
                .setWhen(b.firstStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        builder.addExtras(
            Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                if (b.firstStart == null) putString("android.shortCriticalText", "Brief")
            },
        )
        nm.notify(ID, builder.build())
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** Daten sammeln und – wenn es sich lohnt – den Brief zeigen. */
    suspend fun fire(ctx: Context, force: Boolean = false) {
        val app = ctx.app
        if (app.prefs.readOnly.value) return
        app.repo.awaitLoaded()
        val snap = app.repo.snapshot()
        val today = LocalDate.now().toEpochDay()
        val pinned = app.repo.dailyPins.value.count { it.day == today }
        val b = build(snap, pinnedCount = pinned)
        if (force || b.worthShowing) post(ctx, b) { id -> id?.let { snap.schedule.subjects[it]?.name } }
    }
}

/** Weckt um die eingestellte Zeit, zeigt den Brief und plant den nächsten Tag. */
class BriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            MorningBrief.ACTION_DISMISS -> {
                MorningBrief.cancel(ctx)
                return
            }
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    MorningBrief.scheduleFromPrefs(ctx)
                } else {
                    runCatching { MorningBrief.fire(ctx) }
                    MorningBrief.scheduleFromPrefs(ctx)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
