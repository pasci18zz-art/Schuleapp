package de.schulplaner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.schulplaner.app
import de.schulplaner.ui.components.AppBackground
import de.schulplaner.ui.components.Ic
import de.schulplaner.ui.components.ConfettiOverlay
import de.schulplaner.ui.components.ConfettiState
import de.schulplaner.ui.components.LocalConfetti
import de.schulplaner.ui.components.LocalUndo
import de.schulplaner.ui.components.UndoBar
import de.schulplaner.ui.components.UndoState
import de.schulplaner.ui.components.colors
import de.schulplaner.ui.exams.ExamDetailScreen
import de.schulplaner.ui.exams.ExamsScreen
import de.schulplaner.ui.grades.GradesOverviewScreen
import de.schulplaner.ui.grades.SubjectGradesScreen
import de.schulplaner.ui.settings.HolidaysScreen
import de.schulplaner.ui.settings.PeriodsScreen
import de.schulplaner.ui.settings.SettingsScreen
import de.schulplaner.ui.settings.SubjectsScreen
import de.schulplaner.ui.settings.TermsScreen
import de.schulplaner.ui.tasks.TasksScreen
import de.schulplaner.ui.timetable.TimetableScreen
import de.schulplaner.ui.today.TodayScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

object Routes {
    const val MAIN = "main"
    const val EXAM = "exam/{id}"
    const val SUBJECT_GRADES = "grades/{subjectId}/{termId}"
    const val SET_SUBJECTS = "settings/subjects"
    const val SET_TERMS = "settings/terms"
    const val SET_PERIODS = "settings/periods"
    const val SET_HOLIDAYS = "settings/holidays"
    const val SET_KINDS = "settings/kinds"

    fun exam(id: String) = "exam/$id"
    fun subjectGrades(subjectId: String, termId: String) = "grades/$subjectId/$termId"
}

object Tabs {
    const val TODAY = 0
    const val TIMETABLE = 1
    const val TASKS = 2
    const val EXAMS = 3
    const val GRADES = 4
    const val SETTINGS = 5
}

private data class Tab(val icon: ImageVector, val label: String, val owns: String?)

private val tabs = listOf(
    Tab(Ic.Home, "Heute", null),
    Tab(Ic.Calendar, "Stundenplan", null),
    Tab(Ic.Tasks, "Aufgaben", null),
    Tab(Ic.Exam, "Klausuren", "exam"),
    Tab(Ic.Grades, "Noten", "grades"),
    Tab(Ic.Settings, "Einstellungen", "settings"),
)

/** Navigation: Tabs liegen nebeneinander (wischbar), Unterseiten öffnen sich darüber. */
class AppNavigator(val nav: NavHostController, val pager: PagerState, private val scope: CoroutineScope) {
    /** Zu einem Tab wechseln – immer auf dessen Startseite (Unterseiten werden geschlossen). */
    fun tab(index: Int) {
        if (nav.currentDestination?.route != Routes.MAIN) nav.popBackStack(Routes.MAIN, inclusive = false)
        scope.launch { pager.animateScrollToPage(index, animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f)) }
    }

    fun open(route: String) = nav.navigate(route)
    fun back() = nav.popBackStack()
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("Kein Navigator") }

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val navigator = remember(nav, pager) { AppNavigator(nav, pager, scope) }
    val undo = remember { UndoState() }
    val confetti = remember { ConfettiState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.MAIN

    NotificationPermission()

    CompositionLocalProvider(LocalNavigator provides navigator, LocalUndo provides undo, LocalConfetti provides confetti) {
        AppBackground {
            NavHost(
                navController = nav,
                startDestination = Routes.MAIN,
                enterTransition = { slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { slideOutHorizontally(tween(260)) { -it / 10 } + fadeOut(tween(200)) },
                popEnterTransition = { slideInHorizontally(tween(260)) { -it / 10 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 3 } + fadeOut(tween(200)) },
            ) {
                composable(Routes.MAIN) { MainPager(pager) }
                composable(Routes.EXAM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    Centered { ExamDetailScreen(e.arguments?.getString("id") ?: "") }
                }
                composable(
                    Routes.SUBJECT_GRADES,
                    arguments = listOf(
                        navArgument("subjectId") { type = NavType.StringType },
                        navArgument("termId") { type = NavType.StringType },
                    ),
                ) { e ->
                    Centered {
                        SubjectGradesScreen(e.arguments?.getString("subjectId") ?: "", e.arguments?.getString("termId") ?: "")
                    }
                }
                composable(Routes.SET_SUBJECTS) { Centered { SubjectsScreen() } }
                composable(Routes.SET_TERMS) { Centered { TermsScreen() } }
                composable(Routes.SET_PERIODS) { Centered { PeriodsScreen() } }
                composable(Routes.SET_HOLIDAYS) { Centered { HolidaysScreen() } }
                composable(Routes.SET_KINDS) { Centered { de.schulplaner.ui.settings.TaskKindsScreen() } }
            }
            UndoBar(undo, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp))
            BottomNav(navigator, route, Modifier.align(Alignment.BottomCenter))
            ConfettiOverlay(confetti)
        }
        BackHandler(enabled = route == Routes.MAIN && pager.currentPage != Tabs.TODAY) { navigator.tab(Tabs.TODAY) }
    }
}

