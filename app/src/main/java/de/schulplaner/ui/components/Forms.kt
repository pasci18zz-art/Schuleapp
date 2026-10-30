package de.schulplaner.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.schulplaner.app
import de.schulplaner.data.Attachment
import de.schulplaner.data.Subject
import de.schulplaner.domain.GradeCalc
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Bottom-Sheet im App-Stil. [content] bekommt eine Funktion, die das Sheet animiert schließt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.sheet,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(c.line))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content(close)
        }
    }
}

/** Kopfzeile eines Sheets: Titel links, Löschen/Speichern rechts. */
@Composable
fun SheetHeader(title: String, onSave: (() -> Unit)?, onDelete: (() -> Unit)? = null, saveLabel: String = "Speichern") {
    val c = colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.title.copy(fontSize = T.section.fontSize * 1.4f), color = c.text, modifier = Modifier.weight(1f))
        if (onDelete != null) {
            SoftIconButton(Ic.Trash, "Löschen", onDelete, tint = c.warn, background = c.warnSoft)
            HSpace(8.dp)
        }
        if (onSave != null) PrimaryButton(saveLabel, onSave)
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = colors
    Row(
        modifier
            .height(42.dp)
            .pressable(if (enabled) onClick else null)
            .clip(RoundedCornerShape(21.dp))
            .background(if (enabled) c.accent else c.line)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = c.accentInk, modifier = Modifier.size(18.dp))
        Text(label, style = T.label.copy(fontSize = T.body.fontSize * 0.95f), color = if (enabled) c.accentInk else c.sub)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = colors.accent) {
    val c = colors
    Row(
        modifier
            .height(50.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(25.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(25.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Text(label, style = T.label.copy(fontSize = T.body.fontSize), color = color)
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val c = colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(18.dp),
        textStyle = T.bodyRegular.copy(color = c.text),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.line,
            focusedContainerColor = c.card,
            unfocusedContainerColor = c.card,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.sub,
            cursorColor = c.accent,
        ),
    )
}

/** Eine antippbare Zeile mit Symbol, Beschriftung und Wert (z. B. Datum). */
@Composable
fun PickerRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Text(label, style = T.body, color = c.sub, modifier = Modifier.weight(1f))
        Text(value, style = T.body, color = c.text)
    }
}

@Composable
fun FormLabel(text: String) {
    Text(text, style = T.label, color = colors.sub, modifier = Modifier.padding(start = 4.dp))
}

/** Fachauswahl als waagerechte Chip-Liste. */
@Composable
fun SubjectPicker(subjects: List<Subject>, selectedId: String?, onSelect: (String?) -> Unit, allowNone: Boolean = true) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        if (allowNone) {
            item { ChoiceChip("Ohne Fach", selectedId == null, { onSelect(null) }) }
        }
        items(subjects, key = { it.id }) { s ->
            ChoiceChip(s.name, selectedId == s.id, { onSelect(s.id) }, leading = { SubjectDot(subjectColor(s.color), 9.dp) })
        }
    }
}

/** Punkte 0–15 als Raster. */
@Composable
fun PointsPicker(points: Int, onPick: (Int) -> Unit) {
    val c = colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf((15 downTo 8).toList(), (7 downTo 0).toList()).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    val selected = p == points
                    val bg by animateColorAsState(if (selected) c.accent else c.card, tween(150), label = "pts")
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(1.dp, if (selected) Color.Transparent else c.line, RoundedCornerShape(12.dp))
                            .clickable { onPick(p) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$p", style = T.label.copy(fontSize = T.body.fontSize), color = if (selected) c.accentInk else c.text)
                    }
                }
            }
        }
        Text(
            "$points Punkte ≈ Note ${GradeCalc.note(points.toDouble())}",
            style = T.small,
            color = c.sub,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        DatePicker(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = { TimeInput(state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Löschen", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm, color = colors.warn) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * Fotos und Dateien zu einem Eintrag: anzeigen, öffnen, aufnehmen, aus Galerie/Dateien hinzufügen.
 * Langes Drücken auf ein Element löscht es.
 */
@Composable
fun AttachmentStrip(ownerType: String, ownerId: String, attachments: List<Attachment>, allowFiles: Boolean = true) {
    val c = colors
    val ctx = LocalContext.current
    val repo = ctx.app.repo
    val appScope = ctx.app.appScope
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Attachment?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera
        pendingCamera = null
        if (ok && path != null) {
            appScope.launch(Dispatchers.IO) {
                repo.files.importCamera(File(path), ownerType, ownerId)?.let(repo::addAttachment)
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        appScope.launch(Dispatchers.IO) {
            uris.forEach { uri -> repo.files.importUri(uri, ownerType, ownerId)?.let(repo::addAttachment) }
        }
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            appScope.launch(Dispatchers.IO) {
                repo.files.importUri(uri, ownerType, ownerId)?.let(repo::addAttachment)
            }
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { a ->
            val file = repo.files.fileFor(a)
            Box(
                Modifier
                    .size(76.dp)
                    .pressable(onClick = { repo.files.open(ctx, a) }, onLongClick = { toDelete = a })
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (a.mimeType.startsWith("image/")) {
                    AsyncImage(model = file, contentDescription = a.displayName, contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                        Icon(Ic.File, null, tint = c.accent, modifier = Modifier.size(22.dp))
                        Text(a.displayName, style = T.small.copy(fontSize = T.small.fontSize * 0.85f), color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        item {
            AddTile(Ic.Camera, "Foto aufnehmen") {
                val (file, uri) = repo.files.newCameraTarget()
                pendingCamera = file.absolutePath
                camera.launch(uri)
            }
        }
        item {
            AddTile(Ic.ImagePlus, "Aus Galerie wählen") {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (allowFiles) {
            item { AddTile(Ic.Clip, "Datei anhängen") { document.launch(arrayOf("*/*")) } }
        }
    }

    toDelete?.let { a ->
        ConfirmDialog("Anhang löschen?", "\"${a.displayName}\" wird entfernt.", onDismiss = { toDelete = null }) {
            repo.deleteAttachment(a)
        }
    }
}

@Composable
private fun AddTile(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = colors
    Box(
        Modifier
            .size(76.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, c.line, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = c.accent, modifier = Modifier.size(24.dp))
    }
}

/** Kleine Zahl-Eingabe mit −/+ (z. B. Gewichtung). */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SoftIconButton(Ic.Minus, "Weniger", onMinus, tint = c.text, background = c.line)
        Text(value, style = T.label.copy(fontSize = T.body.fontSize), color = c.text, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
        SoftIconButton(Ic.Plus, "Mehr", onPlus, tint = c.text, background = c.line)
    }
}
