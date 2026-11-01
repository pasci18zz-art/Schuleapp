package de.schulplaner.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.schulplaner.app
import de.schulplaner.data.Attachment
import de.schulplaner.data.AttachmentStore
import de.schulplaner.data.SchulDao
import de.schulplaner.data.SubjectTerm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

data class SyncStatus(
    val running: Boolean = false,
    val lastSync: Long = 0,
    val error: String? = null,
)

/**
 * Abgleich mit Supabase: erst Änderungen holen (neueste Änderung gewinnt), dann eigene hochladen.
 * Beim allerersten Abgleich eines Geräts gewinnt bei Konflikten das Konto,
 * damit ein neu angemeldetes Gerät die vorhandenen Daten übernimmt.
 */
class SyncEngine(
    private val context: Context,
    private val dao: SchulDao,
    private val files: AttachmentStore,
    private val scope: CoroutineScope,
) {
    val supabase = Supabase(context)
    private val types = syncTypes(dao)
    private val mutex = Mutex()
    private var pending: Job? = null
    private var foregroundLoop: Job? = null

    private val _status = MutableStateFlow(SyncStatus(lastSync = supabase.lastSync))
    val status: StateFlow<SyncStatus> = _status

    private val _account = MutableStateFlow(supabase.session?.email)
    /** E-Mail des angemeldeten Kontos oder null. */
    val account: StateFlow<String?> = _account

    val configured get() = supabase.configured
    val host get() = supabase.host
    val urlOverride get() = supabase.urlOverride
    val keyOverride get() = supabase.keyOverride

    /** Server-Adresse/Schlüssel auf diesem Gerät setzen (leer = Wert aus GitHub). */
    fun setServer(url: String, key: String) {
        supabase.session = null
        supabase.cursor = null
        _account.value = null
        supabase.urlOverride = url
        supabase.keyOverride = key
    }

    // ---------- Konto ----------

    suspend fun signIn(email: String, password: String, create: Boolean) = withContext(Dispatchers.IO) {
        val s = if (create) supabase.signUp(email.trim(), password) else supabase.signIn(email.trim(), password)
        supabase.cursor = null
        _account.value = s.email
        schedulePeriodic()
        syncNow()
    }

    fun signOut() {
        supabase.session = null
        supabase.cursor = null
        _account.value = null
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    // ---------- Auslöser ----------

    /** Nach lokalen Änderungen: kurz warten (mehrere Änderungen bündeln), dann abgleichen. */
    fun requestSoon() {
        if (!canSync()) return
        pending?.cancel()
        pending = scope.launch {
            delay(2500)
            syncNow()
        }
    }

    /** Solange die App offen ist, regelmäßig abgleichen (Änderungen vom anderen Gerät). */
    fun onForeground() {
        if (!canSync()) return
        foregroundLoop?.cancel()
        foregroundLoop = scope.launch {
            while (isActive) {
                syncNow()
                delay(60_000)
            }
        }
    }

    fun onBackground() {
        foregroundLoop?.cancel()
        foregroundLoop = null
    }

    fun schedulePeriodic() {
        if (!canSync()) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun canSync() = configured && supabase.session != null

    /** Ansichtsmodus dieses Geräts. */
    var readOnly: () -> Boolean = { false }

    // ---------- Abgleich ----------

    suspend fun syncNow() {
        if (!canSync()) return
        mutex.withLock {
            _status.value = _status.value.copy(running = true)
            try {
                withContext(Dispatchers.IO) {
                    val first = supabase.cursor == null
                    pullAll(first)
                    // Im Ansichtsmodus nur holen, nie etwas hochladen oder zusammenführen.
                    if (!readOnly()) {
                        dedupe()
                        pushAll()
                    }
                }
                val now = System.currentTimeMillis()
                supabase.lastSync = now
                _status.value = SyncStatus(running = false, lastSync = now, error = null)
            } catch (e: Exception) {
                if (supabase.session == null) _account.value = null
                _status.value = _status.value.copy(running = false, error = e.message ?: "Fehler beim Abgleich")
            }
        }
    }

    private suspend fun pullAll(first: Boolean) {
        val byName = types.associateBy { it.name }
        var since = supabase.cursor ?: "1970-01-01T00:00:00Z"
        while (true) {
            val rows = supabase.pull(overlap(since))
            var maxSeen = since
            for (row in rows) {
                val o = row.jsonObject
                val type = byName[o["type"]?.jsonPrimitive?.content] ?: continue
                applyRemote(type, o, first)
                val su = o["server_updated_at"]?.jsonPrimitive?.content
                if (su != null && OffsetDateTime.parse(su).isAfter(OffsetDateTime.parse(maxSeen))) maxSeen = su
            }
            supabase.cursor = maxSeen
            if (rows.size < 1000 || maxSeen == since) break
            since = maxSeen
        }
    }

    /** Ein paar Sekunden zurückgehen, damit gleichzeitig geschriebene Einträge nicht verloren gehen. */
    private fun overlap(since: String): String =
        runCatching { OffsetDateTime.parse(since).minusSeconds(5).toString() }.getOrDefault(since)

    private suspend fun <T : Any> applyRemote(type: SyncType<T>, row: kotlinx.serialization.json.JsonObject, first: Boolean) {
        val data: JsonElement = row["data"] ?: return
        val remote = runCatching { supabase.json.decodeFromJsonElement(type.serializer, data) }.getOrNull() ?: return
        val remoteUpdated = row["updated_at"]?.jsonPrimitive?.long ?: type.updatedAt(remote)
        val remoteDeleted = row["deleted"]?.jsonPrimitive?.boolean ?: type.deleted(remote)
        val local = type.get(type.id(remote))
        val take = when {
            local == null -> true
            first -> true
            remoteUpdated > type.updatedAt(local) -> true
            else -> false
        }
        if (!take) return
        type.upsert(type.asClean(remote))
        // Gelöscht-Markierung und Zeit aus der Zeile übernehmen (falls abweichend vom JSON).
        if (remoteDeleted != type.deleted(remote) || remoteUpdated != type.updatedAt(remote)) {
            val fixed = supabase.json.decodeFromJsonElement(
                type.serializer,
                buildJsonObject {
                    data.jsonObject.forEach { (k, v) -> put(k, v) }
                    put("deleted", remoteDeleted)
                    put("updatedAt", remoteUpdated)
                },
            )
            type.upsert(type.asClean(fixed))
        }
        if (remote is Attachment && !remoteDeleted) downloadIfMissing(remote)
    }

    private suspend fun pushAll() {
        for (type in types) pushType(type)
    }

    private suspend fun <T : Any> pushType(type: SyncType<T>) {
        val dirty = type.dirty()
        if (dirty.isEmpty()) return
        dirty.chunked(200).forEach { chunk ->
            val ok = chunk.filter { item ->
                // Dateien zuerst hochladen, sonst bleibt der Eintrag "dirty" für den nächsten Versuch.
                if (item is Attachment && !item.deleted) uploadIfPresent(item) else true
            }
            val rows: JsonArray = buildJsonArray {
                ok.forEach { item ->
                    add(
                        buildJsonObject {
                            put("type", type.name)
                            put("id", type.id(item))
                            put("data", supabase.json.encodeToJsonElement(type.serializer, item))
                            put("updated_at", type.updatedAt(item))
                            put("deleted", type.deleted(item))
                        },
                    )
                }
            }
            supabase.push(rows)
            ok.forEach { type.clean(type.id(it), type.updatedAt(it)) }
        }
    }

    private fun remotePath(a: Attachment): String = "${supabase.session?.userId}/${a.fileName}"

    private fun uploadIfPresent(a: Attachment): Boolean {
        val file = files.fileFor(a)
        if (!file.exists()) return true
        return runCatching { supabase.upload(remotePath(a), file, a.mimeType.ifBlank { "application/octet-stream" }) }.isSuccess
    }

    private fun downloadIfMissing(a: Attachment) {
        val file = files.fileFor(a)
        if (file.exists()) return
        runCatching { supabase.download(remotePath(a), file) }
    }

    // ---------- Doppelte Einträge zusammenführen ----------

    /**
     * Wenn beide Geräte vor dem ersten Abgleich dieselben Halbjahre, Fächer oder Kategorien angelegt haben,
     * gibt es sie doppelt (mit verschiedenen IDs). Alle Geräte behalten dann denselben (kleinste ID)
     * und hängen Noten, Stunden usw. um.
     */
    private suspend fun dedupe() {
        val now = System.currentTimeMillis()

        // Halbjahre
        dao.syncAllTerm().groupBy { it.name.trim().lowercase() }.values.filter { it.size > 1 }.forEach { group ->
            val keep = group.minBy { it.id }
            group.filter { it.id != keep.id }.forEach { dup ->
                dao.syncAllGrade().filter { it.termId == dup.id }.forEach { dao.upsertGrade(it.copy(termId = keep.id, updatedAt = now, dirty = true)) }
                dao.syncAllGradeCategory().filter { it.termId == dup.id }.forEach { dao.upsertCategory(it.copy(termId = keep.id, updatedAt = now, dirty = true)) }
                dao.syncAllSubjectTerm().filter { it.termId == dup.id }.forEach { st ->
                    dao.upsertSubjectTerm(st.copy(deleted = true, updatedAt = now, dirty = true))
                    dao.upsertSubjectTerm(st.copy(id = SubjectTerm.idFor(st.subjectId, keep.id), termId = keep.id, updatedAt = now, dirty = true))
                }
                dao.upsertTerm(dup.copy(deleted = true, updatedAt = now, dirty = true))
            }
        }

        // Fächer mit gleichem Namen
        dao.syncAllSubject().groupBy { it.name.trim().lowercase() }.values.filter { it.size > 1 }.forEach { group ->
            val keep = group.minBy { it.id }
            group.filter { it.id != keep.id }.forEach { dup ->
                val d = dup.id
                val k = keep.id
                dao.syncAllLessonSlot().filter { it.subjectId == d }.forEach { dao.upsertSlot(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllTask().filter { it.subjectId == d }.forEach { dao.upsertTask(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllExam().filter { it.subjectId == d }.forEach { dao.upsertExam(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllGrade().filter { it.subjectId == d }.forEach { dao.upsertGrade(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllGradeCategory().filter { it.subjectId == d }.forEach { dao.upsertCategory(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllSubjectNote().filter { it.subjectId == d }.forEach { dao.upsertNote(it.copy(subjectId = k, updatedAt = now, dirty = true)) }
                dao.syncAllSubjectTerm().filter { it.subjectId == d }.forEach { st ->
                    dao.upsertSubjectTerm(st.copy(deleted = true, updatedAt = now, dirty = true))
                    dao.upsertSubjectTerm(st.copy(id = SubjectTerm.idFor(k, st.termId), subjectId = k, updatedAt = now, dirty = true))
                }
                dao.upsertSubject(dup.copy(deleted = true, updatedAt = now, dirty = true))
            }
        }

        // Bewertungskategorien (gleiches Fach, Halbjahr und Name)
        dao.syncAllGradeCategory().groupBy { Triple(it.subjectId, it.termId, it.name.trim().lowercase()) }.values.filter { it.size > 1 }.forEach { group ->
            val keep = group.minBy { it.id }
            group.filter { it.id != keep.id }.forEach { dup ->
                dao.syncAllGrade().filter { it.categoryId == dup.id }.forEach { dao.upsertGrade(it.copy(categoryId = keep.id, updatedAt = now, dirty = true)) }
                dao.upsertCategory(dup.copy(deleted = true, updatedAt = now, dirty = true))
            }
        }
    }

    companion object {
        const val PERIODIC = "sync_periodisch"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.app.sync.syncNow()
        return Result.success()
    }
}
