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
import com.calldad.ui.screens.PairingScreen
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
    incomingCall: Boolean = false
) {
    // Killed-app entry (FCM full-screen intent): drop straight into the
    // incoming overlay. The overlay itself validates the static room —
    // a stale push bounces home instead of stranding. Single-shot.
    LaunchedEffect(incomingCall) {
        if (incomingCall) {
            navController.navigate("${Routes.CALL}?mode=incoming") {
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
            HomeScreen(
                onNavigate = navController::navigateGuarded,
                onOpenPairing = {
                    navController.navigate(Routes.PAIRING) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = "${Routes.CALL}?mode={mode}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "caller" }
            )
        ) { entry ->
            CallScreen(
                onFinished = navController::returnHome,
                mode = entry.arguments?.getString("mode") ?: "caller"
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
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = { navController.popBackStack() }
            )
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
