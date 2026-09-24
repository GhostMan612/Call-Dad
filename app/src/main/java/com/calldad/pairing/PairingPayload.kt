// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// pairing/PairingPayload.kt — the QR contents, encoded and validated
// Location: app/src/main/java/com/calldad/pairing/PairingPayload.kt
package com.calldad.pairing

import org.json.JSONException
import org.json.JSONObject

/** What one phone shows in its pairing QR: who it is + this session's nonce. */
data class PairingPayload(val uid: String, val nonce: String) {

    fun encode(): String = JSONObject().apply {
        put("v", VERSION)
        put("uid", uid)
        put("nonce", nonce)
    }.toString()

    sealed interface Parsed {
        data class Ok(val payload: PairingPayload) : Parsed
        data object WrongVersion : Parsed
        data object OwnCode : Parsed
        data object Invalid : Parsed
    }

    companion object {
        const val VERSION = 2
        private val UID_PATTERN = Regex("^[A-Za-z0-9]{10,128}$")
        private val NONCE_PATTERN = Regex("^[A-Za-z0-9-]{8,64}$")

        /**
         * Validates a scanned code. v1 codes (which also carried an FCM
         * token) are still accepted; the token is ignored. A phone can
         * never pair with itself (a mirror or a photo of its own screen).
         */
        fun parse(raw: String, ownUid: String): Parsed {
            val json = try {
                JSONObject(raw)
            } catch (e: JSONException) {
                return Parsed.Invalid
            }
            val version = json.optInt("v", 0)
            if (version != 1 && version != VERSION) return Parsed.WrongVersion
            val uid = json.optString("uid")
            val nonce = json.optString("nonce")
            if (!UID_PATTERN.matches(uid) || !NONCE_PATTERN.matches(nonce)) return Parsed.Invalid
            if (uid == ownUid) return Parsed.OwnCode
            return Parsed.Ok(PairingPayload(uid, nonce))
        }

        /** Both phones derive the same session id from the two QR nonces. */
        fun sessionNonce(ownQrNonce: String, peerQrNonce: String): String {
            val sorted = listOf(ownQrNonce, peerQrNonce).sorted()
            return "${sorted[0]}:${sorted[1]}"
        }
    }
}
