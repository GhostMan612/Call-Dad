import { tool } from "@opencode-ai/plugin"
import { spawnSync } from "node:child_process"

type Run = { code: number; out: string; err: string }

function run(args: string[], cwd: string, timeoutMs = 60_000): Run {
  const r = spawnSync(args[0], args.slice(1), {
    cwd,
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 64 * 1024 * 1024,
    windowsHide: true,
  })
  return {
    code: r.status ?? -1,
    out: r.stdout ?? "",
    err: r.stderr ?? (r.error ? String(r.error) : ""),
  }
}

function tail(text: string, n: number): string {
  return text.split(/\r?\n/).filter((l) => l.trim().length > 0).slice(-n).join("\n")
}

export default tool({
  description:
    "Reconcile what the repo's own docs CLAIM shipped against what git history actually contains. Compares SESSION_HANDOFF.md and blueprints/CURRENT_STATE.md against the current HEAD, reports drifted version numbers, ADR filenames referenced-but-absent, and uncommitted work. Use after any pull, reset, or cloud-session update.",
  args: {
    ref: tool.schema.string().default("HEAD").describe("Git ref to inspect (usually HEAD or origin/main)."),
    depth: tool.schema.number().default(12).describe("How many commits to summarize."),
  },
  async execute(args, context) {
    const cwd = context.directory || context.worktree
    const parts: string[] = []

    const head = run(["git", "log", "-1", "--format=%H%n%ad%n%s", args.ref], cwd)
    parts.push(`HEAD (${args.ref}):\n${head.out.trim()}`)

    const sync = run(
      ["git", "rev-list", "--left-right", "--count", "origin/main...HEAD"],
      cwd,
    )
    parts.push(`origin/main vs HEAD (behind ahead): ${sync.out.trim()}`)

    const dirty = run(["git", "status", "--porcelain"], cwd)
    parts.push(
      dirty.out.trim()
        ? `uncommitted:\n${tail(dirty.out, 25)}`
        : "uncommitted: none (clean tree)",
    )

    const log = run(["git", "log", "--oneline", `-${args.depth}`, args.ref], cwd)
    parts.push(`recent commits:\n${tail(log.out, args.depth)}`)

    // version drift: docs vs gradle
    const gradle = run(
      ["git", "show", `${args.ref}:app/build.gradle.kts`],
      cwd,
    )
    const code = gradle.out.match(/versionCode\s*=\s*(\d+)/)?.[1] ?? "?"
    const name = gradle.out.match(/versionName\s*=\s*"([^"]+)"/)?.[1] ?? "?"
    parts.push(`authoritative version in ${args.ref}: versionCode ${code} / versionName ${name}`)

    // scan docs for version + stale refs
    const docs = ["SESSION_HANDOFF.md", "blueprints/CURRENT_STATE.md", "AGENTS.md"]
    const drifts: string[] = []
    for (const d of docs) {
      const r = run(["git", "show", `${args.ref}:${d}`], cwd)
      if (r.code !== 0) {
        drifts.push(`${d}: MISSING at ${args.ref}`)
        continue
      }
      const v = r.out.match(/version(?:Code)?\s*[=:]?\s*(\d+)\s*(?:\/|,)\s*"?0?\.(\d+\.\d+)/i)
      if (v && v[1] !== code) {
        drifts.push(`${d}: mentions versionCode ${v[1]} but build says ${code}`)
      }
      const adrs = [...r.out.matchAll(/ADR-(\d+)/g)].map((m) => Number(m[1]))
      if (adrs.length) {
        const max = Math.max(...adrs)
        const listing = run(
          ["git", "ls-tree", "--name-only", args.ref, "blueprints/decisions/"],
          cwd,
        )
        const have = new Set(
          [...listing.out.matchAll(/ADR-(\d+)/g)].map((m) => Number(m[1])),
        )
        const missing = [...new Set(adrs)].filter((n) => !have.has(n))
        if (missing.length) {
          drifts.push(
            `${d}: references ADR(s) ${missing.map((n) => `ADR-${String(n).padStart(3, "0")}`).join(", ")} that do not exist (max on disk: ${max})`,
          )
        }
      }
    }
    parts.push(
      drifts.length
        ? `DOC DRIFT:\n- ${drifts.join("\n- ")}`
        : "doc drift: none detected for version/ADR references",
    )

    return parts.join("\n\n")
  },
})
