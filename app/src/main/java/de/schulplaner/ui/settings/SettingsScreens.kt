package de.schulplaner.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.R
import de.schulplaner.app
import de.schulplaner.data.Holiday
import de.schulplaner.data.HolidayKind
import de.schulplaner.reminders.Reminders
import de.schulplaner.ui.components.ColorRow
import de.schulplaner.ui.components.FormGroup
import de.schulplaner.ui.components.FormRow
import de.schulplaner.ui.components.FormTextRow
import de.schulplaner.ui.components.FormValueRow
import de.schulplaner.ui.components.MiniSegmented
import de.schulplaner.ui.components.MoreSection
import androidx.compose.foundation.layout.width
import de.schulplaner.data.PeriodTime
import de.schulplaner.data.SettingKeys
import de.schulplaner.data.Subject
import de.schulplaner.data.Term
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.Schedule
import de.schulplaner.quick.QuickHaTileService
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.ConfirmDialog
import de.schulplaner.ui.components.DateDialog
import de.schulplaner.ui.components.EmptyHint
import de.schulplaner.ui.components.Field
import de.schulplaner.ui.components.FormLabel
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.PickerRow
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.TimeDialog
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.SubjectPalette
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun SettingsPage(title: String, root: Boolean = false, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNavigator.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (root) {
            ScreenHeader(title = title)
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Ic.ChevronLeft, "Zurück", { nav.back() })
                Box(Modifier.weight(1f))
                action?.invoke()
            }
            ScreenHeader(title = title)
        }
        content()
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = c.text)
            Text(subtitle, style = T.small, color = c.sub)
        }
        Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsScreen() {
    val nav = LocalNavigator.current
    val c = colors
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val prefs = ctx.app.prefs
    val scope = rememberCoroutineScope()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val terms by repo.terms.collectAsStateWithLifecycle()
    val periods by repo.periods.collectAsStateWithLifecycle()
    val holidays by repo.holidays.collectAsStateWithLifecycle()
    val schedule by repo.scheduleData.collectAsStateWithLifecycle()
    val theme by prefs.theme.collectAsStateWithLifecycle("system")
    val reminderOn by prefs.reminderOn.collectAsStateWithLifecycle(true)
    val reminderMinute by prefs.reminderMinute.collectAsStateWithLifecycle(18 * 60)
    var pickTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    SettingsPage("Einstellungen", root = true) {
        GlassCard(padding = PaddingValues(6.dp)) {
            NavRow(Ic.Book, "Fächer", "${subjects.size} Fächer · Farben, Lehrer, Räume") { nav.open(Routes.SET_SUBJECTS) }
            NavRow(Ic.Layers, "Halbjahre", terms.joinToString(", ") { it.name }.ifBlank { "Keine" }) { nav.open(Routes.SET_TERMS) }
            NavRow(Ic.Clock, "Stundenzeiten", "${periods.size} Stunden") { nav.open(Routes.SET_PERIODS) }
            NavRow(Ic.Palm, "Ferien & freie Tage", "${holidays.size} eingetragen") { nav.open(Routes.SET_HOLIDAYS) }
        }

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.Swap, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Text("A/B-Wochen", style = T.section, color = c.text, modifier = Modifier.weight(1f))
                Segmented(listOf(true to "An", false to "Aus"), schedule.abEnabled, {
                    repo.setSetting(SettingKeys.AB_ENABLED, it.toString())
                }, Modifier.size(width = 130.dp, height = 46.dp))
            }
            if (schedule.abEnabled) {
                val current = Schedule.weekTypeOf(today, schedule)
                Text("Diese Woche ist:", style = T.small, color = c.sub, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                val monday = Schedule.mondayOf(today).toEpochDay()
                Segmented(listOf("A" to "A-Woche", "B" to "B-Woche"), current ?: "A", { picked ->
                    val ref = if (picked == "A") monday else monday - 7
                    repo.setSetting(SettingKeys.AB_REFERENCE, ref.toString())
                })
            }
        }

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.Sun, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Text("Darstellung", style = T.section, color = c.text)
            }
            Segmented(
                listOf("system" to "System", "light" to "Hell", "dark" to "Dunkel"),
                theme,
                { v -> scope.launch { prefs.setTheme(v) } },
                Modifier.padding(top = 12.dp),
            )
        }

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.Clock, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Text("Erinnerung am Vorabend", style = T.section, color = c.text, modifier = Modifier.weight(1f))
                MiniSegmented(listOf(true to "An", false to "Aus"), reminderOn, { on ->
                    scope.launch {
                        prefs.setReminder(on, reminderMinute)
                        Reminders.scheduleFromPrefs(ctx, replace = true)
                    }
                }, Modifier.width(110.dp))
            }
            if (reminderOn) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Uhrzeit", style = T.body, color = c.sub, modifier = Modifier.weight(1f))
                    Text(
                        Fmt.minutes(reminderMinute) + " Uhr",
                        style = T.body,
                        color = c.text,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.line).clickable { pickTime = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Text("Erinnert an offene HA und Klausuren für den nächsten Tag.", style = T.small, color = c.sub, modifier = Modifier.padding(top = 8.dp))
            }
        }

        GlassCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.Bookmark, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Text("Schnell-HA", style = T.section, color = c.text)
            }
            Text(
                "Leg dir die Kachel „HA eintragen“ in die Schnelleinstellungen (oben runterziehen). " +
                    "Sie wählt automatisch das Fach der aktuellen Stunde und die nächste Stunde als Termin.",
                style = T.small,
                color = c.sub,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            if (Build.VERSION.SDK_INT >= 33) {
                de.schulplaner.ui.components.PrimaryButton("Kachel hinzufügen", {
                    val sbm = ctx.getSystemService(StatusBarManager::class.java)
                    sbm?.requestAddTileService(
                        ComponentName(ctx, QuickHaTileService::class.java),
                        ctx.getString(R.string.tile_label),
                        AndroidIcon.createWithResource(ctx, R.drawable.ic_tile_ha),
                        ctx.mainExecutor,
                    ) { result ->
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            Toast.makeText(ctx, "Die Kachel ist schon da", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, Modifier.fillMaxWidth(), icon = Ic.Plus)
            } else {
                Text("Schnelleinstellungen bearbeiten → „HA eintragen“ hineinziehen.", style = T.small, color = c.sub)
            }
        }

        SyncCard()
    }
    if (pickTime) {
        TimeDialog(reminderMinute, onDismiss = { pickTime = false }) { m ->
            scope.launch {
                prefs.setReminder(true, m)
                Reminders.scheduleFromPrefs(ctx, replace = true)
            }
        }
    }
}

// ---------------- Fächer ----------------

@Composable
fun SubjectsScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Pair<Subject, Boolean>?>(null) }

    SettingsPage("Fächer", action = {
        GlassIconButton(Ic.Plus, "Fach hinzufügen", {
            val used = subjects.map { it.color }.toSet()
            edit = Subject(color = SubjectPalette.firstOrNull { it !in used } ?: SubjectPalette.first(), sortOrder = subjects.size) to true
        }, filled = true)
    }) {
        if (subjects.isEmpty()) {
            GlassCard { EmptyHint(Ic.Book, "Noch keine Fächer. Tippe oben auf +.") }
        } else {
            GlassCard(padding = PaddingValues(6.dp)) {
                subjects.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { edit = s to false }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(subjectColor(s.color).copy(alpha = c.tint)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s.shortName.ifBlank { s.name.take(2) }, style = T.smallBold, color = c.text, maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.name, style = T.body, color = c.text)
                                if (s.isLk) Pill("LK")
                            }
                            val sub = listOf(s.teacher, s.room.takeIf { it.isNotBlank() }?.let { "R $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = T.small, color = c.sub)
                        }
                    }
                }
            }
        }
    }

    edit?.let { (s, isNew) -> SubjectEditSheet(s, isNew, onDismiss = { edit = null }) }
}

