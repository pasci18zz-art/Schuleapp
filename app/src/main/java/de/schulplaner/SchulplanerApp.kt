package de.schulplaner

import android.app.Application
import android.content.Context
import de.schulplaner.data.AppDatabase
import de.schulplaner.data.AttachmentStore
import de.schulplaner.data.Prefs
import de.schulplaner.data.Repository
import de.schulplaner.reminders.Reminders
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

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.create(this), appScope, AttachmentStore(this))
        prefs = Prefs(this)
        appScope.launch(Dispatchers.IO) {
            repo.seedIfNeeded()
            repo.seedHessenHolidays()
        }
        appScope.launch { Reminders.scheduleFromPrefs(this@SchulplanerApp, replace = false) }
    }
}

val Context.app: SchulplanerApp get() = applicationContext as SchulplanerApp
