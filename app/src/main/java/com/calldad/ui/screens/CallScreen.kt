// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallScreen.kt — video call UI + incoming overlay
// Location: app/src/main/java/com/calldad/ui/screens/CallScreen.kt
//
// No swipe/long-press anywhere; every primary target >= 96dp.
package com.calldad.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ui.components.VideoRenderer
import com.calldad.ui.permissions.rememberCallPermissionRequest
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.CallGreen
import com.calldad.ui.theme.CallGreenDark
import com.calldad.ui.theme.HangUpRed
import com.calldad.webrtc.ConnectionHealth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    onFinished: () -> Unit,
    onOpenGame: () -> Unit,
    modifier: Modifier = Modifier,
    mode: String = "caller",
    viewModel: CallViewModel = callViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraOn.collectAsStateWithLifecycle()
    val isMicOn by viewModel.isMicOn.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val health by viewModel.connectionHealth.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val shouldKeepOn = state.isLive

    DisposableEffect(shouldKeepOn) {
        val window = (context as? android.app.Activity)?.window
        if (window == null) return@DisposableEffect onDispose { }
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Caller: permission-gated auto-start, exactly once. Incoming: the
    // shared ViewModel already holds the ring (or will within a moment,
    // on a cold start from the notification); nothing ringing → home.
    var started by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val requestPermissions = rememberCallPermissionRequest(
        onGranted = {
            permissionDenied = false
            if (mode != "incoming" && !started) {
                started = true
                viewModel.startCall()
            }
        },
        onDenied = { permissionDenied = true }
    )
    LaunchedEffect(Unit) {
        requestPermissions()
        if (mode == "incoming") {
            val ringing = withTimeoutOrNull(INCOMING_WAIT_MS) {
                viewModel.state.first { it is CallState.Ringing && it.isIncoming }
            }
            if (ringing == null && !viewModel.state.value.isLive) onFinished()
        }
    }

    // Lock-screen polish: the camera pauses while the call is not on
    // screen and comes back only if the kid left it on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onUiHidden()
                Lifecycle.Event.ON_START -> viewModel.onUiVisible()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Any return to Idle after real activity follows home so neither side
    // strands on a dead screen. Initial Idle never triggers.
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is CallState.Idle) {
            wasActive = true
        } else if (wasActive) {
            wasActive = false
            onFinished()
        }
    }

    // System back = Hang Up (or Decline on an incoming ring): the peer is
    // always told, never left ringing.
    BackHandler {
        viewModel.endCall()
        onFinished()
    }

    if (permissionDenied && !state.isLive) {
        PermissionContent(
            onRetry = requestPermissions,
            onClose = onFinished,
            modifier = modifier
        )
        return
    }

    CallContent(
        state = state,
        elapsedSeconds = elapsed,
        isCameraOn = isCameraOn,
        isMicOn = isMicOn,
        remoteVideoTrack = remoteVideoTrack,
        localVideoTrack = localVideoTrack,
        eglContext = viewModel.eglContext,
        health = health,
        onToggleCamera = viewModel::onToggleCamera,
        onToggleMic = viewModel::onToggleMic,
        onSwitchCamera = viewModel::onSwitchCamera,
        onOpenGame = onOpenGame,
        onAnswer = viewModel::answerCall,
        onDecline = {
            viewModel.declineCall()
            onFinished()
        },
        onReRing = viewModel::onReRing,
        onDismissError = {
            viewModel.clearError()
            onFinished()
        },
        onHangUp = {
            viewModel.endCall()
            onFinished()
        },
        modifier = modifier
    )
}

private const val INCOMING_WAIT_MS = 6_000L

/**
 * THE call session accessor. Activity-scoped on purpose: the call must
 * survive leaving the call screen (game during a call, rings on any
 * screen), so every caller shares one CallViewModel per activity.
 */
@Composable
fun callViewModel(): CallViewModel {
    val activity = LocalContext.current.findComponentActivity()
    val application = activity.application
    return viewModel(
        viewModelStoreOwner = activity,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(application) as T
            }
        }
    )
}

private fun Context.findComponentActivity(): ComponentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("CallViewModel needs a ComponentActivity host")
}

