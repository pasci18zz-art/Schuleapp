package de.schulplaner.ui.today

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalTileEdit
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import kotlin.math.roundToInt

/**
 * Kacheln der Startseite.
 * [phoneMin]: kleinste Breite auf dem Handy (1 = halbe Breite, 2 = ganze Breite).
 * [stretch]: darf in einer Reihe auf die Höhe der Nachbarn gezogen werden (keine wischbaren Seiten darin).
 */
enum class HomeTile(
    val key: String,
    val title: String,
    val about: String,
    val phoneMin: Int,
    val phoneSpan: Int,
    val tabletSpan: Int,
    val stretch: Boolean,
) {
    DAILY("daily", "Tagesaufgaben", "Was du heute schaffen willst – angepinnte Aufgaben und Unterpunkte", 1, 2, 3, true),
    FERIEN("ferien", "Ferien-Countdown", "Wie lange es noch bis zu den nächsten Ferien dauert", 1, 2, 3, true),
    NOW("now", "Jetzt", "Die aktuelle oder nächste Stunde", 1, 2, 2, true),
    TASKS("tasks", "Aufgaben", "Offene Aufgaben mit Fälligkeit", 1, 1, 1, true),
    EXAMS("exams", "Klausuren", "Die nächsten Klausuren und dein Lernfortschritt", 1, 1, 1, true),
    LESSONS("lessons", "Stunden", "Stundenplan des Tages – wischen für den nächsten Tag", 2, 2, 1, false),
    STUDY("study", "Lernzeit", "Lernuhr mit Tages- und Wochenübersicht", 2, 2, 1, false),
    MONSTER("monster", "Monster", "Monster-Zähler mit Wochenübersicht", 2, 2, 1, false),
    APPS("apps", "Apps", "Schnellzugriff auf Notizen, Claude und Spotify", 2, 2, 1, false),
    GRADES("grades", "Noten", "Notenschnitt und beste Fächer", 1, 2, 1, true),
    ;

    val icon: ImageVector
        get() = when (this) {
            DAILY -> Ic.Pushpin
            FERIEN -> Ic.Palm
            NOW -> Ic.Clock
            TASKS -> Ic.Tasks
            EXAMS -> Sym.Pen
            LESSONS -> Ic.Calendar
            STUDY -> Ic.Hourglass
            MONSTER -> Sym.Star
            APPS -> Ic.Grid
            GRADES -> Ic.Grades
        }

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class TileConf(val tile: HomeTile, val span: Int, val visible: Boolean = true)

/** Aufbau der Startseite – pro Gerät und Spaltenzahl gespeichert (Handy und Tablet unabhängig). */
object HomeLayoutStore {
    /** true = Bearbeiten-Modus der Startseite ist offen. */
    var editing by mutableStateOf(false)

    private val tabletOrder = listOf(
        HomeTile.DAILY, HomeTile.FERIEN, HomeTile.NOW, HomeTile.EXAMS, HomeTile.TASKS,
        HomeTile.LESSONS, HomeTile.STUDY, HomeTile.MONSTER, HomeTile.GRADES, HomeTile.APPS,
    )

    fun defaults(cols: Int): List<TileConf> =
        if (cols >= 3) {
            tabletOrder.map { TileConf(it, it.tabletSpan) }
        } else {
            HomeTile.entries.map { TileConf(it, it.phoneSpan) }
        }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("startseite", Context.MODE_PRIVATE)

    fun load(ctx: Context, cols: Int): List<TileConf> {
        val raw = prefs(ctx).getString("cols_$cols", null) ?: return defaults(cols)
        val parsed = raw.split(',').mapNotNull { part ->
            val bits = part.split(':')
            val tile = HomeTile.of(bits.getOrNull(0) ?: return@mapNotNull null) ?: return@mapNotNull null
            TileConf(tile, bits.getOrNull(1)?.toIntOrNull() ?: tile.phoneSpan, bits.getOrNull(2) != "0")
        }.distinctBy { it.tile }
        // Neue Kacheln (aus späteren Versionen) hinten anhängen.
        val missing = defaults(cols).filter { d -> parsed.none { it.tile == d.tile } }
        return parsed + missing
    }

    fun save(ctx: Context, cols: Int, list: List<TileConf>) {
        prefs(ctx).edit()
            .putString("cols_$cols", list.joinToString(",") { "${it.tile.key}:${it.span}:${if (it.visible) 1 else 0}" })
            .apply()
    }

    fun reset(ctx: Context, cols: Int) {
        prefs(ctx).edit().remove("cols_$cols").apply()
    }

    fun minSpan(tile: HomeTile, cols: Int) = if (cols <= 2) tile.phoneMin else 1
}

/** Teilt die sichtbaren Kacheln der Reihe nach in Zeilen auf (Handy 2, Tablet 3 Spalten). */
private fun packRows(shown: List<TileConf>, cols: Int, span: (TileConf) -> Int): List<List<TileConf>> {
    val out = mutableListOf<MutableList<TileConf>>()
    var used = cols
    shown.forEach { t ->
        val s = span(t)
        if (used + s > cols) {
            out += mutableListOf(t)
            used = s
        } else {
            out.last() += t
            used += s
        }
    }
    return out
}

private fun sizeName(span: Int, cols: Int): String = when {
    span >= cols -> "Ganze Breite"
    cols <= 2 -> "Halbe Breite"
    span == 1 -> "Ein Drittel"
    else -> "Zwei Drittel"
}

private fun sizeShort(span: Int, cols: Int): String = when {
    span >= cols -> "Ganz"
    cols <= 2 -> "½"
    span == 1 -> "⅓"
    else -> "⅔"
}

/**
 * Raster der Startseite. Normal werden die Kacheln der Reihe nach in Zeilen gelegt; bleibt in einer Zeile
 * Platz übrig, teilen sich die Kacheln ihn – so entstehen keine Lücken.
 * Im Anpassen-Modus zeigt es stattdessen eine kompakte Übersicht: gedrückt halten und ziehen verschiebt,
 * Antippen öffnet die Einstellungen (Größe, ausblenden).
 */
@Composable
fun HomeGrid(
    cols: Int,
    gap: Dp,
    editable: Boolean,
    scroll: ScrollState,
    viewportPx: Float,
    available: (HomeTile) -> Boolean,
    tile: @Composable (tile: HomeTile, span: Int, modifier: Modifier) -> Unit,
) {
    val ctx = LocalContext.current
    var layout by remember(cols) { mutableStateOf(HomeLayoutStore.load(ctx, cols)) }
    val editing = HomeLayoutStore.editing && editable
    fun update(list: List<TileConf>) {
        layout = list
        HomeLayoutStore.save(ctx, cols, list)
    }
    fun span(t: TileConf) = t.span.coerceIn(HomeLayoutStore.minSpan(t.tile, cols), cols)

    if (editing) {
        HomeEditor(
            cols = cols,
            gap = gap,
            layout = layout,
            available = available,
            scroll = scroll,
            viewportPx = viewportPx,
            onChange = { update(it) },
            onReset = {
                HomeLayoutStore.reset(ctx, cols)
                layout = HomeLayoutStore.defaults(cols)
            },
            onDone = { HomeLayoutStore.editing = false },
        )
        return
    }

    val shown = layout.filter { it.visible && available(it.tile) }
    val rows = packRows(shown, cols) { span(it) }
    val enterEdit: (() -> Unit)? = if (editable) ({ HomeLayoutStore.editing = true }) else null

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            val stretch = row.size > 1 && row.all { it.tile.stretch }
            Row(
                Modifier.fillMaxWidth().then(if (stretch) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { conf ->
                    key(conf.tile) {
                        val s = span(conf)
                        val outer = Modifier.weight(s.toFloat()).then(if (stretch) Modifier.fillMaxHeight() else Modifier)
                        val inner = if (stretch) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                        Box(outer) {
                            CompositionLocalProvider(LocalTileEdit provides enterEdit) { tile(conf.tile, s, inner) }
                        }
                    }
                }
            }
        }
    }
}

