# Devices: Moto G 2025 (truth) + BLU View 5

- AVDs live: `C:\android\device profiles\Moto_G_2025.avd/.ini` (target android-36), `BLU_View_5.avd/.ini`. Profiles/scripts: `C:\android\device_profiles\` (`Create-AVD-From-Profile.ps1`, `Device-Profiler.ps1`, `packages.txt` android-36 x86_64).
- System images: android-34/35/36 present; emulator 37.1.11. Emulator ≠ device — Moto G hardware is truth for audio/latency/camera.
- Read-only checks (this lane): `C:\android\sdk\platform-tools\adb.exe devices`, `adb shell getprop ro.build.version.sdk`, `dumpsys` version fingerprints. **No `adb install` here unless the operator authorises it in-session** — done 2026-10-02, see below.
- Human device runs per BP: LAN ring <3s, ≥30s voice, hotspot + Wi-Fi-Direct modes, photo/video E2E, no-escape walkthrough. Paste evidence to executor; executor records, never claims.
- Keystore note: only `C:\android\keystores\tacplan-debug.keystore` exists. Debug-signed installs only until operator provisions `call-dad` keystore (never commit).
- **What is actually on the hardware:** `vc11 / 0.3.1` on both phones — Moto G 2025 = `com.calldad.parent` (`0.3.1-parent`, SDK 36), BLU View 5 = `com.calldad.child` (`0.3.1-child`, SDK 34), installed 2026-10-02 (child 10:17:56, parent 10:18:11) under an explicit in-session operator override of the build boundary.
  - Installed with `adb install -r`, so the anonymous Firebase account, the paired peer UID (`peer_store.preferences_pb`, still dated 2026-09-30) and the child's consent grant survived. **No re-pair needed — and do not re-pair: it would destroy the thing under test.**
  - Verified one flavor per device, the other flavor NOT INSTALLED on each, both before and after.
- **Two things a device run must not assume:**
  1. **No `SPEC_SHEET` §2 feature has ever been exercised on hardware** — and that includes vc11, which was installed 2026-10-02 and has never been run by a human. The only proven E2E call is from vc7. **An install is not a witness.**
  2. **No version of this app has ever demonstrably enforced a kill switch.** vc10 — which was on both phones until 2026-10-02 — shipped one that enforced nothing on calling or the walkie talkie and did not stop photo/chat downloads. Fixed in vc11, **now installed, still unverified.** Do not record the kill switch as working on the strength of the install.
- Resolve serials at run time with `adb devices -l` (or the `device-evidence` tool, which maps roles from each line's model field). **Never write a serial into a tracked file** — RULES §1.5a, and `verify_project.py` fails the gate on a 14+ digit serial or an `adb-<SERIAL>-…` form.
