// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// navigation/AppNavigation.kt
// Location: app/src/main/java/com/calldad/navigation/AppNavigation.kt
package com.calldad.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calldad.ui.components.ParentGate
import com.calldad.ui.screens.CallScreen
import com.calldad.ui.screens.CallState
import com.calldad.ui.screens.GameScreen
import com.calldad.ui.screens.HelperScreen
import com.calldad.ui.screens.HomeScreen
import com.calldad.ui.screens.PairingScreen
import com.calldad.ui.screens.PttScreen
import com.calldad.ui.screens.callViewModel
import com.calldad.ui.screens.isLive
import com.calldad.ui.screens.rememberPttViewModel

private const val CALL_ROUTE = "${Routes.CALL}?mode={mode}"
private const val INCOMING_CALL = "${Routes.CALL}?mode=incoming"

/**
 * The complete navigation graph.
 *
 * Child-safety rules baked in here (not in the screens):
 *  1. `launchSingleTop = true` -> 40 rapid taps on "Call Dad" push exactly ONE destination.
 *  2. Every "back home" path uses `popUpTo(HOME)` -> the back stack never grows unbounded.
 *  3. A live incoming ring pulls the kid to the ring screen from ANY screen
 *     (the call session is activity-scoped and always listening).
 *  4. Pairing sits behind a grown-ups-only gate.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    incomingCallRequest: Int = 0
) {
    val callViewModel = callViewModel()
    val callState by callViewModel.state.collectAsStateWithLifecycle()

    // Walkie-talkie session lives app-wide too: Dad's voice clips play on
    // any screen, and a live video call owns the mic and speaker.
    val pttViewModel = rememberPttViewModel()
    val callLive = callState.isLive
    LaunchedEffect(callLive) { pttViewModel.onCallStateChanged(callLive) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(incomingCallRequest) {
        if (incomingCallRequest > 0) navController.openCall(INCOMING_CALL)
    }

    LaunchedEffect(callState, currentRoute) {
        val s = callState
        val onCallOrGame = currentRoute == CALL_ROUTE || currentRoute == Routes.GAME
        if (s is CallState.Ringing && s.isIncoming && currentRoute != CALL_ROUTE) {
            navController.openCall(INCOMING_CALL)
        } else if (s is CallState.Connected && !onCallOrGame && currentRoute != null) {
            navController.openCall(INCOMING_CALL)
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
                    navController.navigate(Routes.PAIRING) { launchSingleTop = true }
                }
            )
        }
        composable(
            route = CALL_ROUTE,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "caller" }
            )
        ) { entry ->
            CallScreen(
                onFinished = navController::returnHome,
                onOpenGame = { navController.navigateGuarded(Routes.GAME) },
                mode = entry.arguments?.getString("mode") ?: "caller"
            )
        }
        composable(Routes.PTT) {
            PttScreen(onBackHome = navController::returnHome)
        }
        composable(Routes.GAME) {
            GameScreen(
                onBack = {
                    if (callViewModel.state.value is CallState.Connected) {
                        if (!navController.popBackStack(CALL_ROUTE, inclusive = false)) {
                            navController.openCall(INCOMING_CALL)
                        }
                    } else {
                        navController.returnHome()
                    }
                }
            )
        }
        composable(Routes.HELPER) {
            HelperScreen(onBackHome = navController::returnHome)
        }
        composable(Routes.PAIRING) {
            var unlocked by rememberSaveable { mutableStateOf(false) }
            if (unlocked) {
                PairingScreen(onPaired = { navController.returnHome() })
            } else {
                ParentGate(
                    onUnlocked = { unlocked = true },
                    onCancel = { navController.returnHome() }
                )
            }
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

/** Opens the call screen on top of Home (never deeper), single-top. */
private fun NavHostController.openCall(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

/** Collapses the stack back to Home. Used by every "get me out of here" affordance. */
private fun NavHostController.returnHome() {
    navigate(Routes.HOME) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
