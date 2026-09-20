// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// navigation/AppNavigation.kt
// Location: app/src/main/java/com/calldad/navigation/AppNavigation.kt
package com.calldad.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calldad.ui.screens.CallScreen
import com.calldad.ui.screens.GameScreen
import com.calldad.ui.screens.HelperScreen
import com.calldad.ui.screens.HomeScreen
import com.calldad.ui.screens.PttScreen

/**
 * The complete Phase 1 navigation graph.
 *
 * Child-safety rules baked in here (not in the screens):
 *  1. `launchSingleTop = true` -> 40 rapid taps on "Call Dad" push exactly ONE destination.
 *  2. Every "back home" path uses `popUpTo(HOME)` -> the back stack can never grow unbounded,
 *     so the child can never get lost three screens deep.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    incomingCallId: String? = null
) {
    // Killed-app entry (FCM full-screen intent): drop straight into the
    // incoming overlay for that call. Single-shot per cold start.
    LaunchedEffect(incomingCallId) {
        if (!incomingCallId.isNullOrBlank()) {
            navController.navigate("${Routes.CALL}?mode=incoming&callId=$incomingCallId") {
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = navController::navigateGuarded)
        }
        composable(
            route = "${Routes.CALL}?mode={mode}&callId={callId}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "caller" },
                navArgument("callId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            CallScreen(
                onFinished = navController::returnHome,
                mode = entry.arguments?.getString("mode") ?: "caller",
                callId = entry.arguments?.getString("callId")?.ifBlank { null }
            )
        }
        composable(Routes.PTT) {
            PttScreen(onBackHome = navController::returnHome)
        }
        composable(Routes.GAME) {
            GameScreen(onBackHome = navController::returnHome)
        }
        composable(Routes.HELPER) {
            HelperScreen(onBackHome = navController::returnHome)
        }
    }
}

/** Tap-spam guard: never stack duplicate destinations. */
private fun NavHostController.navigateGuarded(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

/** Collapses the stack back to Home. Used by every "get me out of here" affordance. */
private fun NavHostController.returnHome() {
    navigate(Routes.HOME) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
