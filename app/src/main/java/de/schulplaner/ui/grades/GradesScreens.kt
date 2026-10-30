package de.schulplaner.ui.grades

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    var sortByPoints by rememberSaveable { mutableStateOf(false) }
    var newGrade by remember { mutableStateOf<Grade?>(null) }
    val term = terms.firstOrNull { it.id == termId } ?: repo.currentTerm(terms)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Noten", overline = term?.let { "Halbjahr ${it.name}" }) {
            if (term != null && subjects.isNotEmpty()) {
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

        GlassCard(padding = PaddingValues(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GESAMTSCHNITT", style = T.overline, color = c.accent)
                    if (avg != null) {
                        val animated by animateFloatAsState(avg.toFloat(), tween(800), label = "avg")
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Text(GradeCalc.format(animated.toDouble()), style = T.bigNumber.copy(fontSize = T.bigNumber.fontSize * 1.08f), color = c.text)
                            Text("P", style = T.body, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Pill("≈ Note ${GradeCalc.note(avg)}", modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("Noch keine Noten", style = T.body, color = c.sub, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Fächer", "${withValue.size}")
                    StatPill("LK", "${subjects.count { it.isLk && !it.archived }}")
                }
            }
        }

        GlassCard(padding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fächer", style = T.section, color = c.text, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(c.line).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortChip("A–Z", !sortByPoints) { sortByPoints = false }
                    SortChip("Punkte", sortByPoints) { sortByPoints = true }
                }
            }
            val sorted = if (sortByPoints) results.sortedByDescending { it.value ?: -1.0 } else results
            sorted.forEach { r ->
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
                    SubjectDot(color)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.subject.name, style = T.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.subject.isLk) Pill("LK")
                        }
                        ProgressBar(((r.value ?: 0.0) / 15.0).toFloat(), color, height = 5.dp)
                    }
                    Text(r.value?.let { GradeCalc.format(it) } ?: "–", style = T.section, color = if ((r.value ?: 99.0) < 5.0) c.warn else c.text, modifier = Modifier.width(44.dp))
                    Icon(Ic.ChevronRight, null, tint = c.sub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    newGrade?.let { g ->
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
    var target by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

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
                    SoftIconButton(Ic.Plus, "Note in ${cat.name} hinzufügen", {
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

        SecondaryButton("Kategorie", {
            editCategory = GradeCategory(
                subjectId = subjectId, termId = termId, name = "", kind = CategoryKind.OTHER,
                weight = 10.0, sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ) to true
        }, Modifier.fillMaxWidth(), icon = Ic.Plus, color = c.sub)

        // Ziel-Rechner
        if (categories.isNotEmpty()) {
            val t = target ?: ((value ?: 10.0).roundToInt() + 1).coerceIn(5, 15)
            val catId = targetCategoryId?.takeIf { id -> categories.any { it.id == id } }
                ?: categories.firstOrNull { it.kind == CategoryKind.WRITTEN }?.id ?: categories.first().id
            val cat = categories.first { it.id == catId }
            val need = GradeCalc.neededPoints(categories, grades, catId, t.toDouble())
            GlassCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.Target, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Text("Was brauche ich?", style = T.section, color = c.text)
                }
                LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((5..15).toList()) { v ->
                        val active = v == t
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.accent else c.line)
                                .clickable { target = v },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$v", style = T.label, color = if (active) c.accentInk else c.text)
                        }
                    }
                }
                if (categories.size > 1) {
                    LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.id }) { k ->
                            ChoiceChip(k.name, k.id == catId, { targetCategoryId = k.id })
                        }
                    }
                }
                val text = when (need) {
                    null -> "Mit einer weiteren Note nicht erreichbar"
                    0 -> "Schon sicher – egal, was kommt"
                    else -> "mind. $need Punkte"
                }
                Text("Für Ø $t in der nächsten Note (${cat.name}):", style = T.small, color = c.sub, modifier = Modifier.padding(top = 12.dp))
                Text(text, style = T.title.copy(fontSize = T.section.fontSize * 1.35f), color = c.text, modifier = Modifier.padding(top = 2.dp))
            }
        }

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

    editGrade?.let { (g, isNew) -> GradeEditSheet(g, isNew, subjectSelectable = false, onDismiss = { editGrade = null }) }
    editCategory?.let { (cat, isNew) -> CategoryEditSheet(cat, isNew, onDismiss = { editCategory = null }) }
    if (overrideSheet) {
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
