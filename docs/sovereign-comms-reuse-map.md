# Sovereign-comms reuse map (donor: C:\sovereign_mantle — READ-ONLY)

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.

> Copy out + adapt into `C:\Call-Dad\app`. Never edit donor. Credit pattern in code headers/docs.

## Direct ports (BP-02..04)
| Donor (absolute) | Use in Call-Dad | Notes |
|---|---|---|
| `android_node\app\src\main\java\com\sovereign\mantle\comms\CallSignalingManager.kt` (319L) | `comms/CallSignalingManager.kt` verbatim-base | Invite→Accept/Decline→End; keep Direct-only, anti-echo/replay |
| `.../comms/LiveCallSession.kt` (`:8789` UDP duplex) | `comms/LiveCallSession.kt` | Voice path; add video track alongside (spike) |
| `.../comms/AudioStreamingSession.kt` | socket template | sequence-tagged streamer `AudioFrameCipher` wraps |
| `.../comms/AudioFrameCipher.kt` (AES-256-GCM per-frame) | `comms/AudioFrameCipher.kt` | `IV(12)\|\|ct+tag(16)`, ECDH per-call, null-on-fail=drop |
| `.../comms/SovereignCommsEngine.kt` (642L chat+PTT+receipts) | `comms/ChatEngine.kt` | ChatMessage/VoiceMemo, idempotent ingest |
| `.../comms/RealTimeTextChannel.kt` (147L) | typing/live captions | `RT+ver+origin/dest+SealedPayload` |
| `.../comms/RealTimeTransportRouter.kt` | `comms/TransportRouter.kt` | probes → P1 WIFI_DIRECT/HOTSPOT → P2 SIM_DATA |
| `.../comms/RendezvousClient.kt` + `RendezvousRegistry.kt` (101L codec) | rendezvous client | REGISTER `0x01`/LOOKUP `0x02`/CANDIDATE `0x03`; null-safe no-op without server |
| `rendezvous-relay\main.go` (`:8792/udp`, TTL45s/sweep30s) + `go.mod` (Go1.21) | relay ops | deploy 1vCPU VPS; reflexive-addr authoritative; never media/keys |
| `.../mesh/SovereignMeshTransport.kt` (845L, 4096B framing) | local-mode framing discipline | ghost-chaff optional later |
| `.../image/SovereignImageEngine.kt` (319L chunked photo) | `comms/PhotoEngine.kt` | downscale→WEBP cap→chunk→reassemble |
| `.../audio/SovereignAudioEngine.kt` + `VoiceCodec/JitterBuffer/LinearResampler` (Concentus-Opus 1.0.2) | audio pipeline | mic→Opus→cipher→socket→jitter→play |
| `.../mesh/WifiDirectGroupManager.kt` | no-internet direct mode | group-owner/client + link-local |
| `.../ChatTerminal.kt` (1589L Compose) | UI reference ONLY | strip to big-button kid UI; do not port wholesale |
| `.../service/SovereignNodeService.kt` | foreground ringing service pattern | persistent call + auto-lock interplay |

## Consent/security models
- `core/engine/consent.py` → Dad-grants-Kid `[call,text,photo]` + expiry cert; revocation = kill switch.
- `identity_event_schemas.py` → `CONSENT_GRANTED_PAYLOAD{granter,grantee,scope,purpose,expires}` shape.
- `.../security/HardwareKeyStore.kt + SecureStorageManager.kt + DualKeyGate.kt + BiometricAuthManager.kt + PeerAttestationPolicy.kt + AuditLogger.kt + MemoryScrubber.kt` → Keystore keys, SQLCipher wrap, parent-approves-contact, TOFU peer pin, auto-lock, key scrub.
- `hub_sync_bridge.py` redaction posture → chat/PTT/photo never sync without parent opt-in.

## NOT reused
- `mesh_sync_protocols.sh` (git/rsync backup only, not realtime) — ignore for calls.
- `tagger/{pipeline,acr_kernel}` (music tagging) — ignore except chunk+hash idiom.
- `core/agents/agents_lib/ai_phone_agent.py` (genealogy Q&A, not VoIP) — ignore.
- `security/encryption_engine.py` Fernet legacy — prefer Keystore+SQLCipher.

## Wire summary
`P1 LAN UDP 4096B E2EE → P2 rendezvous REGISTER/LOOKUP/CANDIDATE + hole-punch :8791/8792 → DTN fallback`. Voice bypasses ledger; chat/photo ride chunked events. Spec: `vault/blueprints/rendezvous-relay-spec.md` (§2 wire, §4 logic, §7 NAT/TURN/HMAC gaps — read before remote work).
