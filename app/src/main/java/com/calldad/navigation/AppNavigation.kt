// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// navigation/AppNavigation.kt
// Location: app/src/main/java/com/calldad/navigation/AppNavigation.kt
package com.calldad.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calldad.BuildConfig
import com.calldad.R
import com.calldad.ui.components.ParentGate
import com.calldad.ui.screens.CallScreen
import com.calldad.ui.screens.CallState
import com.calldad.ui.screens.ChatScreen
import com.calldad.ui.screens.ConsentScreen
import com.calldad.ui.screens.GameScreen
import com.calldad.ui.screens.HelperScreen
import com.calldad.ui.screens.HomeScreen
import com.calldad.ui.screens.PairingScreen
import com.calldad.ui.screens.PhotoScreen
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
    incomingCallRequest: Int = 0,
    onPickPhoto: () -> Unit = {}
) {
    // BP-05 §4 no-escape: the system back button must land on Home, never on
    // the device launcher. A child pressing back on the Helper or Game screen
    // would otherwise drop out of the app into whatever else the phone has,
    // which is the one escape this app exists to prevent. Screens that need
    // their own back behaviour (Call, Game) install their own BackHandler
    // inside their composable, which takes precedence over this one.
    BackHandler(enabled = navController.previousBackStackEntry != null) {
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    // The "Dad is talking" banner must be the OUTERMOST composable, above the
    // NavHost, or the Game screen's full-screen WebView covers it.
    Box(modifier) {
    val callViewModel = callViewModel()
    val callState by callViewModel.state.collectAsStateWithLifecycle()

    // Walkie-talkie session lives app-wide too: Dad's voice clips play on
    // any screen, and a live video call owns the mic and speaker.
    val pttViewModel = rememberPttViewModel()
    val pttState by pttViewModel.state.collectAsStateWithLifecycle()
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
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = navController::navigateGuarded,
                // The callback card must start an OUTGOING call, so it cannot go
                // through `navigateGuarded(Routes.CALL)`: that would open the
                // screen without a mode, and the Call screen defaults to the
                // caller's own flow. `openCall` is the caller's entry point.
                onStartCall = { navController.openCall("${Routes.CALL}?mode=caller") },
                onOpenPairing = {
                    navController.navigate(Routes.PAIRING) { launchSingleTop = true }
                },
                // Sibling of the gear, and equally gated — see ConsentScreen.
                onOpenConsent = {
                    navController.navigate(Routes.CONSENT) { launchSingleTop = true }
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
        composable(Routes.CHAT) {
            // `ChatViewModel` is ACTIVITY-scoped (see `rememberChatViewModel`):
            // a NavBackStackEntry-scoped instance would be a private copy, so two
            // Firestore listeners would race to tick the same receipts.
            ChatScreen(onBackHome = navController::returnHome)
        }
        composable(Routes.PAIRING) {
            // `rememberSaveable`, not `remember`: process death while on the
            // pairing destination used to restore the child straight INTO
            // PairingScreen with the grown-ups gate already passed. The gate
            // must never survive a process death.
            var unlocked by remember { mutableStateOf(false) }
            if (unlocked) {
                PairingScreen(onPaired = { navController.returnHome() })
            } else {
                ParentGate(
                    onUnlocked = { unlocked = true },
                    onCancel = { navController.returnHome() }
                )
            }
        }
        composable(Routes.PHOTO) {
            // Activity-scoped, same reasoning as the thread: a private copy
            // would run a second Firestore listener and two decodes of the same
            // 800KB photo.
            //
            // The picker is handed to the HOST, not launched here. This lane's
            // rule is that the app holds no camera and no gallery handle of its
            // own, and the only way the child's phone ever selects a picture is a
            // system picker the OS owns.
            PhotoScreen(
                onBackHome = navController::returnHome,
                onPickPhoto = onPickPhoto
            )
        }
        composable(Routes.CONSENT) {
            // The kill switch's ONLY trigger, and unreachable from the child's
            // flow. `ConsentScreen` installs its own ParentGate internally, with
            // the same `remember` (not `rememberSaveable`) discipline as pairing
            // so a process death restores the child to the GATE and never into
            // the controls.
            ConsentScreen(onBackHome = navController::returnHome)
        }
    }

    // A parent's voice arriving on the Game screen, with no visual cue and no
    // way to replay it (the clip is deleted after playing), is a message the
    // child cannot attribute and cannot recover. One unmissable banner, on
    // every screen, including Game.
    if (pttState.isReceiving) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(
                    R.string.ptt_receiving_banner,
                    stringResource(
                        if (BuildConfig.APP_THEME == "blue") {
                            R.string.child_peer_name
                        } else {
                            R.string.parent_peer_name
                        }
                    )
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
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
