import { tool } from "@opencode-ai/plugin"
import { spawnSync } from "node:child_process"

const PY = "C:\\venv-hub\\venv\\Scripts\\python.exe"

/**
 * Java for the Firestore emulator, per AGENTS.md ("Android Studio `jbr`").
 *
 * This exists because of a real failure, not a hypothetical one. The emulator
 * refuses to start without a JVM, it does so by exiting non-zero, and the gate
 * simply had no `rules` suite at all -- so an entire security gate could go
 * unreported while the tool printed "GATES GREEN". Hardcoding the documented
 * path is the fix that makes the suite actually run in this lane.
 */
const JBR = "C:\\android\\Android Studio\\jbr"

type Run = { code: number; out: string; err: string }

function run(
  argv: string[],
  cwd: string,
  timeoutMs = 900_000,
  env?: Record<string, string>,
): Run {
  const r = spawnSync(argv[0], argv.slice(1), {
    cwd,
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 64 * 1024 * 1024,
    shell: true,
    windowsHide: true,
    env: env ? { ...process.env, ...env } : process.env,
  })
  return {
    code: r.status ?? -1,
    out: r.stdout ?? "",
    err: r.stderr ?? (r.error ? String(r.error) : ""),
  }
}

function tail(text: string, n = 25): string {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0)
  return lines.slice(-n).join("\n")
}

function cmdline(argv: string[]): string {
  return argv.join(" ")
}

export default tool({
  description:
    "Run the Call-Dad repo verification gate for this lane. Executes tools/verify_project.py, the flavored unit-test and lint tasks, the Cloud Function test, AND the Firestore rules emulator suite. Refuses any assemble/install/connected/deploy command. Reports PASS/FAIL/SKIPPED per gate; a skipped SECURITY gate is called out in the verdict rather than reported as green.",
  args: {
    gate: tool.schema
      .enum(["verify", "unit", "lint", "functions", "rules", "all"])
      .default("all")
      .describe(
        "Which gate to run. 'all' = verify + unit + lint + functions + rules (emulator).",
      ),
    gradle_timeout_minutes: tool.schema
      .number()
      .default(20)
      .describe("Timeout for gradle tasks in minutes."),
  },
  async execute(args, context) {
    const cwd = context.directory || context.worktree
    const want = new Set(
      args.gate === "all"
        ? ["verify", "unit", "lint", "functions", "rules"]
        : [args.gate],
    )
    const results: string[] = []
    let failed = 0
    let skipped = 0

    if (want.has("verify")) {
      const r = run([PY, "tools\\verify_project.py"], cwd, 180_000)
      if (r.code === 0) results.push(`verify: PASS\n${tail(r.out, 5)}`)
      else {
        failed++
        results.push(`verify: FAIL (exit ${r.code})\n${tail(r.out || r.err, 30)}`)
      }
    }

    const gradleTasks: Record<string, string[]> = {
      unit: [":app:testParentDebugUnitTest", ":app:testChildDebugUnitTest"],
      lint: [":app:lintParentDebug", ":app:lintChildDebug"],
    }

    for (const key of ["unit", "lint"] as const) {
      if (!want.has(key)) continue
      const argv = [
        "gradlew.bat",
        ...gradleTasks[key],
        "--no-daemon",
        "--console=plain",
      ]
      const r = run(argv, cwd, args.gradle_timeout_minutes * 60_000)
      const label = key === "unit" ? "unit tests" : "lint"
      if (r.code === 0) results.push(`${label}: PASS\n${cmdline(argv)}\n${tail(r.out, 8)}`)
      else {
        failed++
        // Concatenate, never `out || err`. Kotlin writes its `e:` diagnostics to
        // stderr while Gradle writes task lines to stdout, so `||` silently kept
        // the task lines and threw away the only output that said what was
        // wrong -- a FAIL with no reason in it. This gate reported GREEN while
        // compileParentDebugKotlin was failing, and this line is half of why.
        const full = `${r.out}\n${r.err}`
        // 200 lines: a Kotlin compile failure prints a wall of task lines before
        // the diagnostics, and a short tail truncates the error away, which is a
        // lid rather than a tail.
        results.push(`${label}: FAIL (exit ${r.code})\n${cmdline(argv)}\n${tail(full, 200)}`)
      }
    }

    if (want.has("functions")) {
      const r = run(["node", "--test", "functions/ring.test.js"], cwd, 300_000)
      if (r.code === 0) results.push(`functions: PASS\n${tail(r.out, 12)}`)
      else {
        failed++
        const combined = r.out + "\n" + r.err
        if (/not recognized|ENOENT/i.test(combined)) {
          skipped++
          results.push(
            "functions: SKIPPED (node not on PATH in this shell) — operator must run `node --test functions/ring.test.js`",
          )
        } else {
          results.push(`functions: FAIL (exit ${r.code})\n${tail(combined, 40)}`)
        }
      }
    }

    if (want.has("rules")) {
      // The Firestore rules are the app's real security boundary, so this suite
      // is not optional decoration. It is a SECURITY gate and a SKIP is
      // surfaced loudly in the verdict rather than folded into a green.
      const env = {
        JAVA_HOME: JBR,
        Path: `${JBR}\\bin;${process.env.Path ?? ""}`,
      }
      const r = run(
        [
          "npx",
          "firebase",
          "emulators:exec",
          "--only",
          "firestore",
          "--project",
          "demo-calldad",
          '"node --test"',
        ],
        `${cwd}\\tools\\rules-test`,
        900_000,
        env,
      )
      const combined = r.out + "\n" + r.err
      const counts = combined.match(/^# (pass|fail|tests) (\d+)$/gm)?.join("  ") ?? ""
      if (r.code === 0) {
        results.push(`firestore rules emulator: PASS\n${counts}`)
      } else if (/Could not spawn|not recognized|ENOENT/i.test(combined)) {
        skipped++
        results.push(
          "firestore rules emulator: SKIPPED — no JVM/Node on this lane.\n" +
            `  This is a SECURITY gate and has NOT been verified. Run it yourself:\n` +
            `  cd tools\\rules-test; $env:JAVA_HOME="${JBR}"; $env:Path="$env:JAVA_HOME\\bin;$env:Path"; npx firebase emulators:exec --only firestore --project demo-calldad "node --test"\n` +
            tail(combined, 6),
        )
      } else {
        failed++
        results.push(
          `firestore rules emulator: FAIL (exit ${r.code})\n${counts}\n${tail(combined, 40)}`,
        )
      }
    }

    const verdict =
      failed > 0
        ? `${failed} GATE(S) RED`
        : skipped > 0
          ? `GATES GREEN BUT ${skipped} SKIPPED — see below, NOT fully verified`
          : "GATES GREEN (all suites ran)"
    const disclaimer =
      "NOTE: no assemble/install/deploy was run. Device success is unproven until the operator builds, flashes, and witnesses a call."
    return `${verdict}\n\n${results.join("\n\n")}\n\n${disclaimer}`
  },
})
