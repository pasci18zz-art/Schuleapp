package de.schulplaner.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Aktuelle Uhrzeit, aktualisiert sich alle 30 Sekunden. */
@Composable
fun rememberNow(): LocalDateTime =
    produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }.value

/** Innenabstand für Seiten: oben Statusleiste, unten Platz für die Navigationsleiste. */
@Composable
fun screenPadding(horizontal: Int = 16): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LocalDensity.current
    return PaddingValues(start = horizontal.dp, end = horizontal.dp, top = top + 8.dp, bottom = bottom + 100.dp)
}
