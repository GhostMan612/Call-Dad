// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PttScreen.kt
// Location: app/src/main/java/com/calldad/app/ui/screens/PttScreen.kt
package com.calldad.app.ui.screens

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.app.ui.components.GiantIconButton
import com.calldad.app.ui.theme.CallDadTheme
import com.calldad.app.ui.theme.PttOrange
import com.calldad.app.ui.theme.PttTransmitRed

@Composable
fun PttScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PttViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PttContent(
        isTransmitting = state.isTransmitting,
        onPressStart = viewModel::onPressStart,
        onPressEnd = viewModel::onPressEnd,
        onBackHome = onBackHome,
        modifier = modifier
    )
}

/**
 * SOVEREIGN MANTLE PLACEHOLDER.
 *
 * Press-and-hold is the ONE sanctioned exception to the "single tap only" rule —
 * it is the entire point of a walkie-talkie. Every other interaction on this
 * screen remains a single tap.
 *
 * The whole screen re-colours on state change (orange -> deep red) so the
 * "Listening" vs "Transmitting" distinction does not rely on text at all.
 */
@Composable
private fun PttContent(
    isTransmitting: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (isTransmitting) PttTransmitRed else PttOrange,
        animationSpec = tween(durationMillis = 150),
        label = "pttScreenBackground"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- HEADER ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GiantIconButton(
                    icon = Icons.Filled.Home,
                    contentDescription = "Back to home",
                    containerColor = Color.White,
                    contentColor = if (isTransmitting) PttTransmitRed else PttOrange,
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

            // ---------- PUSH-TO-TALK BUTTON ----------
            PttButton(
                isTransmitting = isTransmitting,
                onPressStart = onPressStart,
                onPressEnd = onPressEnd
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = if (isTransmitting) "TALKING" else "Listening…",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (isTransmitting) "Dad can hear you" else "Hold the big button to talk",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PttButton(
    isTransmitting: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep the gesture handler pointed at the freshest lambdas without
    // restarting the pointerInput coroutine on every recomposition.
    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnPressEnd by rememberUpdatedState(onPressEnd)
    val currentIsTransmitting by rememberUpdatedState(isTransmitting)

    val haloAlpha by rememberInfiniteTransition(label = "pttHalo")
        .animateFloat(
            initialValue = 0.10f,
            targetValue = 0.30f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pttHaloAlpha"
        )

    val buttonColor by animateColorAsState(
        targetValue = Color.White,
        label = "pttButtonColor"
    )
    val contentColor = if (isTransmitting) PttTransmitRed else PttOrange

    Box(
        modifier = modifier.size(340.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing halo — only alive while transmitting.
        if (isTransmitting) {
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = haloAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(300.dp)
                .shadow(elevation = 16.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(buttonColor)
                .border(width = 8.dp, color = Color.White.copy(alpha = 0.65f), shape = CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            currentOnPressStart()
                            tryAwaitRelease()
                            currentOnPressEnd()
                        }
                    )
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (currentIsTransmitting) {
                        "Talking. Let go to stop."
                    } else {
                        "Hold to talk to Dad"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isTransmitting) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(110.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isTransmitting) "TALKING" else "HOLD",
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PttContentListeningPreview() {
    CallDadTheme {
        PttContent(
            isTransmitting = false,
            onPressStart = {},
            onPressEnd = {},
            onBackHome = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PttContentTransmittingPreview() {
    CallDadTheme {
        PttContent(
            isTransmitting = true,
            onPressStart = {},
            onPressEnd = {},
            onBackHome = {}
        )
    }
}
