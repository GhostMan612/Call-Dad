#!/usr/bin/env python3
"""Prove the repo's own gates can actually FAIL.

`tools/verify_project.py` and the unit suite both report green in this project
on a tree where real bugs exist -- twice in one session, a stale `gate` tool
printed GREEN from a failing compile, and a source-text test passed while the
feature it guarded was unreachable. Green is only meaningful if it has been seen
to go red.

This injects a known defect, runs the gate, asserts it FAILS, then restores the
file byte-for-byte and asserts it passes again. It is the only check in the repo
that is about the CHECKS rather than the app.
"""
import subprocess
import sys
import time

ROOT = r"C:\Call-Dad"
PY = r"C:\venv-hub\venv\Scripts\python.exe"

# (file, description, mutate(bytes) -> bytes, needle the failure must mention)
TARGET = "app/src/main/java/com/calldad/helper/KeywordBot.kt"


def gate():
    out = subprocess.run([PY, "tools/verify_project.py"], cwd=ROOT,
                         capture_output=True, text=True)
    return out.returncode, out.stdout


def main():
    baseline_rc, baseline_out = gate()
    if baseline_rc != 0:
        print("baseline is already failing; fix that first:")
        print(baseline_out)
        return 1
    print("baseline: %s" % baseline_out.strip().splitlines()[0])

    with open("%s\\%s" % (ROOT, TARGET.replace("/", "\\")), "rb") as fh:
        original = fh.read()

    def inject(raw):
        # A literal 0x1F in the first Genesis comment line.
        lines = raw.split(b"\n")
        lines[0] = lines[0] + b"\x1F"
        return b"\n".join(lines)

    rc = 1
    try:
        with open("%s\\%s" % (ROOT, TARGET.replace("/", "\\")), "wb") as fh:
            fh.write(inject(original))
        rc, out = gate()
        if rc == 0:
            print("PROOF FAILED: the gate passed a tree with a literal control byte.")
            return 1
        if "control byte" not in out:
            print("PROOF FAILED: gate failed, but not for the expected reason:")
            print(out)
            return 1
        reason = [l.strip() for l in out.splitlines() if "control byte" in l][0]
        print("injected defect -> gate FAILED as required:")
        print("  %s" % reason)
    finally:
        with open("%s\\%s" % (ROOT, TARGET.replace("/", "\\")), "wb") as fh:
            fh.write(original)
        time.sleep(0.2)

    rc2, out2 = gate()
    if rc2 != 0:
        print("PROOF FAILED: the gate did not return to green after restore:")
        print(out2)
        return 1
    if original != open("%s\\%s" % (ROOT, TARGET.replace("/", "\\")), "rb").read():
        print("PROOF FAILED: the file was not restored byte-for-byte.")
        return 1
    print("restored: %s" % out2.strip().splitlines()[0])
    print("PROVEN: this gate can fail, and recovers cleanly.")
    return 0


if __name__ == "__main__":
    sys.exit(main())