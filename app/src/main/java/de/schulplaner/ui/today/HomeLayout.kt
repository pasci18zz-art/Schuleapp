package de.schulplaner.ui.today

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalTileEdit
import de.schulplaner.ui.components.PrimaryButton
import de.schulplaner.ui.components.Sym
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Kacheln der Startseite.
 * [phoneMin]: kleinste Breite auf dem Handy (1 = halbe Breite, 2 = ganze Breite).
 * [stretch]: darf neben gleich hohen Nachbarn auf deren Höhe gezogen werden (keine wischbaren Seiten darin).
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

/** [span] = Breite in Spalten, [rows] = Höhe in Zeilen (0 = automatisch, so hoch wie der Inhalt). */
data class TileConf(val tile: HomeTile, val span: Int, val visible: Boolean = true, val rows: Int = 0)

/** Aufbau der Startseite – pro Gerät und Spaltenzahl gespeichert (Handy und Tablet unabhängig). */
object HomeLayoutStore {
    /** true = Anpassen-Modus der Startseite ist offen. */
    var editing by mutableStateOf(false)

    /** Die gerade ausgewählte Kachel (Rahmen, Ziehgriff, Werkzeugleiste). */
    var selected by mutableStateOf<HomeTile?>(null)

    /** Höhe einer Zeile im Raster. */
    const val UNIT_DP = 56
    const val MAX_ROWS = 16

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
        val p = prefs(ctx)
        // Version 2: neues Raster mit Höhe pro Kachel. Alte Anordnungen passen dazu nicht mehr und starten neu.
        if (p.getInt("version", 1) < 2) {
            p.edit().remove("cols_2").remove("cols_3").putInt("version", 2).apply()
        }
        val raw = p.getString("cols_$cols", null) ?: return defaults(cols)
        val parsed = raw.split(',').mapNotNull { part ->
            val bits = part.split(':')
            val tile = HomeTile.of(bits.getOrNull(0) ?: return@mapNotNull null) ?: return@mapNotNull null
            TileConf(
                tile,
                bits.getOrNull(1)?.toIntOrNull() ?: tile.phoneSpan,
                bits.getOrNull(2) != "0",
                (bits.getOrNull(3)?.toIntOrNull() ?: 0).coerceIn(0, MAX_ROWS),
            )
        }.distinctBy { it.tile }
        // Neue Kacheln (aus späteren Versionen) hinten anhängen.
        val missing = defaults(cols).filter { d -> parsed.none { it.tile == d.tile } }
        return parsed + missing
    }

    fun save(ctx: Context, cols: Int, list: List<TileConf>) {
        prefs(ctx).edit()
            .putString("cols_$cols", list.joinToString(",") { "${it.tile.key}:${it.span}:${if (it.visible) 1 else 0}:${it.rows}" })
            .apply()
    }

    fun reset(ctx: Context, cols: Int) {
        prefs(ctx).edit().remove("cols_$cols").apply()
    }

    fun minSpan(tile: HomeTile, cols: Int) = if (cols <= 2) tile.phoneMin else 1
}

private fun sizeName(span: Int, cols: Int): String = when {
    span >= cols -> "Ganze Breite"
    cols <= 2 -> "Halbe Breite"
    span == 1 -> "Ein Drittel"
    else -> "Zwei Drittel"
}

/** Eine Kachel für die Ablage: Breite in Spalten, Höhe in Zeilen (0 = automatisch). */
private class FlowItem(val tile: HomeTile, val span: Int, val rows: Int, val stretch: Boolean)

/** Wo die Kacheln gerade liegen (Pixel, im Koordinatensystem des Rasters) – wird beim Berühren gelesen. */
private class FlowRefs {
    var container: LayoutCoordinates? = null
    val rects = HashMap<HomeTile, IntRect>()
    var colW = 0
    var gap = 0
    var unit = 0
}

