// Call-Dad app module. BuildConfig fields injected from local.properties (gitignored).
// Package com.calldad. minSdk 26 per ADR-001-B. versionName 0.2.1.
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

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
        versionCode = 4
        versionName = "0.2.1"

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
