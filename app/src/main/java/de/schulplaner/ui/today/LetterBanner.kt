package de.schulplaner.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.Instant
import java.time.ZoneId

val LetterRed = Color(0xFFE53935)

/**
 * Offene Elternbriefe als roter Hinweis oben auf der Startseite. Eingetragen wird über das Plus
 * (Aufgaben-Tab oder Schnelleinstellungs-Kachel); hier wird nur abgehakt.
 */
@Composable
fun LetterBanner(readOnly: Boolean, modifier: Modifier = Modifier) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val letters by repo.letters.collectAsStateWithLifecycle()
    val open = letters.filter { !it.done }.sortedBy { it.createdAt }
    if (open.isEmpty()) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(LetterRed.copy(alpha = if (c.isDark) 0.22f else 0.12f))
            .border(1.dp, LetterRed.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        open.forEach { l ->
            val day = Instant.ofEpochMilli(l.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (readOnly) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(LetterRed))
                } else {
                    CheckCircle(false, {
                        repo.setLetterDone(l, true)
                        undo.show("Elternbrief fertig") { repo.setLetterDone(l, false) }
                    }, size = 26.dp, checkedColor = c.good)
                }
                Column(Modifier.weight(1f)) {
                    val title = if (l.title.isBlank() || l.title == "Elternbrief") "Elternbrief offen" else "Elternbrief: ${l.title}"
                    Text(title, style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = LetterRed, maxLines = 1)
                    Text("seit ${Fmt.dayDate(day)}", style = T.small, color = c.sub)
                }
                if (!readOnly) {
                    Icon(
                        Ic.Close,
                        "Löschen",
                        tint = c.sub,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable {
                            repo.deleteLetter(l)
                            undo.show("Elternbrief gelöscht") { repo.saveLetter(l.copy(deleted = false)) }
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
}
