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
    val orbA: Color,
    val orbB: Color,
    val orbC: Color,
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
    val good: Color,
    val nav: Color,
    val sheet: Color,
    /** Deckkraft für Fächerfarben als Hintergrund (Kacheln im Stundenplan). */
    val tint: Float,
)

val LightSchulColors = SchulColors(
    isDark = false,
    bg = Color(0xFFEDF0F6),
    orbA = Color(0x737A96FF),
    orbB = Color(0x66BEA0FF),
    orbC = Color(0x6BFFBE96),
    card = Color(0x94FFFFFF),
    cardBorder = Color(0xCCFFFFFF),
    shadow = Color(0x141E2850),
    text = Color(0xFF12151C),
    sub = Color(0xFF555C6B),
    line = Color(0x1412151C),
    accent = Color(0xFF4353C9),
    accentInk = Color.White,
    accentSoft = Color(0x1F4353C9),
    warn = Color(0xFFB4410E),
    warnSoft = Color(0x1AB4410E),
    good = Color(0xFF4E9A3E),
    nav = Color(0xDBFFFFFF),
    sheet = Color(0xFFF7F8FC),
    tint = 0.18f,
)

val DarkSchulColors = SchulColors(
    isDark = true,
    bg = Color(0xFF0D1016),
    orbA = Color(0x61465FDC),
    orbB = Color(0x4D8250DC),
    orbC = Color(0x33DC7850),
    card = Color(0x12FFFFFF),
    cardBorder = Color(0x1AFFFFFF),
    shadow = Color(0x59000000),
    text = Color(0xFFEEF0F5),
    sub = Color(0xFFA3A9B8),
    line = Color(0x1AFFFFFF),
    accent = Color(0xFF8C9BFF),
    accentInk = Color(0xFF0D1016),
    accentSoft = Color(0x298C9BFF),
    warn = Color(0xFFFDA56B),
    warnSoft = Color(0x24FDA56B),
    good = Color(0xFF8BD17A),
    nav = Color(0xE61C202A),
    sheet = Color(0xFF181C24),
    tint = 0.26f,
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
    val hero = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, letterSpacing = (-0.6).sp)
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
            surfaceVariant = Color(0xFF232836), onSurfaceVariant = c.sub, outline = Color(0xFF3A4050),
            surfaceContainerHigh = c.sheet, surfaceContainerHighest = Color(0xFF232836), surfaceContainerLow = c.sheet,
            surfaceContainer = c.sheet, error = c.warn,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            background = c.bg, onBackground = c.text, surface = c.sheet, onSurface = c.text,
            surfaceVariant = Color(0xFFE6E9F2), onSurfaceVariant = c.sub, outline = Color(0xFFC5CAD6),
            surfaceContainerHigh = c.sheet, surfaceContainerHighest = Color(0xFFE6E9F2), surfaceContainerLow = c.sheet,
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
)
