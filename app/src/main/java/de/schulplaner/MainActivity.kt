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
        val crash = java.io.File(filesDir, CRASH_FILE)
        if (crash.exists()) {
            showCrash(crash)
            return
        }
        // Aus Widget/Mitteilung: direkt eine Seite öffnen (nur beim ersten Start, nicht nach Drehen).
        val route = if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ROUTE) else null
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle("system")
            SchulTheme(theme) {
                AppRoot(startRoute = route)
            }
        }
    }

    /** Zeigt den letzten Absturz (ohne Compose), mit Kopieren und Weiter. */
    private fun showCrash(file: java.io.File) {
        val text = runCatching { file.readText() }.getOrDefault("")
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
            setBackgroundColor(0xFFF4F5F9.toInt())
        }
        root.addView(android.widget.TextView(this).apply {
            this.text = "Die App ist beim letzten Mal abgestürzt. Bitte kopieren und an Claude schicken:"
            textSize = 16f
            setTextColor(0xFF111418.toInt())
        })
        val buttons = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        buttons.addView(android.widget.Button(this).apply {
            this.text = "Kopieren"
            setOnClickListener {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Absturz", text))
            }
        })
        buttons.addView(android.widget.Button(this).apply {
            this.text = "Weiter"
            setOnClickListener {
                file.delete()
                recreate()
            }
        })
        root.addView(buttons)
        root.addView(android.widget.ScrollView(this).apply {
            addView(android.widget.TextView(this@MainActivity).apply {
                this.text = text
                textSize = 11f
                setTextColor(0xFF333333.toInt())
                setTextIsSelectable(true)
            })
        })
        setContentView(root)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
