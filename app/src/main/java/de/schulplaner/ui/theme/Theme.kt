package de.schulplaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.schulplaner.R

@Immutable
data class SchulColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardBorder: Color,
    val shadow: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val exam: Color,
    val good: Color,
    val nav: Color,
    val sheet: Color,
    val snackbar: Color,
    /** Weiche, langsam treibende Farbflächen im Hintergrund. */
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
    /** Verlauf der "Jetzt"-Kachel. */
    val heroStart: Color,
    val heroEnd: Color,
    /** Deckkraft für Fächerfarben als Hintergrund (Kacheln im Stundenplan). */
    val tint: Float,
)

val LightSchulColors = SchulColors(
    isDark = false,
    bg = Color(0xFFF4F5F9),
    card = Color(0xE6FFFFFF),
    cardBorder = Color(0xFFEBEDF1),
    shadow = Color(0x14101828),
    text = Color(0xFF111418),
    sub = Color(0xFF5B6270),
    line = Color(0xFFEEF0F3),
    accent = Color(0xFF4F5BD5),
    accentInk = Color.White,
    accentSoft = Color(0xFFEEF0FC),
    warn = Color(0xFFC2410C),
    warnSoft = Color(0xFFFDEFE7),
    exam = Color(0xFFD94A4A),
    good = Color(0xFF4E9A3E),
    nav = Color.White,
    sheet = Color.White,
    snackbar = Color(0xFF1C1F26),
    orb1 = Color(0x4D8C9BFF),
    orb2 = Color(0x40FFB98F),
    orb3 = Color(0x385FD3B5),
    heroStart = Color(0xFF4F5BD5),
    heroEnd = Color(0xFF8A5CE0),
    tint = 0.16f,
)

val DarkSchulColors = SchulColors(
    isDark = true,
    bg = Color(0xFF0F1115),
    card = Color(0xE6181B21),
    cardBorder = Color(0xFF262A33),
    shadow = Color(0x00000000),
    text = Color(0xFFEEF0F4),
    sub = Color(0xFF9AA1AF),
    line = Color(0xFF22252C),
    accent = Color(0xFF8E99FF),
    accentInk = Color(0xFF0F1115),
    accentSoft = Color(0xFF22264A),
    warn = Color(0xFFFDA56B),
    warnSoft = Color(0xFF3A261C),
    exam = Color(0xFFF26D6D),
    good = Color(0xFF8BD17A),
    nav = Color(0xFF1C1F26),
    sheet = Color(0xFF181B21),
    snackbar = Color(0xFF2A2E37),
    orb1 = Color(0x4D3A46C8),
    orb2 = Color(0x2EC25A2E),
    orb3 = Color(0x2E1F9E86),
    heroStart = Color(0xFF3D47B8),
    heroEnd = Color(0xFF6A3FB8),
    tint = 0.24f,
)

val LocalSchulColors = staticCompositionLocalOf { LightSchulColors }

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    return Typography(
        displayLarge = base.displayLarge.j(),
        displayMedium = base.displayMedium.j(),
        displaySmall = base.displaySmall.j(),
        headlineLarge = base.headlineLarge.j(),
        headlineMedium = base.headlineMedium.j(),
        headlineSmall = base.headlineSmall.j(),
        titleLarge = base.titleLarge.j(),
        titleMedium = base.titleMedium.j(),
        titleSmall = base.titleSmall.j(),
        bodyLarge = base.bodyLarge.j(),
        bodyMedium = base.bodyMedium.j(),
        bodySmall = base.bodySmall.j(),
        labelLarge = base.labelLarge.j(),
        labelMedium = base.labelMedium.j(),
        labelSmall = base.labelSmall.j(),
    )
}

/** Feste Textstile der App, angelehnt an die Entwürfe. */
object T {
    val hero = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.6).sp)
    val title = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp)
    val section = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val bigNumber = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, letterSpacing = (-1.2).sp)
    val body = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val bodyRegular = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val small = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val smallBold = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val label = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val overline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.7.sp)
}

@Composable
fun SchulTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkSchulColors else LightSchulColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            background = c.bg, onBackground = c.text, surface = c.sheet, onSurface = c.text,
            surfaceVariant = Color(0xFF22252C), onSurfaceVariant = c.sub, outline = Color(0xFF353943),
            surfaceContainerHigh = c.sheet, surfaceContainerHighest = Color(0xFF22252C), surfaceContainerLow = c.sheet,
            surfaceContainer = c.sheet, error = c.warn,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            background = c.bg, onBackground = c.text, surface = c.sheet, onSurface = c.text,
            surfaceVariant = Color(0xFFEEF0F3), onSurfaceVariant = c.sub, outline = Color(0xFFD5D9E0),
            surfaceContainerHigh = c.sheet, surfaceContainerHighest = Color(0xFFEEF0F3), surfaceContainerLow = c.sheet,
            surfaceContainer = c.sheet, error = c.warn,
        )
    }
    CompositionLocalProvider(LocalSchulColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}

/** Farben, die man für Fächer auswählen kann (gedeckt, passend zum Design). */
val SubjectPalette: List<Long> = listOf(
    0xFF5B7FDB, 0xFFE07A5F, 0xFF3FA7A0, 0xFF6BAF5B, 0xFFD9A441, 0xFF8E6BD9,
    0xFFD96B9E, 0xFF4BA3D9, 0xFF7C8A9E, 0xFFB08968, 0xFFC25B7A, 0xFF5E9E7E,
    0xFFE5484D, 0xFFF08C2E, 0xFFC9B12E, 0xFF2F9E6E, 0xFF2B8CBF, 0xFF6F5BD5,
)
