#!/usr/bin/env python3
"""Gate: `opencode.json` must satisfy the PUBLISHED opencode config schema.

WHY THIS EXISTS. `opencode.json` failed to load with:

    ConfigInvalidError
      path: "C:\\Call-Dad\\opencode.json"
      message: "Expected PermissionActionConfig, got \\"Operator-authorized ...\\""
    -> Failed to load sessions

The cause was a note about one narrowly-scoped firebase rule written as a
`"//"` KEY inside `permission.bash`. JSON has no comments, so a key was the only
option available in that position — but every key inside `permission.bash` is a
permission RULE, and every rule's value must be `ask` | `allow` | `deny`. A
paragraph is not one, so the whole config was rejected and no session could
start. The note has to live somewhere else.

This is a gate rather than a one-off fix because the failure mode is invisible
until the app refuses to boot: `flutter build`, `flutter test` and every unit
test in this repo pass happily with an unloadable `opencode.json`, and the only
symptom is a desktop window that never lists your sessions.

TWO THINGS THIS SCRIPT DELIBERATELY DOES.

1. It parses JSONC, not JSON. The schema's root declares `"allowComments": true`
   and `"allowTrailingCommas": true`, so comments are legal in opencode.json —
   and that is precisely how the note is preserved next to the rule it explains.
   `json.load` is NOT that parser, so it is used only after a string-aware
   comment strip. The strip is string-aware on purpose: a naive regex removes a
   `//` that appears INSIDE a string value, and this very file's config contains
   `-- hosting, database, storage, `firebase use`` plus `$schema` URLs. Deleting
   from there to end-of-line would silently truncate a permission pattern and
   then report a schema error about something else entirely.

2. It runs a NEGATIVE CONTROL. A green "PASS" only means something if the check
   can go red, so `--self-test` feeds it five deliberately invalid configs and
   requires all five to be rejected. Without that, a typo in this file (a
   vacuous schema, a `Validator` that never iterates errors) produces a
   confident, permanent, useless gate.

USAGE
    python tools/validate_opencode_config.py              # validate the repo config
    python tools/validate_opencode_config.py --self-test  # prove the checker works
    python tools/validate_opencode_config.py --report     # list hits, always exit 0

Exit codes: 0 pass, 1 config invalid, 2 could not run (network/schema fetch).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "opencode.json"
SCHEMA_URL = "https://opencode.ai/config.json"

# `urllib` sends no User-Agent by default and opencode.ai answers 403 to that,
# which reads as "the schema is unavailable" and tempts a `--skip` workaround.
UA = "call-dad-opencode-config-gate/1.0"


# --------------------------------------------------------------------------
# JSONC -> Python object
# --------------------------------------------------------------------------
def strip_jsonc(src: str) -> str:
    """Remove `//` and comments outside string literals, then trailing commas.

    Hand-written rather than regex-only because the naive version corrupts this
    project's own config (see the module docstring).
    """
    out = []
    i = 0
    n = len(src)
    in_string = False

    while i < n:
        ch = src[i]

        if in_string:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                # Copy the escaped character verbatim so an escaped quote does
                # not close the string.
                out.append(src[i + 1])
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue

        if ch == '"':
            in_string = True
            out.append(ch)
            i += 1
            continue

        if ch == "/" and i + 1 < n and src[i + 1] == "/":
            # Line comment: drop to (not including) the newline, which is kept
            # so line numbers in error messages still line up.
            while i < n and src[i] != "\n":
                i += 1
            continue

        if ch == "/" and i + 1 < n and src[i + 1] == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                i += 1
            i += 2
            continue

        out.append(ch)
        i += 1

    if in_string:
        raise ValueError("unterminated string literal")
    return "".join(out)


def load_jsonc(path: Path):
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", strip_jsonc(path.read_text(encoding="utf-8"))))


# --------------------------------------------------------------------------
# schema
# --------------------------------------------------------------------------
def fetch_schema(cache: Path | None):
    if cache and cache.exists():
        return json.loads(cache.read_text(encoding="utf-8"))
    req = urllib.request.Request(SCHEMA_URL, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        schema = json.load(resp)
    if cache:
        try:
            cache.write_text(json.dumps(schema, indent=2), encoding="utf-8")
        except OSError:
            pass  # a read-only checkout is not a reason to fail the gate
    return schema


def build_validator(schema: dict):
    try:
        from jsonschema import Draft202012Validator
    except ImportError:
        print(
            "FAIL: the `jsonschema` package is not installed, so this gate\n"
            "      cannot run and would pass silently if it did. Install it:\n"
            "        pip install jsonschema"
        )
        raise SystemExit(2)
    return Draft202012Validator(schema)


def describe(err) -> str:
    where = "$"
    for part in err.absolute_path:
        where += "[%r]" % (part,)
    return "%s\n     %s" % (where, err.message)


# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------
def check(config: dict, validator) -> list[str]:
    return [describe(e) for e in sorted(validator.iter_errors(config), key=lambda e: list(e.absolute_path))]


def negative_control(base: dict, validator) -> list[str]:
    """Each case MUST be rejected. A case that passes is a broken gate."""
    import copy

    cases: list[tuple[str, dict]] = []

    def case(name: str, mutate) -> None:
        cfg = copy.deepcopy(base)
        mutate(cfg)
        cases.append((name, cfg))

    def prose_rule(c):
        # The exact bug that stopped every session from loading.
        c["permission"]["bash"]["//"] = "a note, not an action"

    def bad_action(c):
        c["permission"]["bash"]["git*push*"] = "maybe"

    def bad_external(c):
        c["permission"]["external_directory"]["C:/x/**"] = {"nope": "deny"}

    def unknown_prop(c):
        c.setdefault("references", {}).setdefault("blueprints", {})["bogus"] = 1

    def out_of_range(c):
        c.setdefault("tool_output", {})["max_lines"] = 0  # exclusiveMinimum: 0

    case("permission.bash holds a prose value (the original bug)", prose_rule)
    case("permission.bash holds an invalid action word", bad_action)
    case("external_directory holds a non-action value", bad_external)
    case("a references entry has an unknown property", unknown_prop)
    case("tool_output.max_lines is 0 (schema minimum is exclusive)", out_of_range)
    return [name for name, cfg in cases if not check(cfg, validator)]


def selftest(base: dict, validator) -> int:
    missed = negative_control(base, validator)
    if missed:
        print("NEGATIVE CONTROL FAILED — the checker accepted bad config:")
        for name in missed:
            print("  - %s" % name)
        print("\nA green PASS from this script means nothing until this passes.")
        return 1
    print("NEGATIVE CONTROL PASS: 5/5 deliberately invalid configs rejected.")
    print("real opencode.json   : %s" % ("PASS" if not check(base, validator) else "FAIL"))
    return 0


# --------------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--self-test", action="store_true",
                    help="prove the validator rejects invalid configs")
    ap.add_argument("--report", action="store_true",
                    help="list every violation but always exit 0")
    ap.add_argument("--schema-cache", type=Path, default=None,
                    help="read/write the schema here instead of refetching")
    ap.add_argument("--config", type=Path, default=CONFIG)
    args = ap.parse_args()

    if not args.config.exists():
        print("FAIL: %s does not exist. This project ships a repo-local "
              "opencode.json; the app reads it at startup and a session cannot "
              "start without it." % args.config)
        return 1

    try:
        config = load_jsonc(args.config)
    except ValueError as exc:
        # A malformed file is the same class of failure as an invalid one: the
        # app will not load it. Report it as a gate failure, not a crash.
        print("FAIL: %s is not valid JSON/JSONC: %s" % (args.config, exc))
        return 1

    try:
        schema = fetch_schema(args.schema_cache)
    except (urllib.error.URLError, OSError, ValueError) as exc:
        print("Could not fetch %s: %s" % (SCHEMA_URL, exc))
        print("Re-run with --schema-cache <file.json> from a machine with network.")
        return 2

    validator = build_validator(schema)

    if args.self_test:
        return selftest(config, validator)

    problems = check(config, validator)

    if args.report:
        for p in problems:
            print(p)
        print("\n%d schema violation(s)." % len(problems))
        return 0

    if problems:
        for p in problems:
            print("FAIL: %s" % p)
        print(
            "\nopencode.json will NOT load, so no session can start. Every other\n"
            "gate in this repo still passes while this is broken. Notes inside\n"
            "`permission` must be real `//` comments, never `\"//\"` keys — a key\n"
            "there is a permission rule and its value must be ask | allow | deny."
        )
        return 1

    print("PASS: opencode.json matches the published opencode config schema")
    return 0


if __name__ == "__main__":
    sys.exit(main())