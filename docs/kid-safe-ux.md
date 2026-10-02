# Kid-safe UX (6-year-old)

- One giant Call Dad button (≥96dp, high contrast, haptic + ring). One-tap hangup always visible in-call. Auto-reconnect on LAN drop with loud status ("Calling Dad…").
- One action per screen: Home (call), Messages (giant send), Pictures (send/view), Walkie Talkie (hold to talk), Games, Ask Helper. A missed call raises ONE giant "Call back" card on Home (built; no separate Log screen). No tabs-that-trap, no keyboards by default. **Note: text IS a keyboard, deliberately** — a 6-year-old cannot dictate at this age, and the composer is sandboxed instead (no autocorrect, no links, no intents): see criterion 10.
- No escape: no links/browser/store/settings reachable from kid screens; system back lands on Home; parent area behind biometric/PIN gate.
- Statuses read aloud eventually (TTS lane later): sent→delivered→read as words ("Got there" / "Seen"). Words, not ticks — a tick legend needs a parent to explain it.
- Fixtures/art: synthetic only (placeholder dad/kid avatars in `assets/`, fake names). Never commit real photos.

---

## BP-05 §4 audit sheet

Twelve criteria. **Machine-checked** rows are pinned by `KidUxAuditTest` and fail the
gate if they regress. **HUMAN-witnessed** rows cannot be asserted by any host test
and are marked as such rather than quietly ticked — claiming them would be the same
class of invented pass this repo keeps catching.

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Primary touch targets ≥96dp | **PASS (machine)** | `KidUxAuditTest.everyPrimaryControlIsAtLeast96dp`, `theParentGateControlsAlsoClearTheFloor`. `GiantButton` defaults to 100dp; the parent gate's keys are 96dp. |
| 2 | Contrast ratios | **HUMAN** | Needs a rendered frame. Check the giant buttons in both light and dark system themes, and on the child's actual phone at their brightness. Not asserted. |
| 3 | One action per screen | **PASS (machine, partial)** | Home is a fixed no-scroll 2×2 grid (`HomeScreen.kt`). Verified by inspection; no host test, because "one action" is a judgement per screen. |
| 4 | No escape (no browser/store/settings) | **PASS (machine)** | `KidUxAuditTest.thereIsNoBrowserOrStoreEscapeFromAnyScreen` — no `ACTION_VIEW`/`ACTION_BROWSABLE`/`ACTION_SEND`/`ACTION_WEB_SEARCH`/`market://` anywhere in `ui/`. The app holds exactly one `startActivity`, internal. **Back-press fixed 2026-09-30**: `AppNavHost` now installs a `BackHandler` that pops to Home, so the system back button can no longer drop a child out to the launcher. Previously only Call and Game intercepted it. |
| 5 | Loud ring | **HUMAN (partly device-proven)** | `CallAudioManager` is the single ringer. Device-proven for the ring itself. **The voice-message notification was NOT quiet** — K12, fixed in source at vc9 and **carried by vc10, the build on both phones** — unproven on any build. |
| 6 | Missed-call "Call back" card | **PASS (machine) + HUMAN (unwitnessed)** | BUILT 2026-10-01. `CallLog.callbackCard` decides (one card, most-recent only, 24h window, MISSED/FAILED but never DECLINED) and `HomeScreen.CallbackCard` renders it at ≥72dp. Persistence is `CallLogStore` (DataStore — ADR-018, **not** Room), and `CallViewModel.commitTerminal` feeds it from the single funnel every exit path uses. `CallLogTest` pins the card, the window, the decline exclusion, the cap and the drop order. **Unwitnessed on a device.** |
| 7 | Back-stack walk (the system back button) | **PASS (machine, partial)** | `launchSingleTop` on every navigate; every "back home" path uses `popUpTo(HOME)`, so the stack cannot grow unbounded (`AppNavigation.kt`). The press-back walk itself is HUMAN. |
| 8 | Airplane-recovery | **HUMAN** | Needs two phones and a radio. Untested. The source behaviour is bounded: a lost call ends at `LOST_GRACE_MS` and the child returns Home (§1.7a), and an ICE restart re-gathers on the same transport before that. |
| 9 | No real child data in committed screenshots | **PASS (machine)** | `KidUxAuditTest.noScreenshotOrMediaIsCommitted` — no `.png`/`.jpg`/`.jpeg`/`.webp`/`.heic` outside `res/drawable`, `build/`, `.git/`. |
| 10 | The chat thread is not a way out | **PASS (machine)** | `ChatKidSafetyTest` (8 cases). The chat box is the widest hole the allowlist could have: a message is the one place a GROWN-UP authors text for a child. Bodies are plain `Text` — no `ClickableText`, no `autoLink`, no link preview, no `ACTION_VIEW`, no autocorrect/predictive keyboard. `ChatText` also *rejects* link-shaped text at send time, so the parent cannot arm it in the first place. Asserted against CODE, not comments. |
| 11 | The thread cannot trap a child | **PASS (machine)** | Back arrow and send are both 96dp via a named `TOUCH_TARGET_DP` constant the test reads — a literal at the call site is exactly what gets "tidied" down to 64dp later. At most 4 `onClick` sites in the whole screen. |
| 12 | Pictures are not a door to the device | **PASS (machine)** | `PhotoSafetyTest`. The app requests **no** `READ_MEDIA_IMAGES` and no `READ/WRITE_EXTERNAL_STORAGE` — picking goes through the permissionless system photo picker, so a 6-year-old cannot browse the camera roll from inside this app. **The app DOES declare `CAMERA`,** because the video call needs it (Phase 3, ADR-005); what is pinned is the narrow true property — the *photo path* uses no camera, and the camera *feature* is `required="false"` so an audio-only device can still install. An earlier version of this row claimed "no `CAMERA`" and of the test asserted the same, which was **wrong on its own terms** and only ever "passed" because the assertion threw before it ran. Nothing is written back to `MediaStore`, so a received photo cannot be read by any other app. There is no share or save affordance. An unverified photo renders a sentence, never partial bytes. |

