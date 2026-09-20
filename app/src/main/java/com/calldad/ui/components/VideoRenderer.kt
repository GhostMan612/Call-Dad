// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/components/VideoRenderer.kt
// Location: app/src/main/java/com/calldad/ui/components/VideoRenderer.kt
package com.calldad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Hosts a single SurfaceViewRenderer for a single VideoTrack.
 *
 * OWNERSHIP CONTRACT:
 *  - init() runs exactly once, in AndroidView.factory.
 *  - release() runs exactly once, in AndroidView.onRelease.
 *  - addSink/removeSink are paired inside a keyed DisposableEffect so a
 *    track that arrives asynchronously (the remote case) still wires up.
 *
 * The eglContext MUST be the WebRTCClient's context — passing a freshly
 * created EglBase.context yields a black screen with no exception.
 *
 * Pass track = null to render a black placeholder (waiting for remote).
 */
@Composable
fun VideoRenderer(
    track: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    // EGL not ready yet — show black, do not create a view.
    if (eglContext == null) {
        Box(modifier.background(Color.Black))
        return
    }

    val rendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                rendererRef.value = this
            }
        },
        onRelease = { view ->
            // Clear the state BEFORE release so a racing DisposableEffect
            // does not try to removeSink from a released view.
            rendererRef.value = null
            view.release()
        }
    )

    val currentRenderer = rendererRef.value
    DisposableEffect(track, currentRenderer) {
        val t = track
        val r = currentRenderer
        if (t != null && r != null) t.addSink(r)
        onDispose {
            if (t != null && r != null) t.removeSink(r)
        }
    }
}
