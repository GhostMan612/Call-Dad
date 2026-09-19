# SPEC_SHEET.md — Call-Dad v0.1 scope contract

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
5. **Call log + message store:** local Room, call history (missed/answered), message persistence. No cloud sync v0.1.
6. **Allowlist + parent gate:** contacts = Dad only; adding anyone requires parent approval (biometric/PIN gate on Dad device or setup flow). Kid UI exposes zero settings that escape allowlist.

## 3. Non-goals v0.1

Group calls, multi-contact, cloud backup, cross-internet without rendezvous relay, AI features, analytics/crashlytics, accounts/login, app-store release, old-tablet minSdk backport (ADR-001 decides).

## 4. Kid-safe + privacy requirements

- Allowlist-only; Direct route only; no open broadcast/discovery.
- No accounts, analytics, ads, third-party phoning-home SDKs.
- Chat/voice/photo stay on-device; any future hub sync redacts them by default.
- Consent cert: Dad-grants-Kid `[call,text,photo]` + expiry; revocation = kill switch.
- Synthetic fixtures only; no real child data in repo.

## 5. Technical contract

- 100% native Kotlin; single-Activity + Compose Navigation + Hilt; one ViewModel/screen; `StateFlow<UiState>` + `SharedFlow<Event>`.
- Donor ports (adapt, don't copy-paste blind): `CallSignalingManager`, `SovereignCommsEngine`, `LiveCallSession` (:8789 UDP), `AudioFrameCipher` (AES-GCM), `SovereignImageEngine`, `RendezvousClient` + relay `main.go` (:8792/udp), `RealTimeTransportRouter` (P1 LAN → P2 SIM data), `SovereignAudioEngine` (Concentus-Opus), `WifiDirectGroupManager` (no-internet mode).
- Data: Room (contacts, call log, messages) + SQLCipher-at-rest decision per ADR-003.
- Toolchain pins per AGENTS.md; compileSdk 35 / target 35 / minSdk 30 pending ADR-001.
- Gates: `testDebugUnitTest` + `lintDebug` + `verify_project.py` green; device proof only from human runs (Moto G 2025 truth).

## 6. Firebase / Firestore (paid account, deferred)

v0.1 = sovereign P2P-first (LAN → rendezvous → DTN). Firebase/FCM reserved as Phase 4 optional push/signaling fallback (e.g., NAT-hard remote wake). No `google-services.json` until ADR-002 authorizes. See `docs/firebase-firestore-plan.md`.

## 7. Acceptance (v0.1 shippable to kid device)

- Kid taps giant button → Dad rings on LAN in <3s (human-measured), voice intelligible, hangup one tap.
- Remote (different networks): call connects via rendezvous relay (signaling only; media E2EE, relay never sees keys).
- Text + voice memo + photo each deliver Dad↔Kid with receipt, survive app restart (Room).
- Airplane-mode-with-WiFi / hotspot / no-internet-direct modes per BP-04 matrix pass on Moto G (human evidence).
- Kid cannot reach anyone but Dad; cannot open settings/browser/store from app (kid-UX audit per `docs/kid-safe-ux.md`).
