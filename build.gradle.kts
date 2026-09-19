// Call-Dad root build. Plugin versions centralized (donor pins: AGP 8.13.2 / Kotlin 2.1.0).
// NOTE: version catalog lives in gradle/libs.versions.toml; this file keeps legacy string pins as fallback.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    // Phase 2: required for google-services.json processing.
    alias(libs.plugins.google.services) apply false
}