### Known gaps this sheet is recording rather than hiding

- **Nothing in this sheet has been re-witnessed on a device since 2026-09-30**, and
  the phones are on **vc10 / 0.3.0** (flashed 2026-10-01 08:19) while the source is
  **vc11 / 0.3.1**. The machine rows describe vc11; the device describes vc10.
  Treat every device-dependent row as unknown.
- **The build installed on both phones has a parental kill switch that enforces
  nothing.** vc10 closes Messages and Pictures while calling, the walkie talkie
  (both directions, including clips playing aloud) and photo/chat downloads carry
  on regardless. Fixed in vc11 — in source, on no device. **No version of this app
  has ever demonstrably enforced a kill switch**, and this sheet says nothing to
  the contrary about that.
- **Criterion 2 has never been measured.** No contrast ratio has ever been computed
  for either theme. Treat as unknown, not as passing.
- **The consent screen has never been used.** Two buttons, a gate, and a
  sequence-numbered grant — all host-tested, none of it seen by a human. The
  highest-risk untested thing in the app is the one a parent will be looking at
  the first time the child cannot call.
- **A fresh install is inert until a parent grants consent.** Absence of a grant
  DENIES (ADR-017), so on a first run the child can reach nothing until the parent
  side grants. That is intended fail-closed behaviour and it is also the most
  likely cause of "the app does nothing" — it belongs on this sheet as a UX fact,
  not just in an ADR.
- **Auto-reconnect is no longer void.** `RULES.md` §1.7a's "Reconnect: NOT
  implemented" entry was replaced 2026-09-30: ICE-restart ships, scoped to a
  recoverable network drop, not LAN-only calling and not a symmetric-NAT carrier
  proof.