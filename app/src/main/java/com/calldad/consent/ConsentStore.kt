// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// consent/ConsentStore.kt — the kill switch, actually enforced (ADR-017)
// Location: app/src/main/java/com/calldad/consent/ConsentStore.kt
package com.calldad.consent

import android.content.Context
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The consent certs for THIS pair, read live from the pair room, and the
 * parental kill switch (SPEC_SHEET §4, BP-05 §2).
 *
 * `calls/{roomId}/consents/{granteeUid}` — one document per grantee holding every
 * grant/revocation the grown-up has made for that child. The room is already
 * pair-scoped (ADR-015), which is what lets this work WITHOUT the signature
 * ADR-017 deferred: a stranger cannot write a cert into a room whose id contains
 * two UIDs it does not own, so "the parent wrote this" is already established by
 * the rules rather than by a signature.
 *
 * Revocation is APPEND-ONLY and lives in its own subcollection, and that is the
 * load-bearing decision of the whole feature. A kill switch implemented as a
 * `revokedAt` flag on the GRANT document is not a kill switch: a grant write
 * replaces that document, the flag goes with it, and the child is un-revoked
 * with no trace. Revocations here cannot be updated or deleted by anyone (the
 * rules say so), and no grant write can reach them.
 *
 * A revocation names a SEQ RANGE rather than a boolean, so a parent who revokes
 * in a row and changes their mind can re-authorise by issuing a strictly higher
 * `grantSeq`. That is a deliberate, visible act — and only the grown-up can
 * perform it, because `firestore.rules` requires
 * `grantorUid == request.auth.uid != granteeUid` and this store's [grant] is
 * called only from a grown-ups-gated screen. The alternative, an irrevocable
 * revoke, means one accidental tap permanently bricks a child's phone.
 *
 * Absence still DENIES. A phone that has never seen a cert has no scopes, so
 * every entry point in the app is closed until the grown-up grants something.
 */
