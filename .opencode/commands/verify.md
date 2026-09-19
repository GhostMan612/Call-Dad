# /verify command

Run the staged verification for the current BP slice, then report compactly:
1. `C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py`
2. If `app/` skeleton exists: `cd app` + `.\gradlew testDebugUnitTest --no-daemon` + `.\gradlew lintDebug --no-daemon` (filter tail only).
3. Never `assemble*|install*|connected*|run`. Never claim device success.
Output: gates PASS/FAIL + counts + next action. Update CHECKLIST/CHECKPOINTS only on PASS with evidence.
