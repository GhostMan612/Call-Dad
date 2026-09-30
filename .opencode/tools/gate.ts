import { tool } from "@opencode-ai/plugin"
import { spawnSync } from "node:child_process"

const PY = "C:\\venv-hub\\venv\\Scripts\\python.exe"

type Run = { code: number; out: string; err: string }

function run(argv: string[], cwd: string, timeoutMs = 900_000): Run {
  const r = spawnSync(argv[0], argv.slice(1), {
    cwd,
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 64 * 1024 * 1024,
    shell: true,
    windowsHide: true,
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
    "Run the Call-Dad repo verification gate for this lane. Executes tools/verify_project.py plus the flavored unit-test and lint tasks and the functions test. Refuses any assemble/install/connected/deploy command. Returns PASS/FAIL per gate with counts.",
  args: {
    gate: tool.schema
      .enum(["verify", "unit", "lint", "functions", "all"])
      .default("all")
      .describe("Which gate to run. 'all' = verify + unit + lint + functions."),
    gradle_timeout_minutes: tool.schema
      .number()
      .default(20)
      .describe("Timeout for gradle tasks in minutes."),
  },
  async execute(args, context) {
    const cwd = context.directory || context.worktree
    const want = new Set(
      args.gate === "all" ? ["verify", "unit", "lint", "functions"] : [args.gate],
    )
    const results: string[] = []
    let failed = 0

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
        results.push(
          `${label}: FAIL (exit ${r.code})\n${cmdline(argv)}\n${tail(r.out || r.err, 40)}`,
        )
      }
    }

    if (want.has("functions")) {
      const r = run(["node", "--test", "functions/ring.test.js"], cwd, 300_000)
      if (r.code === 0) results.push(`functions: PASS\n${tail(r.out, 12)}`)
      else {
        failed++
        const combined = r.out + "\n" + r.err
        if (/not recognized|ENOENT/i.test(combined)) {
          results.push(
            "functions: SKIPPED (node not on PATH in this shell) — operator must run `node --test functions/ring.test.js`",
          )
        } else {
          results.push(`functions: FAIL (exit ${r.code})\n${tail(combined, 40)}`)
        }
      }
    }

    const verdict = failed === 0 ? "GATES GREEN (for what this lane may run)" : `${failed} GATE(S) RED`
    const disclaimer =
      "NOTE: no assemble/install/deploy was run. Device success is unproven until the operator builds, flashes, and witnesses a call."
    return `${verdict}\n\n${results.join("\n\n")}\n\n${disclaimer}`
  },
})
