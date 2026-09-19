// Call-Dad app module. Phase 2 — donor toolchain pins in ../../gradle/libs.versions.toml.
// Package com.calldad. minSdk 26 per ADR-001-B. versionName 0.2.0 (Firestore signaling).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Phase 2: reads app/google-services.json (gitignored, operator-placed) and initialises FirebaseApp.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.calldad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calldad"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    // MVVM / state
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ---- Phase 2 additions ------------------------------------------------
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Gives us Task<T>.await() so Firestore calls can be suspend functions.
    implementation(libs.kotlinx.coroutines.play.services)

    // Host-side unit tests (G1/G2 gates)
    testImplementation("junit:junit:4.13.2")
}