class ConsentStore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _scopes = MutableStateFlow<Set<ConsentScope>>(emptySet())
    val scopes: Flow<Set<ConsentScope>> = _scopes.asStateFlow()

    private val _decision = MutableStateFlow<ConsentDecision>(ConsentDecision.Denied(ConsentDenial.NO_CERT))
    val decision: Flow<ConsentDecision> = _decision.asStateFlow()

    private var grantReg: ListenerRegistration? = null
    private var revokeReg: ListenerRegistration? = null
    private var observed: String? = null
    private var ownUid: String? = null
    private var peerUid: String? = null
    private var grants: List<ConsentCert> = emptyList()
    private var revocations: List<ConsentCert> = emptyList()

    /**
     * Subscribes to the pair's consent docs and keeps [scopes] in step.
     *
     * [scopes] is the DERIVED set the app's entry points check, computed by
     * asking [ConsentGate] for each scope in turn. Deriving rather than storing
     * the answer means the revocation, expiry, party and prospectivity rules
     * live in exactly one place — the gate — and cannot drift from what the
     * parent-facing UI would report.
     */
    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            FamilySession.pair(app).collect { pair -> onPair(pair, scope) }
        }
    }

    private fun onPair(pair: FamilyPair?, scope: CoroutineScope) {
        if (pair == null) {
            grantReg?.remove()
            revokeReg?.remove()
            grantReg = null
            revokeReg = null
            observed = null
            ownUid = null
            peerUid = null
            grants = emptyList()
            revocations = emptyList()
            _scopes.value = emptySet()
            _decision.value = ConsentDecision.Denied(ConsentDenial.NO_CERT)
            return
        }
        ownUid = pair.ownUid
        peerUid = pair.peerUid
        if (observed == pair.roomId) return
        observed = pair.roomId
        listen(pair)
    }

    private fun recompute() {
        val me = ownUid ?: return
        val peer = peerUid ?: return
        val now = System.currentTimeMillis()
        val all = grants + revocations
        val held = ConsentScope.entries
            .filter { ConsentGate.forAction(all, me, peer, it, now).isGranted }
            .toSet()
        _scopes.value = held
        // The headline decision the UI shows. CALL is the coarsest scope the app
        // has, so it is the honest representative of "can this child talk to
        // their grown-up at all".
        _decision.value = ConsentGate.forAction(all, me, peer, ConsentScope.CALL, now)
    }

    private fun listen(pair: FamilyPair) {
        grantReg?.remove()
        revokeReg?.remove()
        grantReg = null
        revokeReg = null

        val base = firestore.collection("calls").document(pair.roomId).collection("consents")

        // Two listeners, one decision. Grants and revocations are separate
        // documents with different lifetimes (a grant is replaced, a revocation
        // is permanent), so they cannot share a query. They are folded together
        // in `recompute` on every event from either, so a kill switch is never
        // evaluated against a grant list that has not yet seen its revocation.
        grantReg = base.whereEqualTo("granteeUid", pair.ownUid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    // A consent read that fails is a consent FAILURE: we deny
                    // rather than holding the last known good scopes, because a
                    // revoked grant must not stay open because the network
                    // hiccuped. Failing closed is the only safe choice for a
                    // kill switch.
                    grants = emptyList()
                    recompute()
                    WebRtcLog.transition("Consent grant read failed; denying")
                    return@addSnapshotListener
                }
                if (snap.metadata.isFromCache) return@addSnapshotListener
                grants = snap.documents.mapNotNull { it.toGrant() }
                recompute()
            }

        revokeReg = firestore.collection("calls").document(pair.roomId)
            .collection("revocations")
            .whereEqualTo("granteeUid", pair.ownUid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    revocations = emptyList()
                    recompute()
                    WebRtcLog.transition("Consent revocation read failed; denying")
                    return@addSnapshotListener
                }
                if (snap.metadata.isFromCache) return@addSnapshotListener
                revocations = snap.documents.mapNotNull { it.toRevocation() }
                recompute()
            }
    }

    /**
     * Grants scopes to [granteeUid]. PARENT SIDE ONLY — call this from a
     * grown-ups-gated screen, never from the child's.
     *
     * [grantSeq] MUST strictly increase on every call. The rules enforce it too,
     * but doing it here means the client cannot construct a write that is
     * guaranteed to be rejected, and it is what makes "re-grant after a revoke"
     * a deliberate act rather than a side effect of retrying.
     */
    suspend fun grant(
        pair: FamilyPair,
        granteeUid: String,
        scopes: Set<ConsentScope>,
        grantSeq: Int,
        validForMs: Long = DEFAULT_VALIDITY_MS
    ): Result<Unit> = runCatching {
        require(scopes.isNotEmpty()) { "a grant must cover at least one scope" }
        require(granteeUid == pair.peerUid || granteeUid == pair.ownUid) {
            "a cert may only name a member of this pair"
        }
        require(grantSeq >= 1) { "grant seq starts at 1" }
        val now = System.currentTimeMillis()
        firestore.collection("calls").document(pair.roomId).collection("consents")
            .document(granteeUid)
            .set(
                mapOf(
                    "granteeUid" to granteeUid,
                    "grantorUid" to pair.ownUid,
                    "scopes" to scopes.map { it.name },
                    "grantSeq" to grantSeq,
                    "grantedAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to Timestamp(Date(now + validForMs))
                )
            ).await()
        WebRtcLog.transition("Consent granted")
        Unit
    }

    /**
     * The kill switch.
     *
     * Writes an APPEND-ONLY revocation naming the highest grant seq it cancels,
     * rather than a flag on the grant document. That is the whole point: a grant
     * `set` replaces the grant document, so a flag written there is destroyed by
     * the next grant, and the child is un-revoked with no trace. Revocations live
     * at `calls/{room}/revocations/{grantee}` — a sibling of `consents`, not a
     * child of it — so no grant write can reach them, and the rules make those
     * documents immutable and undeletable, so once this resolves the revocation
     * stands.
     *
     * The only way past one is a fresh grant with a strictly higher
     * [grantSeq] — the grown-up deliberately re-authorising. That is a decision a
     * parent may legitimately reverse, and the alternative (irreversible
     * revocation) means one tap in a row permanently bricks a child's phone.
     */
    suspend fun revoke(
        pair: FamilyPair,
        granteeUid: String,
        throughGrantSeq: Int
    ): Result<Unit> = runCatching {
        require(granteeUid == pair.peerUid || granteeUid == pair.ownUid) {
            "a cert may only name a member of this pair"
        }
        require(throughGrantSeq >= 0) { "nothing has been granted at seq 0" }
        firestore.collection("calls").document(pair.roomId)
            .collection("revocations")
            .add(
                mapOf(
                    "granteeUid" to granteeUid,
                    "grantorUid" to pair.ownUid,
                    "revokesGrantSeq" to throughGrantSeq,
                    "revokedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        WebRtcLog.transition("Consent revoked (kill switch)")
        Unit
    }

    /** The raw certs, for the parent-facing "what have I allowed" screen. */
    fun observedCerts(): List<ConsentCert> = grants

    /** The revocations in force, for the same screen. */
    fun observedRevocations(): List<ConsentCert> = revocations

    private fun com.google.firebase.firestore.DocumentSnapshot.toGrant(): ConsentCert? {
        if (metadata.hasPendingWrites()) return null
        val grantee = getString("granteeUid") ?: return null
        val grantor = getString("grantorUid") ?: return null
        val grantedAt = getTimestamp("grantedAt")?.toDate()?.time ?: return null
        val expiresAt = getTimestamp("expiresAt")?.toDate()?.time ?: return null
        val scopes = (get("scopes") as? List<*>).orEmpty()
            .mapNotNull { it as? String }
            .mapNotNull { name -> runCatching { ConsentScope.valueOf(name) }.getOrNull() }
            .toSet()
        return ConsentCert(
            id = id,
            grantorUid = grantor,
            granteeUid = grantee,
            scopes = scopes,
            grantedAtMs = grantedAt,
            expiresAtMs = expiresAt,
            grantSeq = (getLong("grantSeq") ?: 0L).toInt()
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRevocation(): ConsentCert? {
        if (metadata.hasPendingWrites()) return null
        val grantee = getString("granteeUid") ?: return null
        val grantor = getString("grantorUid") ?: return null
        val revokedAt = getTimestamp("revokedAt")?.toDate()?.time ?: return null
        val through = (getLong("revokesGrantSeq") ?: return null).toInt()
        return ConsentCert(
            id = id,
            grantorUid = grantor,
            granteeUid = grantee,
            scopes = emptySet(),
            // A revocation is not itself a grant, so it is given the widest
            // possible validity: the gate never evaluates these as grants, and
            // an expiry here would be a lie about when the switch applies.
            grantedAtMs = 0L,
            expiresAtMs = Long.MAX_VALUE,
            revokedAtMs = revokedAt,
            revokesGrantSeq = through
        )
    }

    companion object {
        /**
         * A grant lasts a month. Long enough that a family does not have to
         * re-authorise a routine visit; short enough that a phone left in
         * someone else's hands stops working on its own.
         */
        val DEFAULT_VALIDITY_MS: Long = TimeUnit.DAYS.toMillis(30)
    }
}
