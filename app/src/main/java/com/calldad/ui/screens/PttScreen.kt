// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PttScreen.kt — Phase 6: engine-driven TX/RX visuals
// Location: app/src/main/java/com/calldad/ui/screens/PttScreen.kt
//
// Phase 1 dimensions preserved (320/280dp center button, 100dp Home).
// Load-bearing gesture change: tryAwaitRelease(), not awaitRelease(), so a
// finger that drifts off the button still releases the mic.
package com.calldad.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calldad.ui.components.GiantIconButton

private val ReceiveBlue = Color(0xFF1565C0)
private val NeutralAmber = Color(0xFFE65100)

@Composable
fun PttScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PttViewModel = rememberPttViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val background by animateColorAsState(
        targetValue = when {
            state.isTransmitting -> com.calldad.ui.theme.PttTransmitRed
            state.isReceiving -> ReceiveBlue
            else -> NeutralAmber
        },
        animationSpec = tween(150),
        label = "pttBackground"
    )

    val statusLabel = when {
        state.isTransmitting -> "TALKING…"
        state.isReceiving -> "DAD IS TALKING…"
        else -> "HOLD TO TALK"
    }

    val hintLabel = when {
        state.isTransmitting -> "Let go when you're done"
        state.isReceiving -> "Wait for Dad to finish"
        else -> "Press and hold the big button"
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GiantIconButton(
                    icon = Icons.Filled.Home,
                    contentDescription = "Back to home",
                    containerColor = Color.White,
                    contentColor = background,
                    size = 100.dp,
                    onClick = onBackHome
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    text = "Walkie Talkie",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            Spacer(Modifier.weight(1f))

            PttCenterButton(
                isTransmitting = state.isTransmitting,
                isReceiving = state.isReceiving,
                onPress = viewModel::onPress,
                onRelease = viewModel::onRelease
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = hintLabel,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            // Inline error toast — no dialogs, no dismiss gesture.
            state.lastError?.let { msg ->
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PttCenterButton(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    // rememberUpdatedState keeps the gesture coroutine pointed at the
    // latest lambdas without restarting pointerInput on every recomposition.
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)

    // Pulse only while transmitting.
    val haloAlpha = if (isTransmitting) {
        val transition = rememberInfiniteTransition(label = "pttHalo")
        val alpha by transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.40f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloAlpha"
        )
        alpha
    } else 0f

    val icon = when {
        isTransmitting -> Icons.Filled.GraphicEq
        isReceiving -> Icons.Filled.GraphicEq
        else -> Icons.Filled.Mic
    }
    val contentColor = when {
        isReceiving -> ReceiveBlue
        else -> if (isTransmitting) com.calldad.ui.theme.PttTransmitRed else NeutralAmber
    }

    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isTransmitting) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = haloAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(elevation = 16.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = 8.dp,
                    color = Color.White.copy(alpha = 0.65f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            currentOnPress()
                            // tryAwaitRelease() returns false if the gesture
                            // was consumed by a parent, and throws if the
                            // button leaves composition mid-hold. Every
                            // outcome is a release: the mic never stays hot.
                            try {
                                tryAwaitRelease()
                            } finally {
                                currentOnRelease()
                            }
                        }
                    )
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = when {
                        isTransmitting -> "Talking. Let go to stop."
                        isReceiving -> "Dad is talking. Please wait."
                        else -> "Hold to talk to Dad"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(110.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        isTransmitting -> "TALKING"
                        isReceiving -> "LISTENING"
                        else -> "HOLD"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
