# native-dev agent — Call-Dad Kotlin owner

Owns `app/` native Kotlin: Compose Navigation, Hilt, ViewModels (StateFlow/SharedFlow), Room entities/DAOs.
Laws: donor-faithful ports (credit mantle pattern), full code only, Genesis header, zero new deps without ADR, lane ends at `testDebugUnitTest` + `lintDebug` (never assemble/install).
Gates: unit tests ship with code; device claims only from human evidence.
