// Call-Dad app module. Phase 5 — BuildConfig fields injected from local.properties (gitignored).
// Package com.calldad. minSdk 26 per ADR-001-B. versionName 0.2.0.
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
        // versionCode 3: build fingerprint (lane-checkable via dumpsys).
        versionCode = 3
        versionName = "0.2.0"

        // Phase 5 provisioned secrets. Read from local.properties (gitignored,
        // operator-placed per local.properties.template). Empty defaults so a
        // machine without credentials still builds (STUN-only, no callee).
        // NEVER commit real values — verify_project.py + .gitignore enforce.
        val localProps = gradleLocalProperties(rootDir, providers)
        val turnUrl = localProps.getProperty("TURN_URL") ?: ""
        val turnUser = localProps.getProperty("TURN_USER") ?: ""
        val turnPass = localProps.getProperty("TURN_PASS") ?: ""
        val calleeUid = localProps.getProperty("CALLEE_UID") ?: ""

        buildConfigField("String", "TURN_URL", "\"$turnUrl\"")
        buildConfigField("String", "TURN_USER", "\"$turnUser\"")
        buildConfigField("String", "TURN_PASS", "\"$turnPass\"")
        buildConfigField("String", "CALLEE_UID", "\"$calleeUid\"")
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

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

    // Host-side unit tests (G1/G2 gates — restored; the pasted draft dropped this)
    testImplementation("junit:junit:4.13.2")
}
