// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallMessagingService.kt — token-targeted ring receiver (ADR-015)
// Location: app/src/main/java/com/calldad/fcm/CallMessagingService.kt
package com.calldad.fcm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calldad.CallDadApplication
import com.calldad.MainActivity
import com.calldad.R
import com.calldad.consent.ConsentScope
import com.calldad.consent.ConsentStore
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "incoming_call" -> handleRing(message)
            // K12: a clip waiting on a phone whose app is not open. The audio
            // never comes through FCM -- it is fetched from Firestore by the
            // app -- so this is a nudge, not a delivery.
            "ptt_clip" -> handleClipWaiting(message)
            else -> return
        }
    }

    private fun handleRing(message: RemoteMessage) {
        if (AppVisibility.isForeground) {
            WebRtcLog.transition("FCM ring skipped: app on screen, room listener rings")
            return
        }

        val callId = message.data["callId"].orEmpty()
        val seq = message.data["seq"]?.toIntOrNull() ?: -1
        if (callId.isEmpty()) return

        // A high-priority FCM may still be DOWNGRADED to normal by FCM, and a
        // downgraded message legally cannot start a foreground service. That
        // used to be a silent permanent no-op: one log line, indistinguishable
        // from a permissions failure, and the giant Call Dad button simply did
        // nothing, forever.
        //
        // There is deliberately no explicit RemoteMessage.getPriority() check:
        // the accessor's type is not stable across the firebase-messaging
        // versions in this catalog, and the exception handlers below already
        // catch the downgrade observably. Firing a probe purely to read a
        // priority is not worth a version-fragile compile dependency.
        try {
            CallForegroundService.startIncomingCall(applicationContext, callId, seq)
        } catch (t: SecurityException) {
            WebRtcLog.transition("Foreground service start denied by platform")
            gatedFallback(callId, seq)
        } catch (t: IllegalStateException) {
            WebRtcLog.transition("Foreground service start not allowed right now")
            gatedFallback(callId, seq)
        } catch (t: Throwable) {
            WebRtcLog.transition("Foreground service start failed")
            gatedFallback(callId, seq)
        }
    }

    /**
     * The fallback notification, but only if a parent has NOT switched calling off.
     *
     * This path used to post the notification unconditionally, which meant the kill
     * switch had a hole exactly where it is weakest: the FGS could fail to start
     * (a downgraded push, a background restriction) and the child's phone would
     * then light up and buzz — on the high-importance call channel — for a call
     * their grown-up had turned off. The in-app gate and the service's own gate are
     * both irrelevant here, because neither of them runs.
     *
     * So the fallback asks first, and asks FAIL-CLOSED via
     * [ConsentStore.decisionNow]: an error, a timeout or a cached read denies.
     * Consequence to accept deliberately: a revoked child sees nothing at all and
     * the parent's call rings out. That is the correct direction to fail. The
     * inverse — a visible incoming call the parent believes is switched off — is
     * the failure this whole feature exists to prevent.
     */
    private fun gatedFallback(callId: String, seq: Int) {
        val ctx = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val allowed = hasConsent(ctx, callId, ConsentScope.CALL)
            if (allowed) postIncomingCallFallback(ctx, callId, seq)
            else WebRtcLog.transition("Ring fallback suppressed: consent denied")
        }
    }

    /**
     * One-shot consent for a caller with no ViewModel.
     *
     * Shares [ConsentStore.decisionNow] with the foreground service so the two
     * paths cannot drift apart — a ring and a ring-fallback that disagree about
     * the same kill switch would reintroduce the hole this closes.
     */
    private suspend fun hasConsent(
        context: Context,
        callId: String,
        scope: ConsentScope
    ): Boolean {
        val ownUid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val peerUid = SecurePeerStore(context).observePeerUid().first { it != null }
            ?: return false
        // decisionNow already fails CLOSED on its own timeout, so no second
        // timeout is layered on top — one place decides what "could not check"
        // means, for every caller.
        return ConsentStore().decisionNow(callId, ownUid, peerUid, scope).isGranted
    }

    /**
     * Tells the holder a voice message is waiting, without ringing, without a
     * foreground service, and without putting the message itself on the lock
     * screen. Deliberately quiet: this is a "dad left you something", not a
     * call, and it must not wake the house at 2am.
     *
     * CONSENT-GATED. This used to post unconditionally, so a child whose parent
     * had switched the walkie talkie off still got an unsolicited "A message is
     * waiting" heads-up — an unwanted signal that something was said to them when
     * the parent had deliberately closed that door. Nothing leaked (the channel is
     * IMPORTANCE_LOW, VISIBILITY_PRIVATE and carries no content), which is exactly
     * why it went unnoticed: it looks harmless and it still contradicts the
     * control. Fail-closed, same as every other gate.
     */
    private fun handleClipWaiting(message: RemoteMessage) {
        val callId = message.data["callId"].orEmpty()
        if (callId.isEmpty()) return
        // Already in the app: the Firestore listener is live and will play it.
        if (AppVisibility.isForeground) {
            WebRtcLog.transition("PTT push skipped: app on screen, listener will play")
            return
        }
        val ctx = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            if (!hasConsent(ctx, callId, ConsentScope.PTT)) {
                WebRtcLog.transition("PTT nudge suppressed: consent denied")
                return@launch
            }
            postClipWaitingNotification(ctx)
        }
    }

    private fun postClipWaitingNotification(context: Context) {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                REQUEST_CODE_CLIP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            nm.notify(
                NOTIFICATION_ID_CLIP_WAITING,
                NotificationCompat.Builder(context, CallDadApplication.CHANNEL_PTT_MESSAGE)
                    .setSmallIcon(R.drawable.ic_mic)
                    .setContentTitle("A message is waiting")
                    .setContentText("Tap to hear it")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
            )
            WebRtcLog.transition("PTT clip waiting notification posted")
        }
    }

    /**
     * A notification-payload-free data message cannot show its own
     * notification, so when the FGS path is unavailable we post a heads-up
     * ourselves. Ringing is lost in this path, but the kid gets a visible,
     * tappable "Dad is calling" instead of silence.
     */