@Composable
private fun SubjectEditSheet(initial: Subject, isNew: Boolean, onDismiss: () -> Unit) {
    val c = colors
    val repo = LocalContext.current.app.repo
    var s by remember { mutableStateOf(initial) }
    var confirm by remember { mutableStateOf(false) }
    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            if (isNew) "Neues Fach" else "Fach",
            onSave = if (s.name.isNotBlank()) {
                {
                    repo.saveSubject(s.copy(name = s.name.trim(), shortName = s.shortName.trim()))
                    close()
                }
            } else {
                null
            },
        )
        FormGroup {
            FormTextRow("Name", s.name, { s = s.copy(name = it) }, placeholder = "z. B. Mathematik", capitalization = KeyboardCapitalization.Words)
            FormTextRow("Kürzel", s.shortName, { s = s.copy(shortName = it.take(5)) }, placeholder = "z. B. M", capitalization = KeyboardCapitalization.Characters)
            FormRow("Kurs") {
                MiniSegmented(listOf(false to "GK", true to "LK"), s.isLk, { s = s.copy(isLk = it) }, Modifier.weight(1f))
            }
            FormRow("Farbe", divider = false) {
                ColorRow(s.color) { s = s.copy(color = it) }
            }
        }
        MoreSection(initiallyOpen = !isNew && (s.teacher.isNotBlank() || s.room.isNotBlank())) {
            FormGroup {
                FormTextRow("Lehrer/in", s.teacher, { s = s.copy(teacher = it) }, placeholder = "optional", capitalization = KeyboardCapitalization.Words)
                FormTextRow("Raum", s.room, { s = s.copy(room = it) }, placeholder = "optional", divider = false)
            }
        }
        if (!isNew) {
            Text(
                "Fach löschen",
                style = T.label.copy(fontSize = T.body.fontSize * 0.93f),
                color = c.warn,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { confirm = true }.padding(12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (confirm) {
        ConfirmDialog("Fach löschen?", "\"${s.name}\" wird aus Stundenplan und Listen entfernt.", onDismiss = { confirm = false }) {
            repo.deleteSubject(s)
            onDismiss()
        }
    }
}

// ---------------- Halbjahre ----------------

@Composable
fun TermsScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val terms by repo.terms.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Pair<Term, Boolean>?>(null) }
    val current = repo.currentTerm(terms)

    SettingsPage("Halbjahre", action = {
        GlassIconButton(Ic.Plus, "Halbjahr hinzufügen", {
            val last = terms.maxByOrNull { it.endDate }
            val start = last?.let { LocalDate.ofEpochDay(it.endDate).plusDays(1) } ?: LocalDate.now()
            edit = Term(
                name = "",
                startDate = start.toEpochDay(),
                endDate = start.plusMonths(6).minusDays(1).toEpochDay(),
                sortOrder = (terms.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ) to true
        }, filled = true)
    }) {
        GlassCard(padding = PaddingValues(6.dp)) {
            terms.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { edit = t to false }.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, style = T.section, color = c.text, modifier = Modifier.weight(1f))
                    if (t.id == current?.id) Pill("aktuell", modifier = Modifier.padding(end = 10.dp))
                    Text("${Fmt.dateShort(LocalDate.ofEpochDay(t.startDate))}${LocalDate.ofEpochDay(t.startDate).year % 100} – ${Fmt.dateShort(LocalDate.ofEpochDay(t.endDate))}${LocalDate.ofEpochDay(t.endDate).year % 100}", style = T.small, color = c.sub)
                }
            }
        }
        Text("Noten werden über das Datum dem passenden Halbjahr zugeordnet.", style = T.small, color = c.sub, modifier = Modifier.padding(horizontal = 8.dp))
    }

    edit?.let { (t, isNew) -> TermEditSheet(t, isNew, onDismiss = { edit = null }) }
}