/**
 * Legt die Kacheln spaltenweise von oben nach unten ab: Jede kommt dorthin, wo sie am weitesten oben Platz hat
 * (so entstehen keine großen Lücken unter niedrigen Kacheln). Automatisch hohe Kacheln, die auf gleicher Höhe
 * nebeneinander liegen, werden gleich hoch gezogen.
 */
@Composable
private fun FlowLayout(
    modifier: Modifier,
    cols: Int,
    gapPx: Int,
    unitPx: Int,
    items: List<FlowItem>,
    selectedIndex: Int,
    dragIndex: Int,
    dragTopLeft: () -> Offset,
    refs: FlowRefs,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val colW = ((width - gapPx * (cols - 1)) / cols).coerceAtLeast(1)
        refs.colW = colW
        refs.gap = gapPx
        refs.unit = unitPx
        val n = items.size
        val widths = IntArray(n) { i -> items[i].span * colW + (items[i].span - 1) * gapPx }
        val heights = IntArray(n)
        val placeables = arrayOfNulls<Placeable>(n)
        val equalizable = BooleanArray(n)

        // 1) Höhen bestimmen.
        for (i in 0 until n) {
            val item = items[i]
            val natural = Constraints(widths[i], widths[i], 0, Constraints.Infinity)
            if (item.rows > 0) {
                heights[i] = item.rows * unitPx + (item.rows - 1) * gapPx
            } else if (!item.stretch) {
                val p = measurables[i].measure(natural)
                placeables[i] = p
                heights[i] = p.height
            } else {
                val h = try {
                    measurables[i].minIntrinsicHeight(widths[i])
                } catch (e: Exception) {
                    -1
                }
                if (h >= 0) {
                    heights[i] = h
                    equalizable[i] = true
                } else {
                    val p = measurables[i].measure(natural)
                    placeables[i] = p
                    heights[i] = p.height
                }
            }
        }

        // 2) Ablegen.
        val colTop = IntArray(cols)
        val xs = IntArray(n)
        val ys = IntArray(n)
        val startCol = IntArray(n)
        for (i in 0 until n) {
            val s = items[i].span.coerceIn(1, cols)
            var bestC = 0
            var bestY = Int.MAX_VALUE
            for (c in 0..cols - s) {
                var y = 0
                for (k in c until c + s) y = max(y, colTop[k])
                if (y < bestY) {
                    bestY = y
                    bestC = c
                }
            }
            if (equalizable[i]) {
                // Kacheln auf gleicher Höhe, unter denen noch nichts liegt, gleich hoch machen.
                val mates = (0 until i).filter { j ->
                    equalizable[j] && ys[j] == bestY && (startCol[j] until startCol[j] + items[j].span.coerceIn(1, cols)).all { k ->
                        colTop[k] == ys[j] + heights[j] + gapPx
                    }
                }
                if (mates.isNotEmpty()) {
                    val h = max(heights[i], mates.maxOf { heights[it] })
                    heights[i] = h
                    for (j in mates) {
                        heights[j] = h
                        for (k in startCol[j] until startCol[j] + items[j].span.coerceIn(1, cols)) colTop[k] = ys[j] + h + gapPx
                    }
                }
            }
            xs[i] = bestC * (colW + gapPx)
            ys[i] = bestY
            startCol[i] = bestC
            for (k in bestC until bestC + s) colTop[k] = bestY + heights[i] + gapPx
        }

        // 3) Endgültig messen.
        for (i in 0 until n) {
            if (placeables[i] == null) placeables[i] = measurables[i].measure(Constraints.fixed(widths[i], heights[i].coerceAtLeast(0)))
        }
        val total = if (n == 0) 0 else (colTop.max() - gapPx).coerceAtLeast(0)
        val decor = if (selectedIndex in 0 until n && measurables.size > n) {
            val p = placeables[selectedIndex]!!
            measurables[n].measure(Constraints.fixed(p.width, p.height))
        } else {
            null
        }

        layout(width, total) {
            for (i in 0 until n) {
                val p = placeables[i]!!
                refs.rects[items[i].tile] = IntRect(xs[i], ys[i], xs[i] + p.width, ys[i] + p.height)
                if (i == dragIndex) {
                    val o = dragTopLeft()
                    p.place(o.x.roundToInt(), o.y.roundToInt(), 2f)
                } else {
                    p.place(xs[i], ys[i])
                }
            }
            if (decor != null) {
                if (selectedIndex == dragIndex) {
                    val o = dragTopLeft()
                    decor.place(o.x.roundToInt(), o.y.roundToInt(), 3f)
                } else {
                    decor.place(xs[selectedIndex], ys[selectedIndex], 1f)
                }
            }
        }
    }
}

