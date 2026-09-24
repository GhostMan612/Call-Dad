# /verify command

Run the staged verification for the current BP slice, then report compactly:
1. `C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py`
2. From the repo root: `.\gradlew :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug --no-daemon` (filter tail only).
3. `node --test functions/` (ring decision) and, when rules changed, `tools/rules-test/` against the local emulator (see its package.json).
4. Never `assemble*|install*|connected*|run`. Never claim device success.
Output: gates PASS/FAIL + counts + next action. Update CHECKLIST/CHECKPOINTS only on PASS with evidence.
