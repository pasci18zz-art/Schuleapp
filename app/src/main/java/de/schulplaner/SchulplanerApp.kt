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
import kotlinx.coroutines.launch

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
    }
}

val Context.app: SchulplanerApp get() = applicationContext as SchulplanerApp
