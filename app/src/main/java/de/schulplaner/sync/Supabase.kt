package de.schulplaner.sync

import android.content.Context
import de.schulplaner.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseException(val code: Int, message: String) : IOException(message)

/**
 * Macht aus dem eingetragenen Wert eine saubere Adresse "https://<projekt>.supabase.co".
 * Unsichtbare Zeichen, Leerzeichen, Pfade oder ein kopierter Dashboard-Link werden toleriert.
 */
internal fun normalizeUrl(raw: String): String {
    val clean = raw.filter { it.code in 33..126 }
    Regex("([a-z0-9-]+)\\.supabase\\.(co|in)(?![a-z])", RegexOption.IGNORE_CASE).find(clean)?.let {
        return "https://" + it.value.lowercase()
    }
    Regex("project/([a-z0-9]{10,})", RegexOption.IGNORE_CASE).find(clean)?.let {
        return "https://" + it.groupValues[1].lowercase() + ".supabase.co"
    }
    return clean.trimEnd('/')
}

/** Angemeldete Sitzung (wird lokal gespeichert). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

/**
 * Schlanker Zugriff auf Supabase über die REST-Schnittstellen (Auth, Datenbank, Speicher).
 * URL und öffentlicher Schlüssel kommen beim Bauen aus den GitHub-Secrets.
 */
class Supabase(context: Context) {
    val baseUrl = normalizeUrl(BuildConfig.SUPABASE_URL)
    private val apiKey = BuildConfig.SUPABASE_KEY.filter { it.code in 33..126 }

    /** Nur der Servername (zur Anzeige in den Einstellungen). */
    val host: String get() = baseUrl.removePrefix("https://")
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val configured: Boolean get() = baseUrl.startsWith("https://") && apiKey.isNotBlank()

    // ---------- Sitzung ----------

