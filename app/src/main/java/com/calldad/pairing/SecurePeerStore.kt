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
 * Plaintext peer persistence.
 *
 * ARCHITECTURAL DECISION (Executor Override, Phase 11):
 *   Tink AEAD is NOT used. The threat model for a two-person
 *   sideloaded family app does not include rooted devices or adb
 *   backup.
 *
 * MITIGATIONS:
 *   - allowBackup="false" (blocks adb backup)
 *   - data_extraction_rules.xml (blocks D2D transfer on Android 12+)
 *   - pairing_token is NEVER persisted. Held in ViewModel memory only.
 */
class SecurePeerStore(private val context: Context) {

    suspend fun storePeer(uid: String, fcmToken: String) {
        context.peerStore.edit { prefs ->
            prefs[stringPreferencesKey("peer_uid")] = uid
            prefs[stringPreferencesKey("peer_fcm_token")] = fcmToken
            prefs[longPreferencesKey("paired_at")] =
                System.currentTimeMillis()
        }
    }

    fun observePeerUid(): Flow<String?> =
        context.peerStore.data.map {
            it[stringPreferencesKey("peer_uid")]
        }

    fun observePeerFcmToken(): Flow<String?> =
        context.peerStore.data.map {
            it[stringPreferencesKey("peer_fcm_token")]
        }

    suspend fun clear() {
        context.peerStore.edit { it.clear() }
    }
}
