# SPEC_SHEET.md — Call-Dad v0.1 scope contract

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.
>
> The banner covers a plan that was **superseded**. It does not cover a plan that was
> **rejected**: Hilt, Room, SQLCipher, OkHttp, Concentus and KSP are not in
> `gradle/libs.versions.toml` and were never adopted. §5 below had three of them as
> part of its technical contract, which is an unbuildable instruction rather than a
> historical note, so they are now negated inline. `SPEC_SHEET.json` (machine truth)
> already recorded each as NOT-in-the-build; the prose had not caught up.

> Human-readable companion to `SPEC_SHEET.json` (machine truth). RULES.md wins conflicts.

## 1. Product

- Working title: **Call Dad** (undecided, operator confirms).
- Package (proposed): `com.calldad`. Label: "Call Dad".
- User: 6-year-old girl (kid device). Counterparty: Dad (operator device). v0.1 allowlist = Dad only.
- Core promise: **one giant Call Dad button that always works on LAN; remote works via rendezvous; nothing else reachable.**

## 2. v0.1 features (frozen — new ideas go to ROADMAP Phase 5+)

1. **Voice call** (LAN-first, rendezvous-remote): Invite→Accept/Decline→End, E2EE voice, one-tap hangup, auto-reconnect on LAN drop.
2. **Video call** (LAN-first): same signaling + CameraX preview both ends, mute-video fallback to voice. Codec/spike per ADR (extend UDP pattern vs WebRTC).
3. **Text + voice-memo chat:** 1:1 thread with Dad, delivery receipts, voice memos (Opus), no group threads.
4. **Photo sharing:** 1:1, chunked + verified, downscaled + WEBP-capped (donor `SovereignImageEngine` idiom), on-device only.
5. **Call log + message store:** call history (missed/answered) and message persistence. **Shipped as DataStore preferences + pair-scoped Firestore, not local Room** (ADR-018).
6. **Allowlist + parent gate:** contacts = Dad only; adding anyone requires parent approval (biometric/PIN gate on Dad device or setup flow). Kid UI exposes zero settings that escape allowlist.

## 3. Non-goals v0.1

Group calls, multi-contact, cloud backup, cross-internet without rendezvous relay, AI features, analytics/crashlytics, accounts/login, app-store release, old-tablet minSdk backport (ADR-001 decides).

## 4. Kid-safe + privacy requirements

- Allowlist-only; Direct route only; no open broadcast/discovery.
- No accounts, analytics, ads, third-party phoning-home SDKs.
- Chat/voice/photo stay on-device; any future hub sync redacts them by default.
- Consent grants: Dad-grants-Kid over **five** scopes — `CALL` / `PTT` / `TEXT` / `PHOTO` / `VOICE` — plus expiry; absence DENIES; revocation is an append-only seq range and is the kill switch. (This line originally read `[call,text,photo]`, which is what the spec asked for; `PTT` was added before v0.1 shipped and `VOICE` on 2026-10-02, because the walkie talkie and the Helper microphone were the two features outside the consent model — a control that says "nothing is allowed" while a microphone is still open is not a control. See ADR-017.)
- Synthetic fixtures only; no real child data in repo.

## 5. Technical contract

- 100% native Kotlin; single-Activity + Compose Navigation; **hand-written ViewModel factories, no Hilt**; one ViewModel/screen; `StateFlow<UiState>` + `SharedFlow<Event>`.
- Donor ports (adapt, don't copy-paste blind): `CallSignalingManager`, `SovereignCommsEngine`, `LiveCallSession` (:8789 UDP), `AudioFrameCipher` (AES-GCM), `SovereignImageEngine`, `RendezvousClient` + relay `main.go` (:8792/udp), `RealTimeTransportRouter` (P1 LAN → P2 SIM data), `SovereignAudioEngine` (Concentus-Opus), `WifiDirectGroupManager` (no-internet mode). **Of these, only the mantle/idiom patterns shipped** — the LAN/UDP sockets, the rendezvous relay, the AES-GCM frame cipher, Concentus-Opus and `WifiDirectGroupManager` are NOT in the build (WebRTC supplies DTLS-SRTP + Opus; signaling is pair-scoped Firestore, ADR-015).
- Data: **no Room and no SQLCipher.** The call log is DataStore preferences and the message thread is pair-scoped Firestore (ADR-018); at-rest crypto is deferred-with-justification (ADR-003).
- Toolchain pins per AGENTS.md; compileSdk 35 / target 35 / **minSdk 26** (ADR-001-B decided).
- Gates: `:app:testParentDebugUnitTest :app:testChildDebugUnitTest` + `:app:lintParentDebug :app:lintChildDebug` + `tools/verify_project.py` green, via the `gate` tool (the unflavored `testDebugUnitTest`/`lintDebug` task names do not exist — flavors rename every variant task). Device proof only from human runs (Moto G 2025 truth).

## 6. Firebase / Firestore (paid account, deferred)

v0.1 was specified here as sovereign P2P-first (LAN → rendezvous → DTN) with Firebase/FCM reserved as an optional push/signaling fallback. **ADR-002 is now DECIDED and Firebase is the shipped signaling + wakeup path** (ADR-015 pair-scoped rooms), so `app/google-services.json` is **required and operator-placed** — the merged download containing both `com.calldad.parent` and `com.calldad.child`, or the flavored builds fail. An earlier version of this section withheld the file until ADR-002 authorized it; following that literally would leave the app unbuildable. See `docs/firebase-firestore-plan.md`.

## 7. Acceptance (v0.1 shippable to kid device)

- Kid taps giant button → Dad rings on LAN in <3s (human-measured), voice intelligible, hangup one tap.
- Remote (different networks): call connects via rendezvous relay (signaling only; media E2EE, relay never sees keys).
- Text + voice memo + photo each deliver Dad↔Kid with receipt, survive app restart.
- Airplane-mode-with-WiFi / hotspot / no-internet-direct modes per BP-04 matrix pass on Moto G (human evidence).
- Kid cannot reach anyone but Dad; cannot open settings/browser/store from app (kid-UX audit per `docs/kid-safe-ux.md`).
