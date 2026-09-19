// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallScreen.kt
// Location: app/src/main/java/com/calldad/app/ui/screens/CallScreen.kt
package com.calldad.app.ui.screens

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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.app.ui.theme.CallDadTheme
import com.calldad.app.ui.theme.CallGreenDark
import com.calldad.app.ui.theme.HangUpRed

@Composable
fun CallScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CallContent(
        state = state,
        onToggleCamera = viewModel::onToggleCamera,
        onHangUp = {
            viewModel.onHangUp()
            onFinished()
        },
        modifier = modifier
    )
}

/**
 * Stateless call surface.
 *  - Status area: 220dp avatar well + oversized status label.
 *  - Hang Up: 140dp tall, fills all remaining width, saturated red. Unmissable.
 *  - Camera: secondary 140dp square. Present but visually subordinate.
 */
@Composable
private fun CallContent(
    state: CallUiState,
    onToggleCamera: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusLabel = when (state.status) {
        CallStatus.CONNECTING -> "Calling Dad…"
        CallStatus.CONNECTED -> "Dad is here!"
        CallStatus.ENDED -> "Bye bye!"
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
            text = state.timerLabel,
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
                isCameraOn = state.isCameraOn,
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
private fun CameraToggleButton(
    isCameraOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val background by animateColorAsState(
        targetValue = if (isCameraOn) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
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
            state = CallUiState(status = CallStatus.CONNECTED, elapsedSeconds = 42),
            onToggleCamera = {},
            onHangUp = {}
        )
    }
}
