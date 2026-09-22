// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// pairing/ModuleAvailabilityCheck.kt
// Location: app/src/main/java/com/calldad/pairing/ModuleAvailabilityCheck.kt
package com.calldad.pairing

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Verifies that the ML Kit barcode module is available.
 *
 * For unbundled ML Kit, the model is downloaded via Google Play Services.
 * The BarcodeScanner returned by BarcodeScanning.getClient() implements
 * OptionalModuleApi in the unbundled version, so it can be passed
 * directly to ModuleInstallClient.
 *
 * KEY BEHAVIOR: installModules() returns a ModuleInstallResponse whose
 * areModulesAlreadyInstalled() indicates whether the module was already
 * present when the request was sent. If false, the download is in
 * progress and the scanner will not detect anything until it completes.
 * This function polls until the module is available, with a timeout.
 */
object ModuleAvailabilityCheck {

    private const val POLL_INTERVAL_MS = 500L
    private const val MAX_WAIT_MS = 30_000L

    /**
     * @return true if the module is installed and ready. false if the
     *         device lacks Play Services, the download failed, or the
     *         timeout elapsed.
     */
    suspend fun ensureBarcodeModule(context: Context): Boolean {
        return try {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val moduleInstallClient = ModuleInstall.getClient(context)

            // Fast path: already available.
            val availability = moduleInstallClient
                .areModulesAvailable(scanner).await()
            if (availability.areModulesAvailable()) return true

            // Request install.
            val request = ModuleInstallRequest.newBuilder()
                .addApi(scanner)
                .build()
            val installResponse = moduleInstallClient
                .installModules(request).await()

            // If the module was already installed when the request was
            // sent, we are done. Otherwise, poll until it becomes
            // available.
            if (installResponse.areModulesAlreadyInstalled()) return true

            val deadline = System.currentTimeMillis() + MAX_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_INTERVAL_MS)
                val check = moduleInstallClient
                    .areModulesAvailable(scanner).await()
                if (check.areModulesAvailable()) return true
            }
            false
        } catch (t: Throwable) {
            false
        }
    }
}
