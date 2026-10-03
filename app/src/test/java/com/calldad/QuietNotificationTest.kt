// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host tests for K12 notification loudness (Contract 11, operator-found).
//
// WHY THIS IS A SOURCE-SCAN TEST. Notification channels and FCM priorities are
// Android framework behaviour; a host JVM test cannot construct a
// NotificationChannel or observe what a phone does with one. The behavioural
// proof is the operator's locked-phone test, recorded in blueprints.
//
// WHAT IT PINS. The K12 clip notification shipped on the CALL channel, which is
// IMPORTANCE_HIGH and built to ring. On Android 8+ the channel's importance
// wins and the notification's own priority is IGNORED, so a "quiet" clip
// notification rang at full volume on a locked phone. A per-notification
// PRIORITY_LOW check would have passed the whole time while the phone still
// rang, because the bug was never in the priority -- it was in the channel.
// These tests therefore assert on the CHANNEL, which is the thing that was
// wrong.
//
// LESSON BAKED IN. Three earlier versions of this file produced false failures
// against correct code, all from the same mistake: scanning raw text. Slices
// anchored on indentation-sensitive strings missed, and the fix's own comment
// above the builder names CHANNEL_INCOMING_CALL and PRIORITY_DEFAULT to
// DESCRIBE the bug, so any raw scan matches the prose. Everything here strips
// comments first and anchors on a unique function name, never on whitespace.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuietNotificationTest {

    private fun read(path: String): String {
        val file = listOf(File(path), File("app", path)).firstOrNull(File::exists)
        checkNotNull(file) { "could not find " + path }
        return file.readText()
    }

    private val app = read("src/main/java/com/calldad/CallDadApplication.kt")
    private val service = read("src/main/java/com/calldad/fcm/CallMessagingService.kt")
    private val service2 = read("src/main/java/com/calldad/fcm/CallForegroundService.kt")
    private val activity = read("src/main/java/com/calldad/MainActivity.kt")

    /** A source scan must assert on CODE. The fix's comment names the bug. */
    private fun codeOnly(src: String): String = Regex("""//[^\n]*""").replace(src, "")

    /** The body of a function, anchored on its declaration, comments removed. */
    private fun body(src: String, signature: String): String =
        codeOnly(src.substringAfter(signature).substringBefore("\n    }"))

    private val messageChannelBody = body(app, "private fun createMessageChannel()")
    // Sliced on the BUILDER, not on handleClipWaiting. That handler became a thin
    // consent gate that delegates the notification to a separate function, so a
    // slice of the handler now sees the gate and no builder at all — the same
    // "substringBefore returned the wrong region" trap this suite has already been
    // bitten by once. The property under test (which channel, which priority) lives
    // in the builder, so that is what is asserted.
    private val clipHandlerBody = body(service, "private fun postClipWaitingNotification(")

    // ---- the message channel must exist and must be quiet ----

    @Test
    fun aDedicatedQuietChannelExistsForVoiceMessages() {
        assertTrue(
            "a separate voice-message channel must exist; the ring channel is IMPORTANCE_HIGH",
            app.contains("CHANNEL_PTT_MESSAGE")
        )
        assertTrue(
            "the message channel must be created at startup, or it is missing on a cold launch",
            app.contains("createMessageChannel()")
        )
        assertTrue("createMessageChannel must actually create a channel", messageChannelBody.contains("createNotificationChannel"))
    }

    @Test
    fun theMessageChannelIsLowImportance() {
        // The single most important assertion in this file. IMPORTANCE_DEFAULT
        // or HIGH here reproduces the exact bug the operator reported.
        assertTrue(
            "the voice-message channel must be IMPORTANCE_LOW, not DEFAULT or HIGH. Body: " +
                messageChannelBody,
            messageChannelBody.contains("IMPORTANCE_LOW")
        )
        assertFalse(
            "the voice-message channel must NOT be IMPORTANCE_DEFAULT or HIGH. Body: " +
                messageChannelBody,
            messageChannelBody.contains("IMPORTANCE_DEFAULT") || messageChannelBody.contains("IMPORTANCE_HIGH")
        )
    }

    @Test
    fun theMessageChannelMakesNoSoundAndNoVibration() {
        assertTrue(
            "the message channel must setSound(null, null). Body: " + messageChannelBody,
            messageChannelBody.contains("setSound(null, null)")
        )
        assertTrue(
            "the message channel must disable vibration",
            messageChannelBody.contains("enableVibration(false)")
        )
    }

    // ---- the clip notification must not use the ring channel ----

    @Test
    fun theClipNotificationUsesTheQuietChannelNotTheRingChannel() {
        assertTrue(
            "the clip notification must be built on CHANNEL_PTT_MESSAGE. Builder body: " +
                clipHandlerBody,
            clipHandlerBody.contains("CHANNEL_PTT_MESSAGE")
        )
        assertFalse(
            "the clip notification must NOT be built on the ring channel -- from Android 8 " +
                "the channel importance wins and the per-notification priority is ignored, " +
                "which is exactly how a voice message rang at full volume. Builder body: " +
                clipHandlerBody,
            clipHandlerBody.contains("CHANNEL_INCOMING_CALL")
        )
        // And the pending-consent ring channel must never carry a voice message
        // either. It is silent by design, but it is still a RING channel.
        assertFalse(
            "a clip notification must not be built on the pending-consent ring channel",
            clipHandlerBody.contains("CHANNEL_RING_PENDING_CONSENT")
        )
    }

    @Test
    fun theClipNotificationStaysLowPriorityEvenThoughTheChannelIsWhatMatters() {
        // Belt and braces: if the channel is ever refactored away, the priority
        // should still say "quiet".
        assertTrue(
            "the clip notification must request PRIORITY_LOW. Builder body: " + clipHandlerBody,
            clipHandlerBody.contains("PRIORITY_LOW")
        )
        assertFalse(
            "the clip notification must never request HIGH or MAX priority. Builder body: " +
                clipHandlerBody,
            clipHandlerBody.contains("PRIORITY_HIGH") || clipHandlerBody.contains("PRIORITY_MAX")
        )
    }

    /**
     * The PTT nudge is now consent-GATED, and the gate must not have been paid for
     * with the K12 quiet-channel property. Splitting the handler into "ask, then
     * post" is exactly the refactor that could quietly move the builder onto the
     * wrong channel, so both halves are asserted.
     */
    @Test
    fun theClipNudgeIsConsentGatedAndStillQuiet() {
        val handler = body(service, "private fun handleClipWaiting(")
        assertTrue(
            "the PTT nudge must be consent-gated on ConsentScope.PTT. An unsolicited " +
                "'A message is waiting' on a phone whose parent closed the walkie " +
                "talkie contradicts the control even though nothing leaks. Body: " + handler,
            handler.contains("ConsentScope.PTT")
        )
        assertTrue(
            "the gate must be fail-closed: no consent, no notification",
            handler.contains("PTT nudge suppressed: consent denied")
        )
    }

    // ---- the ring path must not have been weakened in the fix ----

    @Test
    fun theRingPathIsStillOnTheHighImportanceChannel() {
        // The fix must not have quieted the actual ring. A call that does not
        // ring is a worse failure than a message that rings.
        //
        // Sliced on the COMPANION's implementation, not on the private wrapper.
        // The builder moved there so `CallForegroundService` can reuse it when
        // its own `startForeground` is rejected — a path that previously produced
        // total silence. Slicing the wrapper instead would assert against a
        // one-line delegation and pass or fail for reasons unrelated to the
        // channel, which is the "substringBefore returned the whole file" trap
        // this suite has already been bitten by.
        val ring = body(service, "fun postIncomingCallFallback(")
        assertTrue(
            "the incoming-call fallback must stay on CHANNEL_INCOMING_CALL. Body: " + ring,
            ring.contains("CHANNEL_INCOMING_CALL")
        )
        assertTrue(
            "and the private wrapper must actually delegate to it, rather than " +
                "posting something of its own: " + ring,
            body(service, "private fun postHeadsUpFallback(")
                .contains("postIncomingCallFallback(this, callId, seq)")
        )
    }

    @Test
    fun theRingChannelItselfStaysHighImportance() {
        val ring = body(app, "private fun createIncomingCallChannel()")
        assertTrue(
            "the call channel must remain IMPORTANCE_HIGH, or calls stop ringing",
            ring.contains("IMPORTANCE_HIGH")
        )
    }

    // ---- the keyguard must always belong to the phone's owner ----

    @Test
    fun theActivityNeverDisablesTheLockScreen() {
        // Operator-reported: while a call rang, the app kept drawing over the
        // lock screen and the grown-up could not get past it to unlock their own
        // phone. An activity that opts into showWhenLocked/turnScreenOn keeps
        // showing OVER the keyguard once launched, so the fix is to never opt in
        // and let the platform handle a locked screen.
        val onCreate = codeOnly(
            activity.substringAfter("override fun onCreate(").substringBefore("\n    }")
        )
        assertTrue(
            "MainActivity must call setShowWhenLocked(false). Found: " + onCreate,
            onCreate.contains("setShowWhenLocked(false)")
        )
        assertTrue(
            "MainActivity must call setTurnScreenOn(false)",
            onCreate.contains("setTurnScreenOn(false)")
        )
        assertFalse(
            "MainActivity must NEVER setShowWhenLocked(true) -- that is what trapped " +
                "the operator on their own lock screen",
            onCreate.contains("setShowWhenLocked(true)") || onCreate.contains("setTurnScreenOn(true)")
        )
        // minSdk is 26 and these are API 27+, so an unguarded call is a crash on
        // Android 8.0. Lint caught exactly this; the guard must not be removed.
        assertTrue(
            "the lock-screen calls must be API-guarded (minSdk 26, these are API 27+)",
            Regex("""SDK_INT\s*>=\s*Build\.VERSION_CODES\.O_MR1""").containsMatchIn(onCreate)
        )
    }

    @Test
    fun theRingOnlyTakesOverTheScreenWhenTheKeyguardIsNotLocked() {
        // The other half. A full-screen intent dropped unconditionally is what
        // launches the activity over the lock screen; it must be conditional on
        // the phone already being usable.
        val build = codeOnly(
            service2.substringAfter("private fun buildIncomingCallNotification(")
                .substringBefore("\n    }")
        )
        assertTrue(
            "the ring must check the keyguard before taking over the screen",
            build.contains("isKeyguardLocked")
        )
        assertTrue(
            "the full-screen intent must be conditional, not hardcoded true",
            Regex("""setFullScreenIntent\([^)]*,\s*takeOverScreen\)""").containsMatchIn(build)
        )
        assertFalse(
            "setFullScreenIntent must never be hardcoded to true -- that is the " +
                "unconditional keyguard takeover the operator reported",
            Regex("""setFullScreenIntent\([^)]*,\s*true\)""").containsMatchIn(build)
        )
    }

    @Test
    fun theRingStillRingsWhileLocked() {
        // The fix must not have made a locked phone silent. The ring still
        // needs PRIORITY_MAX and a heads-up; only the screen takeover is gated.
        val build = codeOnly(
            service2.substringAfter("private fun buildIncomingCallNotification(")
                .substringBefore("\n    }")
        )
        assertTrue(
            "the call notification must stay CATEGORY_CALL so Android shows it on " +
                "the lock screen",
            build.contains("CATEGORY_CALL")
        )
        assertTrue(
            "the call notification must stay PRIORITY_MAX, or calls stop ringing",
            build.contains("PRIORITY_MAX")
        )
    }

    @Test
    fun theMessageChannelIdIsDistinctFromTheRingChannel() {
        // Two channels sharing an id is a silent collision: the first created
        // wins and the second silently inherits its importance, which would
        // resurrect this exact bug if the ids were ever made equal.
        val ring = Regex("""CHANNEL_INCOMING_CALL = "([^"]+)"""").find(app)?.groupValues?.get(1)
        val msg = Regex("""CHANNEL_PTT_MESSAGE = "([^"]+)"""").find(app)?.groupValues?.get(1)
        assertTrue("CHANNEL_INCOMING_CALL id not found", ring != null)
        assertTrue("CHANNEL_PTT_MESSAGE id not found", msg != null)
        assertFalse("the two channel ids must differ (both = $ring)", ring != null && ring == msg)
    }
}
