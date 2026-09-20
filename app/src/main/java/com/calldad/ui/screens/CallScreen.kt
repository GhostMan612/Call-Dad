// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallScreen.kt — Phase 4: video rendering + incoming overlay
// Location: app/src/main/java/com/calldad/ui/screens/CallScreen.kt
//
// Phase 1 hierarchy preserved. Phase 4 deltas: VideoRenderer-backed InCall,
// full-screen Incoming overlay, nav-arg QA hook (mode=incoming, DEBUG only).
// No swipe/long-press anywhere; every target >= 100dp.
package com.calldad.ui.screens

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ui.components.VideoRenderer
import com.calldad.ui.permissions.rememberCallPermissionRequest
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.CallGreenDark
import com.calldad.ui.theme.HangUpRed
import org.webrtc.EglBase
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    mode: String = "caller",
    viewModel: CallViewModel = callViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraOn.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val eglContext by viewModel.eglContext.collectAsStateWithLifecycle()

    // Caller (production path): permission-gated auto-start, exactly once.
    // Incoming QA path: permissions only; overlay drives answerCall().
    var started by remember { mutableStateOf(false) }
    val requestPermissions = rememberCallPermissionRequest(
        onGranted = {
            if (mode != "incoming" && !started) {
                started = true
                viewModel.startCall()
            }
        },
        onDenied = { /* Phase 4: route to a parent-facing helper screen */ }
    )
    LaunchedEffect(Unit) {
        requestPermissions()
        if (mode == "incoming") viewModel.simulateIncomingCall()
    }

    CallContent(
        state = state,
        elapsedSeconds = elapsed,
        isCameraOn = isCameraOn,
        remoteVideoTrack = remoteVideoTrack,
        localVideoTrack = localVideoTrack,
        eglContext = eglContext,
        onToggleCamera = viewModel::onToggleCamera,
        onRetry = viewModel::clearError,
        onAnswer = { viewModel.answerCall() },
        onDecline = {
            viewModel.endCall()
            onFinished()
        },
        onHangUp = {
            viewModel.endCall()
            onFinished()
        },
        modifier = modifier
    )
}

/**
 * Executor fix (carried from Phase 3): CallViewModel is an
 * AndroidViewModel (non-empty constructor), so the default viewModel()
 * factory cannot build it. Manual factory.
 */
@Composable
private fun callViewModel(): CallViewModel {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(application) as T
            }
        }
    )
}

@Composable
private fun CallContent(
    state: CallState,
    elapsedSeconds: Int,
    isCameraOn: Boolean,
    remoteVideoTrack: VideoTrack?,
    localVideoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    onToggleCamera: () -> Unit,
    onRetry: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is CallState.Error -> ErrorContent(
            message = state.message,
            onRetry = onRetry,
            onHangUp = onHangUp,
            modifier = modifier
        )
        is CallState.Incoming -> IncomingContent(
            fromDisplayName = state.fromDisplayName,
            onAnswer = onAnswer,
            onDecline = onDecline,
            modifier = modifier
        )
        is CallState.InCall -> InCallContent(
            remoteVideoTrack = remoteVideoTrack,
            localVideoTrack = localVideoTrack,
            eglContext = eglContext,
            elapsedSeconds = elapsedSeconds,
            isCameraOn = isCameraOn,
            onToggleCamera = onToggleCamera,
            onHangUp = onHangUp,
            modifier = modifier
        )
        else -> ConnectedContent(
            state = state,
            elapsedSeconds = elapsedSeconds,
            isCameraOn = isCameraOn,
            onToggleCamera = onToggleCamera,
            onHangUp = onHangUp,
            modifier = modifier
        )
    }
}

@Composable
private fun IncomingContent(
    fromDisplayName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ring + buzz while the overlay is up. Scoped here so leaving (answer,
    // decline, hangup) always silences. System default tone; no asset.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val ringtone = RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        ringtone?.play()
        val vibrator = context.getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0))
        onDispose {
            ringtone?.stop()
            vibrator?.cancel()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CallGreenDark)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Face,
            contentDescription = null,
            modifier = Modifier.size(220.dp),
            tint = Color.White
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "$fromDisplayName is calling!",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))

        // Screen-dominating accept/decline. Each button is >= 160dp tall
        // and fills half the row width. No swipe gestures anywhere.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GiantCallButton(
                label = "No",
                icon = Icons.Filled.CallEnd,
                containerColor = HangUpRed,
                onClick = onDecline,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GiantCallButton(
                label = "Answer",
                icon = Icons.Filled.Call,
                containerColor = Color(0xFF2E7D32),
                onClick = onAnswer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun GiantCallButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = shape)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun InCallContent(
    remoteVideoTrack: VideoTrack?,
    localVideoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    elapsedSeconds: Int,
    isCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // Full-screen remote video.
        VideoRenderer(
            track = remoteVideoTrack,
            eglContext = eglContext,
            mirror = false,
            modifier = Modifier.fillMaxSize()
        )

        // Local picture-in-picture, top-end corner.
        VideoRenderer(
            track = localVideoTrack,
            eglContext = eglContext,
            mirror = true,   // local preview is mirrored, remote is not
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(width = 140.dp, height = 200.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        // Timer overlay, top-start.
        Text(
            text = formatElapsed(elapsedSeconds),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        )

        // Controls row, bottom. Camera toggle + Hang Up.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CameraToggleButton(
                isCameraOn = isCameraOn,
                onClick = onToggleCamera,
                modifier = Modifier.size(140.dp).fillMaxHeight()
            )
            HangUpButton(
                onClick = onHangUp,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    state: CallState,
    elapsedSeconds: Int,
    isCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusLabel = when (state) {
        CallState.Idle -> "Ready"
        CallState.Connecting -> "Calling Dad…"
        is CallState.InCall -> "Dad is here!"
        is CallState.Incoming -> ""
        is CallState.Error -> "" // handled by ErrorContent
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CallGreenDark)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // ---------- STATUS AREA ----------
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Face,
                contentDescription = null,
                modifier = Modifier.size(140.dp),
                tint = Color.White
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = formatElapsed(elapsedSeconds),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        // ---------- CONTROLS ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CameraToggleButton(
                isCameraOn = isCameraOn,
                onClick = onToggleCamera,
                modifier = Modifier
                    .size(140.dp)
                    .fillMaxHeight()
            )

            HangUpButton(
                onClick = onHangUp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CallGreenDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(140.dp),
            tint = Color.White
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Retry — green
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF2E7D32))
                    .clickable(onClick = onRetry)
                    .semantics { contentDescription = "Try again" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.White
                )
            }
            // Hang up — red
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(HangUpRed)
                    .clickable(onClick = onHangUp)
                    .semantics { contentDescription = "Go back home" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CameraToggleButton(
    isCameraOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val background by animateColorAsState(
        targetValue = if (isCameraOn) Color.White.copy(alpha = 0.22f)
        else Color.White.copy(alpha = 0.08f),
        label = "cameraBackground"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isCameraOn) "Turn camera off" else "Turn camera on"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun HangUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape)
            .clip(shape)
            .background(HangUpRed)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Hang up and go home" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CallEnd,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color.White
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Hang Up",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun CallContentPreview() {
    CallDadTheme {
        CallContent(
            state = CallState.InCall(CallRole.CALLER, startedAtMillis = 0L),
            elapsedSeconds = 42,
            isCameraOn = true,
            remoteVideoTrack = null,
            localVideoTrack = null,
            eglContext = null,
            onToggleCamera = {},
            onRetry = {},
            onAnswer = {},
            onDecline = {},
            onHangUp = {}
        )
    }
}
