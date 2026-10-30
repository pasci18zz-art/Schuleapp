package de.schulplaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.ui.AppRoot
import de.schulplaner.ui.theme.SchulTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Aus Widget/Mitteilung: direkt eine Seite öffnen (nur beim ersten Start, nicht nach Drehen).
        val route = if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ROUTE) else null
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle("system")
            SchulTheme(theme) {
                AppRoot(startRoute = route)
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
