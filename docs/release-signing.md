# Release signing (BP-05 §5)

> Operator-only for the key. This lane never creates a keystore and never
> commits one — RULES §1.3 and `tools/verify_project.py` enforce that.

## What is already done in this repo

- **Versioning** is explicit and lane-checkable: `versionCode` / `versionName` in
  `app/build.gradle.kts`, with `-parent` / `-child` `versionNameSuffix`es so the two
  flavors are distinguishable on a device. Bump `versionCode` whenever a flashed
  build must be told apart from an older one; `dumpsys package` is the check.
- **`signingConfigs` is wired** and reads a gitignored `keystore.properties` at the
  repo root, shaped like `local.properties`.
- **An explicit `release` build type** exists with `proguardFiles` and a warning when
  no keystore is configured. Debug keeps AGP's default signing config, so ordinary
  device testing is unaffected.
- **No secrets in the repo** is machine-enforced: `tools/verify_project.py` fails the
  gate on `*.keystore` / `*.jks` files and on an assigned `RELEASE_STORE_PASSWORD`.

## What the operator does, once

1. Create the keystore. **Choose the password yourself and keep it off this machine's
   backups-in-repo** — it is the one secret that, once lost, means the app can never be
   updated on a store without a new package name.

   ```powershell
   C:\android\Android Studio\jbr\bin\keytool.exe -genkeypair -v `
     -keystore C:\android\calldad-release.keystore `
     -alias calldad -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` at the repo root (**not** in `app/`), copying from
   `keystore.properties.template`:

   ```properties
   storeFile=C\:\\android\\calldad-release.keystore
   storePassword=<yours>
   keyAlias=calldad
   keyPassword=<yours>
   ```

3. Confirm it is ignored: `git status --porcelain` must not list it. If it does, the
   template's entry is missing — add it before doing anything else.

4. Build and check the signature:

   ```powershell
   .\gradlew.bat assembleParentRelease assembleChildRelease
   C:\android\sdk\build-tools\35.0.0\apksigner.bat verify --print-certs `
     .\app\build\outputs\apk\parent\release\app-parent-release.apk
   ```

## Why the keystore is outside the repo

`app/build/` and the repo are both backed up and synced. A keystore inside either
means a child-device signing key ends up wherever the source ends up, which is
exactly the "no secrets in the repo" law this plan would otherwise be laundering
around. `C:\android\` is a tool home already outside the project.

## Open, recorded honestly

- **ProGuard rules are a stub.** `app/proguard-rules.pro` needs real entries before a
  minified release is trustworthy — chiefly keep rules for the Stream WebRTC JNI
  (`org.webrtc.**`), the FCM receiver, and the Firestore/lifecycle reflectively-used
  classes. Shipping a minified release without them is a runtime crash, not a smaller
  APK, so treat this as **not release-ready** until a release build is actually run
  and smoke-tested.
- **No Play upload key separation.** A real release process wants a separate upload key
  so the app signing key can be rotated for enrolled devices. That is beyond v0.1.
- **`assembleRelease` has never been run.** No release artifact exists, and this lane
  never runs builds without operator authorization.