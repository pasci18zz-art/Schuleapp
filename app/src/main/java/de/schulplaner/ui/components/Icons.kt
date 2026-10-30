package de.schulplaner.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Linien-Symbole der App (wie in den Entwürfen), als Vektoren gebaut. */
object Ic {
    private fun n(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${n(cx - r)} ${n(cy)}a${n(r)} ${n(r)} 0 1 0 ${n(2 * r)} 0a${n(r)} ${n(r)} 0 1 0 ${n(-2 * r)} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
        val iw = w - 2 * r
        val ih = h - 2 * r
        return "M${n(x + r)} ${n(y)}h${n(iw)}a${n(r)} ${n(r)} 0 0 1 ${n(r)} ${n(r)}v${n(ih)}" +
            "a${n(r)} ${n(r)} 0 0 1 ${n(-r)} ${n(r)}h${n(-iw)}a${n(r)} ${n(r)} 0 0 1 ${n(-r)} ${n(-r)}" +
            "v${n(-ih)}a${n(r)} ${n(r)} 0 0 1 ${n(r)} ${n(-r)}z"
    }

    private fun icon(name: String, vararg paths: String, stroke: Float = 1.9f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home by lazy { icon("home", "M3 10.5 12 3l9 7.5", "M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5") }
    val Calendar by lazy { icon("calendar", rect(3f, 4f, 18f, 17f, 3f), "M3 9h18M8 2.5v3M16 2.5v3M8 13h2M14 13h2M8 17h2M14 17h2") }
    val CalendarPlain by lazy { icon("calendar_plain", rect(3f, 4f, 18f, 17f, 3f), "M3 9h18M8 2.5v3M16 2.5v3") }
    val Tasks by lazy { icon("tasks", rect(3f, 3f, 18f, 18f, 5f), "m8 12 3 3 5-6") }
    val Exam by lazy { icon("exam", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5M9 13h6M9 17h4") }
    val Grades by lazy { icon("grades", "M3 21h18", rect(5f, 11f, 3f, 7f, 1f), rect(10.5f, 5f, 3f, 13f, 1f), rect(16f, 13f, 3f, 5f, 1f)) }
    val Settings by lazy {
        icon("settings", "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1", circle(15f, 6f, 2f), circle(9f, 12f, 2f), circle(17f, 18f, 2f))
    }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14", stroke = 2.2f) }
    val Minus by lazy { icon("minus", "M5 12h14", stroke = 2.2f) }
    val Close by lazy { icon("close", "M6 6l12 12M18 6 6 18", stroke = 2.1f) }
    val ChevronLeft by lazy { icon("chevron_left", "m15 18-6-6 6-6", stroke = 2f) }
    val ChevronRight by lazy { icon("chevron_right", "m9 18 6-6-6-6", stroke = 2f) }
    val ChevronDown by lazy { icon("chevron_down", "m6 9 6 6 6-6", stroke = 2.2f) }
    val Pin by lazy { icon("pin", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z", circle(12f, 10f, 2.5f)) }
    val User by lazy { icon("user", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }
    val Bookmark by lazy { icon("bookmark", "M6 3h12v18l-6-4-6 4z", stroke = 2.1f) }
    val Alert by lazy { icon("alert", "M12 3 2 20h20z", "M12 10v4M12 17h.01", stroke = 2.1f) }
    val Camera by lazy { icon("camera", "M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", circle(12f, 13.5f, 3.5f)) }
    val Image by lazy { icon("image", rect(3f, 4f, 18f, 16f, 3f), circle(9f, 10f, 1.8f), "m21 16-5-5-9 9") }
    val ImagePlus by lazy { icon("image_plus", rect(3f, 4f, 18f, 16f, 3f), "M12 9v6M9 12h6") }
    val File by lazy { icon("file", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5") }
    val Clip by lazy { icon("clip", "m21 11-8.5 8.5a5 5 0 0 1-7-7L14 4a3.5 3.5 0 0 1 5 5l-8.5 8.5a2 2 0 0 1-3-3L15 7") }
    val Check by lazy { icon("check", "m5 12 5 5 9-10", stroke = 3f) }
    val Pencil by lazy { icon("pencil", "M4 20h4L19 9l-4-4L4 16z", "m13.5 6.5 4 4") }
    val Target by lazy { icon("target", circle(12f, 12f, 9f), circle(12f, 12f, 5f), circle(12f, 12f, 1f)) }
    val XCircle by lazy { icon("x_circle", circle(12f, 12f, 9f), "m9 9 6 6M15 9l-6 6") }
    val Clock by lazy { icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val Hourglass by lazy { icon("hourglass", "M6 2h12M6 22h12M7 2v4l5 6-5 6v4M17 2v4l-5 6 5 6v4") }
    val Filter by lazy { icon("filter", "M4 5h16l-6 8v5l-4 2v-7z") }
    val Trash by lazy { icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V4h6v3") }
    val Note by lazy { icon("note", rect(4f, 3f, 16f, 18f, 3f), "M8 8h8M8 12h8M8 16h5") }
    val Sun by lazy { icon("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4") }
    val Palm by lazy { icon("palm", "M12 21V11", "M12 11c-1-4-5-5-8-4 3 0 5 2 6 4", "M12 11c1-4 5-5 8-4-3 0-5 2-6 4", "M12 11c0-3-2-6-5-7 2 2 3 4 3 7", "M7 21h10") }
    val Swap by lazy { icon("swap", "M7 4 3 8l4 4", "M3 8h14", "m17 20 4-4-4-4", "M21 16H7") }
    val Layers by lazy { icon("layers", "m12 3 9 5-9 5-9-5z", "m3 13 9 5 9-5") }
    val Sparkle by lazy { icon("sparkle", "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6") }
    val Book by lazy { icon("book", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 21a2 2 0 0 1 2-2h13v2H6") }
    val Music by lazy { icon("music", "M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f)) }
    val Cloud by lazy { icon("cloud", "M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1 0 8z") }
}
