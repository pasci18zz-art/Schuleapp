package de.schulplaner.ui.today

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schulplaner.app
import de.schulplaner.ui.components.GlassCard
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Die drei Apps auf der Startseite. Lange drücken = andere App auswählen. */
private enum class QuickApp(
    val key: String,
    val fallbackName: String,
    val candidates: List<String>,
    val colorA: Color,
    val colorB: Color,
    val icon: () -> ImageVector,
) {
    NOTES(
        "app_notes", "Notes",
        listOf("com.samsung.android.app.notes", "com.google.android.keep"),
        Color(0xFFFF8A4C), Color(0xFFF2553D), { Ic.Note },
    ),
    CLOUD(
        "app_cloud", "Cloud",
        listOf("com.microsoft.skydrive", "com.google.android.apps.docs", "com.samsung.android.scloud", "com.dropbox.android"),
        Color(0xFF5AB2FF), Color(0xFF2F6FE0), { Ic.Cloud },
    ),
    MUSIC(
        "app_music", "Spotify",
        listOf("com.spotify.music"),
        Color(0xFF3BD873), Color(0xFF14A04A), { Ic.Music },
    ),
}

private fun isInstalled(ctx: Context, pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

private fun resolve(ctx: Context, app: QuickApp, saved: String?): String? =
    saved?.takeIf { isInstalled(ctx, it) } ?: app.candidates.firstOrNull { isInstalled(ctx, it) }

private fun appIcon(ctx: Context, pkg: String): ImageBitmap? =
    runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull()

private fun appLabel(ctx: Context, pkg: String): String? =
    runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()

@Composable
fun AppShortcutsRow(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val links by prefs.appLinks.collectAsStateWithLifecycle(emptyMap())
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<QuickApp?>(null) }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickApp.entries.forEach { app ->
            val pkg = remember(links[app.key]) { resolve(ctx, app, links[app.key]) }
            val icon = remember(pkg) { pkg?.let { appIcon(ctx, it) } }
            val label = remember(pkg) { pkg?.let { appLabel(ctx, it) } ?: app.fallbackName }
            GlassCard(
                Modifier.weight(1f),
                padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
                onClick = {
                    val launch = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
                    if (launch != null) {
                        ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        Toast.makeText(ctx, "App nicht gefunden – bitte auswählen", Toast.LENGTH_SHORT).show()
                        picking = app
                    }
                },
                onLongClick = { picking = app },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (icon != null) {
                        Image(icon, label, Modifier.size(40.dp))
                    } else {
                        FallbackIcon(app)
                    }
                    Text(label, style = T.smallBold, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    picking?.let { app ->
        AppPickerDialog(
            title = "App für „${app.fallbackName}“",
            onDismiss = { picking = null },
        ) { pkg ->
            scope.launch { prefs.setAppLink(app.key, pkg) }
            picking = null
        }
    }
}

/** Eigenes Symbol, falls die App nicht installiert ist. */
@Composable
private fun FallbackIcon(app: QuickApp) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(app.colorA, app.colorB))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(app.icon(), null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private class LaunchableApp(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = colors
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .map { pkg -> LaunchableApp(pkg, appLabel(ctx, pkg) ?: pkg, appIcon(ctx, pkg)) }
                .sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            val list = apps
            if (list == null) {
                Text("Apps werden geladen …", style = T.small, color = c.sub)
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    items(list, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a.pkg) }.padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (a.icon != null) Image(a.icon, null, Modifier.size(32.dp)) else Box(Modifier.size(32.dp))
                            Text(a.label, style = T.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}
