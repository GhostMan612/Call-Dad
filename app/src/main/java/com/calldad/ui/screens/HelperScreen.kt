// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HelperScreen.kt — Phase 9: single-button voice helper
// Location: app/src/main/java/com/calldad/ui/screens/HelperScreen.kt
//
// The Phase 1 chat UI is replaced: the child speaks, the bot answers
// aloud. Quick-ask chips and transcript are gone. Single tap only.
package com.calldad.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calldad.ui.components.GiantIconButton
import com.calldad.ui.theme.HelperPurple
import com.calldad.ui.theme.HelperPurpleLight
import com.calldad.ui.theme.InkBlack

@Composable
fun HelperScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HelperViewModel = rememberHelperViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Runtime RECORD_AUDIO permission. Required by SpeechRecognizer.
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val background by animateColorAsState(
        targetValue = when (state.status) {
            HelperStatus.LISTENING -> Color(0xFF1B5E20)
            HelperStatus.SPEAKING  -> HelperPurple
            else                  -> HelperPurpleLight
        },
        label = "helperBackground"
    )

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
                    contentColor = HelperPurple,
                    size = 100.dp,
                    onClick = onBackHome
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    text = "Ask Helper",
                    style = MaterialTheme.typography.headlineMedium,
                    color = HelperPurple
                )
            }

            Spacer(Modifier.weight(1f))

            TapToSpeakButton(
                status = state.status,
                enabled = permissionGranted,
                onClick = viewModel::onTapToSpeak
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = when (state.status) {
                    HelperStatus.IDLE      -> "Tap the big button and ask me!"
                    HelperStatus.LISTENING -> "I'm listening…"
                    HelperStatus.THINKING  -> "Let me think…"
                    HelperStatus.SPEAKING  -> "Listen!"
                    HelperStatus.ERROR     -> state.error ?: "Tap to try again"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = if (background == HelperPurpleLight) InkBlack else Color.White,
                textAlign = TextAlign.Center
            )

            if (!permissionGranted) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "I need to hear you. Please allow the microphone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkBlack,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TapToSpeakButton(
    status: HelperStatus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = status == HelperStatus.LISTENING
    val isSpeaking = status == HelperStatus.SPEAKING
    val isBusy = isListening || isSpeaking

    val haloAlpha = if (isListening) {
        val t = rememberInfiniteTransition(label = "halo")
        val a by t.animateFloat(
            initialValue = 0.15f, targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                tween(600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ), label = "haloA"
        )
        a
    } else 0f

    val icon = if (isSpeaking) Icons.Filled.VolumeUp else Icons.Filled.Mic
    val contentColor = when {
        isListening -> Color(0xFF1B5E20)
        isSpeaking  -> HelperPurple
        else        -> HelperPurple
    }

    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B5E20).copy(alpha = haloAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(8.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                .clickable(enabled = enabled && !isBusy, onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = when {
                        isListening -> "Listening"
                        isSpeaking  -> "Speaking"
                        else        -> "Tap to speak"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        isListening -> "LISTENING"
                        isSpeaking  -> "SPEAKING"
                        else        -> "TAP"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
