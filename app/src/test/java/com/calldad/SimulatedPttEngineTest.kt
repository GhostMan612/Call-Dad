// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 6 host-side tests: simulated engine contract (no mic, no net).
// android.util.Log calls inside WebRtcLog are stubbed to no-ops by
// testOptions.unitTests.isReturnDefaultValues (see app/build.gradle.kts).
package com.calldad

import com.calldad.ptt.PttFailureKind
import com.calldad.ptt.SimulatedPttEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedPttEngineTest {

    @Test
    fun startStop_cycleSucceeds() = runTest {
        val engine = SimulatedPttEngine()
        assertTrue(engine.startTransmitting().isSuccess)
        assertTrue(engine.stopTransmitting().isSuccess)
        engine.release()
    }

    @Test
    fun start_isIdempotent() = runTest {
        val engine = SimulatedPttEngine()
        assertTrue(engine.startTransmitting().isSuccess)
        assertTrue(engine.startTransmitting().isSuccess)
        assertTrue(engine.stopTransmitting().isSuccess)
        engine.release()
    }

    @Test
    fun stop_whenIdle_isNoopSuccess() = runTest {
        val engine = SimulatedPttEngine()
        assertTrue(engine.stopTransmitting().isSuccess)
        engine.release()
    }

    @Test
    fun failureKinds_coverContract() {
        val kinds = PttFailureKind.entries.map { it.name }.toSet()
        setOf(
            "AUDIO_CONTENDED", "PERMISSION_DENIED", "AUDIO_FOCUS_LOST",
            "ENGINE_UNAVAILABLE", "TRANSPORT_ERROR", "UNKNOWN"
        ).forEach { assertTrue(kinds.contains(it)) }
    }
}
