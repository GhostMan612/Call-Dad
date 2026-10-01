# Kid-safe UX (6-year-old)

- One giant Call Dad button (≥96dp, high contrast, haptic + ring). One-tap hangup always visible in-call. Auto-reconnect on LAN drop with loud status ("Calling Dad…").
- One action per screen: Home (call), Chat (giant send + voice-memo hold-to-talk), Photo (take/send/view), Log (missed = giant "Call back" card). No tabs-that-trap, no keyboards by default (voice-memo first, text optional with huge keys).
- No escape: no links/browser/store/settings reachable from kid screens; system back lands on Home; parent area behind biometric/PIN gate.
- Statuses read aloud eventually (TTS lane later): sent→delivered→read as icons + words ("Dad got it").
- Fixtures/art: synthetic only (placeholder dad/kid avatars in `assets/`, fake names). Never commit real photos.

---

## BP-05 §4 audit sheet

Six criteria. **Machine-checked** rows are pinned by `KidUxAuditTest` and fail the
gate if they regress. **HUMAN-witnessed** rows cannot be asserted by any host test
and are marked as such rather than quietly ticked — claiming them would be the same
class of invented pass this repo keeps catching.

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Primary touch targets ≥96dp | **PASS (machine)** | `KidUxAuditTest.everyPrimaryControlIsAtLeast96dp`, `theParentGateControlsAlsoClearTheFloor`. `GiantButton` defaults to 100dp; the parent gate's keys are 96dp. |
| 2 | Contrast ratios | **HUMAN** | Needs a rendered frame. Check the giant buttons in both light and dark system themes, and on the child's actual phone at their brightness. Not asserted. |
| 3 | One action per screen | **PASS (machine, partial)** | Home is a fixed no-scroll 2×2 grid (`HomeScreen.kt`). Verified by inspection; no host test, because "one action" is a judgement per screen. |
| 4 | No escape (no browser/store/settings) | **PASS (machine)** | `KidUxAuditTest.thereIsNoBrowserOrStoreEscapeFromAnyScreen` — no `ACTION_VIEW`/`ACTION_BROWSABLE`/`ACTION_SEND`/`ACTION_WEB_SEARCH`/`market://` anywhere in `ui/`. The app holds exactly one `startActivity`, internal. **Back-press fixed 2026-09-30**: `AppNavHost` now installs a `BackHandler` that pops to Home, so the system back button can no longer drop a child out to the launcher. Previously only Call and Game intercepted it. |
| 5 | Loud ring | **HUMAN (partly device-proven)** | `CallAudioManager` is the single ringer. Device-proven for the ring itself. **The voice-message notification was NOT quiet** — K12, fixed in source at vc9, unproven on device. |
| 6 | Missed-call "Call back" card | **FAIL — not built** | `EndReason.MISSED` exists (`CallState.kt`) and renders as text, but nothing persists it and `HomeScreen` has no callback card. Blocked on local storage, which is what re-opens ADR-003. |
| 7 | Back-stack walk (the system back button) | **PASS (machine, partial)** | `launchSingleTop` on every navigate; every "back home" path uses `popUpTo(HOME)`, so the stack cannot grow unbounded (`AppNavigation.kt`). The press-back walk itself is HUMAN. |
| 8 | Airplane-recovery | **HUMAN** | Needs two phones and a radio. Untested. The source behaviour is bounded: a lost call ends at `LOST_GRACE_MS` and the child returns Home (§1.7a). |
| 9 | No real child data in committed screenshots | **PASS (machine)** | `KidUxAuditTest.noScreenshotOrMediaIsCommitted` — no `.png`/`.jpg`/`.jpeg`/`.webp`/`.heic` outside `res/drawable`, `build/`, `.git/`. |

### Known gaps this sheet is recording rather than hiding

- **Criterion 6 fails.** A missed call is not offered back. This is a real missing
  feature, not an audit artefact, and it is on the Phase 6 list.
- **Criterion 5's quiet-notification half is unproven.** "Loud ring" covers the
  call. The K12 voice-message loudness defect was found by this discipline and fixed
  in source; the device proof is still owed.
- **Criterion 2 has never been measured.** No contrast ratio has ever been computed
  for either theme. Treat as unknown, not as passing.
- **Auto-reconnect is no longer void.** `RULES.md` §1.7a's "Reconnect: NOT
  implemented" entry was replaced 2026-09-30: ICE-restart ships (ADR-017 sibling
  work), scoped to a recoverable network drop, not LAN-only calling and not a
  symmetric-NAT carrier proof.