// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// consent/ConsentCert.kt — consent domain model + kill switch (ADR-017)
//
// WHY THIS EXISTS. BP-05 §2 asks for "Dad-grants-Kid [call,text,photo] +
// expiry; revocation blocks all comms" and ARCHITECTURE.md:32 for a signed
// cert, prospective-by-default, self-revocation-only. Nothing existed.
//
// WHY IT IS NOT WIRED YET, stated here rather than buried: ADR-017 defers the
// SIGNATURE to a later decision, because a signing-key hierarchy with no
// operator custody story is a trust root, not a feature. This file is the
// pure domain half — grant/revoke/expiry/scope — with every rule host-tested
// against synthetic ids only. Nothing calls it yet. RULES §1.7's allowlist is
// currently enforced by the pair-scoped Firestore rules (ADR-015), which is a
// real but coarser boundary: one contact, whole-app scope.
//
// THE ONE RULE THAT MATTERS MOST: absence of a grant DENIES. An
// allowlist-only app whose consent check fails open is not allowlist-only, so
// every entry point here returns DENIED unless a live cert explicitly covers
// the requested scope.
package com.calldad.consent

/** What a grant authorises. Narrower than the app's whole surface. */
enum class ConsentScope {
    /** Audio+video calling, both directions. */
    CALL,
    /** 1:1 text chat (BP-03). Not shipped. */
    TEXT,
    /** Photo sharing (BP-04). Not shipped. */
    PHOTO,
    /** Push-to-talk voice clips. ALREADY SHIPPED without a cert. */
    PTT
}

/**
 * A consent decision, and the kid-safe reason for it.
 *
 * The reason is an enum on purpose. [CallViewModel.fail] already refuses to put a
 * raw throwable or Firestore string on a child's screen because it is both a UX
 * failure and a small information leak; the same rule applies here, so a denied
 * decision can never put a UID, a timestamp, or a cert id in front of a
 * 6-year-old.
 */
enum class ConsentDenial {
    /** No cert exists at all. Denied by default, never by omission. */
    NO_CERT,

    /** The cert exists but [ConsentCert.revokedAtMs] is set. */
    REVOKED,

    /** [ConsentCert.expiresAtMs] is at or before now. */
    EXPIRED,

    /** The cert does not cover the requested scope. */
    SCOPE_NOT_GRANTED,

    /** The cert was granted by or to someone other than this device's peer. */
    WRONG_PARTY,

    /** The call happened before the grant existed. Grants are prospective only. */
    NOT_YET_GRANTED,

    /** expiresAt <= grantedAt. A cert that is born expired is a bug, not consent. */
    EXPIRY_BEFORE_GRANT
}

/**
 * One consent grant. Immutable: revocation produces a new value rather than
 * mutating this one, and [ConsentGate] folds them with the rules below.
 *
 * @property id synthetic or Firestore doc id; never shown to a child.
 * @property grantorUid who granted them. Must be the peer for this to apply.
 * @property granteeUid the device being granted rights.
 * @property scopes what it authorises.
 * @property grantSeq monotonically increasing per grantee. A revocation names
 *   the highest seq it cancels, so "revoke everything up to now" is expressible
 *   without ever rewriting a grant. See [ConsentGate] and the class doc.
 * @property grantedAtMs when the grant takes effect. PROSPECTIVE: nothing is
 *   authorised before this instant.
 * @property expiresAtMs when it lapses. Must be strictly after [grantedAtMs].
 * @property revokedAtMs set only on a REVOCATION cert ([revokesGrantSeq] is
 *   non-null). Null on a grant.
 * @property revokesGrantSeq on a revocation: every grant with
 *   `grantSeq <= revokesGrantSeq` is dead. Null on a grant.
 * @property signedBy ADR-017 decision 1: the signature is deferred, so this is
 *   null for every cert the app can currently produce. Non-null is reserved for a
 *   future signed-capable grant, and is checked there rather than here so this
 *   model stays free of crypto.
 */
