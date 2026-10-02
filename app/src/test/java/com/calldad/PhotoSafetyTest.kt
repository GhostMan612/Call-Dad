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

    /**
 * `src/main/...`, NOT `app/src/main/...`.
     *
     * Tests run with the `app/` module directory as the CWD, so `app/` is a SIBLING
     * of the working directory rather than a child of it. This read `app/src/main/
     * AndroidManifest.xml`, which does not exist, and every gallery-permission
     * assertion silently became a FileNotFoundException — i.e. the kid-safety check
     * reported nothing at all while appearing to be enforced.
     *
     * It is the same sibling-vs-child trap `ChatKidSafetyTest` documents for
     * `$mainDir`, hit here by a `read()` helper that used the raw relative path
     * while every other read in the class went through `$mainDir`.
     */
    private fun manifest() = read("src/main/AndroidManifest.xml")

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

    /**
     * The PHOTO path must not need the camera.
     *
     * This test used to assert the app holds no `CAMERA` permission at all, and
     * that assertion is simply WRONG: the app legitimately holds CAMERA for the
     * video call (Phase 3, ADR-005). It only ever "passed" because
     * [manifest] was reading a path that did not exist and every assertion in the
     * method was throwing `FileNotFoundException` before it ran.
     *
     * So the property worth pinning is the narrow one: **capturing a photo is the
     * grown-up's job on their own phone**, and the child's flow reaches an image
     * only through the permissionless system picker. A camera in the manifest is
     * fine; a camera in the PHOTO code path is not.
     */
    @Test
    fun thePhotoPathDoesNotUseTheCamera() {
        listOf(
            "src/main/java/com/calldad/photos/PhotoClient.kt",
            "src/main/java/com/calldad/ui/screens/PhotoScreen.kt",
            "src/main/java/com/calldad/ui/screens/PhotoViewModel.kt"
        ).forEach { path ->
            val text = code(path)
            assertFalse(
                "$path must not request or open the camera. Photo sharing uses the " +
                    "permissionless system picker so the app holds no camera handle " +
                    "of its own; a child must not be able to point this app at the " +
                    "room they are sitting in.",
                text.contains("Manifest.permission.CAMERA") ||
                    text.contains("ACTION_IMAGE_CAPTURE")
            )
        }

        // And the call's camera permission must stay OPTIONAL, or the app cannot
        // install on a device without one — which is the QA/emulator path the
        // manifest comment describes.
        assertTrue(
            "the camera FEATURE must stay required=\"false\" so an audio-only device " +
                "can install; the manifest comment says so and nothing enforced it",
            manifest().contains("""android.hardware.camera" android:required="false""")
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
