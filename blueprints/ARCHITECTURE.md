# ARCHITECTURE.md — Call-Dad

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.
>
> The banner covers a plan that was **superseded**. It does not cover a plan that was
> **rejected**: Hilt, Room, SQLCipher, OkHttp, Concentus and KSP were never adopted
> and are not in `gradle/libs.versions.toml`. Where this file names one as part of the
> technical contract that would be an unbuildable instruction, so each such line now
> negates it inline — see the UI, Data and Consent sections, which have been corrected
> rather than left to the banner.

> Structural truth. Code > prose on conflict.

## UI (pathfinder KOTLIN_PORT_SPEC §1 pattern)

Single-`Activity` + Compose Navigation, **hand-written ViewModel factories — no Hilt** (there is no `core-splashscreen` either), one `ViewModel` per screen, `StateFlow<UiState>` (single emission per mutation) + `SharedFlow<Event>` one-shots (ring, snackbar, navigate). `collectAsStateWithLifecycle()` in composables.

Routes as declared in `navigation/Routes.kt` (whose KDoc calls itself the single source of truth for the graph): `HOME / CALL / CHAT / PHOTO / PTT / GAME / HELPER`, plus `CONSENT` and `PAIRING`. **There is no `Log` route** — history is reached from Home, via the missed-call callback card.

Screens: `HomeViewModel` (six doors + the callback card), `CallViewModel` (Invite/ringing/in-call/hangup), `ChatViewModel` (thread + receipts), `PttViewModel` (hold-to-record clip), `HelperViewModel` (voice helper), `ConsentViewModel` (Dad-gated grant/revoke — the kill switch's only trigger), `PhotoViewModel` (permissionless system picker → send → verified view; **no camera on the photo path** — `PhotoSafetyTest` pins that, and the *camera feature* stays `required="false"` so an audio-only device can install), plus the pairing screens. **The game screen has NO ViewModel** — `GameScreen.kt` talks through `game/GameWebRtcBridge.kt` to `assets/game.html`, so there is no `GameViewModel` to name. **Not built: `LogViewModel` and `SettingsViewModel`** — there is no separate log or settings screen.

## Comms (mantle donor ports — see docs/sovereign-comms-reuse-map.md)

**Everything below this line is the DESIGN AS PROPOSED IN 2026-09-24, NOT WHAT SHIPPED.** The UDP sockets, the AES-GCM frame cipher, the rendezvous relay, `RealTimeTransportRouter`, `RealTimeTextChannel`, `SovereignMeshTransport`, `WifiDirectGroupManager`, `SovereignAudioEngine` and Concentus-Opus are **absent from the tree**. Shipped: pair-scoped Firestore rooms as signaling (ADR-015), WebRTC for media with DTLS-SRTP + Opus internally (ADR-005), a TURN relay for hard NATs, token-targeted FCM for wakeup, and AAC voice clips for the walkie talkie (ADR-016). What was ported is the *shape* — the Invite→Accept/Decline→End state machine and the mantle-comment idiom — not the transports.

- `CallSignalingManager` (transport-agnostic): `Invite→Accept/Decline→End`, `Direct` route only, anti-echo/replay, expiry.
- `LiveCallSession` + `AudioStreamingSession`: UDP `:8789` duplex voice sockets; sequence-tagged frames.
- `AudioFrameCipher`: AES-256-GCM `IV(12)||ciphertext+tag(16)` per-frame, per-call ECDH key, null-on-fail=drop.
- `SovereignCommsEngine`: `ChatMessage`/`VoiceMemo` ingest (idempotent) + delivery receipts.
- `RealTimeTextChannel`: `RT+ver+origin/dest+SealedPayload` typing/live-caption datagrams.
- `RealTimeTransportRouter`: link probes → ranked `P1 WIFI_DIRECT/HOTSPOT (0-cost)` → `P2 SIM_DATA`.
- `RendezvousClient` + `RendezvousRegistry` codec (`REGISTER 0x01 / LOOKUP 0x02 / CANDIDATE 0x03`) + Go relay `:8792/udp` (signaling only).
- `SovereignMeshTransport` 4096B framing discipline for local mode; `WifiDirectGroupManager` for no-internet direct.
- `SovereignAudioEngine` + `VoiceCodec`/`JitterBuffer` (Concentus-Opus 1.0.2); `SovereignImageEngine` chunked photo.
- `SovereignNodeService` foreground-service pattern for ringing/call persistence.

## Data

**There is no `CallDadDatabase`, no Room, no `kotlinx.serialization`, and no SQLCipher in this build** (ADR-018, and ADR-003 deferred-with-justification). What shipped instead:

- **Call log** — DataStore preferences, one record per call.
- **Messages** — pair-scoped Firestore `calls/{uidA_uidB}/chat/`, **not** `messages/`. The collection is `chat` (`ChatClient.kt`, and the only chat match in `firestore.rules:116`); there is no `messages` stanza anywhere, so a write to `/messages/` is denied by default. §2.5's "local message store" is recorded as **unmet** in `SPEC_SHEET.json`, not reinterpreted.
- **Allowlist** — a single paired peer UID in an in-memory peer store, backed by a mutual `pairings/` handshake. There is no `contacts` table.
- **Consent** — fail-closed per-scope grants in the pair room (ADR-017); absence denies.
- **Photos** — chunked + SHA-256-verified under `calls/{room}/photos/`, never written to shared storage.

## Consent (mantle consent.py model)

**Historical — superseded by ADR-017.** There is no signed cert and no `expires` field. What shipped: **fail-closed per-scope grants** (`CALL`/`PTT`/`TEXT`/`PHOTO`/`VOICE`) written to the pair room by the parent, where **absence DENIES**, and revocation is an **append-only seq range** rather than a self-revocation-only rule. Scopes gained: PTT (the walkie talkie was outside the model) and VOICE (the Helper microphone was too — and it was falling back to the NETWORK recognizer, uploading a child's speech off-device). Revocation = kill switch (calls/messages/photos blocked + explicit UI), and it is enforced at the point of use **and** on every feature's Firestore listener **and in `CallForegroundService`**, the only consumer that runs when the app is not running.

The lesson from that supersession is recorded in `ADR-017`: the signed-cert model was complete, documented and emulator-proven, and vc10 enforced none of it on the two oldest features in the app. A consent model that is only *stored* is not a consent model.
