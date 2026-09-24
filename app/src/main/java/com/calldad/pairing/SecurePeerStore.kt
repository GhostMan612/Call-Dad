// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// pairing/SecurePeerStore.kt
// Location: app/src/main/java/com/calldad/pairing/SecurePeerStore.kt
package com.calldad.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.peerStore: DataStore<Preferences> by
    preferencesDataStore("peer_store")

/**
 * Plaintext peer persistence: the single allowlisted contact's UID.
 *
 * Tink AEAD is NOT used. The threat model for a two-person sideloaded
 * family app does not include rooted devices; allowBackup="false" and
 * data_extraction_rules.xml keep the store on this device. The peer is
 * written only after a mutual handshake (PairingViewModel).
 */
class SecurePeerStore(private val context: Context) {

    suspend fun storePeer(uid: String) {
        context.peerStore.edit { prefs ->
            prefs[PEER_UID] = uid
            prefs.remove(LEGACY_PEER_FCM)
            prefs[PAIRED_AT] = System.currentTimeMillis()
        }
    }

    fun observePeerUid(): Flow<String?> =
        context.peerStore.data.map { it[PEER_UID] }

    suspend fun clear() {
        context.peerStore.edit { it.clear() }
    }

    private companion object {
        val PEER_UID = stringPreferencesKey("peer_uid")
        val LEGACY_PEER_FCM = stringPreferencesKey("peer_fcm_token")
        val PAIRED_AT = longPreferencesKey("paired_at")
    }
}