/** Zustand und Berührungs-Logik des Anpassen-Modus (Verschieben, Größe ziehen). Wird bei jeder Komposition aktualisiert. */
private class GridCtl {
    var layout: List<TileConf> = emptyList()
    var shown: List<TileConf> = emptyList()
    var cols = 2
    var swapPx = 100f
    var handlePx = 150
    var onChange: (List<TileConf>) -> Unit = {}
    val refs = FlowRefs()

    var dragging by mutableStateOf<HomeTile?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    private var lastSwap: Offset? = null
    private var resizeGrab = Offset.Zero

    private fun Offset.toInt() = IntOffset(x.roundToInt(), y.roundToInt())

    /** Berührungsfläche des Ziehgriffs unten rechts an der ausgewählten Kachel. */
    fun handleRect(): IntRect? {
        if (dragging != null) return null
        val s = HomeLayoutStore.selected ?: return null
        if (shown.none { it.tile == s }) return null
        val r = refs.rects[s] ?: return null
        return IntRect(r.right - handlePx, r.bottom - handlePx, r.right + handlePx / 5, r.bottom + handlePx / 5)
    }

    fun beginMove(at: Offset): Boolean {
        val hit = shown.firstOrNull { refs.rects[it.tile]?.contains(at.toInt()) == true } ?: return false
        val r = refs.rects[hit.tile] ?: return false
        HomeLayoutStore.selected = hit.tile
        grab = Offset(at.x - r.left, at.y - r.top)
        pointer = at
        lastSwap = null
        dragging = hit.tile
        return true
    }

    fun moveTo(p: Offset) {
        pointer = p
        maybeSwap()
    }

    fun endGesture() {
        dragging = null
        lastSwap = null
    }

    /**
     * Liegt der Finger über einer anderen Kachel, tauscht die gezogene den Platz mit ihr.
     * Danach muss der Finger erst ein Stück wandern, sonst springen die Kacheln hin und her.
     */
    fun maybeSwap() {
        val d = dragging ?: return
        val last = lastSwap
        if (last != null && (pointer - last).getDistance() < swapPx) return
        val p = pointer.toInt()
        val target = shown.firstOrNull { it.tile != d && refs.rects[it.tile]?.contains(p) == true } ?: return
        val list = layout.toMutableList()
        val from = list.indexOfFirst { it.tile == d }
        val to = list.indexOfFirst { it.tile == target.tile }
        if (from < 0 || to < 0) return
        val item = list.removeAt(from)
        list.add(to, item)
        lastSwap = pointer
        onChange(list)
    }

    fun beginResize(at: Offset) {
        val s = HomeLayoutStore.selected ?: return
        val r = refs.rects[s] ?: return
        resizeGrab = Offset(r.right - at.x, r.bottom - at.y)
    }

