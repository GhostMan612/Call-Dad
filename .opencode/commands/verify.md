# /verify command

Run the staged verification for the current BP slice, then report compactly:
1. `C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py`
2. From the repo root: `.\gradlew.bat :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug --no-daemon --console=plain`
3. `node --test functions/ring.test.js` (ring decision) and, when rules changed, `tools/rules-test/` against the local emulator (see its package.json).
4. Never `assemble*|install*|connected*|run`. Never claim device success.

Rules for the run itself (RULES §1.4a):
- This is a **phase-closing** step. Do NOT run it after each individual edit; fix
  everything visible first with `grep`/`edit`, then run it once.
- **Read the source with `read`/`grep`/`glob`/`edit`, never the shell.** No
  `Get-Content`, `Select-String`, `Get-ChildItem`, `Test-Path`, or `rg` — they are
  denied in `opencode.json`. Do not filter build output through a pipe; read the
  gate output once and take the summary from it.
- Batch everything: one parallel batch of checks, not a chain of one-off commands.
- The Node gates need Node on PATH and Java for the emulator. If either is absent
  they are **SKIPPED, not failed** — report SKIPPED and never carry a previous
  run's count forward.

Output: gates PASS/FAIL + counts + next action. Update CHECKLIST/CHECKPOINTS only on PASS with evidence.