@Composable
private fun TermEditSheet(initial: Term, isNew: Boolean, onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repo
    var t by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            if (isNew) "Neues Halbjahr" else "Halbjahr",
            onSave = if (t.name.isNotBlank() && t.endDate >= t.startDate) {
                {
                    repo.saveTerm(t.copy(name = t.name.trim()))
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                { confirm = true }
            } else {
                null
            },
        )
        Field(t.name, { t = t.copy(name = it) }, "Name (z. B. Q2)", capitalization = KeyboardCapitalization.Characters)
        PickerRow(Ic.CalendarPlain, "Beginn", Fmt.dayDate(LocalDate.ofEpochDay(t.startDate)) + LocalDate.ofEpochDay(t.startDate).year, { pick = "start" })
        PickerRow(Ic.CalendarPlain, "Ende", Fmt.dayDate(LocalDate.ofEpochDay(t.endDate)) + LocalDate.ofEpochDay(t.endDate).year, { pick = "end" })
    }
    pick?.let { which ->
        DateDialog(LocalDate.ofEpochDay(if (which == "start") t.startDate else t.endDate), onDismiss = { pick = null }) { d ->
            t = if (which == "start") t.copy(startDate = d.toEpochDay()) else t.copy(endDate = d.toEpochDay())
        }
    }
    if (confirm) {
        ConfirmDialog("Halbjahr löschen?", "Die Noten bleiben gespeichert, sind aber nicht mehr sichtbar.", onDismiss = { confirm = false }) {
            repo.deleteTerm(t)
            onDismiss()
        }
    }
}

// ---------------- Stundenzeiten ----------------

