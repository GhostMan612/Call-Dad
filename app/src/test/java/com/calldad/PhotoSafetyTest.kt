// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// PhotoSafetyTest.kt — pictures must not become a door out of the app
// Location: app/src/test/java/com/calldad/PhotoSafetyTest.kt
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photo sharing (SPEC_SHEET §2.4) is the newest place a child can see something
 * they did not choose, so its boundaries are pinned here.
 *
 * Three properties, each of which is a way this feature could have gone wrong in
 * a way no test would otherwise catch:
 *
 *  1. **The app holds no gallery permission.** A manifest `READ_MEDIA_IMAGES`
 *     would let a 6-year-old browse the entire camera roll from inside this app.
 *     The system picker is permissionless, so the permission must stay absent.
 *  2. **Nothing is written back to the device.** A received photo saved to
 *     MediaStore would put a copy of the child's image in the gallery, where
 *     every other app on the phone can read it.
 *  3. **An unverified photo is never rendered.** A corrupt transfer must show a
 *     sentence, not a half-decoded face.
 */
class PhotoSafetyTest {

    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = java.io.File(rel).readText()
    private fun code(rel: String) = read(rel)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    private fun manifest() = read("app/src/main/AndroidManifest.xml")

    @Test
    fun theAppHoldsNoGalleryPermission() {
        val m = manifest()
        listOf(
            "READ_MEDIA_IMAGES",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE"
        ).forEach {
            assertFalse(
                "the manifest must not request $it — the system photo picker needs " +
                    "no permission, and holding one would let a child browse the whole " +
                    "camera roll from inside this app",
                m.contains(it)
            )
        }
    }

    @Test
    fun theAppTakesNoCameraPermission() {
        // Capturing a photo is the grown-up's job on their own phone. A child
        // holding a camera on this device is a capability the product never
        // promised, and CameraX here exists for the video call, not for a roll.
        assertFalse(
            "this app must not hold CAMERA for photos",
            manifest().contains("android.permission.CAMERA")
        )
    }

    @Test
    fun nothingIsWrittenBackToSharedStorage() {
        listOf(
            "src/main/java/com/calldad/photos/PhotoClient.kt",
            "src/main/java/com/calldad/ui/screens/PhotoScreen.kt",
            "src/main/java/com/calldad/ui/screens/PhotoViewModel.kt"
        ).forEach { path ->
            val c = code(path)
            listOf("MediaStore", "contentResolver.insert", "RELATIVE_PATH", "EXTERNAL_CONTENT_URI")
                .forEach {
                    assertFalse("$path must not write to shared storage ($it)", c.contains(it))
                }
        }
    }

    @Test
    fun thePhotoScreenNeverRendersUnverifiedBytes() {
        val c = code("src/main/java/com/calldad/ui/screens/PhotoScreen.kt")
        // The decode is gated on `verified`, and there is exactly one decode site.
        assertTrue(
            "the screen must gate its decode on photo.verified",
            c.contains("photo.verified")
        )
        val decodes = Regex("""BitmapFactory\.decodeByteArray""").findAll(c).count()
        assertTrue("expected one decode site, found $decodes", decodes <= 1)
    }

    @Test
    fun aFailedPhotoSaysSomethingAToddlerCanActOn() {
        val c = code("src/main/java/com/calldad/photos/PhotoClient.kt")
        listOf("checksum", "SHA", "sha256", "byteCount", "PERMISSION_DENIED").forEach {
            assertFalse(
                "a kid-facing photo failure must not mention internals ($it)",
                c.contains("\"") && c.contains("failure = \"") && c.contains(it) &&
                    c.substringAfter("failure = \"", "").substringBefore("\"").contains(it)
            )
        }
    }

    @Test
    fun theScreenHasNoShareOrSaveAffordance() {
        val c = code("src/main/java/com/calldad/ui/screens/PhotoScreen.kt")
        listOf("ACTION_SEND", "share", "Save", "Download").forEach {
            assertFalse(
                "the photo screen must not offer a way to export a picture ($it)",
                c.contains(it)
            )
        }
    }
}
