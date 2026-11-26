package de.schulplaner.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T

/** Eine Version mit ihren Neuerungen. [id] zählt einfach hoch. */
data class ChangeEntry(val id: Int, val title: String, val items: List<String>)

/**
 * Neuigkeiten: Beim ersten Start nach einem Update werden alle Einträge gezeigt,
 * die seit der zuletzt installierten Version dazugekommen sind. Neue Einträge immer unten anhängen.
 */
object Changelog {
    val entries = listOf(
        ChangeEntry(
            1, "Lernzeit-Kachel",
            listOf("Lernzeit über die Schnelleinstellungen starten: Bereich und Fach wählen, Uhr läuft"),
        ),
        ChangeEntry(
            2, "Termine",
            listOf(
                "Termine im Stundenplan mit Farbe, Uhrzeit, Raum und Stunden – ersetzen dort den Unterricht",
                "Abends Erinnerung an Termine für morgen",
            ),
        ),
        ChangeEntry(
            3, "Elternbrief, Stunden ersetzen & mehr",
            listOf(
                "Elternbrief über das Plus eintragen (Aufgaben-Tab oder Schnell-Kachel → „Elternbrief“)",
                "Offener Elternbrief: roter Punkt bei „Heute“ und roter Hinweis oben zum Abhaken",
                "Eltern-Geräte (Ansichtsmodus) bekommen eine Mitteilung bei neuem Elternbrief und neuer Note",
                "Stundenplan: „Stunden ersetzen“ – Stunden antippen, auch über mehrere Tage, dann durch eine Veranstaltung ersetzen",
                "Neuigkeiten wie diese erscheinen jetzt einmal nach jedem Update",
            ),
        ),
        ChangeEntry(
            4, "Fragen zur Klausur",
            listOf("Neuer Reiter „Fragen“ in der Klausur und im Lernzettel – dieselbe Liste, abhaken, wenn geklärt"),
        ),
    )

    /** Stand vor Einführung der Neuigkeiten (Build 41): Wer von dort aktualisiert, sieht alles danach. */
    private const val BEFORE_CHANGELOG = 2
    private const val PREFS = "neuigkeiten"
    private const val KEY = "gesehen"

    fun unseen(context: Context): List<ChangeEntry> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = if (sp.contains(KEY)) {
            sp.getInt(KEY, 0)
        } else {
            // Frisch installiert: keine Neuigkeiten zeigen. Aktualisiert: alles seit der letzten Version.
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
            if (fresh) entries.maxOf { it.id } else BEFORE_CHANGELOG
        }
        return entries.filter { it.id > seen }
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, entries.maxOf { it.id }).apply()
    }
}

/** Zeigt die Neuigkeiten einmal nach einem Update. */
@Composable
fun ChangelogDialog() {
    val ctx = LocalContext.current
    val c = colors
    var list by remember { mutableStateOf(Changelog.unseen(ctx)) }
    if (list.isEmpty()) {
        // Auch bei frischer Installation merken, damit spätere Updates richtig zählen.
        remember { Changelog.markSeen(ctx) }
        return
    }
    val close = {
        Changelog.markSeen(ctx)
        list = emptyList()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Neu in der App") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                list.asReversed().forEach { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(e.title, style = T.label, color = c.accent)
                        e.items.forEach { item ->
                            Row {
                                Text("•", style = T.body, color = c.sub, modifier = Modifier.padding(end = 8.dp))
                                Text(item, style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.text)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Alles klar") } },
    )
}
