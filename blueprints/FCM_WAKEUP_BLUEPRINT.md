# FCM Wakeup Blueprint (Contract 7 implementation target)

> Status: BLUEPRINT. Nothing below is implemented — Contract 6 only
> executes Parts 1–4 (pairing hold, NoAnswer hardening, watchdog).
> Package in this repo is `com.calldad` (not `com.calldad.app`).

## Current state (already present, keep)

- `CallDadApplication` creates `CHANNEL_INCOMING_CALL` (IMPORTANCE_HIGH),
  signs in anonymously, subscribes both flavors to `incoming_calls`.
- `MainActivity` requests POST_NOTIFICATIONS (API 33+), checks
  `canUseFullScreenIntent()` (API 34+), routes FSI action to the overlay.
- `CallMessagingService` receives topic pushes, starts the FGS.
- `CallForegroundService` runs `phoneCall` type, validates the static
  room before promoting, carries `ACTION_INCOMING_CALL`.
- Manifest already declares: POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT,
  FOREGROUND_SERVICE (+PHONE_CALL, +MICROPHONE), RECORD_AUDIO, WAKE_LOCK,
  CAMERA/INTERNET, ML Kit `DEPENDENCIES=barcode` metadata.

## Proposed changes (Contract 7)

### 5A. Permissions — verify present, no change expected (see list above).

### 5B. Manifest FGS type change: `phoneCall` → `microphone`

```xml
<service
    android:name=".fcm.CallForegroundService"
    android:exported="false"
    android:foregroundServiceType="microphone" />
```

Rationale: the service guards the mic for the WebRTC session; the
`phoneCall` type implies Telecom/ConnectionService integration this app
does not have. Pair with the RECORD_AUDIO pre-check (5F).

### 5C. Manifest launchMode

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    ... >
```

Prevents duplicate activities when the FSI PendingIntent fires while the
app is alive. Works with the existing `FLAG_ACTIVITY_SINGLE_TOP`.

### 5D. ACTION_INCOMING_CALL canonical value

```kotlin
const val ACTION_INCOMING_CALL = "com.calldad.fcm.INCOMING_CALL"
```

Current code uses `"com.calldad.INCOMING_CALL"`. Both sides reference the
constant, so the rename is safe — but Contract 7 must change the single
declaration AND confirm no stale string literal remains. The FCM data
payload never carries this action (internal use only).

### 5E. CallMessagingService — post FSI notification

Move FSI construction into the receiver (current: service builds it).
Keep `REQUEST_CODE_FSI = 847291`, `NOTIFICATION_ID = 1001`. PendingIntent
MUST include action + `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP`
+ API-34-guarded `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED`
+ `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`. Post via `NotificationManagerCompat`.

### 5F. CallForegroundService — instance AtomicBoolean + RECORD_AUDIO

```kotlin
private val isRunning = AtomicBoolean(false) // INSTANCE field, not companion
```

- `onStartCommand`: `compareAndSet(false, true)` else log + `START_NOT_STICKY`.
- RECORD_AUDIO check before `startForeground()`; log + `stopSelf()` on denial.
- `onDestroy`: `isRunning.set(false)`.
- A companion field would persist across instances and block legitimate
  restarts after a process kill.

### 5G. MainActivity — deferred FGS start via onResume

`onCreate`/`onNewIntent` only set `pendingCallStart`. `onResume` consumes
it: navigate + `startCallForegroundService()` + clear intent action.
Rationale: `onNewIntent` fires while PAUSED; while-in-use FGS rules need
RESUMED. Handle `ForegroundServiceStartNotAllowedException` gracefully.

### 5H. Full-screen intent check — ALREADY PRESENT

`MainActivity.checkFullScreenIntentAccess()` implements this. Keep.

### 5I. POST_NOTIFICATIONS dependency — ALREADY PRESENT

`MainActivity.requestNotificationPermission()` implements this (launch,
non-blocking). Keep. If denied, FCM wakeup is dead by platform design —
document, do not work around.

### 5J. Component summary

| Component            | Purpose                                    |
|----------------------|--------------------------------------------|
| CallMessagingService | Receives FCM, posts FSI notification       |
| MainActivity         | onResume() starts FGS when visible         |
| CallForegroundService| microphone FGS (instance AtomicBoolean)    |
| NotificationManager  | Posts the FSI notification                 |
| CallViewModel        | Observes Firestore for call status         |

FSI demotion (screen ON + unlocked → heads-up, not full-screen) is
expected Android 14 behavior, not a bug.

## Contract 6 corrections folded in from the blueprint review

- `onNewIntent` fires while PAUSED → defer to `onResume()` (5G).
- `isRunning` is instance-level (5F).
- API-34 `ActivityOptions` branch is version-guarded (5E).
- POST_NOTIFICATIONS pairing/first-call request points already implemented
  (5I); FSI check already implemented (5H).
