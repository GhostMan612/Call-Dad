#!/usr/bin/env python3
"""Scan tracked text files for literal control bytes and report them.

Used by tools/verify_project.py as a gate: a stray 0x00 or 0x1F in a .kt file
turns it into a "binary" file for git, which silently breaks grep, diffs, and
code review on exactly the file most likely to be safety-critical.
"""
import os
import subprocess
import sys

SKIP_DIRS = {".git", "build", ".gradle", "node_modules", ".idea"}
EXTS = {".kt", ".kts", ".py", ".js", ".ts", ".json", ".md", ".rules", ".html",
        ".pro", ".toml", ".yml", ".yaml", ".xml", ".gradle", ".properties"}
# 0x09 tab, 0x0A LF, 0x0D CR are legitimate in text.
ALLOWED = {0x09, 0x0A, 0x0D}


def tracked_files(root):
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=root, capture_output=True, check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return []
    return [p.decode("utf-8", "replace") for p in out.split(b"\0") if p]


def scan(root):
    findings = []
    for rel in tracked_files(root):
        base = os.path.basename(rel)
        if os.path.splitext(base)[1].lower() not in EXTS:
            continue
        if any(part in SKIP_DIRS for part in rel.split("/")):
            continue
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "rb") as fh:
                data = fh.read()
        except OSError:
            continue
        line = 1
        for offset, byte in enumerate(data):
            if byte == 0x0A:
                line += 1
                continue
            if byte in ALLOWED:
                continue
            # Everything printable is fine. The suspects are C0 controls and DEL.
            if byte >= 0x20 and byte != 0x7F:
                continue
            findings.append((rel, line, offset, byte))
            if len(findings) > 40:
                return findings
    return findings


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    findings = scan(root)
    if not findings:
        print("CONTROL BYTES: none")
        return 0
    print("CONTROL BYTES: %d finding(s)" % len(findings))
    for rel, line, offset, byte in findings:
        print("  %s:%d byte 0x%02X at offset %d" % (rel, line, byte, offset))
    print(
        "\nA literal control byte makes git treat the file as binary, which "
        "silently\nbreaks grep, diff and review on it. Write the ESCAPE TEXT "
        "(\\x00) inside a\ncomment, never the raw byte -- exactly the mistake "
        "ChatText.kt:57 made,\nin a comment explaining why control bytes matter."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())