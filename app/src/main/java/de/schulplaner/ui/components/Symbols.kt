package de.schulplaner.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.schulplaner.data.Holiday
import de.schulplaner.data.HolidayKind
import de.schulplaner.data.Subject

/** Bildchen für Fächer, Aufgabenarten und Ferien – im selben Linienstil wie die übrigen Symbole. */
object Sym {
    private val c = Ic::circle
    private val r = Ic::rect

    // ---------- Fächer ----------
    val Deutsch by lazy { Ic.icon("deutsch", "M3 5.5C5.5 4 8.5 4 12 6c3.5-2 6.5-2 9-.5V19c-2.5-1.5-5.5-1.5-9 .5-3.5-2-6.5-2-9-.5z", "M12 6v13.5", "M6 9h3M15 9h3M6 12.5h3M15 12.5h3") }
    val Mathe by lazy {
        Ic.icon("mathe", "M7 4v6M4 7h6", "M14 7h6", "m4.8 14.8 4.4 4.4M9.2 14.8l-4.4 4.4", "M14 17h6", c(17f, 14.2f, 0.4f), c(17f, 19.8f, 0.4f))
    }
    val Physik by lazy {
        Ic.icon(
            "physik",
            "M3 12a9 3.5 0 1 0 18 0a9 3.5 0 1 0-18 0",
            "M7.5 4.21a9 3.5 60 1 0 9 15.59a9 3.5 60 1 0-9-15.59",
            "M7.5 19.79a9 3.5 -60 1 0 9-15.59a9 3.5 -60 1 0-9 15.59",
            c(12f, 12f, 1f),
            stroke = 1.6f,
        )
    }
    val Chemie by lazy { Ic.icon("chemie", "M9 3h6", "M10 3v6l-5.2 9.2A2 2 0 0 0 6.5 21h11a2 2 0 0 0 1.7-2.8L14 9V3", "M7 15h10", c(10.5f, 18f, 0.6f), c(14f, 17.5f, 0.4f)) }
    val Englisch by lazy {
        Ic.icon("englisch", "M4 4h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-8l-5 4v-4H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M7 8.5h10M7 12h6")
    }
    val Sprache by lazy {
        Ic.icon("sprache", "M3 4h11a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8l-3 3v-3H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M18 9h3a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-1v3l-3-3h-5a1 1 0 0 1-1-1v-1")
    }
    val Sport by lazy { Ic.icon("sport", c(12f, 12f, 9f), "M3 12h18", "M12 3v18", "M5.6 5.6c3.2 3.4 3.2 9.4 0 12.8", "M18.4 5.6c-3.2 3.4-3.2 9.4 0 12.8") }
    val Powi by lazy { Ic.icon("powi", "M3 21h18", "M5 18h14", "M3.5 9 12 4l8.5 5z", "M7 11v5M12 11v5M17 11v5") }
    val Geschichte by lazy {
        Ic.icon("geschichte", "M8 3h10a2 2 0 0 1 0 4H8", "M8 3a2 2 0 0 0 0 4", "M8 7v10", "M18 7v10", "M18 17H6a2 2 0 0 0 0 4h10a2 2 0 0 0 2-2v-2", "M11 10.5h4M11 13.5h4")
    }
    val Kunst by lazy {
        Ic.icon(
            "kunst",
            "M12 3a9 9 0 0 0 0 18c1.4 0 2-0.9 2-1.9 0-1.1-1-1.6-1-2.6 0-1.2 1-2 2.4-2H18a3 3 0 0 0 3-3C21 6.9 17 3 12 3z",
            c(7.5f, 11f, 1f), c(10f, 7f, 1f), c(15f, 7.5f, 1f),
        )
    }
    val Religion by lazy { Ic.icon("religion", "M12 3v18", "M7 8.5h10", stroke = 2.2f) }
    val Biologie by lazy { Ic.icon("biologie", "M12 21v-9", "M12 12c0-4-3-6.5-7-6.5 0 4 3 6.5 7 6.5z", "M12 10c0-3 2.5-5.5 6.5-5.5 0 3-2.5 5.5-6.5 5.5z") }
    val Erdkunde by lazy { Ic.icon("erdkunde", c(12f, 12f, 9f), "M3 12h18", "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18") }
    val Informatik by lazy { Ic.icon("informatik", "m8 8-4 4 4 4", "m16 8 4 4-4 4", "m13.5 6-3 12") }
    val Idee by lazy {
        Ic.icon("idee", "M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2V16h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z")
    }

    // ---------- Aufgabenarten ----------
    val House by lazy { Ic.icon("house", "M3 10.5 12 3l9 7.5", "M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5") }
    val Presentation by lazy { Ic.icon("presentation", r(3f, 4f, 18f, 11f, 2f), "M12 15v3", "M8 21l4-3 4 3", "M7 11l3-3 2 2 4-4") }
    val Star by lazy { Ic.icon("star", "m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z") }
    val Dots by lazy { Ic.icon("dots", c(6f, 12f, 1.3f), c(12f, 12f, 1.3f), c(18f, 12f, 1.3f)) }
    val Pen by lazy { Ic.icon("pen", "M4 20h4L19 9l-4-4L4 16z", "m13.5 6.5 4 4") }

