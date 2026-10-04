# Q8K tablet — device reference

> **READ THIS FIRST. This device is on loan and leaves the bench after
> 2026-10-10 (Saturday).** Everything here was captured in one session on
> 2026-10-03 because it will not be available again until the following Saturday.
> If you need a fact about this tablet and it is not on this page, capture it
> **now**, not next week.
>
> Every value below was read off the device with `adb shell getprop`,
> `dumpsys`, and `wm`. Nothing is guessed. Where the device lies about itself,
> that is called out — see `ro.build.version.release` below.

## Identity

| | |
|---|---|
| Model | `Q8K` (`ro.product.model`, `ro.product.device`, `ro.product.name` all `77-EV`) |
| Brand | `MASTERTECH` |
| Manufacturer | `incar` |
| Board / hardware | `sp7731e_1h10`, platform `sp7731e` (**Spreadtrum/Unisoc**) |
| Build display id | `Q8K_20250725` |
| Fingerprint | `MASTERTECH/77-EV/77-EV:11/RP1A.201005.001/20250120:user/release-keys` |
| Build date | Fri Jul 25 09:29:47 CST 2025 |
| Form factor | `ro.build.characteristics = tablet` |

**Serial — resolve at run time, never hardcode:** `Q8KQ1219PC002109`.
It is 16 digits, and `tools/verify_project.py` FAILS the gate on any 14+ digit
serial in a tracked file. Use `adb devices -l` (or the `device-evidence` tool)
and map the role from the model field, exactly as `flash.md` §0 says.

## Android — and the lie in it

| Property | Value |
|---|---|
| `ro.build.version.sdk` | **30** |
| `ro.build.version.release` | **`12`** ← **WRONG** |
| `ro.build.version.security_patch` | 2025-02-05 |
| `ro.build.id` | `RP1A.201005.001` |
| `ro.build.version.incremental` | 20250120 |

> ### ⚠ `ro.build.version.release` claims 12. The device is Android 11.
> `ro.build.version.sdk = 30` is the authoritative value, and every
> version-gated branch in this app keys off `Build.VERSION.SDK_INT`, so the
> **app behaves as Android 11** and all API-level reasoning here must use 30.
> This is a cheap tablet with a doctored user-facing property. **Never gate on
> `VERSION.RELEASE`; always gate on `VERSION.SDK_INT`.** A future session that
> trusts `release` will draw conclusions that are simply false on this device.

### What SDK 30 costs this app

