# CHECKPOINTS.md — Call-Dad gates

> No checklist tick without its gate passing. Evidence before status.

- **G0 scaffold:** `C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py` exits 0 (tree + required docs + no secrets + no real-child-data strings). Evidence: pasted tail output.
- **G1 skeleton:** `.\gradlew :app:testParentDebugUnitTest :app:testChildDebugUnitTest` (≥1 test PASS) + `:app:lintParentDebug :app:lintChildDebug` (0 errors for touched modules). Evidence: pasted `Select-Object -Last 5` blocks. No `assemble*` claims.
- **G2 voice (host):** signaling state-machine unit tests (Invite→Accept/Decline→End + replay/echo guards) + cipher round-trip (encrypt→decrypt, tamper→drop) + Opus encode/decode smoke. Evidence: test counts.
- **G2-device:** human on Moto G: LAN ring <3s, intelligible voice ≥30s, hangup + redial. Evidence: human pasted `adb devices` + narrative (never claimed by this lane).
- **G3 chat+remote:** receipt transitions (sent→delivered→read) unit-proven; idempotent ingest (duplicate→single); remote signaling via relay (human proof both networks).
- **G4 photo+video:** photo chunk→reassemble byte-identical (host fixture) + on-device E2E (human); video spike: decision ADR + LAN preview both ends (human).
- **G5 hardening:** parent-gate test (kid flow cannot add contact; parent flow can), consent-expiry enforcement, SQLCipher open/close (if ADR-003 yes), kid-UX audit sheet signed, no-escape audit (no browser/store/settings exit).
- **G-C8 (ADR-015) host:** `:app:testParentDebugUnitTest :app:testChildDebugUnitTest` PASS + `:app:lintParentDebug :app:lintChildDebug` 0 errors + `node --test functions/` PASS + `tools/rules-test` 15/15 on the emulator + verify PASS. STATUS: GREEN 2026-09-24 (lane-run).
- **G-C8 device (human):** after rules+functions deploy and a fresh re-pair: (1) ring from Home, PTT, Game, Helper and with the app killed; (2) answer, hang up from each side (no crash, other side returns home); (3) no answer → "No answer yet" on caller, ring stops on callee; (4) both tap Call at once → one connected call; (5) kill one app mid-call → other side ends within ~25s; (6) game synced during a call and solo without; (7) pairing blocked without the gate answer; scanning only one direction never changes the contact. Evidence: operator narrative + logcat `-s WebRTC:D`.