    // ---------- Ferien ----------
    val Tree by lazy { Ic.icon("tree", "M12 3 7.5 9h3L6 14.5h4L5 19.5h14l-5-5h4L13.5 9h3z", "M12 19.5V22") }
    val Leaf by lazy { Ic.icon("leaf", "M5 19C5 11 10 5 20 5c0 10-6 15-14 15", "M3.5 20.5 14 10") }
    val Flower by lazy {
        Ic.icon("flower", c(12f, 9f, 1.8f), c(12f, 5.2f, 2f), c(15.8f, 9f, 2f), c(12f, 12.8f, 2f), c(8.2f, 9f, 2f), "M12 14.8V21", "M12 18.5c-1.7-1.8-3.7-2.2-5-1.5 1.2 2 3.3 2.4 5 1.5z")
    }
}

/** Fach-Symbole zum Auswählen (Schlüssel → Bild). */
object SubjectIcons {
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "deutsch" to Sym.Deutsch, "mathe" to Sym.Mathe, "physik" to Sym.Physik, "chemie" to Sym.Chemie,
            "englisch" to Sym.Englisch, "sprache" to Sym.Sprache, "sport" to Sym.Sport, "powi" to Sym.Powi,
            "geschichte" to Sym.Geschichte, "kunst" to Sym.Kunst, "religion" to Sym.Religion, "biologie" to Sym.Biologie,
            "erdkunde" to Sym.Erdkunde, "informatik" to Sym.Informatik, "musik" to Ic.Music, "idee" to Sym.Idee,
        )
    }
    private val byKey by lazy { all.toMap() }

    /** Vorschlag nach dem Fachnamen. */
    fun guess(name: String): String? {
        val n = name.lowercase()
        return when {
            "deutsch" in n -> "deutsch"
            "mathe" in n -> "mathe"
            "physik" in n -> "physik"
            "chemie" in n -> "chemie"
            "englisch" in n || n == "e" || n == "en" -> "englisch"
            "franz" in n || "latein" in n || "spanisch" in n || "italien" in n || "russisch" in n -> "sprache"
            "sport" in n -> "sport"
            "powi" in n || "politik" in n || "wirtschaft" in n || "sozial" in n -> "powi"
            "geschichte" in n -> "geschichte"
            "kunst" in n -> "kunst"
            "reli" in n -> "religion"
            "bio" in n -> "biologie"
            "erdkunde" in n || "geo" in n -> "erdkunde"
            "info" in n -> "informatik"
            "musik" in n -> "musik"
            "ethik" in n || "philo" in n -> "idee"
            else -> null
        }
    }

    fun keyFor(subject: Subject): String? = subject.icon.ifBlank { null }?.takeIf { it in byKey } ?: guess(subject.name)

    fun of(subject: Subject?): ImageVector? = subject?.let { keyFor(it) }?.let { byKey[it] }

    fun of(key: String): ImageVector? = byKey[key]
}

/** Symbole für Aufgabenarten. */
object KindIcons {
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "house" to Sym.House, "presentation" to Sym.Presentation, "star" to Sym.Star, "dots" to Sym.Dots,
            "pen" to Sym.Pen, "book" to Ic.Book, "bookmark" to Ic.Bookmark, "target" to Ic.Target,
            "note" to Ic.Note, "clock" to Ic.Clock, "sparkle" to Ic.Sparkle, "idee" to Sym.Idee,
        )
    }
    private val byKey by lazy { all.toMap() }

    fun of(key: String): ImageVector = byKey[key] ?: Sym.Dots
}

/** Bild und Farbe für Ferien und Feiertage. */
object HolidayLook {
    fun icon(h: Holiday): ImageVector = when (key(h)) {
        "tree" -> Sym.Tree
        "sun" -> Ic.Sun
        "leaf" -> Sym.Leaf
        "flower" -> Sym.Flower
        "star" -> Sym.Star
        else -> Ic.Palm
    }

    fun color(h: Holiday): Color = when (key(h)) {
        "tree" -> Color(0xFF2F9E6E)
        "sun" -> Color(0xFFF2A11F)
        "leaf" -> Color(0xFFE0702E)
        "flower" -> Color(0xFFE0609E)
        "star" -> Color(0xFFD9A62E)
        else -> Color(0xFFC77700)
    }

    /** "tree", "sun", "leaf", "flower", "star" oder "palm" (auch für Widgets). */
    fun key(h: Holiday): String {
        val n = h.name.lowercase()
        return when {
            "weihnacht" in n -> "tree"
            "sommer" in n -> "sun"
            "herbst" in n -> "leaf"
            "oster" in n -> "flower"
            h.kind == HolidayKind.FEIERTAG -> "star"
            else -> "palm"
        }
    }
}
