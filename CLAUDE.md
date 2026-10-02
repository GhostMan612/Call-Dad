# CLAUDE.md — Call-Dad pointer

> Compact pointer. **RULES.md is canonical and wins every conflict.**

- Cold start: `SESSION_HANDOFF.md` → `RULES.md` → `blueprints/CURRENT_STATE.md` → task `BP-*.md`.
- Build boundary: NEVER `assemble*|install*|connected*|build apk|run`. Human builds in Android Studio. Lane ends at `:app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug` (repo root) + `tools/verify_project.py` + `node --test functions/*.test.js` (13 tests) + `tools/rules-test/`. **No suite here compiles the app — `assembleParentDebug` is the only thing that has ever caught a non-compiling tree, and the gate printed GREEN over one.**
- Tool use (RULES §1.4a): read/search/edit with the `read`/`grep`/`glob`/`edit`/`write` tools. NEVER shell out to read, search, or modify a file (`cat`, `type`, `Get-Content`, `Select-String`, `findstr`, `rg`, `Get-ChildItem`, `Test-Path`, `Set-Content`, `Out-File` are denied). Batch lookups into one parallel batch. Run the gate ONCE at the END of a phase, never after each individual edit.
- Externals READ-ONLY: `C:\pathfinder_god`, `C:\Recovery for All`, `C:\sovereign_mantle`, `C:\sovereign_tagger`, `C:\Sovereign-Atlas-Engine`, `C:\vision engine`, plus `C:\Godot`, `C:\Program Files\Unity Hub`, `C:\Program Files\Microsoft Visual Studio` (ask for override with reason).
- Hub python `C:\venv-hub\venv\Scripts\python.exe` as-is; isolated lane `C:\venv-hub\call-dad\`.
- Synthetic data only. Genesis header on new `.kt`/`.py`. Explicit-path git only. No push unless told.
- Team: operator (dad/human) / executor (this lane) / Gemini (R&D) / DeepSeek (architect). Keep handoffs readable by all three.