    /** Ecke ziehen: Breite rastet auf Spalten, Höhe auf Zeilen ein. */
    fun resizeTo(p: Offset) {
        val s = HomeLayoutStore.selected ?: return
        val r = refs.rects[s] ?: return
        val conf = layout.firstOrNull { it.tile == s } ?: return
        val colW = refs.colW
        val gap = refs.gap
        val unit = refs.unit
        if (colW <= 0 || unit <= 0) return
        val wantW = p.x + resizeGrab.x - r.left
        val wantH = p.y + resizeGrab.y - r.top
        val min = HomeLayoutStore.minSpan(s, cols)
        val startCol = ((r.left + (colW + gap) / 2) / (colW + gap)).coerceIn(0, cols - 1)
        val maxSpan = (cols - startCol).coerceAtLeast(min)
        val span = ((wantW + gap).toFloat() / (colW + gap)).roundToInt().coerceIn(min, maxSpan)
        val rows = if (conf.rows == 0 && abs(wantH - r.height) < (unit + gap) * 0.6f) {
            // Nur in die Breite gezogen: Höhe bleibt automatisch.
            0
        } else {
            ((wantH + gap).toFloat() / (unit + gap)).roundToInt().coerceIn(1, HomeLayoutStore.MAX_ROWS)
        }
        val curSpan = conf.span.coerceIn(min, cols)
        if (span != curSpan || rows != conf.rows) {
            onChange(layout.map { if (it.tile == s) it.copy(span = span, rows = rows) else it })
        }
    }

    /** Ungefähre Zeilenzahl der Kachel (für "Höhe ändern" aus automatisch). */
    fun naturalRows(tile: HomeTile): Int {
        val r = refs.rects[tile] ?: return 3
        val unit = refs.unit
        if (unit <= 0) return 3
        return ((r.height + refs.gap).toFloat() / (unit + refs.gap)).roundToInt().coerceIn(1, HomeLayoutStore.MAX_ROWS)
    }
}

/**
 * Raster der Startseite. Normal liegen die Kacheln spaltenweise (Handy 2, Tablet 3 Spalten) mit ihrer Breite
 * und Höhe da. Im Anpassen-Modus (lang auf eine Kachel drücken) bleiben sie sichtbar:
 * Antippen wählt eine Kachel aus (Rahmen), an der Ecke unten rechts zieht man Breite und Höhe wie bei Samsung-Widgets,
 * gedrückt halten und ziehen verschiebt, das Zahnrad öffnet die genauen Einstellungen.
 */