/** Referenzen auf die Layout-Knoten, die nur bei Berührungen gelesen werden (kein Neuaufbau nötig). */
private class EditorRefs {
    var container: LayoutCoordinates? = null
    val slots = HashMap<HomeTile, LayoutCoordinates>()

    /** Rechteck einer Kachel im Koordinatensystem des Containers (bleibt beim Scrollen gleich). */
    fun boundsOf(tile: HomeTile): Rect? {
        val cc = container ?: return null
        val sc = slots[tile] ?: return null
        if (!cc.isAttached || !sc.isAttached) return null
        return cc.localBoundingBoxOf(sc, false)
    }
}

@Composable
private fun HomeEditor(
    cols: Int,
    gap: Dp,
    layout: List<TileConf>,
    available: (HomeTile) -> Boolean,
    scroll: ScrollState,
    viewportPx: Float,
    onChange: (List<TileConf>) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val refs = remember { EditorRefs() }
    val shown = layout.filter { it.visible && available(it.tile) }
    fun spanOf(t: TileConf) = t.span.coerceIn(HomeLayoutStore.minSpan(t.tile, cols), cols)
    val rows = packRows(shown, cols) { spanOf(it) }

    // Die Berührungs-Auswertung läuft lange; sie liest deshalb immer den neuesten Stand.
    val layoutNow by rememberUpdatedState(layout)
    val shownNow by rememberUpdatedState(shown)
    val onChangeNow by rememberUpdatedState(onChange)

    var dragging by remember { mutableStateOf<HomeTile?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var ghostSize by remember { mutableStateOf(IntSize.Zero) }
    var lastSwap by remember { mutableStateOf<Offset?>(null) }
    var settings by remember { mutableStateOf<HomeTile?>(null) }
    val cardHeight = if (cols >= 3) 64.dp else 58.dp

    /**
     * Liegt der Finger über einer anderen Kachel, tauscht der gezogene Platz mit ihr.
     * Nach einem Tausch muss der Finger erst ein Stück wandern, sonst springen die Kacheln hin und her.
     */
    fun maybeSwap() {
        val d = dragging ?: return
        val last = lastSwap
        if (last != null && (pointer - last).getDistance() < with(density) { 36.dp.toPx() }) return
        val target = shownNow.firstOrNull { it.tile != d && refs.boundsOf(it.tile)?.contains(pointer) == true } ?: return
        val list = layoutNow.toMutableList()
        val from = list.indexOfFirst { it.tile == d }
        val to = list.indexOfFirst { it.tile == target.tile }
        if (from < 0 || to < 0) return
        val item = list.removeAt(from)
        list.add(to, item)
        lastSwap = pointer
        onChangeNow(list)
    }

    // Am oberen/unteren Rand des Bildschirms scrollt die Seite von selbst weiter.
    LaunchedEffect(dragging != null) {
        if (dragging == null) return@LaunchedEffect
        val zone = with(density) { 120.dp.toPx() }
        val topLimit = with(density) { 96.dp.toPx() } + zone
        val bottomLimit = viewportPx - with(density) { 120.dp.toPx() } - zone
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val cc = refs.container
            if (cc == null || !cc.isAttached) continue
            val y = cc.localToRoot(pointer).y
            val delta = when {
                y < topLimit -> -((topLimit - y) / zone).coerceIn(0f, 1f) * 20f
                y > bottomLimit -> ((y - bottomLimit) / zone).coerceIn(0f, 1f) * 20f
                else -> 0f
            }
            if (delta != 0f) {
                val used = scroll.scrollBy(delta)
                if (used != 0f) {
                    // Der Inhalt ist gewandert, der Finger nicht: Position im Container mitziehen.
                    pointer = pointer + Offset(0f, used)
                    maybeSwap()
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        EditBar(onReset = onReset, onDone = onDone)

        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { refs.container = it }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val hit = shownNow.firstOrNull { refs.boundsOf(it.tile)?.contains(start) == true }
                            val r = hit?.let { refs.boundsOf(it.tile) }
                            if (hit != null && r != null) {
                                dragging = hit.tile
                                pointer = start
                                grab = start - r.topLeft
                                ghostSize = IntSize(r.width.roundToInt(), r.height.roundToInt())
                                lastSwap = null
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragging != null) {
                                change.consume()
                                pointer = change.position
                                maybeSwap()
                            }
                        },
                        onDragEnd = {
                            dragging = null
                            lastSwap = null
                        },
                        onDragCancel = {
                            dragging = null
                            lastSwap = null
                        },
                    )
                },
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { conf ->
                            val s = spanOf(conf)
                            EditorCard(
                                conf = conf,
                                span = s,
                                cols = cols,
                                placeholder = dragging == conf.tile,
                                ghost = false,
                                modifier = Modifier
                                    .weight(s.toFloat())
                                    .height(cardHeight)
                                    .onGloballyPositioned { refs.slots[conf.tile] = it },
                                onClick = { settings = conf.tile },
                            )
                        }
                    }
                }
            }

            // Die angehobene Kachel folgt dem Finger.
            val d = dragging
            val dragged = if (d != null) layout.firstOrNull { it.tile == d } else null
            if (dragged != null) {
                EditorCard(
                    conf = dragged,
                    span = spanOf(dragged),
                    cols = cols,
                    placeholder = false,
                    ghost = true,
                    modifier = Modifier
                        .offset { IntOffset((pointer.x - grab.x).roundToInt(), (pointer.y - grab.y).roundToInt()) }
                        .size(with(density) { ghostSize.width.toDp() }, with(density) { ghostSize.height.toDp() }),
                    onClick = {},
                )
            }
        }

        val hidden = layout.filter { !it.visible && available(it.tile) }
        HiddenTiles(hidden) { t -> onChange(layout.map { if (it.tile == t) it.copy(visible = true) else it }) }
    }

    settings?.let { t ->
        val conf = layout.firstOrNull { it.tile == t }
        if (conf != null) {
            TileSettingsSheet(
                conf = conf,
                cols = cols,
                onSize = { s -> onChange(layout.map { if (it.tile == t) it.copy(span = s) else it }) },
                onHide = {
                    onChange(layout.map { if (it.tile == t) it.copy(visible = false) else it })
                    settings = null
                },
                onDismiss = { settings = null },
            )
        }
    }
}

