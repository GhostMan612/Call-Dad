# /verify command

Run the staged verification for the current BP slice, then report compactly:
0. **PREFER THE `gate` TOOL** (RULES §1.4a). It names every suite in its verdict line, so a
   skipped security gate cannot fold into a green. Steps 1–3 below are the raw equivalent,
   for the operator or for when the tool is unavailable.
1. `C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py`
2. From the repo root: `.\gradlew.bat :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug --no-daemon --console=plain`
3. `node --test functions/*.test.js` (**13 tests**: 6 ring + 7 clip — running only
   `ring.test.js` gives 6 and reads as a regression) and, when rules changed,
   `tools/rules-test/` against the local emulator (see its package.json).
4. Never `assemble*|install*|connected*|run`. Never claim device success.

**AND state what a green verdict does NOT prove: no suite in this repo compiles the app.**
`8f47512` was pushed to `origin/main` with a duplicated brace in `ChatViewModel.init` and
this whole procedure printed GREEN on it. `assembleParentDebug` is the only thing that has
ever caught that class, and it is an `ask` task. If you are about to report a build as
verified, say explicitly that the compiler was not run.
`C:\venv-hub\venv\Scripts\python.exe tools\prove_gates_bite.py` asserts this procedure goes
red on a non-compiling tree — run it after changing anything about how the gate works.

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
