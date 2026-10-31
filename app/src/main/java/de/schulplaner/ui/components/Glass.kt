package de.schulplaner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.schulplaner.ui.theme.LocalSchulColors
import de.schulplaner.ui.theme.SchulColors
import de.schulplaner.ui.theme.T

val colors: SchulColors
    @Composable get() = LocalSchulColors.current

fun subjectColor(argb: Long?): Color = if (argb == null) Color(0xFF7C8A9E) else Color(argb)

/** Ansichtsmodus: true = auf diesem Gerät nur anschauen, nichts ändern. */
val LocalReadOnly = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Gemeinsamer Zustand für den Weichzeichner: Der Hintergrund ist die Quelle, Glas-Kacheln zeichnen ihn verschwommen. */
val LocalHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/** Lebendiger Hintergrund mit treibenden Farbflächen; Kacheln darüber zeigen ihn weichgezeichnet. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = colors
    val haze = remember { HazeState() }
    // Langsam treibende, weiche Farbflächen und ein paar schwebende Punkte – nur im Zeichnen gelesen,
    // damit nichts neu aufgebaut werden muss.
    val t = rememberInfiniteTransition(label = "bg")
    val a = t.animateFloat(0f, 1f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = t.animateFloat(0f, 1f, infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    Box(modifier.fillMaxSize().background(c.bg)) {
        // Eigene Ebene nur für den Hintergrund (Geschwister der Inhalte), damit die Kacheln genau diese Ebene weichzeichnen.
        Box(
            Modifier
                .fillMaxSize()
                .haze(haze)
                .drawBehind {
                    drawRect(c.bg)
                    val w = size.width
                    val h = size.height
                    val av = a.value
                    val bv = b.value
                    fun orb(color: Color, x: Float, y: Float, r: Float) {
                        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
                    }
                    orb(c.orb1, w * (0.05f + 0.35f * av), h * (0.05f + 0.08f * bv), w * 0.85f)
                    orb(c.orb2, w * (1.0f - 0.3f * bv), h * (0.42f + 0.12f * av), w * 0.7f)
                    orb(c.orb3, w * (0.1f + 0.3f * bv), h * (0.92f - 0.1f * av), w * 0.8f)
                    // Schwebende Punkte, die langsam nach oben wandern.
                    val dv = drift.value
                    for (i in 0 until 14) {
                        val seed = (i * 7919) % 1000 / 1000f
                        val x = w * ((seed * 13.7f + i * 0.071f) % 1f)
                        val speed = 0.6f + (i % 4) * 0.25f
                        val y = h * (1.1f - ((dv * speed + seed) % 1f) * 1.2f)
                        val r = (3f + (i % 3) * 1.8f) * density
                        drawCircle(if (i % 2 == 0) c.accent.copy(alpha = 0.16f) else c.orb2.copy(alpha = 0.5f), r, Offset(x, y))
                    }
                },
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Glas: weichgezeichneter Hintergrund mit leichter Tönung (ohne Blur-Unterstützung: nur die Tönung). */
@Composable
fun Modifier.frosted(tint: Color = colors.card, blur: Dp = 26.dp): Modifier {
    val haze = LocalHaze.current ?: return this.background(tint)
    val c = colors
    return this.hazeChild(
        haze,
        HazeStyle(backgroundColor = c.bg, tints = listOf(HazeTint(tint)), blurRadius = blur, noiseFactor = 0.06f),
    )
}

/** Gibt beim Drücken leicht nach (wie bei Samsung). */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?, onLongClick: (() -> Unit)? = null, role: Role = Role.Button): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    return this
        .scale(scale)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickableCompat(source, onClick, onLongClick)
            } else {
                Modifier.clickable(interactionSource = source, indication = null, role = role, onClick = onClick)
            },
        )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(source: MutableInteractionSource, onClick: () -> Unit, onLongClick: () -> Unit) =
    this.then(
        Modifier.combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick),
    )

/** Kachel aus mattem Glas: leicht durchsichtig, damit der Hintergrund durchschimmert. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = colors
    Column(
        modifier
            .pressable(onClick, onLongClick)
            .shadow(if (c.isDark) 0.dp else 1.5.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .frosted()
            .background(Brush.verticalGradient(listOf(c.glassShine, Color.Transparent), endY = 260f))
            .border(1.dp, c.cardBorder, shape)
            .padding(padding),
        content = content,
    )
}

/** Große Überschrift oben auf jeder Seite (One-UI-Stil). */
@Composable
fun ScreenHeader(
    title: String,
    overline: String? = null,
    chip: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = colors
    Row(
        modifier.fillMaxWidth().padding(start = 6.dp, end = 0.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null || chip != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (overline != null) Text(overline, style = T.body.copy(fontSize = T.label.fontSize), color = c.sub)
                    if (chip != null) Pill(chip)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = T.hero, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Kleines Kennzeichen, z. B. "A-Woche". */
@Composable
fun Pill(text: String, background: Color = colors.accentSoft, color: Color = colors.accent, modifier: Modifier = Modifier) {
    Text(
        text,
        style = T.smallBold,
        color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

/** Runder Glas-Knopf mit Symbol. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, size: Dp = 44.dp, filled: Boolean = false) {
    val c = colors
    Box(
        Modifier
            .size(size)
            .pressable(onClick)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(c.accent) else Modifier.frosted())
            .border(1.dp, if (filled) Color.Transparent else c.cardBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (filled) c.accentInk else c.text, modifier = Modifier.size(20.dp))
    }
}

/** Kleiner runder Knopf in Kacheln (z. B. "+"). */
@Composable
fun SoftIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = colors.accent, background: Color = colors.accentSoft) {
    Box(
        Modifier.size(34.dp).pressable(onClick).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SubjectDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Abhak-Kreis mit Animation. */
@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, ringColor: Color = colors.sub, size: Dp = 26.dp, square: Boolean = false, checkedColor: Color = colors.accent) {
    val c = colors
    val readOnly = LocalReadOnly.current
    val bg by animateColorAsState(if (checked) checkedColor else Color.Transparent, tween(180), label = "check-bg")
    val border by animateColorAsState(if (checked) checkedColor else ringColor, tween(180), label = "check-border")
    val shape = if (square) RoundedCornerShape(8.dp) else CircleShape
    // Konfetti beim Abhaken – wird in einer Ebene über der ganzen App gezeichnet, damit nichts abgeschnitten wird.
    val confetti = LocalConfetti.current
    val center = remember { arrayOf(Offset.Unspecified) }
    var previous by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked && !previous && center[0] != Offset.Unspecified) confetti.pop(center[0])
        previous = checked
    }
    Box(
        Modifier
            .size(size + 18.dp)
            .onGloballyPositioned { center[0] = it.boundsInRoot().center }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, enabled = !readOnly, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(shape).background(bg).border(BorderStroke(2.dp, border), shape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(checked, enter = scaleIn(spring(dampingRatio = 0.5f)), exit = scaleOut(tween(120))) {
                Icon(Ic.Check, null, tint = if (checkedColor == c.accent) c.accentInk else Color.White, modifier = Modifier.size(size * 0.55f))
            }
        }
    }
}

/** Fortschrittsring (Lernstand). */
@Composable
fun ProgressRing(progress: Float, color: Color, size: Dp = 64.dp, stroke: Dp = 7.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val c = colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            drawArc(c.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Fortschrittsbalken. */
@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(c.line)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Segment-Umschalter (z. B. Offen / Alle / Erledigt). */
@Composable
fun <K> Segmented(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
) {
    val c = colors
    SlidingSegments(
        options, selected, onSelect,
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .frosted(c.glassTrack, blur = 18.dp)
            .border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
            .padding(4.dp),
        itemHeight = 38.dp, gap = 4.dp, radius = 18.dp,
        indicator = c.accent, activeText = c.accentInk, inactiveText = c.sub,
        textStyle = T.label.copy(fontSize = T.body.fontSize * 0.93f),
        indicatorShadow = true,
        icons = icons,
    )
}

/**
 * Umschalter mit gleitender Glas-Markierung (wie "Liquid Glass"): Die Markierung ist zu 90 % deckend,
 * hat oben einen hellen Glanz und dehnt sich beim Gleiten kurz wie ein Tropfen.
 */
@Composable
fun <K> SlidingSegments(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier,
    itemHeight: Dp,
    gap: Dp,
    radius: Dp,
    indicator: Color,
    activeText: Color,
    inactiveText: Color,
    textStyle: androidx.compose.ui.text.TextStyle,
    indicatorShadow: Boolean = false,
    icons: List<ImageVector?>? = null,
) {
    val c = colors
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val posState = animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.66f, stiffness = 380f), label = "seg-pos")
    val pos by posState
    val shape = RoundedCornerShape(radius)
    val shine = if (c.isDark) 0.16f else 0.45f
    val shadowColor = if (indicator.luminance() > 0.7f) Color(0x55101828) else indicator
    val travel = kotlin.math.abs(pos - index).coerceAtMost(1f)
    // Eigenes Layout statt BoxWithConstraints: so funktioniert der Umschalter auch in Zeilen,
    // die ihre Höhe nach dem Inhalt richten (IntrinsicSize), z. B. auf dem Tablet.
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = 1f + 0.2f * travel
                        scaleY = 1f - 0.08f * travel
                    }
                    .then(if (indicatorShadow && !c.isDark) Modifier.shadow(5.dp, shape, ambientColor = shadowColor, spotColor = shadowColor) else Modifier)
                    .clip(shape)
                    .background(indicator.copy(alpha = indicator.alpha * 0.9f))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = shine), Color.Transparent, Color.White.copy(alpha = shine * 0.25f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = shine * 1.4f), Color.White.copy(alpha = 0.05f))), shape),
            )
            options.forEachIndexed { i, (key, label) ->
                val f = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .clip(shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = androidx.compose.ui.graphics.lerp(inactiveText, activeText, f)
                    val icon = icons?.getOrNull(i)
                    if (icon != null) {
                        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
                    } else {
                        Text(label, style = textStyle, color = tint, maxLines = 1)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val n = options.size.coerceAtLeast(1)
        val gapPx = gap.roundToPx()
        val heightPx = itemHeight.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (n * 72.dp.roundToPx() + gapPx * (n - 1))
        val itemW = ((width - gapPx * (n - 1)) / n).coerceAtLeast(0)
        val fixed = androidx.compose.ui.unit.Constraints.fixed(itemW, heightPx)
        val placeables = measurables.map { it.measure(fixed) }
        layout(width, heightPx) {
            // Die Markierung liest die Animation erst beim Platzieren – das spart Neuaufbau.
            placeables.first().place(((itemW + gapPx) * posState.value).toInt(), 0)
            placeables.drop(1).forEachIndexed { i, p -> p.place(i * (itemW + gapPx), 0) }
        }
    }
}

/** Auswahl-Chip. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val c = colors
    val bg by animateColorAsState(if (selected) c.accentSoft else c.card, tween(180), label = "chip")
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(label, style = T.label, color = if (selected) c.accent else c.sub, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = colors.sub) {
    Text(text, style = T.label, color = color, modifier = modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp))
}

@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.sub, modifier = Modifier.size(34.dp))
        Text(text, style = T.body, color = c.sub, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Text(
                action,
                style = T.label,
                color = c.accentInk,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** Zeile in einer Liste: Abhaken, Titel, Untertitel. */
@Composable
fun TaskRow(
    title: String,
    subtitle: String,
    dotColor: Color?,
    done: Boolean,
    overdue: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = colors
    val titleColor by animateColorAsState(if (done) c.sub else c.text, tween(200), label = "title")
    val alpha by animateFloatAsState(if (done) 0.55f else 1f, tween(250), label = "done-alpha")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .graphicsLayer { this.alpha = alpha }
            .padding(end = 10.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(done, onToggle, ringColor = if (overdue) c.warn else c.sub)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = T.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (dotColor != null) SubjectDot(dotColor, 8.dp)
                Text(subtitle, style = T.small, color = if (overdue && !done) c.warn else c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(colors.line))
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))


/** Kacheln gleiten beim ersten Anzeigen nacheinander herein. */
@Composable
fun Modifier.appear(index: Int): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        anim.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
    }
    return this.graphicsLayer {
        alpha = anim.value.coerceIn(0f, 1f)
        translationY = (1f - anim.value) * 28.dp.toPx()
    }
}

/** Farbige Hauptkachel mit Verlauf und sanftem Glanz, der ab und zu darüberläuft. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(24.dp)
    val t = rememberInfiniteTransition(label = "hero")
    val shine = t.animateFloat(-1f, 2.5f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "shine")
    Column(
        modifier
            .shadow(if (c.isDark) 0.dp else 10.dp, shape, ambientColor = c.heroStart, spotColor = c.heroStart)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .drawBehind {
                // Zwei weiche Kreise als Deko im Hintergrund der Kachel.
                drawCircle(Color.White.copy(alpha = 0.10f), size.height * 0.9f, Offset(size.width * 0.95f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), size.height * 0.55f, Offset(size.width * 0.78f, size.height * 1.05f))
                val x = shine.value * size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(x - size.width * 0.3f, 0f),
                        end = Offset(x, size.height),
                    ),
                )
            }
            .padding(18.dp),
        content = content,
    )
}


/** Symbol des Fachs in seiner Farbe (ohne Symbol: Farbpunkt). */
@Composable
fun SubjectIcon(subject: de.schulplaner.data.Subject?, size: Dp = 18.dp, tint: Color = subjectColor(subject?.color)) {
    val icon = SubjectIcons.of(subject)
    if (icon != null) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { SubjectDot(tint, size * 0.55f) }
    }
}

/** Farbmarke einer Stunde: Punkt für Einzelstunden, abgerundeter Balken für Doppelstunden. */
@Composable
fun LessonMark(color: Color, length: Int) {
    if (length >= 2) {
        Box(Modifier.size(width = 6.dp, height = 34.dp).clip(RoundedCornerShape(3.dp)).background(color))
    } else {
        Box(Modifier.width(6.dp), contentAlignment = Alignment.Center) { SubjectDot(color, 10.dp) }
    }
}

/** Grün → Gelb → Orange → Rot, je dringender ([level] 0 = entspannt, 1 = sehr dringend). */
fun urgencyColor(level: Float): Color {
    val stops = listOf(0f to Color(0xFF3FA36B), 0.4f to Color(0xFFE0A21F), 0.7f to Color(0xFFEB6B2E), 1f to Color(0xFFE03B3B))
    val l = level.coerceIn(0f, 1f)
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i - 1]
        val (p2, c2) = stops[i]
        if (l <= p2) return androidx.compose.ui.graphics.lerp(c1, c2, (l - p1) / (p2 - p1))
    }
    return stops.last().second
}

/** Klausuren: ab 3 Wochen entspannt, morgen/heute ganz rot. */
fun examUrgency(days: Long): Float = when {
    days <= 1 -> 1f
    days >= 21 -> 0f
    else -> 1f - (days - 1) / 20f
}

/** Aufgaben: je mehr offen sind, desto röter (ab 8 ganz rot). */
fun taskUrgency(open: Int): Float = (open / 8f).coerceIn(0f, 1f)
