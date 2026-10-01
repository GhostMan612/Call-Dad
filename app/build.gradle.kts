// Call-Dad app module. BuildConfig fields injected from local.properties (gitignored).
// Package com.calldad. minSdk 26 per ADR-001-B. versionName 0.2.6 / versionCode 9.
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.calldad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calldad"
        minSdk = 26
        targetSdk = 35
        // versionCode 4: build fingerprint (lane-checkable via dumpsys).
        // versionCode 5: Contract 9 follow-up. Bumps past 4/0.2.1 because that
        // fingerprint was reused across ADR-015 and ADR-016, which made every
        // device claim unattributable via dumpsys. 5 is the fingerprint that
        // proves the PTT-honesty, stale-teardown, speaker-timing and
        // no-Settings-escape fixes are on the phone.
        // versionCode 6: PTT encoder-drain fix. MUST NOT reuse 5: 5 is the
        // fingerprint that PROVES the tail-clipping bug (operator-witnessed on
        // both devices). A dumpsys reading of 5 after the fix is meaningless,
        // because 5 and 6 differ only in that fix.
        // versionCode 7: closes K8 (TURN), K12 (PTT push), the pairings read
        // hole, orphan cleanup, and the clip cap. Bumps because the APK's ICE
        // servers changed: a phone on 6 would silently lack a relay and fail
        // on mobile data, and that is not visible from dumpsys at all.
        // versionCode 9: re-lands K12 (dedicated IMPORTANCE_LOW PTT channel --
        // the clip notification shipped on the IMPORTANCE_HIGH call channel and
        // rang at full volume on a locked phone) and K21 (the activity no longer
        // opts into showWhenLocked/turnScreenOn, and the full-screen intent is
        // conditional on the keyguard, so a ring no longer traps the grown-up on
        // their own lock screen). Bumps because 7 and 9 are behaviourally
        // different in exactly the case that matters: locked screen. A phone on
        // 7 rings the whole house for a voice message and blocks the unlock.
        versionCode = 9
        versionName = "0.2.6"

        // Phase 5 provisioned secrets. Read from local.properties (gitignored,
        // operator-placed per local.properties.template). Empty defaults so a
        // machine without credentials still builds (STUN-only, no callee).
        // NEVER commit real values — verify_project.py + .gitignore enforce.
        val localProps = gradleLocalProperties(rootDir, providers)
        // Phase 9: comma-separated TURN_URLS replaces singular TURN_URL.
        val turnUrls = localProps.getProperty("TURN_URLS") ?: ""
        val turnUser = localProps.getProperty("TURN_USER") ?: ""
        val turnPass = localProps.getProperty("TURN_PASS") ?: ""
        val calleeUid = localProps.getProperty("CALLEE_UID") ?: ""

        buildConfigField("String", "TURN_URLS", "\"$turnUrls\"")
        buildConfigField("String", "TURN_USER", "\"$turnUser\"")
        buildConfigField("String", "TURN_PASS", "\"$turnPass\"")
        // Derived alias: first URL, for any code still reading TURN_URL.
        buildConfigField("String", "TURN_URL",
            "\"${turnUrls.split(",").firstOrNull()?.trim() ?: ""}\"")
        buildConfigField("String", "CALLEE_UID", "\"$calleeUid\"")
    }

    // ---- Release signing (BP-05 §5, operator-provisioned) ----
    //
    // The keystore NEVER enters the repo. The operator creates it once
    // (keytool, password of their choosing) and points `keystore.properties`
    // -- gitignored, shaped like local.properties -- at it. Every value falls
    // back to empty, so a machine without the file still configures and still
    // builds the debug flavors; only an actual release assemble fails, and it
    // fails with a clear message rather than silently shipping a debug-signed
    // APK that no store will accept.
    //
    // This exists because "no secrets in repo" was enforced while the release
    // plan itself did not exist: the ban was real and the plan was missing.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile.inputStream().use { load(it) }
        }
    }
    val releaseStorePath = keystoreProps.getProperty("storeFile", "")
    val releaseHasKeystore = releaseStorePath.isNotBlank() &&
        file(releaseStorePath).exists()

    signingConfigs {
        if (releaseHasKeystore) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    // Phase 10: audience flavors. BOTH APKs install side-by-side
    // (applicationIdSuffix), enabling two-device testing on one phone.
    // THEME names are operator-ordered: child keeps "Call of Daddy".
    flavorDimensions += "audience"

    productFlavors {
        create("parent") {
            dimension = "audience"
            applicationIdSuffix = ".parent"
            versionNameSuffix = "-parent"
            buildConfigField("String", "APP_THEME", "\"blue\"")
            resValue("string", "app_name", "Call of Daddy (Parent)")
        }
        create("child") {
            dimension = "audience"
            applicationIdSuffix = ".child"
            versionNameSuffix = "-child"
            buildConfigField("String", "APP_THEME", "\"pink\"")
            resValue("string", "app_name", "Call of Daddy")
        }
    }

    buildTypes {
        // No explicit debug block: AGP's default already applies the debug
        // signing config, which is what every operator device install uses.
        getByName("release") {
            // Warn rather than throw: a machine without keystore.properties must
            // still be able to configure and build debug (the whole test lane
            // depends on that). What must never happen is a mis-signed release
            // artifact that silently installs, so the build says so loudly and
            // docs/release-signing.md says what to do.
            if (!releaseHasKeystore) {
                logger.warn(
                    "Call-Dad: no release keystore configured (keystore.properties " +
                        "missing or storeFile not found). Any release build will NOT be " +
                        "correctly signed -- see docs/release-signing.md."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        // QA incoming-call hook is DEBUG-gated via BuildConfig.DEBUG.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Firebase 24.x ships Kotlin metadata 2.3.0; our KGP is 2.0.21.
        // Skips the version gate — the auth surface we touch (getInstance,
        // signInAnonymously, currentUser) is ancient and stable. Revisit
        // with a KGP upgrade if a newer Firebase uses new language features.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    // JVM unit tests run against the unmocked android.jar stub, where every
    // framework call (e.g. android.util.Log) throws. returnDefaultValues
    // makes them no-op instead — donor-proven pattern that lets host tests
    // exercise logging-touching pure logic (e.g. SimulatedPttEngine).
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ---- Phase 2 ----
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    // ---- Phase 3: Stream WebRTC fork (drop-in org.webrtc.*, ~20MB native) ----
    implementation(libs.webrtc.android)

    // ---- Phase 5: auth + messaging (+play-services-auth per architect) ----
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.auth)

    // ---- Phase 7: WebViewAssetLoader (hardened asset serving) ----
    implementation(libs.androidx.webkit)

    // ---- Contracts 2&3 (ADR-014): peer persistence (SecurePeerStore, plaintext per catalog note) ----
    implementation(libs.datastore.preferences)

    // ---- Contract 4: QR pairing (CameraX + unbundled ML Kit + ZXing) ----
    // NOTE: kotlinx-coroutines-play-services intentionally NOT repeated here:
    // Phase 2 already declares implementation(libs.kotlinx.coroutines.play.services),
    // which now resolves to 1.10.2 via the catalog re-point above.
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)
    implementation(libs.play.services.base)

    // Host-side unit tests. org.json: the android.jar stub returns
    // defaults for JSONObject, so pairing-payload tests need the real one.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