data class ConsentCert(
    val id: String,
    val grantorUid: String,
    val granteeUid: String,
    val scopes: Set<ConsentScope>,
    val grantedAtMs: Long,
    val expiresAtMs: Long,
    val revokedAtMs: Long? = null,
    val signedBy: String? = null,
    val grantSeq: Int = 0,
    val revokesGrantSeq: Int? = null
) {
    /** A revocation cancels a grant range; it grants nothing itself. */
    val isRevocation: Boolean get() = revokesGrantSeq != null
}

/**
 * Evaluates a set of certs against one requested action. Pure and total: every
 * input yields a decision, and no path throws.
 *
 * The multi-cert shape is deliberate. A revocation does not erase the revoked
 * grant (that would make the kill switch depend on a delete, and a delete can
 * arrive late or never); it adds a second, later cert. [forAction] must consider
 * ALL of them and treat any revocation covering the grantee as authoritative.
 *
 * ## Why revocation is a RANGE and not a flag
 *
 * The first version marked a cert `revokedAtMs` on the GRANT document and
 * treated any revocation as final forever. That is broken in two directions at
 * once, and both showed up as emulator failures:
 *
 *  - A flag ON THE GRANT doc means the kill switch lives or dies on a single
 *    mutable write. A revocation written as a merge can be lost, arrive late, or
 *    be dropped by a concurrent grant write that replaces the document — and
 *    then the child is un-revoked with no trace of why. This was not
 *    hypothetical: a re-grant's `set` dropped the revocation, and the rules test
 *    caught the revocation being denied outright for a different reason.
 *  - Making it truly un-overridable, though, means one accidental tap bricks the
 *    child's phone permanently. A parent who revoked in a row and changed their
 *    mind has no way back, which is its own kind of harm.
 *
 * So revocation names a SEQ RANGE. Grants carry a monotonic
 * [ConsentCert.grantSeq]; revocations are separate append-only documents
 * carrying [ConsentCert.revokesGrantSeq]. A revocation cannot be lost by a later
 * grant write because it lives where no grant write reaches, and a parent CAN
 * re-authorise — but only by issuing a strictly higher seq, which is a
 * deliberate, visible act rather than an accident of ordering.
 *
 * The threat this is built against is the CHILD self-authorising, and that is
 * closed unconditionally elsewhere: `firestore.rules` requires
 * `grantorUid == request.auth.uid != granteeUid`, and the gate independently
 * requires the grantor to be the paired peer. So "a later grant resurrected it"
 * can only ever mean "the grown-up deliberately re-authorised", which is exactly
 * when it should.
 */
object ConsentGate {

