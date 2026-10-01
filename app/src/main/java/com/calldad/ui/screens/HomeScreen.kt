// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeScreen.kt
// Location: app/src/main/java/com/calldad/ui/screens/HomeScreen.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ui.components.GiantActionCard
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.CallGreen
import com.calldad.ui.theme.ChatPurple
import com.calldad.ui.theme.GameBlue
import com.calldad.ui.theme.HelperPurple
import com.calldad.ui.theme.PhotoAmber
import com.calldad.ui.theme.PttOrange

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenConsent: () -> Unit,
    onStartCall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            (LocalContext.current.applicationContext as Application)
        )
    )
) {
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val callback by viewModel.callback.collectAsStateWithLifecycle()
    val isPaired by callViewModel().isPaired.collectAsStateWithLifecycle()

    HomeContent(
        destinations = destinations,
        showPairingHint = isPaired == false,
        callback = callback,
        onCallbackTapped = {
            viewModel.onCallbackTapped()
            onStartCall()
        },
        onActionSelected = onNavigate,
        onOpenPairing = onOpenPairing,
        onOpenConsent = onOpenConsent,
        modifier = modifier
    )
}

/**
 * Stateless. State is hoisted into [HomeViewModel] and the nav lambda.
 *
 * LAYOUT CONTRACT: the grid is built from plain Rows inside a Column with
 * weighted heights. There is NO LazyVerticalGrid and NO scroll container, so
 * there is no nested scrolling for a child to get stuck in — everything is
 * always on screen and always one tap away.
 *
 * The callback card takes its space from the rows, not by pushing them off
 * screen: a grid whose last row can be scrolled out of reach is a grid that
 * stops being one-tap-everything, and that contract is the reason this screen
 * has no scroll container at all.
 */
@Composable
private fun HomeContent(
    destinations: List<HomeDestination>,
    showPairingHint: Boolean,
    callback: com.calldad.history.CallRecord?,
    onCallbackTapped: () -> Unit,
    onActionSelected: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenConsent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hi! What do you want to do?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (showPairingHint) {
                    Text(
                        text = "Grown-ups: tap the gear to pair this phone first.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                callback?.let { record ->
                    CallbackCard(
                        wasOutgoing = record.wasOutgoing,
                        onTap = onCallbackTapped
                    )
                }

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
            }
        }

        // The grown-ups area, top-end. TWO 64dp targets rather than one: the
        // kill switch has to be reachable by a parent, and burying it inside a
        // sub-menu of the gear is how a control that "exists but nobody can find
        // it" happens. Both land on gated screens, so neither is a child-reachable
        // capability — the gate is the boundary, not the button count.
        //
        // Visually subordinate to the tiles (50% alpha, small) so a 6-year-old
        // does not press them by accident.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenConsent,
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = "Grown-ups settings" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            IconButton(
                onClick = onOpenPairing,
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = "Pair devices" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * The missed-call callback card (BP-05 §4).
 *
 * WHY IT EXISTS. The product is for infrequent scheduled visits, which makes a
 * missed call the NORMAL case rather than the exception. A 6-year-old who taps
 * the giant green button, gets no answer, and has no way to try again learns that
 * the button does not always work — and "the button always works" is the entire
 * product promise. So a miss raises one card with one action.
 *
 * Deliberate choices:
 *  - ONE card, ever, for the most recent miss. Two would ask a child to choose
 *    between two moments, which they cannot do.
 *  - A DECLINE does not raise it. A grown-up who declined is busy, and
 *    re-ringing them because the child asked again is nagging on their behalf.
 *  - The wording is a fact, not a feeling. No "are you ok?", no emoji, no
 *    exclamation count. A child must be able to re-read it and not infer
 *    something worse than what happened.
 *  - It is a card, not a dialog: it cannot block anything, and it cannot be
 *    missed by a child who looks at the tiles first.
 */
@Composable
private fun CallbackCard(wasOutgoing: Boolean, onTap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (wasOutgoing) {
                    "Dad didn't answer. Tap to call again."
                } else {
                    "You missed a call. Tap to call again."
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class ActionVisuals(val icon: ImageVector, val container: Color)

private fun HomeDestination.visuals(): ActionVisuals = when (this) {
    HomeDestination.CALL -> ActionVisuals(Icons.Filled.Call, CallGreen)
    HomeDestination.CHAT -> ActionVisuals(Icons.AutoMirrored.Filled.Chat, ChatPurple)
    HomeDestination.PHOTO -> ActionVisuals(Icons.Filled.Photo, PhotoAmber)
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
            showPairingHint = false,
            callback = null,
            onCallbackTapped = {},
            onActionSelected = {},
            onOpenPairing = {},
            onOpenConsent = {}
        )
    }
}