@Composable
private fun MainPager(pager: PagerState) {
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        when (page) {
            Tabs.TODAY -> Centered(wide = true) { TodayScreen() }
            Tabs.TIMETABLE -> Centered(wide = true) { TimetableScreen() }
            Tabs.TASKS -> Centered { TasksScreen() }
            Tabs.EXAMS -> Centered { ExamsScreen() }
            Tabs.GRADES -> Centered { GradesOverviewScreen() }
            else -> Centered { SettingsScreen() }
        }
    }
}

/** Auf dem Tablet wird der Inhalt mittig mit maximaler Breite gezeigt. */
@Composable
private fun Centered(wide: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = if (wide) 1400.dp else 760.dp)) { content() }
    }
}

private val ItemW = 48.dp
private val ItemGap = 2.dp
private val BarPad = 6.dp

@Composable
private fun BottomNav(navigator: AppNavigator, route: String, modifier: Modifier) {
    val c = colors
    val pager = navigator.pager
    val density = LocalDensity.current
    val onMain = route == Routes.MAIN
    val root = route.substringBefore('/')
    val ownerIndex = tabs.indexOfFirst { it.owns == root }.takeIf { it >= 0 } ?: pager.currentPage
    // Auf der Hauptseite folgt die Markierung direkt dem Wischen, sonst gleitet sie hin.
    val animated by animateFloatAsState(
        if (onMain) pager.currentPage.toFloat() else ownerIndex.toFloat(),
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "nav-pos",
    )
    val position = if (onMain) pager.currentPage + pager.currentPageOffsetFraction else animated
    val shape = RoundedCornerShape(28.dp)
    val step = ItemW + ItemGap

    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(if (c.isDark) 0.dp else 12.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.nav)
            .border(1.dp, c.cardBorder, shape)
            .height(56.dp)
            .padding(horizontal = BarPad)
            // Über die Leiste wischen: der Tab unter dem Finger wird ausgewählt.
            .pointerInputScrub(tabs.size, with(density) { step.toPx() }) { navigator.tab(it) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = step * position)
                .size(ItemW, 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(c.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ItemGap), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, tab ->
                val f = (1f - abs(position - i)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .size(ItemW, 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .semantics { contentDescription = tab.label }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            navigator.tab(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, null, tint = lerp(c.sub, c.accentInk, f), modifier = Modifier.size(21.dp).graphicsLayer { scaleX = 1f + 0.1f * f; scaleY = 1f + 0.1f * f })
                }
            }
        }
    }
}

private fun Modifier.pointerInputScrub(count: Int, stepPx: Float, onSelect: (Int) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(count, stepPx) {
            var last = -1
            detectHorizontalDragGestures(
                onDragStart = { last = -1 },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    val idx = (change.position.x / stepPx).toInt().coerceIn(0, count - 1)
                    if (idx != last) {
                        last = idx
                        onSelect(idx)
                    }
                },
            )
        },
    )

/** Fragt einmalig nach der Erlaubnis für Benachrichtigungen (Erinnerung am Vorabend). */
@Composable
private fun NotificationPermission() {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.notificationsAsked.first() && prefs.reminderOn.first()) {
            prefs.setNotificationsAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
