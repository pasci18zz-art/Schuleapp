package de.schulplaner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.schulplaner.ui.components.AppBackground
import de.schulplaner.ui.components.Ic
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

object Routes {
    const val TODAY = "today"
    const val TIMETABLE = "timetable"
    const val TASKS = "tasks"
    const val EXAMS = "exams"
    const val EXAM = "exam/{id}"
    const val GRADES = "grades"
    const val SUBJECT_GRADES = "grades/{subjectId}/{termId}"
    const val SETTINGS = "settings"
    const val SET_SUBJECTS = "settings/subjects"
    const val SET_TERMS = "settings/terms"
    const val SET_PERIODS = "settings/periods"
    const val SET_HOLIDAYS = "settings/holidays"

    fun exam(id: String) = "exam/$id"
    fun subjectGrades(subjectId: String, termId: String) = "grades/$subjectId/$termId"
}

private data class Tab(val route: String, val icon: ImageVector, val label: String, val matches: List<String>)

private val tabs = listOf(
    Tab(Routes.TODAY, Ic.Home, "Heute", listOf("today", "settings")),
    Tab(Routes.TIMETABLE, Ic.Calendar, "Stundenplan", listOf("timetable")),
    Tab(Routes.TASKS, Ic.Tasks, "Aufgaben", listOf("tasks")),
    Tab(Routes.EXAMS, Ic.Exam, "Klausuren", listOf("exams", "exam")),
    Tab(Routes.GRADES, Ic.Grades, "Noten", listOf("grades")),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    AppBackground {
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.TODAY) { Centered(wide = true) { TodayScreen(nav) } }
            composable(Routes.TIMETABLE) { Centered { TimetableScreen() } }
            composable(Routes.TASKS) { Centered { TasksScreen() } }
            composable(Routes.EXAMS) { Centered { ExamsScreen(nav) } }
            composable(Routes.EXAM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                Centered { ExamDetailScreen(entry.arguments?.getString("id") ?: "", nav) }
            }
            composable(Routes.GRADES) { Centered { GradesOverviewScreen(nav) } }
            composable(
                Routes.SUBJECT_GRADES,
                arguments = listOf(
                    navArgument("subjectId") { type = NavType.StringType },
                    navArgument("termId") { type = NavType.StringType },
                ),
            ) { entry ->
                Centered {
                    SubjectGradesScreen(
                        subjectId = entry.arguments?.getString("subjectId") ?: "",
                        termId = entry.arguments?.getString("termId") ?: "",
                        nav = nav,
                    )
                }
            }
            composable(Routes.SETTINGS) { Centered { SettingsScreen(nav) } }
            composable(Routes.SET_SUBJECTS) { Centered { SubjectsScreen(nav) } }
            composable(Routes.SET_TERMS) { Centered { TermsScreen(nav) } }
            composable(Routes.SET_PERIODS) { Centered { PeriodsScreen(nav) } }
            composable(Routes.SET_HOLIDAYS) { Centered { HolidaysScreen(nav) } }
        }
        BottomNav(nav, Modifier.align(Alignment.BottomCenter))
    }
}

/** Auf dem Tablet wird der Inhalt mittig mit maximaler Breite gezeigt. */
@Composable
private fun Centered(wide: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = if (wide) 1400.dp else 760.dp)) { content() }
    }
}

@Composable
private fun BottomNav(nav: NavHostController, modifier: Modifier) {
    val c = colors
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.TODAY
    val shape = RoundedCornerShape(29.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(if (c.isDark) 0.dp else 14.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.nav)
            .border(1.dp, c.cardBorder, shape)
            .height(58.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val root = route.substringBefore('/')
            val active = tab.matches.any { it == root }
            val bg by animateColorAsState(if (active) c.accent else Color.Transparent, tween(220), label = "nav-bg")
            val tint by animateColorAsState(if (active) c.accentInk else c.sub, tween(220), label = "nav-tint")
            Box(
                Modifier
                    .size(width = 50.dp, height = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .semantics { contentDescription = tab.label }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) {
                        if (!active || route != tab.route) {
                            nav.navigate(tab.route) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = !active
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
