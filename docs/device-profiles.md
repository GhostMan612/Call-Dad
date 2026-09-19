# Devices: Moto G 2025 (truth) + BLU View 5

- AVDs live: `C:\android\device profiles\Moto_G_2025.avd/.ini` (target android-36), `BLU_View_5.avd/.ini`. Profiles/scripts: `C:\android\device_profiles\` (`Create-AVD-From-Profile.ps1`, `Device-Profiler.ps1`, `packages.txt` android-36 x86_64).
- System images: android-34/35/36 present; emulator 37.1.11. Emulator ≠ device — Moto G hardware is truth for audio/latency/camera.
- Read-only checks (this lane): `C:\android\sdk\platform-tools\adb.exe devices`, `adb shell getprop ro.build.version.sdk`. No `adb install` here.
- Human device runs per BP: LAN ring <3s, ≥30s voice, hotspot + Wi-Fi-Direct modes, photo/video E2E, no-escape walkthrough. Paste evidence to executor; executor records, never claims.
- Keystore note: only `C:\android\keystores\tacplan-debug.keystore` exists. Debug-signed installs only until operator provisions `call-dad` keystore (never commit).
