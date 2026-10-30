package de.schulplaner

import android.app.Application
import android.content.Context
import de.schulplaner.data.AppDatabase
import de.schulplaner.data.AttachmentStore
import de.schulplaner.data.Prefs
import de.schulplaner.data.Repository
import de.schulplaner.reminders.Reminders
import de.schulplaner.sync.SyncEngine
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import de.schulplaner.study.StudyNotifier
import de.schulplaner.widget.Widgets

class SchulplanerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var sync: SyncEngine
        private set

    override fun onCreate() {
        super.onCreate()
        // Absturz merken, damit die App ihn beim nächsten Start anzeigen kann (statt nur zu schließen).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { java.io.File(filesDir, CRASH_FILE).writeText(android.util.Log.getStackTraceString(error)) }
            previous?.uncaughtException(thread, error)
        }
        val db = AppDatabase.create(this)
        repo = Repository(db, appScope, AttachmentStore(this))
        prefs = Prefs(this)
        sync = SyncEngine(this, db.dao(), repo.files, appScope)
        repo.onLocalChange = { sync.requestSoon() }
        sync.schedulePeriodic()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> sync.onForeground()
                    Lifecycle.Event.ON_STOP -> sync.onBackground()
                    else -> Unit
                }
            },
        )
        appScope.launch(Dispatchers.IO) {
            repo.seedIfNeeded()
            repo.seedHessenHolidays()
        }
        appScope.launch { Reminders.scheduleFromPrefs(this@SchulplanerApp, replace = false) }

        // Lern-Mitteilung folgt der aktuellen Session (auch wenn sie vom anderen Gerät kommt).
        appScope.launch {
            combine(repo.currentStudy, repo.subjects) { s, subjects -> s to subjects.associateBy { it.id } }
                .collectLatest { (s, subjects) -> StudyNotifier.show(this@SchulplanerApp, s, subjects) }
        }
        // Widgets nach Änderungen neu zeichnen (gebündelt).
        appScope.launch(Dispatchers.IO) {
            combine(repo.tasks, repo.scheduleData, repo.sessions, repo.settings) { _, _, _, _ -> Unit }
                .collectLatest {
                    delay(700)
                    runCatching { Widgets.render(this@SchulplanerApp, repo.snapshot()) }
                }
        }
    }
}

const val CRASH_FILE = "last_crash.txt"

val Context.app: SchulplanerApp get() = applicationContext as SchulplanerApp