    /**
     * Decides whether [actorUid] — the device about to act — may perform
     * [scope] at [nowMs], given that [peerUid] is the only phone it is paired
     * with.
     *
     * Both uids are required, and both are checked, because consent in a
     * two-person app is only meaningful when it binds BOTH sides: the cert must
     * be held BY the actor (that is who it authorises) and must have been
     * issued BY the paired peer. Checking only one is how a self-issued or
     * stranger-issued cert slips through — "the kid consented" proves nothing if
     * the kid is also the one who wrote the grant. Both bad cases therefore
     * share [ConsentDenial.WRONG_PARTY] rather than each needing a reason,
     * because a 6-year-old is not helped by learning which of the two it was.
     *
     * Evaluation order is fixed and each step has a distinct reason, because a
     * kid-facing denial has to say one specific thing:
     *
     *  1. no cert names the actor as grantee -> NO_CERT
     *  2. a malformed expiry on a grant -> EXPIRY_BEFORE_GRANT (a bug in the
     *     writer, and treating it as merely "expired" would hide the bug)
     *  3. nothing issued by the paired peer -> WRONG_PARTY
     *  4. every grant is cancelled by a revocation, or every grant is still in
     *     the future -> REVOKED / NOT_YET_GRANTED. Revocation is reported ahead
     *     of expiry, because a kill switch that says "expired" is misleading: a
     *     deliberate act is what actually happened.
     *  5. expiry not after now -> EXPIRED
     *  6. scope absent -> SCOPE_NOT_GRANTED
     *
     * Otherwise [ConsentDecision.GRANTED].
     */
    fun forAction(
        certs: List<ConsentCert>,
        actorUid: String,
        peerUid: String,
        scope: ConsentScope,
        nowMs: Long
    ): ConsentDecision {
        // Held by the actor, whoever issued it. Revocation and malformed
        // expiry are evaluated over this set, not over the peer-issued subset,
        // so a revoked-but-misissued cert still reports REVOKED.
        val held = certs.filter { it.granteeUid == actorUid }
        if (held.isEmpty()) return ConsentDecision.Denied(ConsentDenial.NO_CERT)

        val grants = held.filterNot { it.isRevocation }
        if (grants.any { it.expiresAtMs <= it.grantedAtMs }) {
            return ConsentDecision.Denied(ConsentDenial.EXPIRY_BEFORE_GRANT)
        }

        // The highest cancelled seq from ANY revocation naming this actor. A
        // revocation is a RANGE, so this is a max() and not a membership test:
        // "revoked" is a property of the sequence, not of one document.
        //
        // The `it.revokesGrantSeq!!` is required, not a shortcut: the property is
        // nullable by design, and `maxOfOrNull { it.revokesGrantSeq }` is an
        // overload-resolution AMBIGUITY between Double/Float/Comparable rather
        // than a compile error, so the type has to be pinned to Int.
        val cancelledThrough = held
            .filter { it.isRevocation }
            .maxOfOrNull { it.revokesGrantSeq ?: Int.MIN_VALUE }
            ?: NOTHING_CANCELLED

        val valid = grants.filter { it.grantorUid == peerUid }
        if (valid.isEmpty()) {
            return ConsentDecision.Denied(
                if (cancelledThrough != NOTHING_CANCELLED) ConsentDenial.REVOKED
                else ConsentDenial.WRONG_PARTY
            )
        }

        // PROSPECTIVE, and among the certs that have already started, pick the
        // one that lasts longest rather than the first one found.
        //
        // `firstOrNull { it.grantedAtMs <= nowMs }` was wrong: after a renewal
        // the pair holds BOTH the expired original and the new cert, and the
        // reducer visits the older one first. The child is then told their
        // permission "expired" at the exact moment a grown-up renewed it — and
        // renewal is the one thing a parent does precisely to keep a kid
        // talking. `maxByOrNull` on expiry picks the grant that actually still
        // covers now, so a renewal is honoured the moment it starts and only
        // genuinely lapses when the LAST cert has lapsed.
        val started = valid.filter { it.grantedAtMs <= nowMs }
        if (started.isEmpty()) {
            return ConsentDecision.Denied(
                if (cancelledThrough != NOTHING_CANCELLED) ConsentDenial.REVOKED
                else ConsentDenial.NOT_YET_GRANTED
            )
        }

        // Survives only a revocation whose range covers its seq. A strictly
        // higher seq is the parent's deliberate re-authorisation.
        val live = started
            .filter { it.grantSeq > cancelledThrough }
            .maxByOrNull { it.expiresAtMs }
            ?: return ConsentDecision.Denied(ConsentDenial.REVOKED)

        if (live.expiresAtMs <= nowMs) {
            return ConsentDecision.Denied(ConsentDenial.EXPIRED)
        }

        if (scope !in live.scopes) {
            return ConsentDecision.Denied(ConsentDenial.SCOPE_NOT_GRANTED)
        }

        return ConsentDecision.Granted
    }

    /** Below any real seq, so an absent revocation cancels nothing. */
    private const val NOTHING_CANCELLED = Int.MIN_VALUE
}

/** Result of a consent check. [cert] is null on denial. */
sealed interface ConsentDecision {
    data object Granted : ConsentDecision

    data class Denied(val reason: ConsentDenial) : ConsentDecision

    val isGranted: Boolean get() = this is Granted
}