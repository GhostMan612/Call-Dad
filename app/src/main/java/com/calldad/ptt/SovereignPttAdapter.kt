// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ptt/SovereignPttAdapter.kt — Phase 6: proprietary boundary (reflection only)
// Location: app/src/main/java/com/calldad/ptt/SovereignPttAdapter.kt
package com.calldad.ptt

import android.content.Context
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Production PTT engine backed by the private Sovereign Mantle module.
 *
 * =====================================================================
 * PROPRIETARY BOUNDARY
 * =====================================================================
 * The `:sovereign-mantle` Gradle dependency is NOT in build.gradle.kts.
 * Operators with repo access add it locally to app/build.gradle.kts:
 *
 *     implementation(project(":sovereign-mantle"))
 *     // or, if consumed as a maven artifact from a private registry:
 *     // implementation("com.ghostman.sovereign:mantle:${version}")
 *
 * When the module is absent, constructing this class does NOT throw —
 * startTransmitting() returns a typed failure so the ViewModel can fall
 * back gracefully.
 *
 * =====================================================================
 * INTEGRATION SHAPE (do not uncomment — reference only)
 * =====================================================================
 * Sovereign Mantle is expected to expose an API shaped like:
 *
 *     com.sovereign.mantle.PttChannel
 *         fun open(config: PttConfig): PttHandle
 *         fun close(handle: PttHandle)
 *     com.sovereign.mantle.PttHandle
 *         suspend fun beginTx(): Boolean
 *         suspend fun endTx()
 *         val incoming: Flow<MantleAudioFrame>
 *
 * The exact signatures are private. If they diverge, only the three
 * `mantle*` private helpers in this file need to change. The public
 * PttEngine surface must not move.
 */
class SovereignPttAdapter(
    private val context: Context
) : PttEngine {

    private val _incoming = MutableSharedFlow<PttAudioState>(replay = 1)
    override fun observeIncomingAudio(): Flow<PttAudioState> = _incoming.asSharedFlow()

    // Reflective handle. Populated by tryLoadMantle().
    private var mantleHandle: Any? = null

    init {
        mantleHandle = tryLoadMantle()
        if (mantleHandle == null) {
            WebRtcLog.transition("Sovereign Mantle not on classpath")
        } else {
            WebRtcLog.transition("Sovereign Mantle adapter initialized")
        }
    }

    override suspend fun startTransmitting(): Result<Unit> {
        val handle = mantleHandle ?: return Result.failure(
            PttFailure(
                kind = PttFailureKind.ENGINE_UNAVAILABLE,
                userMessage = "Walkie Talkie is not available on this build."
            )
        )
        return try {
            // PHASE 6 INTEGRATION POINT:
            //   (handle as com.sovereign.mantle.PttHandle).beginTx()
            WebRtcLog.transition("PTT sovereign: TX started")
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(
                PttFailure(PttFailureKind.TRANSPORT_ERROR, "Could not start talking.", t)
            )
        }
    }

    override suspend fun stopTransmitting(): Result<Unit> {
        val handle = mantleHandle ?: return Result.success(Unit)
        return try {
            // PHASE 6 INTEGRATION POINT:
            //   (handle as com.sovereign.mantle.PttHandle).endTx()
            WebRtcLog.transition("PTT sovereign: TX stopped")
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(
                PttFailure(PttFailureKind.TRANSPORT_ERROR, "Could not stop talking.", t)
            )
        }
    }

    override fun release() {
        mantleHandle = null
        WebRtcLog.transition("PTT sovereign: released")
    }

    /**
     * Attempts to load Sovereign Mantle via reflection. Returns null (never
     * throws) if the module is absent.
     *
     * Reflection — not a direct import — is what keeps this file compiling
     * on a machine that has never seen the private module.
     */
    private fun tryLoadMantle(): Any? = try {
        val clazz = Class.forName("com.sovereign.mantle.PttChannel")
        // PHASE 6 INTEGRATION POINT:
        //   val instance = clazz.getMethod("open", PttConfig::class.java)
        //       .invoke(null, PttConfig.default(context))
        // For now we only verify the class exists.
        clazz
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }
}
