// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// pairing/QrGenerator.kt
// Location: app/src/main/java/com/calldad/pairing/QrGenerator.kt
package com.calldad.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object QrGenerator {

    private const val DEFAULT_SIZE = 512

    fun generate(payload: String, size: Int = DEFAULT_SIZE): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size
        )
        val bitmap = Bitmap.createBitmap(
            matrix.width,
            matrix.height,
            Bitmap.Config.ARGB_8888
        )
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }
        return bitmap
    }
}
