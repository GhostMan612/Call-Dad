import { tool } from "@opencode-ai/plugin"
import { spawnSync } from "node:child_process"

const ADB = "C:\\android\\sdk\\platform-tools\\adb.exe"
const KNOWN: Record<string, string> = {
  parent: "com.calldad.parent",
  child: "com.calldad.child",
}

type Run = { code: number; out: string; err: string }

function run(args: string[], timeoutMs = 60_000): Run {
  const r = spawnSync(ADB, args, {
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

function lines(text: string, n: number): string[] {
  return text.split(/\r?\n/).filter((l) => l.trim().length > 0).slice(-n)
}

export default tool({
  description:
    "Read-only Android device evidence for Call-Dad. Actions: devices, version (dumpsys versionCode/versionName for parent+child), logs (filtered logcat tail, optionally scoped to a package or crash), sdk, apk-age. Never installs, never writes to the device.",
  args: {
    action: tool.schema
      .enum(["devices", "version", "logs", "sdk", "apk-age"])
      .describe("What evidence to collect."),
    serial: tool.schema
      .string()
      .optional()
      .describe("Device serial. Required for version/logs/sdk/apk-age."),
    package: tool.schema
      .string()
      .optional()
      .describe("Filter logs to this package (e.g. com.calldad.parent), or crash-only when 'crash'."),
    lines: tool.schema.number().default(80).describe("How many log lines to return (tail)."),
  },
  async execute(args) {
    if (args.action === "devices") {
      const r = run(["devices", "-l"])
      // RULES 1.5a: no real device serial in a committable file. Map the
      // parent/child roles from the model field of the line above, at run
      // time -- never from a table baked into this file.
      return [
        `adb devices -l:\n${r.out.trim()}`,
        "",
        "Map roles from each line's model: Moto G 2025 is the PARENT",
        "(com.calldad.parent), BLU View 5 is the CHILD (com.calldad.child).",
        "The Q8K tablet is a TARGET TEST RIG at SDK 30 -- it is NOT a third",
        "member of the pair (ADR-015 is two-person: calls/{uidA_uidB}, one",
        "paired contact). Do not pair it; do not install both flavors on it.",
        "The Moto is wireless-adb and its mDNS port changes per session; the",
        "BLU and the Q8K are usually on USB, and plugging one in can drop the",
        "other off the list until it is replugged.",
      ].join("\n")
    }

    if (!args.serial) {
      return "This action needs `serial`. Run action=devices first."
    }

    if (args.action === "version") {
      const out: string[] = []
      for (const [flavor, pkg] of Object.entries(KNOWN)) {
        const r = run(["-s", args.serial, "shell", "dumpsys", "package", pkg])
        const body = `${r.out}\n${r.err}`
        const trimmed = body.split(/\r?\n/).map((l) => l.trim())
        const vc = trimmed.find((l) => /versionCode=/.test(l))
        const vn = trimmed.find((l) => /versionName=/.test(l))
        const ut = trimmed.find((l) => /lastUpdateTime=/.test(l))
        out.push(
          `${flavor} (${pkg}):\n  ${vc ?? "versionCode: NOT INSTALLED"}\n  ${vn ?? "versionName: NOT INSTALLED"}\n  ${ut ?? "lastUpdateTime: n/a"}`,
        )
      }
      return `version fingerprint on ${args.serial}:\n\n${out.join("\n\n")}\n\nCompare against app/build.gradle.kts versionCode/versionName. If they disagree you are reading logs from a stale APK.`
    }

    if (args.action === "sdk") {
      const r = run(["-s", args.serial, "shell", "getprop", "ro.build.version.sdk"])
      const m = run(["-s", args.serial, "shell", "getprop", "ro.product.model"])
      return `${args.serial}\nsdk=${r.out.trim()}\nmodel=${m.out.trim()}`
    }

    if (args.action === "apk-age") {
      return `Use action=version and read lastUpdateTime — that is the authoritative install age. This lane does not stat on-device paths.`
    }

    // logs
    const filter: string[] = []
    if (args.package && args.package !== "crash") filter.push(`${args.package}:V`)
    if (args.package === "crash") filter.push("AndroidRuntime:E", "libc:E", "DEBUG:V")
    else filter.push("WebRTC:V", "CallDad:V", "CallForegroundService:V", "Firestore:V")
    const r = run(["-s", args.serial, "logcat", "-d", "-v", "threadtime", ...filter])
    const body = r.out || r.err
    const fatals = lines(body, 4000).filter((l) => /FATAL EXCEPTION|AndroidRuntime/.test(l))
    const out = lines(body, args.lines).join("\n")
    return [
      `logcat tail from ${args.serial} (${args.lines} lines):`,
      out || "(no matching lines — wrong filter, or the app never started)",
      "",
      `FATAL/exception lines in buffer: ${fatals.length}`,
      fatals.slice(-10).join("\n"),
    ].join("\n")
  },
})
