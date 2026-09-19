# BP-04 — Photo share + video-call spike + offline matrix (Phase 4)

Goal: chunked verified photo E2E; video approach decided + LAN-proven; offline modes mapped.

## Port/research
1. `SovereignImageEngine` (downscale→WEBP cap→chunk→reassemble→verify) + `PhotoViewModel` (capture/pick/send/view + receipt). Byte-identical host test on synthetic fixture.
2. Video spike (Gemini R&D): option A extend `LiveCallSession` UDP with CameraX frames (donor-faithful, more work); option B WebRTC (new dep, NAT-friendly, needs ADR + privacy review). Spike both on paper; implement winner on LAN only.
3. Offline matrix (human runs): airplane+WiFi, hotspot-host, hotspot-join, no-internet Wi-Fi-Direct group; record which routes connect.

## Firebase note
Design (don't build) FCM-wake fallback for NAT-hard remote; no `google-services.json` until ADR-002. See docs/firebase-firestore-plan.md.

## Gates
G4: photo byte-proof (host) + human E2E; video LAN proof (human); matrix table filled from human evidence.
