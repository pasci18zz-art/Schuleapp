package de.schulplaner.notes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast

/**
 * Notizbuch-Knopf pro Fach.
 * Samsung bietet keine offizielle Schnittstelle, um ein bestimmtes Notizbuch zu öffnen.
 * Deshalb: Verknüpfung (falls eine App eine anbietet), Link oder einfach Samsung Notes starten.
 */
object Notebooks {
    const val SAMSUNG_NOTES = "com.samsung.android.app.notes"
    const val APP = "app"

    fun label(link: String): String = when {
        link.isBlank() -> "Keins"
        link == APP -> "Samsung Notes"
        link.startsWith("intent:") -> "Verknüpfung"
        else -> "Link"
    }

    fun open(context: Context, link: String) {
        try {
            when {
                link.startsWith("intent:") -> {
                    val intent = Intent.parseUri(link, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                link.startsWith("http://") || link.startsWith("https://") -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                else -> openSamsungNotes(context)
            }
        } catch (e: Exception) {
            // Verknüpfung kaputt oder App fehlt: wenigstens Samsung Notes öffnen.
            if (!openSamsungNotes(context)) {
                Toast.makeText(context, "Notizbuch konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSamsungNotes(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SAMSUNG_NOTES) ?: run {
            Toast.makeText(context, "Samsung Notes ist nicht installiert", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Apps, die Verknüpfungen anbieten (Samsung Notes zuerst). */
    fun shortcutProviders(context: Context): List<ResolveInfo> {
        val list = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
        return list.sortedBy { if (it.activityInfo.packageName == SAMSUNG_NOTES) 0 else 1 }
    }

    /** Liest die Verknüpfung aus dem Ergebnis einer CREATE_SHORTCUT-Activity (alte Form). */
    @Suppress("DEPRECATION")
    fun linkFromShortcutResult(data: Intent?): String? {
        val target = data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return null
        return target.toUri(Intent.URI_INTENT_SCHEME)
    }
}
