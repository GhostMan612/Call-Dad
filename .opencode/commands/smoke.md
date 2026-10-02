# /smoke command

Human-device smoke script (human runs in Studio/on-device, executor records pasted evidence):

0. **GRANT CONSENT FIRST, or every step below will look like a broken app.** A fresh
   install is deliberately INERT: absence of a grant DENIES (ADR-017), so Messages and
   Pictures read "turned off right now" and the Call button refuses. On the PARENT phone:
   shield icon (top-right, beside the gear) → grown-ups gate → "Allow everything". The
   grown-up's own phone is NOT gated — the rules forbid a self-named grant, so the parent
   derives its own role (`ConsentStore.isGrantor`). **Do not file the inert first run as a
   bug.**
1. Launch → giant Call Dad visible → tap → Dad rings (note latency + network: LAN/hotspot/remote).
2. 30s voice → hangup → redial.
3. Send text both ways → receipts. Include a link-shaped message (must be REFUSED).
4. Send a voice memo both ways. 5. Send a real photo → received + verified.
6. Airplane+WiFi recovery.
7. **The kill switch — the check nobody has ever run.** On the PARENT: "Turn everything
   off". On the CHILD, confirm ALL of: the Call button refuses with "Calling is turned off
   right now"; an incoming call does NOT ring; the walkie talkie reads "TURNED OFF" and the
   button does nothing; Messages and Pictures show the "ask a grown-up" notice; and **the
   child's phone pulls no photo chunks** (turn mobile data off first, so a download is
   visible as usage). Then "Allow everything" and confirm it all comes back.
8. No-escape walk (back button, no browser/store/settings exit).

**Which build to flash.** Only flash `vc11 / 0.3.1` or later. `vc10` — which was on both
phones — presents a parental kill switch that enforces NOTHING on calling or the walkie
talkie and does not stop photo/chat downloads, so step 7 will fail on it and you will have
no idea whether the app or the test is wrong.

Record: PASS/FAIL per line + device + build flavor + the `dumpsys` fingerprint. Executor
never claims; only transcribes human evidence into CURRENT_STATE.

**A smoke pass is not proof of enforcement.** Every one of these is a first run of its
kind — no `SPEC_SHEET` §2 feature has ever been exercised on any current build.