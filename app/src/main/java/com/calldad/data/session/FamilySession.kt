// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/session/FamilySession.kt — who am I, who is my one contact, which room
// Location: app/src/main/java/com/calldad/data/session/FamilySession.kt
package com.calldad.data.session

import android.content.Context
import com.calldad.data.signaling.CallRoom
import com.calldad.pairing.SecurePeerStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The local UID plus the single paired peer, and the room they share. */
data class FamilyPair(val ownUid: String, val peerUid: String, val roomId: String)

object FamilySession {

    /** Emits the signed-in UID (null while signed out), live. */
    fun authUid(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /** Emits the current pair, or null when signed out or not paired yet. */
    fun pair(context: Context): Flow<FamilyPair?> =
        combine(authUid(), SecurePeerStore(context).observePeerUid()) { own, peer ->
            if (own == null || peer.isNullOrBlank()) return@combine null
            val room = CallRoom.idFor(own, peer) ?: return@combine null
            FamilyPair(own, peer, room)
        }.distinctUntilChanged()
}
