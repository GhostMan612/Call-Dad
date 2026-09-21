# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
"""Call-Dad scaffold gate (stdlib only). Run with hub python as-is:
C:\\venv-hub\\venv\\Scripts\\python.exe tools\\verify_project.py
Checks tree + required docs + bans secrets/real-child-data markers. Never builds.

Studio-note: the human builds in Android Studio, so Studio-generated local
artifacts (build/, .gradle/, local.properties, gradle-daemon-jvm.properties,
app/google-services.json) are EXPECTED on disk and gitignored. The secret
check therefore targets git-TRACKED files (index + HEAD via `git ls-files`);
only when git is unavailable does it fall back to a strict on-disk scan that
skips known Studio output dirs.
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REQUIRED_DIRS = ["blueprints", "blueprints/blueprint-sections", "blueprints/decisions",
                 "docs", "tools", "assets", "app", ".opencode/agents",
                 ".opencode/commands", "fixtures"]
REQUIRED_FILES = ["AGENTS.md", "RULES.md", "SESSION_HANDOFF.md", "CLAUDE.md",
                  "README.md", "SPEC_SHEET.md", "SPEC_SHEET.json", ".gitignore",
                  "local.properties.template",
                  "blueprints/CALL_DAD_MASTER_BLUEPRINT.md", "blueprints/ROADMAP.md",
                  "blueprints/CURRENT_STATE.md", "blueprints/CHECKLIST.md",
                  "blueprints/CHECKPOINTS.md", "blueprints/ARCHITECTURE.md",
                  "blueprints/blueprint-sections/BP-01-skeleton.md",
                  "blueprints/blueprint-sections/BP-02-voice.md",
                  "blueprints/blueprint-sections/BP-03-chat-remote.md",
                  "blueprints/blueprint-sections/BP-04-photo-video.md",
                  "blueprints/blueprint-sections/BP-05-hardening.md",
                  "blueprints/decisions/ADR-001-minSdk.md",
                  "blueprints/decisions/ADR-002-signaling.md",
                  "blueprints/decisions/ADR-003-sqlcipher.md",
                  "docs/setup-android-studio.md", "docs/device-profiles.md",
                  "docs/firebase-firestore-plan.md", "docs/kid-safe-ux.md",
                  "docs/sovereign-comms-reuse-map.md",
                  "blueprints/decisions/ADR-004-toolchain.md",
                  "blueprints/decisions/ADR-005-webrtc.md",
                  "blueprints/decisions/ADR-006-phase4-qa.md",
                  "blueprints/decisions/ADR-007-phase5.md",
                  "blueprints/GEMINI_HANDOFF.md",
                  "app/src/main/java/com/calldad/data/signaling/CallDocument.kt",
                  "app/src/main/java/com/calldad/data/signaling/OwnSdpRegistry.kt",
                  "app/src/main/java/com/calldad/data/signaling/SignalingClient.kt",
                  "app/src/main/java/com/calldad/webrtc/WebRTCClient.kt",
                  "app/src/main/java/com/calldad/ui/screens/CallViewModel.kt",
                  "app/src/main/java/com/calldad/ui/screens/CallScreen.kt",
                  "app/src/main/java/com/calldad/CallDadApplication.kt",
                  "app/src/main/java/com/calldad/fcm/CallMessagingService.kt",
                  "app/src/main/java/com/calldad/fcm/CallForegroundService.kt",
                  "app/src/main/AndroidManifest.xml",
                  "firestore.rules", "firebase.json",
                  "functions/index.js", "functions/package.json",
                  "blueprints/decisions/ADR-008-phase6-ptt.md",
                  "app/src/main/java/com/calldad/ptt/PttEngine.kt",
                  "app/src/main/java/com/calldad/ptt/SimulatedPttEngine.kt",
                  "app/src/main/java/com/calldad/ptt/SovereignPttAdapter.kt",
                  "app/src/main/java/com/calldad/ptt/PttAudioManager.kt",
                  "app/src/main/java/com/calldad/ui/screens/PttViewModel.kt",
                  "app/src/main/java/com/calldad/ui/screens/PttScreen.kt",
                  "blueprints/decisions/ADR-009-phase7-game.md",
                  "app/src/main/java/com/calldad/game/GameWebRtcBridge.kt",
                  "app/src/main/assets/game.html",
                  "app/src/main/java/com/calldad/ui/screens/GameScreen.kt",
                  "blueprints/decisions/ADR-010-phase8-game.md",
                  "app/src/main/java/com/calldad/webrtc/ConnectionState.kt",
                  "blueprints/decisions/ADR-011-phase9-voice.md",
                  "app/src/main/java/com/calldad/audio/CallAudioManager.kt",
                  "app/src/main/java/com/calldad/helper/KeywordBot.kt",
                  "app/src/main/java/com/calldad/ui/screens/HelperViewModel.kt",
                  "app/src/main/java/com/calldad/ui/screens/HelperScreen.kt",
                  "blueprints/decisions/ADR-012-phase10-flavors.md",
                  "blueprints/decisions/ADR-013-phase11-rooms.md"]
BANNED_NAMES = ["google-services.json", "local.properties", ".env"]
BANNED_SUFFIXES = (".keystore", ".jks")
BANNED_STRINGS = ["AIza", "BEGIN PRIVATE KEY", "RELEASE_STORE_PASSWORD="]
# Studio output dirs skipped by the fallback on-disk scan.
SKIP_DIRS = {"build", ".gradle", ".cxx", ".idea", "captures",
             ".externalNativeBuild", "__pycache__", ".git"}


def tracked_files():
    """Paths tracked in the git index/HEAD, relative to ROOT. None if no git."""
    try:
        out = subprocess.run(
            ["git", "ls-files"], cwd=ROOT, capture_output=True,
            text=True, timeout=30)
    except (OSError, ValueError):
        return None
    if out.returncode != 0:
        return None
    return [l for l in out.stdout.splitlines() if l]


def main() -> int:
    errors = []
    for d in REQUIRED_DIRS:
        if not (ROOT / d).is_dir():
            errors.append(f"missing dir: {d}")
    for f in REQUIRED_FILES:
        if not (ROOT / f).is_file():
            errors.append(f"missing file: {f}")
    tracked = tracked_files()
    if tracked is None:
        for p in ROOT.rglob("*"):
            if not p.is_file():
                continue
            if any(part in SKIP_DIRS for part in p.relative_to(ROOT).parts):
                continue
            rel = str(p.relative_to(ROOT))
            if p.name in BANNED_NAMES:
                errors.append(f"banned file present: {rel}")
            if p.suffix in BANNED_SUFFIXES:
                errors.append(f"banned keystore present: {rel}")
    else:
        for rel in tracked:
            name = Path(rel).name
            if name in BANNED_NAMES:
                errors.append(f"banned file TRACKED by git: {rel}")
            if Path(rel).suffix in BANNED_SUFFIXES:
                errors.append(f"banned keystore TRACKED by git: {rel}")
    for f in REQUIRED_FILES:
        p = ROOT / f
        if p.is_file() and p.suffix == ".md":
            text = p.read_text(encoding="utf-8", errors="strict")
            for s in BANNED_STRINGS:
                if s in text:
                    errors.append(f"banned string {s!r} in {f}")
    if errors:
        print("VERIFY FAIL:")
        for e in errors:
            print(f"  - {e}")
        return 1
    mode = "git-tracked" if tracked is not None else "on-disk"
    print(f"VERIFY PASS ({mode}): {len(REQUIRED_DIRS)} dirs + {len(REQUIRED_FILES)} files, no secrets.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