private fun postHeadsUpFallback(callId: String, seq: Int) {
        postIncomingCallFallback(this, callId, seq)
    }

    @Deprecated("FCM registration tokens stay until the FID-based Admin SDK migration.")
    override fun onNewToken(token: String) {
        WebRtcLog.transition("FCM token refreshed")
        PushTokenRegistrar.save(token)
    }

    companion object {
        /**
         * A notification-payload-free data message cannot show its own
         * notification, so when the FOREGROUND SERVICE path is unavailable we post
         * a heads-up ourselves. Ringing is lost in this path, but the kid gets a
         * visible, tappable "Incoming call" instead of silence.
         *
         * STATIC AND SHARED, because there are two distinct failures that need it
         * and only one of them used to have a fallback:
         *
         *  - `startForegroundService()` throwing (a high-priority FCM downgraded
         *    to normal cannot start an FGS). Handled in [handleRing].
         *  - `ServiceCompat.startForeground()` being REJECTED once the service is
         *    already running. That throw happens inside the service, so
         *    `handleRing` has already returned and its catch blocks never see it.
         *    Before this was shared, that path was TOTAL SILENCE — no ring, no
         *    notification, no trace — which is the exact permanent no-op
         *    [handleRing]'s comment claims to have fixed.
         *
         * This notification does NOT bypass the kill switch: it is only posted
         * when the service could not start, and the service's own consent gate
         * runs before it rings. A visible "Incoming call" on a phone whose parent
         * pulled the switch is still wrong, so the fallback is a degraded outcome
         * for a promote failure, not a substitute for the gate — and the gate runs
         * first in the normal path.
         */
        fun postIncomingCallFallback(context: Context, callId: String, seq: Int) {
            runCatching {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_RING_NOTIFICATION
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_SEQ, seq)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pi = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_FALLBACK,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                nm.notify(
                    NOTIFICATION_ID_FALLBACK,
                    NotificationCompat.Builder(context, CallDadApplication.CHANNEL_INCOMING_CALL)
                        .setSmallIcon(R.drawable.ic_call)
                        .setContentTitle("Incoming call")
                        .setContentText("Tap to see who's calling")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build()
                )
            }
        }

        private const val ACTION_RING_NOTIFICATION = "com.calldad.RING_NOTIFICATION"
        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_SEQ = "seq"
        private const val NOTIFICATION_ID_FALLBACK = 1002
        private const val REQUEST_CODE_FALLBACK = 2002
        private const val NOTIFICATION_ID_CLIP_WAITING = 1003
        private const val REQUEST_CODE_CLIP = 2003
    }
}