- **The Ask Helper tile is WITHHELD.** The Helper is on-device-only by design
  (the network recognizer uploads a child's voice to the OEM), and
  `SpeechRecognizer.isOnDeviceRecognitionAvailable` is **API 31+**. On this
  device the Helper is not "unconfigured", it is *structurally* unavailable, so
  `HomeViewModel` filters the tile out rather than showing a button that always
  refuses. `minSdk` is 26, so the same applies to any Android 8–11 device.
- Full-screen intent behaves differently again from the API 34/36 phone fleet,
  so this is new ground for the K12/K21 locked-phone checks.

## Hardware

| | |
|---|---|
| CPU ABI | **`armeabi-v7a` only** (`ro.product.cpu.abilist = armeabi-v7a,armeabi`) |
| RAM class | 32-bit SoC — **no arm64 at all** |
| OpenGL ES | `ro.opengles.version = 196610` = **ES 3.2** |
| Boot state | `ro.boot.verifiedbootstate = green`, `ro.secure = 1`, `ro.debuggable = 0` |
| Build type | `user`, `release-keys` (stock-ish, bootloader locked) |
| `ro.carrier` | `oversea` |

The **32-bit-only** ABI is the single most likely source of a native crash that
only reproduces here: WebRTC, Firebase and ML Kit all normally ship arm64 first.
If the app dies on this tablet and nowhere else, suspect an `armeabi-v7a`-only
`.so` before you suspect the app's own logic.

## Screen — a display-override, which matters for the ≥96dp audit

| | |
|---|---|
| Physical | **600 x 1024** |
| Physical density | **160** (mdpi) |
| **Override density** | **212** |

`wm density` reports an override of 212 against a physical 160. Effective logical
viewport is therefore roughly **452 x 772 dp** — much wider than either phone.
This is the device that will expose any layout assumption the kid-UX audit only
*reasoned* about: the Home grid, the ≥96dp tap targets and the ParentGate's
multiplication were all specified for a phone-sized screen.

## Google Play Services and WebView — both PRESENT

Checked explicitly, because their absence would be the most expensive possible
finding on this device.

| | |
|---|---|
| Play Services | **installed**, `versionName 26.36.35`, `versionCode 263635054`, `primaryCpuAbi armeabi-v7a` |
| WebView | **installed** as `com.google.android.webview` **153.0.8010.39** (`versionCode 801003900`, `targetSdk 36`) |

So: **ML Kit barcode scanning should work** (GMS is there), and **the games
should render** (a WebView provider is there — note it is
`com.google.android.webview`, not the AOSP stub `com.android.webview`, which is
correctly absent when a provider is installed).

> ### A false reading I nearly published
> My first pass concluded "Play Services NOT INSTALLED" from a regex that matched
> `Unable to find package: com.google.android.gms.checkin` — a *different*,
> legitimately-absent package. GMS itself was installed the whole time. This is
> recorded because the same trap is available to the next session: **confirm a
> package exists with `dumpsys package <exact.name>` and read only THAT
> section.** Do not grep a multi-package dump and attribute the result to the
> package you asked about.

## Current install state (2026-10-03)

| | |
|---|---|
| Role in the pair | **CHILD** (`com.calldad.child`) |
| Installed build | **vc12 / 0.3.2-child**, 2026-10-03 16:38:44 |
| Source is now | **vc13 / 0.3.3** — see "Known bugs found ON this device" below |
| Parent flavor on this device | NOT INSTALLED (verified — no crossed install) |
| Paired? | **NO.** Fresh install: no anonymous Firebase account, no peer UID |

## Known bugs found ON this device, and what each one is

All three were visible only by **looking at the screen**. Every host suite was
green, nothing threw, and no log line mentioned them. They are recorded here
because this device is what surfaced them and it will not be back until next
Saturday.

1. **The child app said "Mama is calling".** `strings.xml` had
   `child_peer_name = "Mama"`, but that resource is used BOTH to name the grown-up
   on the child's phone AND to name the child on the parent's phone — and the
   values were inverted relative to the resource names. There were also two
   different mappings in the tree; `CallViewModel.peerDisplayName()` was the odd
   one out, so the call screen and the message thread named the same person two
   different ways. **Fixed in vc13:** `name_of_grown_up` = `Dad`,
   `name_of_child` = `Your kid`, one uniform mapping, pinned by
   `KidNamesRegressionTest`.
2. **The walkie talkie said "Dad will hear it right away" on both flavors.** Both
   flavors have a walkie talkie, so on the *grown-up's* device it cheerfully told
   them that Dad would hear their own message. Hardcoded literal; now interpolates
   the peer name.
3. **When one device won a game, the other one's screen congratulated the wrong
   human.** `winText()` returned `"Dad wins!"` for every loss. `role === "caller"`
   IS the parent (`GameScreen` maps `APP_THEME` blue → caller), so when the CHILD
   won, the PARENT's own screen congratulated Dad for a game Dad had just lost.
   Now role-aware: "Your kid wins!" on the parent, "Dad wins!" on the child.

Two further "phone" literals in `ConsentScreen` were found by the regression test
rather than by eye, because **this device is a tablet** and the text did not match
the hardware in the child's hand. Worth remembering when writing UI copy for a
fleet that is not all phones.

**LESSON, and it is the reason the test class exists:** none of these are
reachable by a behavioural host test. "We fixed the one we saw" is how the second
and third got shipped. `KidNamesRegressionTest` pins the *mapping* rather than the
strings, so a new screen that names the peer cannot silently invent a fourth one.

**The pair is being re-formed.** The Q8K replaces the retired BLU View 5 as the
child; Moto G 2025 (parent, `0.3.2-parent`, installed 16:39:00) is unchanged.
The Q8K will generate a NEW uid, so the room becomes a new `calls/{Q8K_MOTO}` and
the QR must be re-scanned from parent to child. The Moto's peer store still holds
the retired BLU's uid; a stale room will simply never connect.

The backend is **LIVE** as of 2026-10-04: `firebase deploy --only
firestore:rules,functions` ran from the lane, rules were released to
`calldad-508d7`, and both functions are v2 / us-central1 / 256MB / nodejs22 with
"no changes detected" — deployed code equals the tree. So the consent read is
**not** the blocker on this device any more.

What still gates a first launch is **consent itself**: a fresh install has no
grant, so every feature denies until a grown-up taps the shield icon →
grown-ups gate → "Allow everything". That looks identical to a broken app, and it
is the intended fail-closed behaviour — check it before filing anything.

## Before Saturday — checklist for this window

Capture anything you will want and cannot get back:

- [ ] `adb shell getprop` → re-verify if a factory reset or update happens
- [ ] First-run consent sequence (shield icon → grown-ups gate → Allow everything)
- [ ] **The kill switch with the app force-stopped** — revoke on the parent,
      force-stop this app, then call. A check with the app OPEN passes on vc11
      and proves nothing; that hole is what vc12 exists to close.
- [ ] A real call (SD 30, 32-bit, different WebRTC path than either phone)
- [ ] Game win/lose labelling **on both devices** (fixed in vc12 source — the
      parent's phone used to congratulate Dad when the child won)
- [ ] Ask Helper tile is **absent** — confirm that is what you see, and do not
      file it as a missing feature
- [ ] Back at Home does not leave the app (launcher-escape fix)
- [ ] Call → Game → Home: camera indicator must go dark
- [ ] `adb logcat` for any `UnsatisfiedLinkError` / `dlopen failed` — the
      armeabi-v7a-only ABI is the thing most likely to break here alone
- [ ] Locked-screen ring behaviour (K12/K21) on a platform you have not tested