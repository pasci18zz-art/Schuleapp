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
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle("system")
            SchulTheme(theme) {
                AppRoot()
            }
        }
    }
}
