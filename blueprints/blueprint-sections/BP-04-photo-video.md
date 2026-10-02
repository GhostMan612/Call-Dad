# BP-04 — Photo share + video-call spike + offline matrix (Phase 4)

> **HISTORICAL (2026-09-24):** option B won — the video spike below shipped as WebRTC (ADR-005),
> not the donor's `LiveCallSession` UDP extension, and the offline matrix was never run from human
> evidence. Photos shipped as chunked Firestore in the pair room (ADR-016). Current shape:
> `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md`, ADR-005 and ADR-016.

Goal: chunked verified photo E2E; video approach decided + LAN-proven; offline modes mapped.

## Port/research
1. `SovereignImageEngine` (downscale→WEBP cap→chunk→reassemble→verify) + `PhotoViewModel` (capture/pick/send/view + receipt). Byte-identical host test on synthetic fixture.
2. Video spike (Gemini R&D): option A extend `LiveCallSession` UDP with CameraX frames (donor-faithful, more work); option B WebRTC (new dep, NAT-friendly, needs ADR + privacy review). Spike both on paper; implement winner on LAN only.
3. Offline matrix (human runs): airplane+WiFi, hotspot-host, hotspot-join, no-internet Wi-Fi-Direct group; record which routes connect.

## Firebase note — SHIPPED, NOT A NOTE
ADR-002 is DECIDED. `app/google-services.json` is **REQUIRED and operator-placed** (gitignored;
the merged download containing both `com.calldad.parent` and `com.calldad.child`, or the flavored
builds fail). An earlier version of this section deferred the file until ADR-002 was decided —
that was stale, and following it literally would leave the app unbuildable. FCM wakeup also
shipped, as a data-only token-targeted push via `onCallRoomWritten` (ADR-015 §2), superseding the
topic design in `blueprints/FCM_WAKEUP_BLUEPRINT.md`. See `docs/firebase-firestore-plan.md`.

## Gates
G4: photo byte-proof (host) + human E2E; video LAN proof (human); matrix table filled from human evidence.