@Composable
private fun PermissionContent(
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
            .background(CallGreenDark).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MicOff,
            contentDescription = null,
            modifier = Modifier.size(140.dp),
            tint = Color.White
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "The phone needs the camera and microphone to call. Ask a grown-up to allow them.",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GiantCallButton(
                label = "Go Home",
                icon = Icons.Filled.Close,
                containerColor = HangUpRed,
                onClick = onClose,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GiantCallButton(
                label = "Allow",
                icon = Icons.Filled.Refresh,
                containerColor = CallGreen,
                onClick = onRetry,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CallContent(
    state: CallState,
    elapsedSeconds: Int,
    isCameraOn: Boolean,
    isMicOn: Boolean,
    remoteVideoTrack: VideoTrack?,
    localVideoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    health: ConnectionHealth,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenGame: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onReRing: () -> Unit,
    onDismissError: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is CallState.Ringing -> if (state.isIncoming) {
            IncomingContent(
                fromDisplayName = state.peerName,
                onAnswer = onAnswer,
                onDecline = onDecline,
                modifier = modifier
            )
        } else {
            ConnectedContent(
                state = state,
                elapsedSeconds = elapsedSeconds,
                isCameraOn = isCameraOn,
                onToggleCamera = onToggleCamera,
                onHangUp = onHangUp,
                modifier = modifier
            )
        }
        is CallState.Connected -> InCallContent(
            remoteVideoTrack = remoteVideoTrack,
            localVideoTrack = localVideoTrack,
            eglContext = eglContext,
            health = health,
            elapsedSeconds = elapsedSeconds,
            isCameraOn = isCameraOn,
            isMicOn = isMicOn,
            onToggleCamera = onToggleCamera,
            onToggleMic = onToggleMic,
            onSwitchCamera = onSwitchCamera,
            onOpenGame = onOpenGame,
            onHangUp = onHangUp,
            modifier = modifier
        )
        is CallState.NoAnswer -> NoAnswerContent(
            onReRing = onReRing,
            onHangUp = onHangUp,
            modifier = modifier
        )
        is CallState.Error -> ErrorContent(
            kind = state.kind,
            message = state.message,
            onRetry = onReRing,
            onDismiss = onDismissError,
            modifier = modifier
        )
        is CallState.Declined, is CallState.Ended, CallState.Idle -> ConnectedContent(
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
    // Visual only. Ringtone + vibration are owned SOLELY by CallAudioManager
    // (driven from CallViewModel and CallForegroundService). See ADR-011.
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
    health: ConnectionHealth,
    elapsedSeconds: Int,
    isCameraOn: Boolean,
    isMicOn: Boolean,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenGame: () -> Unit,
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

        // Timer overlay, top-start. Health banner below it while
        // reconnecting (DEGRADED shows nothing, per spec).
        Text(
            text = formatElapsed(elapsedSeconds),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        )
        if (health == ConnectionHealth.LOST ||
            health == ConnectionHealth.RECONNECTING
        ) {
            Text(
                text = "Connection lost. Reconnecting…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 64.dp)
            )
        }

        // Controls, bottom: small toggles row above a giant Hang Up.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CameraToggleButton(
                    isCameraOn = isCameraOn,
                    onClick = onToggleCamera,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RoundControl(
                    icon = if (isMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                    description = if (isMicOn) "Mute microphone" else "Unmute microphone",
                    active = isMicOn,
                    onClick = onToggleMic,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RoundControl(
                    icon = Icons.Filled.Cameraswitch,
                    description = "Flip camera",
                    active = true,
                    onClick = onSwitchCamera,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RoundControl(
                    icon = Icons.Filled.SportsEsports,
                    description = "Play a game together",
                    active = true,
                    onClick = onOpenGame,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            HangUpButton(
                onClick = onHangUp,
                modifier = Modifier.fillMaxWidth().height(120.dp)
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
        is CallState.Ringing -> if (state.isIncoming) "" else "Calling ${state.peerName}…"
        is CallState.Connected -> "Connected!"
        is CallState.NoAnswer -> ""
        is CallState.Declined -> "They can't talk right now"
        is CallState.Ended -> when (state.reason) {
            EndReason.MISSED -> "Missed call"
            EndReason.NETWORK_FAILURE -> "Connection lost"
            else -> "Call ended"
        }
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
private fun NoAnswerContent(
    onReRing: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
            .background(CallGreenDark).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Face,
            contentDescription = null,
            modifier = Modifier.size(160.dp),
            tint = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No answer yet",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GiantCallButton(
                label = "Stop",
                icon = Icons.Filled.CallEnd,
                containerColor = HangUpRed,
                onClick = onHangUp,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GiantCallButton(
                label = "Try Again",
                icon = Icons.Filled.Refresh,
                containerColor = CallGreen,
                onClick = onReRing,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ErrorContent(
    kind: CallErrorKind,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecoverable = kind.isRecoverable

    Column(
        modifier = modifier.fillMaxSize()
            .background(CallGreenDark).padding(24.dp),
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
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isRecoverable) {
                GiantCallButton(
                    label = "Try Again",
                    icon = Icons.Filled.Refresh,
                    containerColor = CallGreen,
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            GiantCallButton(
                label = "Go Home",
                icon = Icons.Filled.Close,
                containerColor = HangUpRed,
                onClick = onDismiss,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
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
private fun RoundControl(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (active) 0.22f else 0.08f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
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
            state = CallState.Connected(seq = 1),
            elapsedSeconds = 42,
            isCameraOn = true,
            isMicOn = true,
            remoteVideoTrack = null,
            localVideoTrack = null,
            eglContext = null,
            health = ConnectionHealth.HEALTHY,
            onToggleCamera = {},
            onToggleMic = {},
            onSwitchCamera = {},
            onOpenGame = {},
            onAnswer = {},
            onDecline = {},
            onReRing = {},
            onDismissError = {},
            onHangUp = {}
        )
    }
}
