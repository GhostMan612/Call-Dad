// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ptt/SimulatedPttEngine.kt — Phase 6: default loopback engine
// Location: app/src/main/java/com/calldad/ptt/SimulatedPttEngine.kt
package com.calldad.ptt

import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback engine. Does NOT touch the microphone or the network.
 *
 * This is the default engine. It is what CI uses, what a fresh clone uses,
 * and what a developer without Sovereign Mantle repo access uses.
 *
 * NOTE: emits Idle forever — the Receiving visual is reachable only via a
 * real engine. (A synthetic auto-pulse was considered and rejected: a fake
 * "Dad is talking" firing unprompted would confuse QA and the child.)
 */
class SimulatedPttEngine : PttEngine {

    private val transmitting = AtomicBoolean(false)
    private val _incoming = MutableSharedFlow<PttAudioState>(replay = 1)
    override fun observeIncomingAudio(): Flow<PttAudioState> = _incoming.asSharedFlow()

    override suspend fun startTransmitting(): Result<Unit> {
        if (!transmitting.compareAndSet(false, true)) return Result.success(Unit)
        WebRtcLog.transition("PTT simulated: TX started")
        return Result.success(Unit)
    }

    override suspend fun stopTransmitting(): Result<Unit> {
        if (!transmitting.compareAndSet(true, false)) return Result.success(Unit)
        WebRtcLog.transition("PTT simulated: TX stopped")
        return Result.success(Unit)
    }

    override fun release() {
        transmitting.set(false)
        WebRtcLog.transition("PTT simulated: released")
    }
}
