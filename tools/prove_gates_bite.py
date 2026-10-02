#!/usr/bin/env python3
# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
"""Prove the repo's own gates can actually FAIL.

`tools/verify_project.py` and the unit suite both reported green in this project
on a tree where real bugs exist. Three separate times:

  - the `gate` tool printed GREEN from a tree where
    `compileParentDebugKotlin` was failing;
  - nine defects shipped through five green gates, four of them features a child
    could not use;
  - a duplicated brace in `ChatViewModel.init` made the app uncompilable, and the
    full gate suite still said GATES GREEN.

Green is only meaningful if it has been seen to go red. This injects a known
defect, asserts the gate FAILS, restores the file byte-for-byte, and asserts green
returns.

## Why the injected defect is a BRACE

An earlier version of this file injected a literal control byte into a comment,
which `verify_project.py` catches by reading bytes. That proved the *scanner*
works, and it proved nothing about the *Kotlin compiler*. The duplicated-brace bug
is exactly the class that walked straight through: a bracket-balance error is
invisible in a diff review, produces forty cascading `Unresolved reference`
symptoms rather than one clear one, and is the single most common way a large
agent-made edit breaks a file.

So the default probe is a compile-breaking syntax error in a .kt file, and it
asserts the gate notices via the COMPILER -- not via a lint rule, not via a byte
scan. `tools/verify_project.py` additionally keeps a byte-level control-byte
check for the narrower hazard it is actually good at finding.
"""
import subprocess
import sys
import time

ROOT = r"C:\Call-Dad"
PY = r"C:\venv-hub\venv\Scripts\python.exe"

# A small, harmless, well-tested file. Editing a leaf keeps the blast radius of a
# failed restore obvious.
TARGET = "app/src/main/java/com/calldad/helper/KeywordBot.kt"

# Duplicated opening brace: a brace-balance error, deliberately. Not an
# unresolvable symbol -- that could fail for an unrelated reason and make the
# proof ambiguous.
INJECT = "}\n"


def run_gate():
    """The full standing gate: verify + unit + lint, exactly as the lane runs it.

    Deliberately NOT the `gate` TOOL. That tool is a TypeScript wrapper whose own
    caching behaviour is one of the things under suspicion (see the module
    docstring), so proving *it* bites is a separate experiment. This proves the
    underlying commands bite, which is the thing a reader actually cares about.
    """
    verify = subprocess.run([PY, "tools/verify_project.py"], cwd=ROOT,
                            capture_output=True, text=True)
    unit = subprocess.run(
        [r".\gradlew.bat", ":app:testParentDebugUnitTest",
         ":app:testChildDebugUnitTest", "--no-daemon", "--console=plain"],
        cwd=ROOT, capture_output=True, text=True, timeout=3600)
    return verify.returncode, unit.returncode, verify.stdout, unit.stdout


def brief(label, verify_rc, unit_rc, verify_out):
    status = "GATE PASS" if (verify_rc == 0 and unit_rc == 0) else "GATE FAIL"
    print("  %-28s verify=%d unit=%d  -> %s" % (label, verify_rc, unit_rc, status))
    for line in verify_out.splitlines():
        if line.strip().startswith("-"):
            print("      %s" % line.strip())


def main():
    target = "%s\\%s" % (ROOT, TARGET.replace("/", "\\"))
    with open(target, "rb") as fh:
        original = fh.read()

    print("baseline (this takes a couple of minutes):")
    v_rc, u_rc, v_out, _ = run_gate()
    if v_rc != 0 or u_rc != 0:
        print("BASELINE IS ALREADY FAILING -- fix that before trusting this proof:")
        brief("baseline", v_rc, u_rc, v_out)
        return 1
    brief("baseline", v_rc, u_rc, v_out)

    print("\ninjecting a DUPLICATED BRACE into %s:" % TARGET)
    print("    this is a brace-balance error, not a missing import -- a missing")
    print("    symbol could fail for an unrelated reason and muddy the proof.")
    try:
        with open(target, "wb") as fh:
            fh.write(INJECT.encode("ascii") + original)

        v_rc, u_rc, v_out, u_out = run_gate()
        brief("injected", v_rc, u_rc, v_out)

        if u_rc == 0:
            print("\nPROOF FAILED: the unit gate passed a tree that does not compile.")
            print("This is the exact failure being hunted: a green gate over a")
            print("broken app. Do not trust the gate until this goes red.")
            return 1
        # The compiler must be what complained. A byte-level scanner catching it
        # instead would mean the proof is measuring the wrong thing.
        #
        # Gradle prints its diagnostics to whichever stream the rich console picks
        # and truncates the head of a long run, so accept any of the shapes it
        # uses rather than one literal string -- but require one of them, so a
        # failure for some unrelated reason cannot pass as a proof.
        evidence = ("e: ", "Compilation error",
                    "compileParentDebugKotlin", "compileChildDebugKotlin")
        if not any(tok in u_out for tok in evidence):
            print("\nPROOF INCONCLUSIVE: unit failed but not via the compiler.")
            print("gradle output tail:")
            for line in u_out.splitlines()[-25:]:
                print("      %s" % line)
            return 1
        print("\n  evidence the compiler was the cause:")
        for line in u_out.splitlines():
            if line.startswith("e: ") or "compile" in line and "Kotlin" in line:
                print("      %s" % line.strip())
                break
        else:
            print("      (gradle reported the Kotlin compile task failed)")
    finally:
        with open(target, "wb") as fh:
            fh.write(original)
        time.sleep(0.5)

    v_rc, u_rc, v_out, _ = run_gate()
    brief("restored", v_rc, u_rc, v_out)
    if v_rc != 0 or u_rc != 0:
        print("\nPROOF FAILED: gate did not return to green after restore.")
        return 1
    with open(target, "rb") as fh:
        if fh.read() != original:
            print("\nPROOF FAILED: the file was not restored byte-for-byte.")
            return 1

    print("\nPROVEN: a non-compiling tree turns the gate RED, and it comes back")
    print("green. Run this after changing anything about how the gate works.")
    return 0


if __name__ == "__main__":
    sys.exit(main())