@Composable
fun PeriodsScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val periods by repo.periods.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<PeriodTime, Boolean>?>(null) }

    SettingsPage("Stundenzeiten", action = {
        GlassIconButton(Ic.Plus, "Stunde hinzufügen", {
            val last = periods.maxByOrNull { it.number }
            val n = (last?.number ?: 0) + 1
            val start = last?.endMinute ?: (8 * 60)
            repo.savePeriod(PeriodTime(PeriodTime.idFor(n), n, start, start + 45))
        }, filled = true)
    }) {
        GlassCard(padding = PaddingValues(6.dp)) {
            periods.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${p.number}.", style = T.section, color = c.text, modifier = Modifier.size(width = 34.dp, height = 24.dp))
                    TimeChip(Fmt.minutes(p.startMinute)) { editing = p to true }
                    Text("–", style = T.body, color = c.sub)
                    TimeChip(Fmt.minutes(p.endMinute)) { editing = p to false }
                    Box(Modifier.weight(1f))
                    if (p.number == periods.maxOf { it.number }) {
                        SoftIconButton(Ic.Trash, "Letzte Stunde entfernen", { repo.deletePeriod(p) }, tint = c.warn, background = c.warnSoft)
                    }
                }
            }
        }
        Text("Tipp: Doppelstunden trägst du im Stundenplan als „Doppel“ ein.", style = T.small, color = c.sub, modifier = Modifier.padding(horizontal = 8.dp))
    }

    editing?.let { (p, isStart) ->
        TimeDialog(if (isStart) p.startMinute else p.endMinute, onDismiss = { editing = null }) { m ->
            if (isStart) {
                repo.savePeriod(p.copy(startMinute = m, endMinute = if (p.endMinute <= m) m + 45 else p.endMinute))
            } else {
                repo.savePeriod(p.copy(endMinute = m))
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    val c = colors
    Text(
        text,
        style = T.body,
        color = c.text,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.line).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---------------- Ferien ----------------

@Composable
fun HolidaysScreen() {
    val c = colors
    val repo = LocalContext.current.app.repo
    val holidays by repo.holidays.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Pair<Holiday, Boolean>?>(null) }

    SettingsPage("Ferien", action = {
        GlassIconButton(Ic.Swap, "Hessen-Ferien neu laden", { repo.reloadHessenHolidays() })
        Box(Modifier.width(8.dp))
        GlassIconButton(Ic.Plus, "Ferien hinzufügen", {
            val d = LocalDate.now().toEpochDay()
            edit = Holiday(startDate = d, endDate = d) to true
        }, filled = true)
    }) {
        if (holidays.isEmpty()) {
            GlassCard { EmptyHint(Ic.Palm, "Trag Ferien und freie Tage ein – dann zeigt der Stundenplan dort nichts an.") }
        } else {
            Text("Hessische Ferien und Feiertage sind schon eingetragen. Bewegliche Ferientage deiner Schule kannst du mit + ergänzen.", style = T.small, color = c.sub, modifier = Modifier.padding(horizontal = 8.dp))
            GlassCard(padding = PaddingValues(6.dp)) {
                holidays.filter { it.endDate >= LocalDate.now().minusMonths(2).toEpochDay() }.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { edit = h to false }.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectDot(if (h.kind == HolidayKind.FEIERTAG) c.exam else c.accent)
                        Text(h.name.ifBlank { "Frei" }, style = T.body, color = c.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        val s = LocalDate.ofEpochDay(h.startDate)
                        val e = LocalDate.ofEpochDay(h.endDate)
                        Text(if (s == e) Fmt.dayDate(s) else "${Fmt.dateShort(s)} – ${Fmt.dateShort(e)}", style = T.small, color = c.sub)
                    }
                }
            }
        }
    }

    edit?.let { (h, isNew) -> HolidayEditSheet(h, isNew, onDismiss = { edit = null }) }
}

@Composable
private fun HolidayEditSheet(initial: Holiday, isNew: Boolean, onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repo
    var h by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<String?>(null) }
    AppSheet(onDismiss = onDismiss) { close ->
        SheetHeader(
            if (isNew) "Ferien / frei" else "Ferien",
            onSave = if (h.endDate >= h.startDate) {
                {
                    repo.saveHoliday(h.copy(name = h.name.trim()))
                    close()
                }
            } else {
                null
            },
            onDelete = if (!isNew) {
                {
                    repo.deleteHoliday(h)
                    close()
                }
            } else {
                null
            },
        )
        Field(h.name, { h = h.copy(name = it) }, "Name (z. B. Herbstferien)", capitalization = KeyboardCapitalization.Words)
        PickerRow(Ic.CalendarPlain, "Von", Fmt.dayDate(LocalDate.ofEpochDay(h.startDate)), { pick = "start" })
        PickerRow(Ic.CalendarPlain, "Bis", Fmt.dayDate(LocalDate.ofEpochDay(h.endDate)), { pick = "end" })
    }
    pick?.let { which ->
        DateDialog(LocalDate.ofEpochDay(if (which == "start") h.startDate else h.endDate), onDismiss = { pick = null }) { d ->
            h = if (which == "start") {
                h.copy(startDate = d.toEpochDay(), endDate = maxOf(h.endDate, d.toEpochDay()))
            } else {
                h.copy(endDate = d.toEpochDay())
            }
        }
    }
}


