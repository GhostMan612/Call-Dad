# ADR-016 — Walkie-talkie = voice clips over the pair's room (2026-09-25)

- Status: DECIDED by executor after device evidence (operator logcat 09-24: "Sovereign Mantle not on classpath → simulated engine"). Supersedes ADR-008's engine choice.
- Problem: PTT never transmitted. The only engines were a reflective adapter for a private module that was never in this app, and a loopback simulator.

## Decisions

1. **Hold to record, release to send.** `VoiceClipPttEngine` records mono AAC (16 kHz, 32 kbps, max 15 s, min 0.4 s) with MediaRecorder. On release it writes `calls/{roomId}/ptt/{auto}` = {from, audio (bytes, ≤ 200 KB), durationMs, createdAt (server time)}.
2. **Plays anywhere, in order.** The engine lives in the activity-scoped PttViewModel, created by AppNavHost at startup. It listens to the pair's `ptt` collection, plays the peer's clips one after another on any screen, then deletes each clip. It waits while a video call is live (the call owns mic and speaker). Clips sent while this phone was offline play when it reconnects, if they are under 30 min old.
3. **Rules:** only the two room members read, create or delete; the sender must stamp itself and server time; fixed fields; no edits. 2 new emulator tests (17/17).
4. **Retired:** `SovereignPttAdapter` (placeholder that reported success without the module). `SimulatedPttEngine` stays for host tests.

## Known limits

- No push for voice clips yet: a phone whose app process is dead hears the clip when the app next opens (within 30 min). Next step: extend `onCallRoomWritten` to the `ptt` subcollection.
- Latency is record-then-send (about 1–2 s after release), not live streaming.