@Composable
fun HomeGrid(
    cols: Int,
    gap: Dp,
    editable: Boolean,
    scroll: ScrollState,
    viewportPx: Float,
    available: (HomeTile) -> Boolean,
    tile: @Composable (tile: HomeTile, span: Int, modifier: Modifier, height: Dp?) -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val c = colors
    var layout by remember(cols) { mutableStateOf(HomeLayoutStore.load(ctx, cols)) }
    val editing = HomeLayoutStore.editing && editable
    fun update(list: List<TileConf>) {
        layout = list
        HomeLayoutStore.save(ctx, cols, list)
    }
    fun spanOf(t: TileConf) = t.span.coerceIn(HomeLayoutStore.minSpan(t.tile, cols), cols)

    val shown = layout.filter { it.visible && available(it.tile) }
    val items = shown.map { FlowItem(it.tile, spanOf(it), it.rows, it.tile.stretch) }
    val gapPx = with(density) { gap.roundToPx() }
    val unitPx = with(density) { HomeLayoutStore.UNIT_DP.dp.roundToPx() }
    val selected = if (editing) HomeLayoutStore.selected?.takeIf { s -> shown.any { it.tile == s } } else null
    val ctl = remember { GridCtl() }
    ctl.layout = layout
    ctl.shown = shown
    ctl.cols = cols
    ctl.swapPx = with(density) { 36.dp.toPx() }
    ctl.handlePx = with(density) { 56.dp.roundToPx() }
    ctl.onChange = { update(it) }
    var settings by remember { mutableStateOf<HomeTile?>(null) }

    // Am oberen/unteren Rand des Bildschirms scrollt die Seite beim Ziehen von selbst weiter.
    LaunchedEffect(ctl.dragging != null) {
        if (ctl.dragging == null) return@LaunchedEffect
        val zone = with(density) { 120.dp.toPx() }
        val topLimit = with(density) { 96.dp.toPx() } + zone
        val bottomLimit = viewportPx - with(density) { 120.dp.toPx() } - zone
        while (true) {
            withFrameNanos { }
            val cc = ctl.refs.container
            if (cc == null || !cc.isAttached) continue
            val y = cc.localToRoot(ctl.pointer).y
            val delta = when {
                y < topLimit -> -((topLimit - y) / zone).coerceIn(0f, 1f) * 20f
                y > bottomLimit -> ((y - bottomLimit) / zone).coerceIn(0f, 1f) * 20f
                else -> 0f
            }
            if (delta != 0f) {
                val used = scroll.scrollBy(delta)
                if (used != 0f) {
                    // Der Inhalt ist gewandert, der Finger nicht: Position im Raster mitziehen.
                    ctl.pointer = ctl.pointer + Offset(0f, used)
                    ctl.maybeSwap()
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (editing) {
            EditBar(
                onReset = {
                    HomeLayoutStore.reset(ctx, cols)
                    layout = HomeLayoutStore.defaults(cols)
                },
                onDone = {
                    HomeLayoutStore.editing = false
                    HomeLayoutStore.selected = null
                },
            )
        }

        FlowLayout(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ctl.refs.container = it }
                .then(
                    if (editing) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val handle = ctl.handleRect()
                                if (handle != null && handle.contains(IntOffset(down.position.x.roundToInt(), down.position.y.roundToInt()))) {
                                    // Ecke ziehen: Größe ändern.
                                    down.consume()
                                    ctl.beginResize(down.position)
                                    drag(down.id) { change ->
                                        change.consume()
                                        ctl.resizeTo(change.position)
                                    }
                                } else {
                                    // Gedrückt halten, dann ziehen: Kachel verschieben.
                                    val press = awaitLongPressOrCancellation(down.id)
                                    if (press != null && ctl.beginMove(press.position)) {
                                        drag(press.id) { change ->
                                            change.consume()
                                            ctl.moveTo(change.position)
                                        }
                                        ctl.endGesture()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            cols = cols,
            gapPx = gapPx,
            unitPx = unitPx,
            items = items,
            selectedIndex = if (selected != null) shown.indexOfFirst { it.tile == selected } else -1,
            dragIndex = ctl.dragging?.let { d -> shown.indexOfFirst { it.tile == d } } ?: -1,
            dragTopLeft = { Offset(ctl.pointer.x - ctl.grab.x, ctl.pointer.y - ctl.grab.y) },
            refs = ctl.refs,
        ) {
            shown.forEach { conf ->
                key(conf.tile) {
                    val s = spanOf(conf)
                    val hDp = if (conf.rows > 0) (HomeLayoutStore.UNIT_DP * conf.rows + gap.value * (conf.rows - 1)).dp else null
                    val enterEdit: (() -> Unit)? = if (editable && !editing) {
                        {
                            HomeLayoutStore.selected = conf.tile
                            HomeLayoutStore.editing = true
                        }
                    } else {
                        null
                    }
                    Box(
                        Modifier.graphicsLayer {
                            // Die gezogene Kachel hebt sich leicht ab.
                            val lift = ctl.dragging == conf.tile
                            scaleX = if (lift) 1.03f else 1f
                            scaleY = if (lift) 1.03f else 1f
                            shadowElevation = if (lift) 36f else 0f
                            shape = RoundedCornerShape(22.dp)
                        },
                    ) {
                        CompositionLocalProvider(LocalTileEdit provides enterEdit) {
                            tile(conf.tile, s, Modifier.fillMaxSize(), hDp)
                        }
                        if (editing) {
                            // Fängt alle Berührungen ab, damit die Kachel selbst nichts auslöst; Antippen wählt aus.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(c.bg.copy(alpha = 0.10f))
                                    .border(1.5.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                                    .semantics { contentDescription = "Kachel ${conf.tile.title}" }
                                    .pointerInput(conf.tile) {
                                        detectTapGestures(onTap = {
                                            HomeLayoutStore.selected = if (HomeLayoutStore.selected == conf.tile) null else conf.tile
                                        })
                                    },
                            )
                        }
                    }
                }
            }
            if (selected != null) {
                key("auswahl") {
                    SelectionDecor(
                        onSettings = { settings = selected },
                        onHide = {
                            update(layout.map { if (it.tile == selected) it.copy(visible = false) else it })
                            HomeLayoutStore.selected = null
                        },
                    )
                }
            }
        }

        if (editing) {
            val hidden = layout.filter { !it.visible && available(it.tile) }
            HiddenTiles(hidden) { t ->
                update(layout.map { if (it.tile == t) it.copy(visible = true) else it })
                HomeLayoutStore.selected = t
            }
        }
    }

    settings?.let { t ->
        val conf = layout.firstOrNull { it.tile == t }
        if (conf != null && editing) {
            TileSettingsSheet(
                conf = conf,
                cols = cols,
                naturalRows = ctl.naturalRows(t),
                onSpan = { sp -> update(layout.map { if (it.tile == t) it.copy(span = sp) else it }) },
                onRows = { rw -> update(layout.map { if (it.tile == t) it.copy(rows = rw) else it }) },
                onHide = {
                    update(layout.map { if (it.tile == t) it.copy(visible = false) else it })
                    HomeLayoutStore.selected = null
                    settings = null
                },
                onDismiss = { settings = null },
            )
        }
    }
}

/** Rahmen, Werkzeugleiste und Ziehgriff der ausgewählten Kachel (so groß wie die Kachel selbst). */
@Composable
private fun SelectionDecor(onSettings: () -> Unit, onHide: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().border(2.5.dp, c.accent, shape))
        // Werkzeuge unten links, Ziehgriff unten rechts: Sie überdecken sich auch bei flachen Kacheln nicht.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(c.sheetGlass)
                .border(1.dp, c.cardBorder, RoundedCornerShape(50))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(Ic.Settings, "Einstellungen", c.text, onSettings)
            ToolButton(Ic.EyeOff, "Ausblenden", c.warn, onHide)
        }
        // Griff unten rechts: ziehen ändert Breite und Höhe.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(42.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(c.accent)
                .semantics { contentDescription = "Größe ändern" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Expand, null, tint = c.accentInk, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(19.dp))
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
                Text(
                    "Ecke ziehen = Größe · Halten und ziehen = verschieben",
                    style = T.small,
                    color = c.sub,
                )
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

/** Genaue Einstellungen einer Kachel: Breite, Höhe, ausblenden. */
@Composable
private fun TileSettingsSheet(
    conf: TileConf,
    cols: Int,
    naturalRows: Int,
    onSpan: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = colors
    val tile = conf.tile
    val min = HomeLayoutStore.minSpan(tile, cols)
    val span = conf.span.coerceIn(min, cols)
    val rows = conf.rows
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

        Text("Breite", style = T.label, color = c.sub)
        if (min >= cols) {
            Text("Diese Kachel braucht auf diesem Gerät immer die ganze Breite.", style = T.small, color = c.sub)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (min..cols).forEach { s ->
                    SizeChoice(s, cols, selected = s == span, modifier = Modifier.weight(1f)) { onSpan(s) }
                }
            }
        }

        Text("Höhe", style = T.label, color = c.sub)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val auto = rows == 0
            Text(
                "Automatisch",
                style = T.label,
                color = if (auto) c.accent else c.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (auto) c.accentSoft else c.line)
                    .border(2.dp, if (auto) c.accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onRows(0) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            val cur = if (rows > 0) rows else naturalRows
            Stepper(Ic.Minus, "Niedriger", enabled = cur > 1) { onRows((cur - 1).coerceAtLeast(1)) }
            Text(
                if (auto) "wie der Inhalt" else "$rows ${if (rows == 1) "Zeile" else "Zeilen"}",
                style = T.body,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Stepper(Ic.Plus, "Höher", enabled = cur < HomeLayoutStore.MAX_ROWS) { onRows((cur + 1).coerceAtMost(HomeLayoutStore.MAX_ROWS)) }
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
private fun Stepper(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val c = colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) c.accent else c.sub.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
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
