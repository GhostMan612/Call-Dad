// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeScreen.kt
// Location: app/src/main/java/com/calldad/ui/screens/HomeScreen.kt
package com.calldad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.navigation.Routes
import com.calldad.ui.components.GiantActionCard
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.CallGreen
import com.calldad.ui.theme.GameBlue
import com.calldad.ui.theme.HelperPurple
import com.calldad.ui.theme.PttOrange

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()

    // Auto-popup: a NEW offer while Home is visible jumps straight to the
    // incoming overlay (with ringtone). navigateGuarded keeps it single-top.
    LaunchedEffect(Unit) {
        viewModel.incomingCall.collect {
            onNavigate("${Routes.CALL}?mode=incoming")
        }
    }

    HomeContent(
        destinations = destinations,
        onActionSelected = onNavigate,
        modifier = modifier
    )
}

/**
 * Stateless. State is hoisted into [HomeViewModel] and the nav lambda.
 *
 * LAYOUT CONTRACT: the 2x2 grid is built from plain Rows inside a Column with
 * weighted heights. There is NO LazyVerticalGrid and NO scroll container, so
 * there is no nested scrolling for a child to get stuck in — everything is
 * always on screen and always one tap away.
 */
@Composable
private fun HomeContent(
    destinations: List<HomeDestination>,
    onActionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hi! What do you want to do?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            destinations.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { destination ->
                        val visuals = destination.visuals()
                        GiantActionCard(
                            label = destination.label,
                            icon = visuals.icon,
                            containerColor = visuals.container,
                            onClick = { onActionSelected(destination.route) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    // Preserve the 2x2 rhythm if a row is ever short.
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // QA-only incoming-call hook. DEBUG builds only — never ships.
            // Opens the Call screen in incoming mode so simulateIncomingCall()
            // drives the REAL answerCall() path (no fake overlay, no FCM yet).
            // FIXED height: GiantActionCard's inner fillMaxSize Column would
            // otherwise claim the whole Column and squeeze the grid to zero.
            if (com.calldad.BuildConfig.DEBUG) {
                GiantActionCard(
                    label = "QA: incoming call",
                    icon = androidx.compose.material.icons.Icons.Filled.Call,
                    containerColor = androidx.compose.ui.graphics.Color(0xFF616161),
                    onClick = { onActionSelected("${com.calldad.navigation.Routes.CALL}?mode=incoming") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }
    }
}

private data class ActionVisuals(val icon: ImageVector, val container: Color)

private fun HomeDestination.visuals(): ActionVisuals = when (this) {
    HomeDestination.CALL -> ActionVisuals(Icons.Filled.Call, CallGreen)
    HomeDestination.PTT -> ActionVisuals(Icons.Filled.SettingsVoice, PttOrange)
    HomeDestination.GAME -> ActionVisuals(Icons.Filled.SportsEsports, GameBlue)
    HomeDestination.HELPER -> ActionVisuals(Icons.Filled.SmartToy, HelperPurple)
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun HomeContentPreview() {
    CallDadTheme {
        HomeContent(
            destinations = HomeDestination.entries.toList(),
            onActionSelected = {}
        )
    }
}
