// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host tests for the consent gate + kill switch (ADR-017).
//
// BP-05 §2 states the required tests as "expired/revoked -> blocked". Those
// two are the easy ones. The ones below are here because they are the ways a
// consent check silently becomes decorative, and each is a failure mode that
// passes a naive "does it deny the obvious case" test:
//
//   - a check that fails OPEN when no cert exists
//   - a grant that reaches BACKWARDS and authorises data sent before it
//   - a revocation that a later grant resurrects
//   - a grant for CALL quietly authorising PHOTO
//   - a grant from the wrong party being honoured
//
// Every id here is synthetic (DAD_TEST / KID_TEST). No real UID, no real
// child data, nothing tied to a device.
package com.calldad

import com.calldad.consent.ConsentCert
import com.calldad.consent.ConsentDecision
import com.calldad.consent.ConsentDenial
import com.calldad.consent.ConsentGate
import com.calldad.consent.ConsentScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateTest {

    private val dad = "DAD_TEST_UID"
    private val kid = "KID_TEST_UID"
    private val now = 1_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun cert(
        id: String = "CONSENT_TEST_1",
        scopes: Set<ConsentScope> = ConsentScope.entries.toSet(),
        grantedAt: Long = now - day,
        expiresAt: Long = now + day,
        grantor: String = dad,
        grantee: String = kid,
        revokedAt: Long? = null
    ) = ConsentCert(
        id = id,
        grantorUid = grantor,
        granteeUid = grantee,
        scopes = scopes,
        grantedAtMs = grantedAt,
        expiresAtMs = expiresAt,
        revokedAtMs = revokedAt
    )

    private fun denied(reason: ConsentDenial, certs: List<ConsentCert>, scope: ConsentScope) {
        val actual = ConsentGate.forAction(certs, kid, dad, scope, now)
        assertEquals("expected $reason, got $actual", ConsentDecision.Denied(reason), actual)
    }

    // ---- the happy path, so the denials below mean something ----

    @Test
    fun aLiveCertFromThePeerGrantsEveryScopeItCovers() {
        val c = cert(scopes = setOf(ConsentScope.CALL, ConsentScope.PTT))
        assertEquals(
            ConsentDecision.Granted,
            ConsentGate.forAction(listOf(c), kid, dad, ConsentScope.CALL, now)
        )
        assertEquals(
            ConsentDecision.Granted,
            ConsentGate.forAction(listOf(c), kid, dad, ConsentScope.PTT, now)
        )
    }

    // ---- BP-05's two named cases ----

    @Test
    fun expiredIsBlocked() {
        denied(ConsentDenial.EXPIRED, listOf(cert(expiresAt = now - 1L)), ConsentScope.CALL)
    }

    @Test
    fun revokedIsBlocked() {
        denied(
            ConsentDenial.REVOKED,
            listOf(cert(revokedAt = now - 1L)),
            ConsentScope.CALL
        )
    }

    @Test
    fun expiryIsExclusiveSoAnExactBoundaryIsBlocked() {
        // expiresAt == now is over, not live. Off-by-one here would leave a cert
        // usable for one extra instant forever.
        denied(ConsentDenial.EXPIRED, listOf(cert(expiresAt = now)), ConsentScope.CALL)
    }

    // ---- absence denies; the check must not fail open ----

    @Test
    fun noCertIsBlocked() {
        denied(ConsentDenial.NO_CERT, emptyList(), ConsentScope.CALL)
    }

    @Test
    fun aCertForSomeoneElseIsNotACertForThisKid() {
        denied(
            ConsentDenial.NO_CERT,
            listOf(cert(grantee = "SOMEONE_ELSE_TEST_UID")),
            ConsentScope.CALL
        )
    }

    // ---- the kill switch is sticky ----

    @Test
    fun aLaterGrantCannotResurrectARevokedOne() {
        // The kill switch is only trustworthy if revocation dominates. If a new
        // grant could override an old revocation, "revoke" would be a suggestion
        // and a kid could be re-exposed by any later grant.
        val revokedThenGranted = listOf(
            cert(id = "CONSENT_TEST_1", revokedAt = now - 1L, expiresAt = now + 10 * day),
            cert(id = "CONSENT_TEST_2", grantedAt = now - 1L, expiresAt = now + 10 * day)
        )
        denied(ConsentDenial.REVOKED, revokedThenGranted, ConsentScope.CALL)
    }

    @Test
    fun revocationBeatsExpiryInTheReportedReason() {
        // Both are true at once. Reporting EXPIRED would tell a grown-up the
        // grant simply lapsed when a deliberate act is what actually happened.
        denied(
            ConsentDenial.REVOKED,
            listOf(cert(expiresAt = now - 1L, revokedAt = now - 1L)),
            ConsentScope.CALL
        )
    }

    // ---- grants are prospective only ----

    @Test
    fun aGrantDoesNotReachBackwards() {
        denied(
            ConsentDenial.NOT_YET_GRANTED,
            listOf(cert(grantedAt = now + day, expiresAt = now + 2 * day)),
            ConsentScope.CALL
        )
    }

    @Test
    fun aGrantBecomesLiveAtItsInstantNotAfter() {
        assertEquals(
            ConsentDecision.Granted,
            ConsentGate.forAction(listOf(cert(grantedAt = now)), kid, dad, ConsentScope.CALL, now)
        )
    }

    // ---- scope is checked at the point of use ----

    @Test
    fun aCallGrantDoesNotAuthorisePhoto() {
        denied(
            ConsentDenial.SCOPE_NOT_GRANTED,
            listOf(cert(scopes = setOf(ConsentScope.CALL))),
            ConsentScope.PHOTO
        )
    }

    @Test
    fun aCallGrantDoesNotAuthoriseText() {
        denied(
            ConsentDenial.SCOPE_NOT_GRANTED,
            listOf(cert(scopes = setOf(ConsentScope.CALL, ConsentScope.PTT))),
            ConsentScope.TEXT
        )
    }

    @Test
    fun anEmptyScopeSetGrantsNothing() {
        denied(
            ConsentDenial.SCOPE_NOT_GRANTED,
            listOf(cert(scopes = emptySet())),
            ConsentScope.CALL
        )
    }

    // ---- party binding ----

    @Test
    fun aSelfIssuedGrantIsNotHonoured() {
        // Both sides being the same party is exactly the degenerate case a
        // consent check exists to exclude: nobody granted anybody anything.
        denied(
            ConsentDenial.WRONG_PARTY,
            listOf(cert(grantor = kid, grantee = kid)),
            ConsentScope.CALL
        )
    }

    @Test
    fun aGrantIssuedByAnUnknownPartyIsNotHonoured() {
        denied(
            ConsentDenial.WRONG_PARTY,
            listOf(cert(grantor = "STRANGER_TEST_UID")),
            ConsentScope.CALL
        )
    }

    // ---- malformed data is a writer bug, not consent ----

    @Test
    fun aCertBornExpiredIsRejected() {
        denied(
            ConsentDenial.EXPIRY_BEFORE_GRANT,
            listOf(cert(grantedAt = now, expiresAt = now)),
            ConsentScope.CALL
        )
    }

    @Test
    fun aBackwardsCertIsRejected() {
        denied(
            ConsentDenial.EXPIRY_BEFORE_GRANT,
            listOf(cert(grantedAt = now, expiresAt = now - day)),
            ConsentScope.CALL
        )
    }

    // ---- the decision surface itself ----

    @Test
    fun isGrantedIsTrueOnlyForGranted() {
        assertTrue(ConsentDecision.Granted.isGranted)
        assertFalse(ConsentDecision.Denied(ConsentDenial.REVOKED).isGranted)
    }

    @Test
    fun everyDenialReasonIsReachableAndCarriesNoFreeText() {
        // A denial reason ends up on a 6-year-old's screen, so it must be a
        // closed set with no way to smuggle a UID or timestamp through it.
        val reachable = ConsentDenial.entries.toSet()
        val exercised = reachable.filter { reason ->
            // Both must hold: the gate DENIES, and it denies with THIS reason.
            // Checking only the first would let a reason survive that the gate
            // can never actually produce -- an unreachable reason is a
            // misleading string on a child's screen.
            val d = ConsentGate.forAction(certsFor(reason), kid, dad, ConsentScope.CALL, now)
            d is ConsentDecision.Denied && d.reason == reason
        }.toSet()
        assertEquals(
            "every denial reason must be reachable AND reported. Unreachable: " +
                (reachable - exercised),
            reachable,
            exercised
        )
    }

    private fun certsFor(reason: ConsentDenial): List<ConsentCert> = when (reason) {
        ConsentDenial.NO_CERT -> emptyList()
        ConsentDenial.REVOKED -> listOf(cert(revokedAt = now - 1L))
        ConsentDenial.EXPIRED -> listOf(cert(expiresAt = now - 1L))
        ConsentDenial.SCOPE_NOT_GRANTED -> listOf(cert(scopes = emptySet()))
        // Issued by someone other than the paired peer, so WRONG_PARTY is the
        // reason the gate actually reports for a stranger-issued grant.
        ConsentDenial.WRONG_PARTY -> listOf(cert(grantor = "STRANGER_TEST_UID"))
        ConsentDenial.NOT_YET_GRANTED -> listOf(cert(grantedAt = now + day, expiresAt = now + 2 * day))
        ConsentDenial.EXPIRY_BEFORE_GRANT -> listOf(cert(grantedAt = now, expiresAt = now))
    }
}