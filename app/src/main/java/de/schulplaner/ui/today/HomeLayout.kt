package de.schulplaner.ui.today

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.LocalTileEdit
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import kotlin.math.sin

/**
 * Kacheln der Startseite.
 * [phoneMin]: kleinste Breite auf dem Handy (1 = halbe Breite, 2 = ganze Breite).
 * [stretch]: darf in einer Reihe auf die Höhe der Nachbarn gezogen werden (keine wischbaren Seiten darin).
 */
enum class HomeTile(
    val key: String,
    val title: String,
    val phoneMin: Int,
    val phoneSpan: Int,
    val tabletSpan: Int,
    val stretch: Boolean,
) {
    DAILY("daily", "Tagesaufgaben", 1, 2, 3, true),
    FERIEN("ferien", "Ferien-Countdown", 1, 2, 3, true),
    NOW("now", "Jetzt", 1, 2, 2, true),
    TASKS("tasks", "Aufgaben", 1, 1, 1, true),
    EXAMS("exams", "Klausuren", 1, 1, 1, true),
    LESSONS("lessons", "Stunden", 2, 2, 1, false),
    STUDY("study", "Lernzeit", 2, 2, 1, false),
    MONSTER("monster", "Monster", 2, 2, 1, false),
    APPS("apps", "Apps", 2, 2, 1, false),
    GRADES("grades", "Noten", 1, 2, 1, true),
    ;

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

/**
 * Raster der Startseite: Kacheln werden der Reihe nach in Zeilen gelegt (Handy 2, Tablet 3 Spalten).
 * Bleibt in einer Zeile Platz übrig, teilen sich die Kacheln ihn – so entstehen keine Lücken.
 */
@Composable
fun HomeGrid(
    cols: Int,
    gap: Dp,
    editable: Boolean,
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

    val shown = layout.filter { it.visible && available(it.tile) }
    val rows = remember(shown, cols) {
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
        out
    }
    val enterEdit: (() -> Unit)? = if (editable) ({ HomeLayoutStore.editing = true }) else null

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (editing) {
            EditBar(
                onReset = {
                    HomeLayoutStore.reset(ctx, cols)
                    layout = HomeLayoutStore.defaults(cols)
                },
                onDone = { HomeLayoutStore.editing = false },
            )
        }
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
                        if (!editing) {
                            Box(outer) {
                                CompositionLocalProvider(LocalTileEdit provides enterEdit) { tile(conf.tile, s, inner) }
                            }
                        } else {
                            val index = layout.indexOf(conf)
                            val visibleIdx = shown.indexOf(conf)
                            val sizes = (HomeLayoutStore.minSpan(conf.tile, cols)..cols).toList()
                            EditFrame(
                                conf = conf,
                                seed = visibleIdx,
                                modifier = outer,
                                canEarlier = visibleIdx > 0,
                                canLater = visibleIdx < shown.lastIndex,
                                sizeLabel = sizeLabel(s, cols),
                                canResize = sizes.size > 1,
                                onEarlier = {
                                    val other = shown[visibleIdx - 1]
                                    update(layout.toMutableList().also { l -> l.swap(index, l.indexOf(other)) })
                                },
                                onLater = {
                                    val other = shown[visibleIdx + 1]
                                    update(layout.toMutableList().also { l -> l.swap(index, l.indexOf(other)) })
                                },
                                onResize = {
                                    val next = sizes[(sizes.indexOf(s) + 1) % sizes.size]
                                    update(layout.map { if (it.tile == conf.tile) it.copy(span = next) else it })
                                },
                                onHide = { update(layout.map { if (it.tile == conf.tile) it.copy(visible = false) else it }) },
                            ) { tile(conf.tile, s, inner) }
                        }
                    }
                }
            }
        }
        if (editing) {
            val hidden = layout.filter { !it.visible && available(it.tile) }
            HiddenTiles(hidden) { t -> update(layout.map { if (it.tile == t) it.copy(visible = true) else it }) }
        }
    }
}

private fun <T> MutableList<T>.swap(a: Int, b: Int) {
    if (a !in indices || b !in indices) return
    val x = this[a]
    this[a] = this[b]
    this[b] = x
}

private fun sizeLabel(span: Int, cols: Int): String = when {
    span >= cols -> "Ganz"
    cols == 2 -> "½"
    span == 1 -> "⅓"
    else -> "⅔"
}

@Composable
private fun EditBar(onReset: () -> Unit, onDone: () -> Unit) {
    val c = colors
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Grid, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Startseite anpassen", style = T.section, color = c.text)
                Text("Verschieben, Größe ändern, ausblenden", style = T.small, color = c.sub)
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

/** Kachel im Bearbeiten-Modus: wackelt leicht, gestrichelter Rahmen, Knöpfe oben rechts; Inhalt ist gesperrt. */
@Composable
private fun EditFrame(
    conf: TileConf,
    seed: Int,
    modifier: Modifier,
    canEarlier: Boolean,
    canLater: Boolean,
    sizeLabel: String,
    canResize: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onResize: () -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = colors
    val t = rememberInfiniteTransition(label = "wiggle")
    val w by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "w")
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.graphicsLayer { rotationZ = sin(w + seed * 1.7f) * 0.45f }) {
        Box(Modifier.alpha(0.82f)) { content() }
        // Sperre + gestrichelter Rahmen.
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(c.bg.copy(alpha = 0.18f))
                .pointerInput(Unit) { detectTapGestures { } }
                .drawWithContent {
                    drawContent()
                    val px = 2.dp.toPx()
                    drawRoundRect(
                        c.accent.copy(alpha = 0.75f),
                        topLeft = androidx.compose.ui.geometry.Offset(px / 2, px / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - px, size.height - px),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        style = Stroke(px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                    )
                },
        )
        Text(
            conf.tile.title,
            style = T.smallBold,
            color = c.accentInk,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(c.sheetGlass)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditButton(Ic.ChevronLeft, "Nach vorne", canEarlier, onEarlier)
            EditButton(Ic.ChevronRight, "Nach hinten", canLater, onLater)
            if (canResize) {
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(c.accentSoft).clickable(onClick = onResize).padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Ic.Expand, "Größe ändern", tint = c.accent, modifier = Modifier.size(14.dp))
                        Text(sizeLabel, style = T.smallBold, color = c.accent)
                    }
                }
            }
            EditButton(Ic.EyeOff, "Ausblenden", true, onHide, tint = c.warn)
        }
    }
}

@Composable
private fun EditButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit, tint: Color = colors.text) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.25f), modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun HiddenTiles(hidden: List<TileConf>, onShow: (HomeTile) -> Unit) {
    val c = colors
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
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
