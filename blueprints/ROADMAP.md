# ROADMAP.md — Call-Dad phases

> Phase tracker with gates. Status flips only on evidence (CHECKPOINTS.md).

- [x] **Phase 0 — Scaffold:** workflow docs + reuse map + verify script. Gate G0 GREEN.
- [x] **Phase 1 — Skeleton:** `com.calldad` Compose Nav (Home/Call/Ptt/Game/Helper) + theme + VMs + RoutesTest. Gate G1 GREEN (host).
- [x] **Phase 2 — Signaling:** Firestore `SignalingClient` + `CallState` + VM/Screen rewire (ADR-002 DECIDED Firebase). Gate G2 GREEN (host).
- [x] **Phase 3 — Peer connection:** Stream WebRTC 1.3.10 + trickle ICE + STUN-only + callee fix (ADR-005). Gate G3 GREEN (host + device caller leg).
- [x] **Phase 4 — Rendering + incoming:** VideoRenderer + overlay + auto-popup + ringtone + crash/robustness fixes (ADR-006). Gates GREEN on device (E2E video + audio both ways, zero crashes).
- [x] **Phase 5 — Per-call rooms + FCM + lockdown (PROVEN ON DEVICE 2026-09-30):** anonymous auth + FGS wakeup + strict pair-scoped rules (ADR-015) + token-targeted FCM (ADR-016). Deployed live: rules released, `onCallRoomWritten` v2 us-central1 nodejs22, `databases/(default)` created. First end-to-end call proven: both phones call and answer, video good, PTT both ways. **K8 (TURN) closed and device-proven**: a call completed on mobile data with Wi-Fi off. Still open inside this phase: killed-app / doze / force-stop ring untested, Anonymous sign-in enablement not separately confirmed in console.
- [ ] **Phase 6 — Hardening.** Gate = v0.1 shippable.
  - **✅ parent gate** (multiplication, `ParentGate.kt`)
  - **✅ consent cert + kill switch** (ADR-017) — the model, the store, the parent-side grant/revoke, and the rules. Revocation is an append-only seq-range, not a flag, so a grant write cannot destroy it and a parent can still deliberately re-authorise. Absence denies, so a fresh install is inert until a parent grants.
  - **✅ release-signing plan** (`docs/release-signing.md` + `signingConfigs`); the keystore is operator-only and **no release artifact has ever been built** — `proguard-rules.pro` is a stub.
  - **✅ kid-UX audit** (`docs/kid-safe-ux.md` + `KidUxAuditTest`). Found and fixed a real escape: the system back button was only intercepted on Call and Game, so back on Helper left the app to the launcher.
  - **✅ SQLCipher closed as a deferral-with-justification** (ADR-003; re-opens when local message/photo storage lands).
  - **✅ auto-reconnect via ICE restart** (was voided in RULES §1.7a).
  - **✅ missed-call callback card** (BP-05 §4's last open criterion) + the call log behind it (ADR-018: DataStore, not Room, with the cap and drop order pinned).
  - **✅ 1:1 text chat** (BP-03 / SPEC_SHEET §2.3): thread, honest receipts, no-link rule, pair-scoped rules. **No tappable links, no autoLink, no intents, no autocorrect** — a chat box is the widest hole the allowlist could have.
  - **✅ photo sharing** (BP-04 / §2.4): chunked + SHA-256-verified transport, downscaled to 1080px WEBP, immutable manifest, pair-scoped rules, and a screen. Picking goes through the **permissionless system photo picker**, so the app holds no `READ_MEDIA_IMAGES` and no `CAMERA`; nothing is written back to the device's gallery. An unverified photo renders as a sentence, never as a half-decoded image.
  - **✅ parent-side consent controls** — "Allow everything" / "Turn everything off" behind the grown-ups gate, so the kill switch has a button. Deliberately two actions, not a per-scope checkbox grid.
  - **☐ text chat + photo survive app restart**: neither is witnessed on a device.
  - **☐ every human-witnessed device proof below**, including the locked-phone checks.
- [x] **Phase 6a — PTT encoder-drain re-test (K14):** CLOSED 2026-09-30. vc6/0.2.3 clean-built and installed to both phones; operator-witnessed both directions, no clipping and no dropped syllables. The 700ms silent tail lets the AAC encoder flush before the MPEG-4 container is finalised.
- [ ] **Phase 7 — device proof.** Backend DONE: `firestore.rules` **RELEASED 2026-10-01** (chat, photos, consents, revocations, `negotiationRound` — live, emulator 44/44) plus `onCallRoomWritten` and `onPttClipWritten` (v2, nodejs22, us-central1, confirmed by `functions:list`); **mobile-data call PASSED, closing K8**. **vc10/0.3.0 built clean and INSTALLED to both phones 2026-10-01 08:19** (`dumpsys` verified: parent 08:19:05, child 08:19:19, one flavor per device, `-r` so pairing and consent survived). REMAINING, all human-witnessed and none closeable by writing code: **first-run consent** (the app is inert until a parent taps the shield icon, which looks identical to a broken app — check this first); locked-phone quiet-notification check (K12) and the keyguard no-takeover check (K21), fixed in vc10 but never verified; a call end-to-end on vc10 (the E2E proof is from vc7); a text message both ways including a refused link; a real photo sent and verified; the missed-call callback card; an ICE restart recovered (pull Wi-Fi mid-call); re-pair under the tightened K17 read; killed-app ring, force-stop, doze, no-answer timeout, lost-peer end, game sync mid-call.

Future (not v0.1): multi-contact, group calls, cloud backup, AI, store release.
