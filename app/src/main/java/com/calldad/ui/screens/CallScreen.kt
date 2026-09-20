// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallScreen.kt — Phase 3: permission-gated auto-start
// Location: app/src/main/java/com/calldad/ui/screens/CallScreen.kt
//
// The composable hierarchy and dimensions from Phase 1 are preserved.
// Phase 3 deltas: VM factory (AndroidViewModel needs Application),
// permission-gated startCall(), explicit Connecting branch. No renderers.
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.calldad.ui.permissions.rememberCallPermissionRequest
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.CallGreenDark
import com.calldad.ui.theme.HangUpRed

@Composable
fun CallScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = callViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraOn.collectAsStateWithLifecycle()

    // Permission-gated auto-start: Home → Call Dad requests mic/camera once,
    // then starts the caller path exactly once (tap-spam safe).
    var started by remember { mutableStateOf(false) }
    val requestPermissions = rememberCallPermissionRequest(
        onGranted = { if (!started) { started = true; viewModel.startCall() } },
        onDenied = { /* Phase 4: route to a parent-facing helper screen */ }
    )
    LaunchedEffect(Unit) { requestPermissions() }

    CallContent(
        state = state,
        elapsedSeconds = elapsed,
        isCameraOn = isCameraOn,
        onToggleCamera = viewModel::onToggleCamera,
        onRetry = viewModel::clearError,
        onHangUp = {
            viewModel.endCall()
            onFinished()
        },
        modifier = modifier
    )
}

/**
 * Executor fix (architect prompt missed this): CallViewModel is an
 * AndroidViewModel (non-empty constructor), so the default viewModel()
 * factory cannot build it — it would crash on navigation. Manual factory.
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
    onToggleCamera: () -> Unit,
    onRetry: () -> Unit,
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CameraToggleButton(
                isCameraOn = isCameraOn,
                onClick = onToggleCamera,
                modifier = Modifier.fillMaxHeight().size(140.dp)
            )
            HangUpButton(
                onClick = onHangUp,
                modifier = Modifier.weight(1f).fillMaxHeight()
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
            onToggleCamera = {},
            onRetry = {},
            onHangUp = {}
        )
    }
}