/** Konto & Sync: anmelden, Status, jetzt abgleichen, abmelden. */
@Composable
private fun SyncCard() {
    val c = colors
    val ctx = LocalContext.current
    val sync = ctx.app.sync
    val scope = rememberCoroutineScope()
    val account by sync.account.collectAsStateWithLifecycle()
    val status by sync.status.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Cloud, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("Konto & Sync", style = T.section, color = c.text, modifier = Modifier.weight(1f))
            when {
                !sync.configured -> Pill("nicht eingerichtet", c.warnSoft, c.warn)
                account == null -> Pill("abgemeldet", c.line, c.sub)
                status.running -> Pill("gleicht ab …")
                status.error != null -> Pill("Fehler", c.warnSoft, c.warn)
                else -> Pill("verbunden", Color(0x2234C759), c.good)
            }
        }
        when {
            !sync.configured -> {
                Text(
                    "In GitHub fehlen die Secrets SUPABASE_URL und SUPABASE_KEY (oder sie sind leer). Danach eine neue Version bauen lassen.",
                    style = T.small,
                    color = c.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            account == null -> {
                Text(
                    "Auf beiden Geräten mit demselben Konto anmelden – dann wird alles automatisch abgeglichen.",
                    style = T.small,
                    color = c.sub,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                FormGroup {
                    FormTextRow("E-Mail", email, { email = it }, placeholder = "name@beispiel.de", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, capitalization = KeyboardCapitalization.None)
                    FormRow("Passwort", divider = false) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = T.body.copy(color = c.text),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            decorationBox = { inner ->
                                Box {
                                    if (password.isEmpty()) Text("mind. 6 Zeichen", style = T.bodyRegular, color = c.sub.copy(alpha = 0.7f))
                                    inner()
                                }
                            },
                        )
                    }
                }
                error?.let { Text(it, style = T.small, color = c.warn, modifier = Modifier.padding(top = 8.dp)) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    fun go(create: Boolean) {
                        if (busy) return
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                sync.signIn(email, password, create)
                                password = ""
                            } catch (e: Exception) {
                                error = e.message ?: "Anmeldung fehlgeschlagen"
                            } finally {
                                busy = false
                            }
                        }
                    }
                    de.schulplaner.ui.components.PrimaryButton(if (busy) "…" else "Anmelden", { go(false) }, Modifier.weight(1f), enabled = email.isNotBlank() && password.length >= 6)
                    de.schulplaner.ui.components.PrimaryButton("Konto erstellen", { go(true) }, Modifier.weight(1f), enabled = email.isNotBlank() && password.length >= 6)
                }
                Text(
                    "Auf dem ersten Gerät „Konto erstellen“, auf dem zweiten mit denselben Daten „Anmelden“.",
                    style = T.small,
                    color = c.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> {
                Text(account ?: "", style = T.body, color = c.text, modifier = Modifier.padding(top = 10.dp))
                val last = status.lastSync
                Text(
                    if (last > 0) {
                        val t = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        "Zuletzt abgeglichen: ${Fmt.dayDate(t.toLocalDate())} ${Fmt.time(t.toLocalTime())}"
                    } else {
                        "Noch nicht abgeglichen"
                    },
                    style = T.small,
                    color = c.sub,
                )
                status.error?.let { Text(it, style = T.small, color = c.warn, modifier = Modifier.padding(top = 6.dp)) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    de.schulplaner.ui.components.PrimaryButton(
                        if (status.running) "Gleicht ab …" else "Jetzt abgleichen",
                        { scope.launch { sync.syncNow() } },
                        Modifier.weight(1f),
                        icon = Ic.Swap,
                        enabled = !status.running,
                    )
                    Text(
                        "Abmelden",
                        style = T.label,
                        color = c.warn,
                        modifier = Modifier.clip(RoundedCornerShape(21.dp)).clickable { sync.signOut() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