    var session: Session?
        get() {
            val token = prefs.getString("access", null) ?: return null
            return Session(
                accessToken = token,
                refreshToken = prefs.getString("refresh", "") ?: "",
                expiresAt = prefs.getLong("expires", 0),
                userId = prefs.getString("user", "") ?: "",
                email = prefs.getString("email", "") ?: "",
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove("access"); remove("refresh"); remove("expires"); remove("user"); remove("email")
                } else {
                    putString("access", value.accessToken)
                    putString("refresh", value.refreshToken)
                    putLong("expires", value.expiresAt)
                    putString("user", value.userId)
                    putString("email", value.email)
                }
            }.apply()
        }

    var cursor: String?
        get() = prefs.getString("cursor", null)
        set(value) = prefs.edit().putString("cursor", value).apply()

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit().putLong("last_sync", value).apply()

    fun signUp(email: String, password: String): Session = auth("/auth/v1/signup", email, password)

    fun signIn(email: String, password: String): Session = auth("/auth/v1/token?grant_type=password", email, password)

    private fun auth(path: String, email: String, password: String): Session {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val (code, text) = http("POST", path, body, token = null)
        if (code !in 200..299) throw SupabaseException(code, authError(text))
        val obj = json.parseToJsonElement(text).jsonObject
        if (obj["access_token"] == null) {
            throw SupabaseException(code, "Bitte in Supabase „Confirm email“ ausschalten oder die Bestätigungs-Mail öffnen.")
        }
        return toSession(obj, email).also { session = it }
    }

    private fun toSession(obj: JsonObject, emailFallback: String): Session {
        val user = obj["user"]?.jsonObject
        return Session(
            accessToken = obj["access_token"]!!.jsonPrimitive.content,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: "",
            expiresAt = System.currentTimeMillis() / 1000 + (obj["expires_in"]?.jsonPrimitive?.long ?: 3600),
            userId = user?.get("id")?.jsonPrimitive?.content ?: "",
            email = user?.get("email")?.jsonPrimitive?.content ?: emailFallback,
        )
    }

    /** Gültiges Zugangs-Token, bei Bedarf erneuert. */
    fun validSession(): Session {
        val s = session ?: throw SupabaseException(401, "Nicht angemeldet")
        if (s.expiresAt - 60 > System.currentTimeMillis() / 1000) return s
        val body = buildJsonObject { put("refresh_token", s.refreshToken) }.toString()
        val (code, text) = http("POST", "/auth/v1/token?grant_type=refresh_token", body, token = null)
        if (code == 400 || code == 401) {
            session = null
            throw SupabaseException(code, "Sitzung abgelaufen – bitte neu anmelden")
        }
        if (code !in 200..299) throw SupabaseException(code, "Anmeldung konnte nicht erneuert werden ($code)")
        return toSession(json.parseToJsonElement(text).jsonObject, s.email).also { session = it }
    }

    private fun authError(text: String): String {
        val msg = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            (o["msg"] ?: o["error_description"] ?: o["message"] ?: o["error"])?.jsonPrimitive?.content
        }.getOrNull() ?: text
        return when {
            msg.contains("Invalid login", true) -> "E-Mail oder Passwort falsch"
            msg.contains("already registered", true) || msg.contains("already exists", true) -> "Konto gibt es schon – bitte anmelden"
            msg.contains("Password should be", true) -> "Passwort zu kurz (mindestens 6 Zeichen)"
            msg.contains("email", true) && msg.contains("invalid", true) -> "E-Mail-Adresse ungültig"
            else -> msg
        }
    }

    // ---------- Datenbank ----------

    /** Holt Änderungen seit [since] (server_updated_at), sortiert. */
    fun pull(since: String, limit: Int = 1000): JsonArray {
        val s = validSession()
        val q = "/rest/v1/records?select=type,id,data,updated_at,deleted,server_updated_at" +
            "&server_updated_at=gt." + URLEncoder.encode(since, "UTF-8") +
            "&order=server_updated_at.asc&limit=$limit"
        val (code, text) = http("GET", q, null, s.accessToken)
        if (code !in 200..299) throw SupabaseException(code, "Abruf fehlgeschlagen ($code): ${text.take(200)}")
        return json.parseToJsonElement(text) as JsonArray
    }

    /** Lädt geänderte Einträge hoch (vorhandene werden überschrieben). */
    fun push(rows: JsonArray) {
        if (rows.isEmpty()) return
        val s = validSession()
        val (code, text) = http(
            "POST",
            "/rest/v1/records?on_conflict=user_id,type,id",
            rows.toString(),
            s.accessToken,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        if (code !in 200..299) throw SupabaseException(code, "Hochladen fehlgeschlagen ($code): ${text.take(200)}")
    }

    // ---------- Dateien ----------

    fun upload(path: String, file: File, mime: String) {
        val s = validSession()
        val (code, text) = http(
            "POST", "/storage/v1/object/anhaenge/$path", null, s.accessToken,
            mapOf("Content-Type" to mime, "x-upsert" to "true"), bytes = file.readBytes(),
        )
        if (code !in 200..299) throw SupabaseException(code, "Datei-Upload fehlgeschlagen ($code): ${text.take(200)}")
    }

    fun download(path: String, target: File): Boolean {
        val s = validSession()
        val conn = open("GET", "/storage/v1/object/authenticated/anhaenge/$path", s.accessToken, emptyMap())
        return try {
            if (conn.responseCode !in 200..299) return false
            target.parentFile?.mkdirs()
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            true
        } finally {
            conn.disconnect()
        }
    }

    // ---------- HTTP ----------

    private fun open(method: String, path: String, token: String?, headers: Map<String, String>): HttpURLConnection {
        val conn = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SupabaseException(0, "Server-Adresse ungültig ($host) – bitte das Secret SUPABASE_URL prüfen")
        }
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("apikey", apiKey)
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    private fun http(
        method: String,
        path: String,
        body: String?,
        token: String?,
        headers: Map<String, String> = emptyMap(),
        bytes: ByteArray? = null,
    ): Pair<Int, String> {
        val h = if (bytes == null) mapOf("Content-Type" to "application/json") + headers else headers
        val conn = open(method, path, token, h)
        try {
            val payload = bytes ?: body?.toByteArray(Charsets.UTF_8)
            if (payload != null) conn.doOutput = true
            try {
                conn.connect()
            } catch (e: IllegalArgumentException) {
                throw SupabaseException(0, "Server-Adresse ungültig ($host) – bitte das Secret SUPABASE_URL prüfen")
            } catch (e: java.net.UnknownHostException) {
                throw SupabaseException(0, "Server nicht erreichbar ($host) – Internet da? Adresse richtig?")
            }
            if (payload != null) conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
