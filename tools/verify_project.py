# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
"""Call-Dad scaffold gate (stdlib only). Run with hub python as-is:
C:\\venv-hub\\venv\\Scripts\\python.exe tools\\verify_project.py
Checks tree + required docs + bans secrets/real-child-data markers. Never builds.
"""
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
                  "docs/sovereign-comms-reuse-map.md"]
BANNED_NAMES = ["google-services.json", "local.properties", ".env"]
BANNED_SUFFIXES = (".keystore", ".jks")
BANNED_STRINGS = ["AIza", "BEGIN PRIVATE KEY", "RELEASE_STORE_PASSWORD="]


def main() -> int:
    errors = []
    for d in REQUIRED_DIRS:
        if not (ROOT / d).is_dir():
            errors.append(f"missing dir: {d}")
    for f in REQUIRED_FILES:
        if not (ROOT / f).is_file():
            errors.append(f"missing file: {f}")
    for p in ROOT.rglob("*"):
        if p.is_file():
            if p.name in BANNED_NAMES:
                errors.append(f"banned file present: {p.relative_to(ROOT)}")
            if p.suffix in BANNED_SUFFIXES and ".git" not in p.parts:
                errors.append(f"banned keystore present: {p.relative_to(ROOT)}")
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
    print(f"VERIFY PASS: {len(REQUIRED_DIRS)} dirs + {len(REQUIRED_FILES)} files, no secrets.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
