# Devices: Moto G 2025 (truth) + BLU View 5

- AVDs live: `C:\android\device profiles\Moto_G_2025.avd/.ini` (target android-36), `BLU_View_5.avd/.ini`. Profiles/scripts: `C:\android\device_profiles\` (`Create-AVD-From-Profile.ps1`, `Device-Profiler.ps1`, `packages.txt` android-36 x86_64).
- System images: android-34/35/36 present; emulator 37.1.11. Emulator ≠ device — Moto G hardware is truth for audio/latency/camera.
- Read-only checks (this lane): `C:\android\sdk\platform-tools\adb.exe devices`, `adb shell getprop ro.build.version.sdk`. No `adb install` here.
- Human device runs per BP: LAN ring <3s, ≥30s voice, hotspot + Wi-Fi-Direct modes, photo/video E2E, no-escape walkthrough. Paste evidence to executor; executor records, never claims.
- Keystore note: only `C:\android\keystores\tacplan-debug.keystore` exists. Debug-signed installs only until operator provisions `call-dad` keystore (never commit).
- **What is actually on the hardware:** `vc10 / 0.3.0` on both phones — Moto G 2025 = `com.calldad.parent` (`0.3.0-parent`), BLU View 5 = `com.calldad.child` (`0.3.0-child`), flashed 2026-10-01 08:19, installed `-r` so the pairing and consent grant survived.
  **Source is `vc11 / 0.3.1` and is one pass ahead — not installed.**
- **Two things a device run must not assume:**
  1. **No `SPEC_SHEET` §2 feature has ever been exercised on hardware.** The only proven E2E call is from vc7.
  2. **vc10, the build installed here, ships a parental kill switch that enforces nothing** on calling or the walkie talkie, and does not stop photo/chat downloads. Fixed in vc11 — in source, on no device. **No version of this app has ever demonstrably enforced a kill switch.** Flash vc11 before testing any consent behaviour.
- Resolve serials at run time with `adb devices -l` (or the `device-evidence` tool, which maps roles from each line's model field). **Never write a serial into a tracked file** — RULES §1.5a, and `verify_project.py` fails the gate on a 14+ digit serial or an `adb-<SERIAL>-…` form.