@Composable
private fun EditBar(onReset: () -> Unit, onDone: () -> Unit) {
    val c = colors
    GlassCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Grid, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Startseite anpassen", style = T.section, color = c.text)
                Text("Gedrückt halten und ziehen = verschieben. Antippen = Größe und Einstellungen.", style = T.small, color = c.sub)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c.line).clickable(onClick = onReset),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Reset, "Zurücksetzen", tint = c.sub, modifier = Modifier.size(18.dp)) }
            Text(
                "Fertig",
                style = T.label,
                color = c.accentInk,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Kompakte Karte einer Kachel in der Übersicht. [placeholder] = hier lag sie vorher, [ghost] = wird gerade gezogen. */
@Composable
private fun EditorCard(
    conf: TileConf,
    span: Int,
    cols: Int,
    placeholder: Boolean,
    ghost: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .then(if (ghost) Modifier.shadow(16.dp, shape) else Modifier)
            .clip(shape)
            .background(if (ghost) c.sheet else c.card)
            .background(c.accent.copy(alpha = if (placeholder) 0.03f else 0.09f))
            .border(1.5.dp, c.accent.copy(alpha = if (placeholder) 0.3f else 0.55f), shape)
            .alpha(if (placeholder) 0.4f else 1f)
            .clickable(enabled = !ghost && !placeholder, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(conf.tile.icon, null, tint = c.accent, modifier = Modifier.size(19.dp))
        }
        Text(conf.tile.title, style = T.label, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            sizeShort(span, cols),
            style = T.smallBold,
            color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accentSoft).padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** Einstellungen einer Kachel: Größe wählen oder ausblenden. */
@Composable
private fun TileSettingsSheet(conf: TileConf, cols: Int, onSize: (Int) -> Unit, onHide: () -> Unit, onDismiss: () -> Unit) {
    val c = colors
    val tile = conf.tile
    val min = HomeLayoutStore.minSpan(tile, cols)
    val span = conf.span.coerceIn(min, cols)
    AppSheet(onDismiss = onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(tile.icon, null, tint = c.accent, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tile.title, style = T.title.copy(fontSize = T.section.fontSize * 1.4f), color = c.text)
                Text(tile.about, style = T.small, color = c.sub)
            }
            PrimaryButton("Fertig", { close() })
        }
        Text("Größe", style = T.label, color = c.sub)
        if (min >= cols) {
            Text("Diese Kachel braucht auf diesem Gerät immer die ganze Breite.", style = T.small, color = c.sub)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (min..cols).forEach { s ->
                    SizeChoice(s, cols, selected = s == span, modifier = Modifier.weight(1f)) { onSize(s) }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.warnSoft)
                .clickable(onClick = onHide)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Ic.EyeOff, null, tint = c.warn, modifier = Modifier.size(20.dp))
            Text("Von der Startseite ausblenden", style = T.body, color = c.warn)
        }
    }
}

@Composable
private fun SizeChoice(span: Int, cols: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accentSoft else c.line)
            .border(2.dp, if (selected) c.accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Kleine Vorschau: so viele Spalten sind belegt.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(cols) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (i < span) c.accent else c.sub.copy(alpha = 0.22f)),
                )
            }
        }
        Text(sizeName(span, cols), style = T.label, color = if (selected) c.accent else c.text, maxLines = 1)
    }
}

@Composable
private fun HiddenTiles(hidden: List<TileConf>, onShow: (HomeTile) -> Unit) {
    val c = colors
    GlassCard(padding = PaddingValues(14.dp)) {
        Text("Ausgeblendet", style = T.section, color = c.text)
        if (hidden.isEmpty()) {
            Text("Alle Kacheln sind sichtbar.", style = T.small, color = c.sub, modifier = Modifier.padding(top = 6.dp))
        } else {
            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hidden.forEach { h ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.accentSoft).clickable { onShow(h.tile) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Ic.Plus, null, tint = c.accent, modifier = Modifier.size(15.dp))
                        Text(h.tile.title, style = T.label, color = c.accent)
                    }
                }
            }
        }
    }
}
