package de.schulplaner.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.Letter
import de.schulplaner.domain.Fmt
import de.schulplaner.ui.components.CheckCircle
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import java.time.Instant
import java.time.ZoneId

/**
 * Elternbrief: Ein Tipp auf den Knopf, sobald du einen Brief bekommen hast. Abends kommt dann eine Erinnerung –
 * jeden Abend, bis du ihn als fertig abhakst.
 */
@Composable
fun LetterTile(modifier: Modifier = Modifier) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val undo = LocalUndo.current
    val letters by repo.letters.collectAsStateWithLifecycle()
    val open = letters.filter { !it.done }
    GlassCard(modifier.animateContentSize(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Note, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("Elternbrief", style = T.section, color = c.text, modifier = Modifier.weight(1f))
            if (open.isNotEmpty()) Text("${open.size} offen", style = T.smallBold, color = c.warn)
        }
        // Der Knopf: einmal drücken, abends kommt die Erinnerung.
        Row(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(c.accent)
                .clickable {
                    val l = Letter(createdAt = System.currentTimeMillis())
                    repo.saveLetter(l)
                    undo.show("Elternbrief eingetragen – Erinnerung heute Abend") { repo.deleteLetter(l) }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(Ic.Plus, null, tint = c.accentInk, modifier = Modifier.size(18.dp))
            Text("Elternbrief da", style = T.label.copy(fontSize = T.body.fontSize), color = c.accentInk)
        }
        open.forEach { l ->
            val day = Instant.ofEpochMilli(l.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CheckCircle(l.done, {
                    repo.setLetterDone(l, true)
                    undo.show("Elternbrief fertig") { repo.setLetterDone(l, false) }
                }, size = 24.dp, checkedColor = c.good)
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = T.body.copy(textDecoration = if (l.done) TextDecoration.LineThrough else null), color = c.text, maxLines = 1)
                    Text("seit ${Fmt.dayDate(day)}", style = T.small, color = c.sub)
                }
                Icon(
                    Ic.Close,
                    "Löschen",
                    tint = c.sub,
                    modifier = Modifier.size(26.dp).clip(CircleShape).clickable { repo.deleteLetter(l) }.padding(5.dp),
                )
            }
        }
        if (open.isEmpty()) {
            Text("Kein offener Elternbrief", style = T.small, color = c.sub, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
        }
    }
}
