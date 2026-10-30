package de.schulplaner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.theme.SubjectPalette
import de.schulplaner.ui.theme.T

/*
 * Kompakte Formulare: Zeilen in einer umrandeten Gruppe (Beschriftung links, Wert rechts),
 * statt einzelner großer Eingabefelder.
 */

private val LabelWidth = 92.dp

@Composable
fun FormGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
fun FormDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(colors.line))
}

/** Zeile mit Beschriftung und frei gestaltbarem Inhalt. */
@Composable
fun FormRow(label: String?, onClick: (() -> Unit)? = null, divider: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val c = colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (label != null) Text(label, style = T.small.copy(fontSize = T.label.fontSize), color = c.sub, modifier = Modifier.width(LabelWidth))
        content()
    }
    if (divider) FormDivider()
}

/** Textzeile: Beschriftung links, Eingabe rechts (ohne Rahmen). */
@Composable
fun FormTextRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    divider: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    FormRow(label, divider = divider) {
        InlineTextField(value, onChange, placeholder, Modifier.weight(1f), singleLine, keyboardType, capitalization)
    }
}

@Composable
fun InlineTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    big: Boolean = false,
) {
    val c = colors
    val style = if (big) T.title.copy(fontSize = T.section.fontSize * 1.3f, color = c.text) else T.body.copy(color = c.text)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.sub.copy(alpha = 0.7f), fontWeight = if (big) style.fontWeight else T.bodyRegular.fontWeight))
                inner()
            }
        },
    )
}

/** Zeile mit Wert und Pfeil, öffnet etwas (Datum, Auswahl …). */
@Composable
fun FormValueRow(label: String, value: String, onClick: () -> Unit, divider: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    val c = colors
    FormRow(label, onClick = onClick, divider = divider) {
        leading?.invoke()
        Text(value, style = T.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(16.dp))
    }
}

/** Kleiner Segment-Umschalter für Formularzeilen. */
@Composable
fun <K> MiniSegmented(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector?>? = null,
) {
    val c = colors
    SlidingSegments(
        options, selected, onSelect,
        modifier.clip(RoundedCornerShape(12.dp)).background(c.line.copy(alpha = 0.5f)).padding(3.dp),
        itemHeight = 32.dp, gap = 3.dp, radius = 10.dp,
        indicator = if (c.isDark) Color(0xFF3A3F4B) else Color.White,
        activeText = c.text, inactiveText = c.sub,
        textStyle = T.label,
        indicatorShadow = !c.isDark,
        icons = icons,
    )
}

/** Aufklappbarer Bereich "Mehr" für selten gebrauchte Einstellungen. */
@Composable
fun MoreSection(title: String = "Mehr", initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = colors
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "more")
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = T.label, color = c.sub)
            Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(14.dp).rotate(rot))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Farbauswahl: Vorschläge plus "+" für eigene Farben. */
@Composable
fun ColorRow(selected: Long, onSelect: (Long) -> Unit) {
    val c = colors
    var dialog by remember { mutableStateOf(false) }
    val presets = SubjectPalette.take(5)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        presets.forEach { argb ->
            ColorDot(argb, argb == selected) { onSelect(argb) }
        }
        val custom = selected !in presets
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (custom) {
                        SolidColor(Color(selected))
                    } else {
                        Brush.sweepGradient(listOf(Color(0xFFE5484D), Color(0xFFD9A441), Color(0xFF6BAF5B), Color(0xFF3FA7A0), Color(0xFF5B7FDB), Color(0xFF8E6BD9), Color(0xFFE5484D)))
                    },
                )
                .border(if (custom) 2.dp else 0.dp, if (custom) c.text else Color.Transparent, CircleShape)
                .clickable { dialog = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Plus, "Eigene Farbe", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
    if (dialog) ColorPickerDialog(selected, onDismiss = { dialog = false }, onPick = onSelect)
}

@Composable
private fun ColorDot(argb: Long, selected: Boolean, onClick: () -> Unit) {
    val c = colors
    Box(
        Modifier
            .size(26.dp)
            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .clickable(onClick = onClick),
    )
}

/** Eigene Farbe: alle Vorschläge + Regler für Farbton, Sättigung, Helligkeit + Hex-Code. */
@Composable
fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val c = colors
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember(current) { mutableStateOf(String.format("%06X", current and 0xFFFFFF)) }

    fun setFrom(argb: Int) {
        val f = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, f)
        hue = f[0]
        sat = f[1]
        value = f[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(current.toLong() and 0xFFFFFFFFL)
                onDismiss()
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text("Farbe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(current)))
                    Text("#", style = T.body, color = c.sub)
                    InlineTextField(
                        hex,
                        { v ->
                            val clean = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            hex = clean
                            if (clean.length == 6) setFrom(0xFF000000.toInt() or clean.toInt(16))
                        },
                        "RRGGBB",
                        Modifier.weight(1f),
                        capitalization = KeyboardCapitalization.Characters,
                    )
                }
                SubjectPalette.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { argb ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(argb)).clickable { setFrom(argb.toInt()) },
                            )
                        }
                    }
                }
                SliderRow("Farbton", hue / 360f) { hue = it * 360f }
                SliderRow("Sättigung", sat) { sat = it }
                SliderRow("Helligkeit", value) { value = it }
            }
        },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    val c = colors
    Column {
        Text(label, style = T.small, color = c.sub)
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
        )
    }
}

/** Hilfsfunktion: Compose-Farbe als gespeicherter ARGB-Wert. */
fun Color.toStoredArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL
