package de.schulplaner.ui.grades

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.data.CategoryKind
import de.schulplaner.data.Grade
import de.schulplaner.data.GradeCategory
import de.schulplaner.domain.Fmt
import de.schulplaner.domain.GradeCalc
import de.schulplaner.domain.TermStats
import de.schulplaner.ui.LocalNavigator
import de.schulplaner.ui.Routes
import de.schulplaner.ui.components.AppSheet
import de.schulplaner.ui.components.ChoiceChip
import de.schulplaner.ui.components.EmptyHint
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.GlassIconButton
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.Pill
import de.schulplaner.ui.components.PointsPicker
import de.schulplaner.ui.components.ProgressBar
import de.schulplaner.ui.components.ScreenHeader
import de.schulplaner.ui.components.SecondaryButton
import de.schulplaner.ui.components.Segmented
import de.schulplaner.ui.components.SheetHeader
import de.schulplaner.ui.components.SoftIconButton
import de.schulplaner.ui.components.Stepper
import de.schulplaner.ui.components.SubjectDot
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.components.screenPadding
import de.schulplaner.ui.components.subjectColor
import de.schulplaner.ui.theme.T
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun GradesOverviewScreen() {
    val nav = LocalNavigator.current
    val c = colors
    val repo = LocalContext.current.app.repo
    val terms by repo.terms.collectAsStateWithLifecycle()
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val categories by repo.categories.collectAsStateWithLifecycle()
    val grades by repo.grades.collectAsStateWithLifecycle()
    val subjectTerms by repo.subjectTerms.collectAsStateWithLifecycle()

    var termId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByPoints by rememberSaveable { mutableStateOf(true) }
    var view by rememberSaveable { mutableStateOf(GradeView.ALL) }
    var newGrade by remember { mutableStateOf<Grade?>(null) }
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current
    val term = terms.firstOrNull { it.id == termId } ?: repo.currentTerm(terms)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Noten", overline = term?.let { "Halbjahr ${it.name}" }) {
            if (term != null && subjects.isNotEmpty() && !readOnly) {
                GlassIconButton(Ic.Plus, "Note hinzufügen", {
                    newGrade = Grade(termId = term.id, date = LocalDate.now().toEpochDay())
                }, filled = true)
            }
        }
        if (terms.isNotEmpty()) {
            Segmented(terms.map { it.id to it.name }, term?.id ?: "", { termId = it })
        }
        if (subjects.isEmpty() || term == null) {
            GlassCard {
                EmptyHint(Ic.Grades, "Lege zuerst deine Fächer an.", action = "Fächer anlegen", onAction = { nav.open(Routes.SET_SUBJECTS) })
            }
            return@Column
        }

        val results = TermStats.results(term.id, subjects, categories, grades, subjectTerms)
        val avg = TermStats.average(results)
        val withValue = results.filter { it.value != null }

        val written = TermStats.kindTermAverage(term.id, subjects, de.schulplaner.data.CategoryKind.WRITTEN, categories, grades)
        val oral = TermStats.kindTermAverage(term.id, subjects, de.schulplaner.data.CategoryKind.ORAL, categories, grades)
        GlassCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SCHNITT", style = T.overline, color = c.accent, modifier = Modifier.weight(1f))
                Text("${withValue.size} Fächer · ${subjects.count { it.isLk && !it.archived }} LK", style = T.small, color = c.sub)
            }
            // Schriftlich | Gesamt (groß, Mitte) | Mündlich – feste Größen, nur die Markierung gleitet.
            val order = listOf(GradeView.WRITTEN, GradeView.ALL, GradeView.ORAL)
            val sel by animateFloatAsState(order.indexOf(view).toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f), label = "grade-sel")
            val xs = remember { FloatArray(3) }
            val ws = remember { FloatArray(3) }
            val hl = c.accent
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .drawBehind {
                        val i = sel.toInt().coerceIn(0, 1)
                        val f = sel - i
                        val x = xs[i] + (xs[i + 1] - xs[i]) * f
                        val w = ws[i] + (ws[i + 1] - ws[i]) * f
                        if (w > 0f) drawRoundRect(hl.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val values = mapOf(GradeView.WRITTEN to written, GradeView.ORAL to oral, GradeView.ALL to avg)
                val labels = mapOf(GradeView.WRITTEN to "schriftlich", GradeView.ORAL to "mündlich", GradeView.ALL to "gesamt")
                order.forEachIndexed { i, mode ->
                    if (i > 0) Box(Modifier.width(1.5.dp).height(46.dp).clip(RoundedCornerShape(1.dp)).background(c.accent.copy(alpha = 0.55f)))
                    val active = view == mode
                    val big = mode == GradeView.ALL
                    val shownValue by animateFloatAsState((values[mode] ?: 0.0).toFloat(), tween(700), label = "grade-val")
                    val ink by androidx.compose.animation.animateColorAsState(if (active) c.text else c.sub, tween(300), label = "grade-ink")
                    Column(
                        Modifier
                            .weight(if (big) 1.6f else 1f)
                            .onGloballyPositioned { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { view = mode }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (values[mode] != null) GradeCalc.format(shownValue.toDouble()) else "–",
                            style = T.bigNumber.copy(fontSize = T.bigNumber.fontSize * (if (big) 1.0f else 0.5f)),
                            color = ink,
                            maxLines = 1,
                        )
                        Text(labels.getValue(mode), style = T.small, color = if (active) c.accent else c.sub)
                    }
                }
            }
            val shown = when (view) {
                GradeView.WRITTEN -> written
                GradeView.ORAL -> oral
                GradeView.ALL -> avg
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (shown != null) Pill("≈ Note ${GradeCalc.note(shown)}") else Text("Noch keine Noten", style = T.small, color = c.sub)
                Box(Modifier.weight(1f))
                // Umschalter mit Symbolen: Buch = schriftlich, Kopf = mündlich, = gesamt.
                de.schulplaner.ui.components.MiniSegmented(
                    listOf(GradeView.WRITTEN to "Schriftlich", GradeView.ORAL to "Mündlich", GradeView.ALL to "Gesamt"),
                    view,
                    { view = it },
                    Modifier.width(150.dp),
                    icons = listOf(de.schulplaner.ui.components.Sym.Deutsch, de.schulplaner.ui.components.Sym.Speak, de.schulplaner.ui.components.Sym.Equals),
                )
            }
        }

        GlassCard(padding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fächer", style = T.section, color = c.text, modifier = Modifier.weight(1f))
                de.schulplaner.ui.components.MiniSegmented(listOf(false to "A–Z", true to "Punkte"), sortByPoints, { sortByPoints = it }, Modifier.width(150.dp))
            }
            fun valueOf(r: de.schulplaner.domain.SubjectResult): Double? = when (view) {
                GradeView.WRITTEN -> TermStats.kindAverage(term.id, r.subject.id, de.schulplaner.data.CategoryKind.WRITTEN, categories, grades)
                GradeView.ORAL -> TermStats.kindAverage(term.id, r.subject.id, de.schulplaner.data.CategoryKind.ORAL, categories, grades)
                GradeView.ALL -> r.value
            }
            val sorted = if (sortByPoints) results.sortedByDescending { valueOf(it) ?: -1.0 } else results
            sorted.forEach { r ->
                val v = valueOf(r)
                val color = subjectColor(r.subject.color)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { nav.open(Routes.subjectGrades(r.subject.id, term.id)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    de.schulplaner.ui.components.SubjectIcon(r.subject, 20.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.subject.name, style = T.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.subject.isLk) Pill("LK")
                        }
                        ProgressBar(((v ?: 0.0) / 15.0).toFloat(), color, height = 5.dp)
                    }
                    val shownV by animateFloatAsState((v ?: 0.0).toFloat(), tween(500), label = "row-val")
                    Text(if (v != null) GradeCalc.format(shownV.toDouble()) else "–", style = T.section, color = if ((v ?: 99.0) < 5.0) c.warn else c.text, modifier = Modifier.width(44.dp))
                    Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (!readOnly) newGrade?.let { g ->
        GradeEditSheet(g, isNew = true, subjectSelectable = true, onDismiss = { newGrade = null })
    }
}

@Composable
private fun StatPill(label: String, value: String, warn: Boolean = false) {
    val c = colors
    Row(
        Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).background(if (warn) c.warnSoft else c.line).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.small, color = if (warn) c.warn else c.sub, modifier = Modifier.weight(1f))
        Text(value, style = T.label, color = if (warn) c.warn else c.text)
    }
}

@Composable
private fun SortChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = colors
    Text(
        label,
        style = T.smallBold,
        color = if (active) c.text else c.sub,
        modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(if (active) c.card else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun SubjectGradesScreen(subjectId: String, termId: String) {
    val nav = LocalNavigator.current
    val c = colors
    val repo = LocalContext.current.app.repo
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val terms by repo.terms.collectAsStateWithLifecycle()
    val allCategories by repo.categories.collectAsStateWithLifecycle()
    val allGrades by repo.grades.collectAsStateWithLifecycle()
    val subjectTerms by repo.subjectTerms.collectAsStateWithLifecycle()
    val attachments by repo.attachments.collectAsStateWithLifecycle()

    LaunchedEffect(subjectId, termId) { repo.ensureCategories(subjectId, termId) }

    val subject = subjects.firstOrNull { it.id == subjectId } ?: return
    val term = terms.firstOrNull { it.id == termId }
    val categories = allCategories.filter { it.subjectId == subjectId && it.termId == termId }.sortedBy { it.sortOrder }
    val grades = allGrades.filter { it.subjectId == subjectId && it.termId == termId }
    val override = subjectTerms.firstOrNull { it.subjectId == subjectId && it.termId == termId }?.overridePoints
    val computed = GradeCalc.subjectAverage(categories, grades)
    val value = override?.toDouble() ?: computed

    var editGrade by remember { mutableStateOf<Pair<Grade, Boolean>?>(null) }
    var editCategory by remember { mutableStateOf<Pair<GradeCategory, Boolean>?>(null) }
    var overrideSheet by remember { mutableStateOf(false) }
    val readOnly = de.schulplaner.ui.components.LocalReadOnly.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Ic.ChevronLeft, "Zurück", { nav.back() })
            Box(Modifier.weight(1f))
            if (term != null) Pill(term.name)
        }
        Column(Modifier.padding(horizontal = 6.dp)) {
            Text(subject.name, style = T.title.copy(fontSize = T.hero.fontSize * 0.85f), color = c.text)
            val sub = listOfNotNull(if (subject.isLk) "LK" else null, subject.teacher.ifBlank { null }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = T.label.copy(fontSize = T.body.fontSize * 0.93f), color = c.sub, modifier = Modifier.padding(top = 4.dp))
        }

        // Schnitt + Verlauf + Gewichtung
        GlassCard(padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (value != null) {
                        val animated by animateFloatAsState(value.toFloat(), tween(800), label = "subj-avg")
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(GradeCalc.format(animated.toDouble()), style = T.bigNumber, color = c.text)
                            Text("P", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("≈ Note ${GradeCalc.note(value)}")
                            if (override != null) Pill("Zeugnis", c.line, c.sub)
                        }
                    } else {
                        Text("Noch keine Noten", style = T.body, color = c.sub)
                    }
                }
                Sparkline(grades.sortedBy { it.date }.map { it.points }, c.accent)
            }
            if (categories.isNotEmpty()) {
                val total = categories.sumOf { it.weight }.coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp).height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    categories.forEachIndexed { i, cat ->
                        if (cat.weight > 0) {
                            Box(Modifier.weight(cat.weight.toFloat()).height(12.dp).background(c.accent.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.25f))))
                        }
                    }
                }
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                cat.name,
                                style = T.label,
                                color = c.text,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editCategory = cat to false }.padding(vertical = 4.dp),
                            )
                            Stepper(
                                "${cat.weight.roundToInt()} %",
                                onMinus = { repo.saveCategory(cat.copy(weight = (cat.weight - 5).coerceAtLeast(0.0))) },
                                onPlus = { repo.saveCategory(cat.copy(weight = (cat.weight + 5).coerceAtMost(100.0))) },
                            )
                        }
                    }
                    if (total.roundToInt() != 100) {
                        Text("Summe ${total.roundToInt()} % – wird anteilig verrechnet.", style = T.small, color = c.warn)
                    }
                }
            }
        }

        // Kategorien mit Noten
        categories.forEach { cat ->
            val catGrades = grades.filter { it.categoryId == cat.id }.sortedBy { it.date }
            GlassCard(padding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
                Row(Modifier.padding(start = 12.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cat.name, style = T.section, color = c.text, modifier = Modifier.weight(1f))
                    GradeCalc.mean(catGrades)?.let { Text("Ø ${GradeCalc.format(it)}", style = T.label, color = c.sub) }
                    if (!readOnly) SoftIconButton(Ic.Plus, "Note in ${cat.name} hinzufügen", {
                        editGrade = Grade(
                            subjectId = subjectId, termId = termId, categoryId = cat.id,
                            date = LocalDate.now().toEpochDay(),
                            title = if (cat.kind == CategoryKind.WRITTEN) "${catGrades.size + 1}. Klausur" else "",
                        ) to true
                    })
                }
                if (catGrades.isEmpty()) {
                    Text("Noch keine Noten", style = T.small, color = c.sub, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                }
                catGrades.forEach { g ->
                    val hasAttachment = attachments.any { it.ownerId == g.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { editGrade = g to false }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(if (g.points < 5) c.warnSoft else c.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${g.points}", style = T.section.copy(fontSize = T.section.fontSize * 1.05f), color = if (g.points < 5) c.warn else c.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(g.title.ifBlank { cat.name }, style = T.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                Fmt.dateShort(LocalDate.ofEpochDay(g.date)),
                                if (g.weight != 1.0) formatFactor(g.weight) else null,
                                g.reason.ifBlank { null },
                            ).joinToString(" · ")
                            Text(meta, style = T.small, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (hasAttachment) Icon(Ic.Clip, "Anhang", tint = c.sub, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        if (!readOnly) SecondaryButton("Kategorie", {
            editCategory = GradeCategory(
                subjectId = subjectId, termId = termId, name = "", kind = CategoryKind.OTHER,
                weight = 10.0, sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ) to true
        }, Modifier.fillMaxWidth(), icon = Ic.Plus, color = c.sub)

        // Notenziel-Rechner
        if (categories.isNotEmpty()) GoalCard(subject, termId, categories, grades, value, readOnly)

        // Zeugnisnote
        GlassCard(padding = PaddingValues(16.dp), onClick = { overrideSheet = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Zeugnisnote", style = T.section, color = c.text)
                    Text(
                        if (override != null) "$override Punkte (ersetzt den berechneten Schnitt)" else "Falls der Lehrer anders rundet: echte Note eintragen",
                        style = T.small,
                        color = c.sub,
                    )
                }
                Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (!readOnly) editGrade?.let { (g, isNew) -> GradeEditSheet(g, isNew, subjectSelectable = false, onDismiss = { editGrade = null }) }
    if (!readOnly) editCategory?.let { (cat, isNew) -> CategoryEditSheet(cat, isNew, onDismiss = { editCategory = null }) }
    if (overrideSheet && !readOnly) {
        var points by remember { mutableStateOf(override ?: computed?.roundToInt() ?: 10) }
        AppSheet(onDismiss = { overrideSheet = false }) { close ->
            SheetHeader("Zeugnisnote", onSave = {
                repo.setOverride(subjectId, termId, points)
                close()
            }, onDelete = if (override != null) {
                {
                    repo.setOverride(subjectId, termId, null)
                    close()
                }
            } else {
                null
            })
            PointsPicker(points) { points = it }
        }
    }
}

/** Kleiner Notenverlauf. */
@Composable
private fun Sparkline(points: List<Int>, color: Color) {
    if (points.size < 2) return
    val c = colors
    Canvas(
        Modifier
            .size(width = 132.dp, height = 56.dp)
            .semantics { contentDescription = "Notenverlauf: " + points.joinToString(", ") + " Punkte" },
    ) {
        val pad = 5.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val step = w / (points.size - 1)
        val pts = points.mapIndexed { i, p -> Offset(pad + i * step, pad + h - (p / 15f) * h) }
        drawLine(c.line, Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 1.dp.toPx())
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(color, 3.5.dp.toPx(), it) }
    }
}

@Suppress("unused")
private fun Modifier.outlined(color: Color) = this.border(1.dp, color, RoundedCornerShape(12.dp))

/** Welche Noten oben und in der Fächerliste gezeigt werden. */
private enum class GradeView { WRITTEN, ORAL, ALL }

/**
 * Notenziel: Ziel in Punkten eingeben (Planung der Klausuren und mündlichen Noten kommt vom Fach, pro Halbjahr
 * änderbar). Die Karte zeigt, wie viele Punkte die noch fehlenden Noten im Schnitt haben müssten.
 */
@Composable
private fun GoalCard(
    subject: de.schulplaner.data.Subject,
    termId: String,
    categories: List<GradeCategory>,
    grades: List<Grade>,
    current: Double?,
    readOnly: Boolean,
) {
    val c = colors
    val repo = LocalContext.current.app.repo
    val settings by repo.settings.collectAsStateWithLifecycle()
    val goalKey = de.schulplaner.data.SettingKeys.GOAL_PREFIX + subject.id + "_" + termId
    val planKey = de.schulplaner.data.SettingKeys.PLAN_PREFIX + subject.id + "_" + termId
    val target = settings[goalKey]?.toIntOrNull() ?: ((current ?: 10.0).roundToInt() + 1).coerceIn(5, 15)
    val plan = settings[planKey]?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
    val plannedW = plan?.get(0) ?: subject.plannedExams
    val plannedO = plan?.get(1) ?: subject.plannedOral
    val goal = GradeCalc.goal(categories, grades, plannedW, plannedO, target.toDouble())
    val hasWritten = categories.any { it.kind == CategoryKind.WRITTEN }
    val hasOral = categories.any { it.kind == CategoryKind.ORAL }

    GlassCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Target, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("Notenziel", style = T.section, color = c.text, modifier = Modifier.weight(1f))
        }
        // Ziel in Notenpunkten.
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ich will im Halbjahr", style = T.small, color = c.sub)
                Text("$target Punkte  ·  Note ${GradeCalc.note(target.toDouble())}", style = T.label, color = c.text)
            }
            if (!readOnly) de.schulplaner.ui.components.Stepper(
                "$target",
                onMinus = { repo.setSetting(goalKey, (target - 1).coerceAtLeast(0).toString()) },
                onPlus = { repo.setSetting(goalKey, (target + 1).coerceAtMost(15).toString()) },
            )
        }
        // Planung dieses Halbjahrs.
        if (hasWritten) PlanRow("Klausuren", plannedW, grades.count { g -> categories.any { it.id == g.categoryId && it.kind == CategoryKind.WRITTEN } }, readOnly) {
            repo.setSetting(planKey, "$it,$plannedO")
        }
        if (hasOral) PlanRow("Mündliche Noten", plannedO, grades.count { g -> categories.any { it.id == g.categoryId && it.kind == CategoryKind.ORAL } }, readOnly) {
            repo.setSetting(planKey, "$plannedW,$it")
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(c.line))
        val parts = listOfNotNull(
            if (goal.remainingWritten > 0) "${goal.remainingWritten} ${if (goal.remainingWritten == 1) "Klausur" else "Klausuren"}" else null,
            if (goal.remainingOral > 0) "${goal.remainingOral} mündlich" else null,
        ).joinToString(" und ")
        when {
            goal.nothingLeft -> {
                Text("Alle geplanten Noten sind eingetragen.", style = T.small, color = c.sub)
                Text(
                    (goal.best?.let { "Aktuell Ø ${GradeCalc.format(it)}" } ?: "Noch keine Noten") + if ((goal.best ?: 0.0) >= target - 1e-9) " – Ziel erreicht" else " – Ziel verfehlt",
                    style = T.title.copy(fontSize = T.section.fontSize * 1.2f),
                    color = c.text,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            goal.needed == null -> {
                Text("Bei den noch fehlenden Noten ($parts)", style = T.small, color = c.sub)
                Text(
                    "Nicht mehr erreichbar" + (goal.best?.let { " (max. Ø ${GradeCalc.format(it)})" } ?: ""),
                    style = T.title.copy(fontSize = T.section.fontSize * 1.2f),
                    color = c.warn,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            goal.needed == 0 -> {
                Text("Bei den noch fehlenden Noten ($parts)", style = T.small, color = c.sub)
                Text("Schon sicher – egal, was kommt", style = T.title.copy(fontSize = T.section.fontSize * 1.2f), color = c.good, modifier = Modifier.padding(top = 2.dp))
            }
            else -> {
                Text("Du brauchst bei den noch fehlenden Noten ($parts) im Schnitt", style = T.small, color = c.sub)
                Text(
                    "ca. ${goal.needed} Punkte  ·  Note ${GradeCalc.note(goal.needed.toDouble())}",
                    style = T.title.copy(fontSize = T.section.fontSize * 1.35f),
                    color = c.text,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanRow(label: String, planned: Int, done: Int, readOnly: Boolean, onChange: (Int) -> Unit) {
    val c = colors
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.small, color = c.sub)
            Text("$done von $planned eingetragen", style = T.label, color = c.text)
        }
        if (!readOnly) de.schulplaner.ui.components.Stepper(
            "$planned",
            onMinus = { onChange((planned - 1).coerceAtLeast(0)) },
            onPlus = { onChange((planned + 1).coerceAtMost(20)) },
        )
    }
}
