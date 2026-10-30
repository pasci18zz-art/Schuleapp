package de.schulplaner.notes

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.components.Field
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T

/** Auswahl, was der Notizbuch-Knopf eines Fachs öffnet. */
@Composable
fun NotesLinkDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = colors
    val ctx = LocalContext.current
    var step by remember { mutableStateOf("menu") }
    var link by remember { mutableStateOf(if (current.startsWith("http")) current else "") }

    val shortcutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = Notebooks.linkFromShortcutResult(result.data)
        if (picked != null) {
            onPick(picked)
        } else {
            Toast.makeText(ctx, "Diese App gibt keine Verknüpfung frei – der Knopf öffnet Samsung Notes.", Toast.LENGTH_LONG).show()
            onPick(Notebooks.APP)
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notizbuch") },
        confirmButton = {
            if (step == "link") {
                TextButton(onClick = {
                    if (link.startsWith("http")) onPick(link.trim())
                    onDismiss()
                }) { Text("Übernehmen") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (step) {
                    "menu" -> {
                        Option("Samsung Notes öffnen", "Öffnet die App (zuletzt geöffnete Notiz)") {
                            onPick(Notebooks.APP)
                            onDismiss()
                        }
                        Option("Verknüpfung auswählen …", "Falls eine App Verknüpfungen zu Notizen anbietet") { step = "shortcut" }
                        Option("Link einfügen …", "z. B. Link zu einem Notizbuch in OneNote") { step = "link" }
                        if (current.isNotBlank()) {
                            Option("Kein Notizbuch", "Knopf ausblenden") {
                                onPick("")
                                onDismiss()
                            }
                        }
                    }
                    "shortcut" -> {
                        val providers = remember { Notebooks.shortcutProviders(ctx) }
                        if (providers.isEmpty()) {
                            Text("Keine App bietet Verknüpfungen an.", style = T.small, color = c.sub)
                        }
                        providers.forEach { info ->
                            val label = info.loadLabel(ctx.packageManager).toString()
                            val app = info.activityInfo.applicationInfo.loadLabel(ctx.packageManager).toString()
                            Option(label, app) {
                                val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
                                    .setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                                try {
                                    shortcutLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    else -> {
                        Field(link, { link = it }, "https://…", keyboardType = KeyboardType.Uri)
                    }
                }
            }
        },
    )
}

@Composable
private fun Option(title: String, subtitle: String, onClick: () -> Unit) {
    val c = colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(title, style = T.body, color = c.text)
        Text(subtitle, style = T.small, color = c.sub)
    }
}
