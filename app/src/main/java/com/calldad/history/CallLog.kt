// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// history/CallLog.kt — the call history, and the "Call back" card (BP-05 §4)
// Location: app/src/main/java/com/calldad/history/CallLog.kt
package com.calldad.history

/**
 * Call history and the missed-call callback (SPEC_SHEET §2.5, BP-05 §4).
 *
 * Pure domain, so the rules are host-testable. This closes BP-05 §4's last open
 * criterion, "missed-call callback card", which the kid-UX audit had been
 * recording as a genuine FAIL rather than an artefact.
 *
 * WHY THIS EXISTS AT ALL. The product is for infrequent scheduled visits, which
 * makes a missed call the NORMAL case rather than the exception: the whole point
 * of the app is "later today", and a 6-year-old who taps Call, gets no answer,
 * and has no way to try again has learned that the button does not always work.
 * That is the one lesson this app cannot afford to teach, because the button
 * working is the entire product promise. So a missed call surfaces a card on
 * Home with a single obvious action.
 *
 * STORAGE. DataStore preferences, not Room. SPEC_SHEET §2.5 says "local Room",
 * and the toolchain deliberately has no Room, KSP, or SQLCipher (ADR-004,
 * AGENTS.md "Not in the build"). This file plus [CallLogStore] is the ADR-018
 * answer to that: a call log is a bounded, append-mostly list of small records
 * with no joins, no ad-hoc queries, and no relational integrity to protect, and
 * a child's phone holding 100 rows of {when, who, outcome, seconds} is a few
 * kilobytes. Room would add a compiler plugin, a schema, a migration story, and
 * an encryption question to store a log. The moment there is a real message
 * store — which BP-03's thread is NOT, it is pair-scoped in Firestore — that is
 * when a database earns its cost, and ADR-018 says so.
 *
 * PRIVACY. Nothing here leaves the device, and no row carries anything but an
 * enum outcome and a duration. No names, no numbers, no contact details: the
 * peer is a single allowlisted UID, so a row says "dad" by construction rather
 * than by a name the repo would have to invent.
 */
enum class CallOutcome {
    /** The call connected and either side hung up. */
    ANSWERED,

    /** It rang out. Nobody picked up. This is the callback case. */
    MISSED,

    /** The other side actively declined. */
    DECLINED,

    /** We gave up before it connected, or the transport failed. */
    FAILED;

    /**
     * Whether this outcome should raise a callback card.
     *
     * Only MISSED and FAILED. Not DECLINED: a grown-up who declined is telling
     * us they are busy, and putting "Call back" in front of a child who
     * immediately re-rings them is the app nagging on their behalf. Not ANSWERED
     * either. The card is for the one case where the child's tap produced
     * nothing and they deserve a way to try again.
     */
    val wantsCallback: Boolean get() = this == MISSED || this == FAILED
}

data class CallRecord(
    val id: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val outcome: CallOutcome,
    val wasOutgoing: Boolean,
    /**
     * When the callback card for THIS row was acted on, or null.
     *
     * The card is suppressed once this is set, and the row itself is kept. A
     * separate flag rather than a deletion because a call log that erases itself
     * when you look at it is not a call log — see [dismissCallback].
     */
    val dismissedAtMs: Long? = null
) {
    val isCallbackCandidate: Boolean get() = outcome.wantsCallback
}

/** Pure rules over the log. */
object CallLog {

    /**
     * How many rows are kept. A visit schedule means a handful of calls a month,
     * so 100 is years of history on a device that will be replaced long before
     * it fills, and the bound is what makes a preference list the right storage.
     */
    const val MAX_ROWS = 100

    /** Newest first, id as the tiebreak so equal timestamps cannot flap. */
    fun ordered(rows: List<CallRecord>): List<CallRecord> =
        rows.sortedWith(compareByDescending<CallRecord> { it.startedAtMs }.thenBy { it.id })

    /** Adds a row, trims to [MAX_ROWS], and de-duplicates by id. */
    fun add(rows: List<CallRecord>, record: CallRecord): List<CallRecord> {
        val kept = rows.filterNot { it.id == record.id }
        return ordered(kept + record).take(MAX_ROWS)
    }

    /**
     * The callback card, or null when there is nothing to offer.
     *
     * Only the MOST RECENT callback candidate, ever. Two cards would mean
     * choosing between two moments ("did I call and not get through?"), which a
     * 6-year-old cannot do and should not be asked to. One card, one action.
     *
     * A candidate is suppressed once it is older than [CARD_WINDOW_MS]: a card
     * for a call from last week is not actionable, and a Home screen that still
     * says "Dad didn't answer" a week later reads as the app being broken
     * rather than as history.
     */
    fun callbackCard(
        rows: List<CallRecord>,
        nowMs: Long
    ): CallRecord? = ordered(rows)
        .firstOrNull { it.isCallbackCandidate && it.dismissedAtMs == null }
        ?.takeIf { nowMs - it.startedAtMs <= CARD_WINDOW_MS }

    /**
     * Marks a callback card as DISMISSED, keeping the row.
     *
     * This used to be called `clearCallbacks` and it DELETED the rows — while its
     * own KDoc promised "Dismisses the callback card without erasing history" and
     * `CallLogStore` promised "History stays; only the prompt goes." The code and
     * its two promises disagreed, and the loser was the history: one tap on the
     * callback card would have silently removed every miss in the window from the
     * call log, and the parent would never know. It was latent only because the
     * card was never raised at all (the no-answer path bypassed the log funnel),
     * so fixing that bug would have activated this one.
     *
     * A stored timestamp of dismissal is the honest representation: the card
     * stops offering itself, the fact that the call was missed stays true.
     *
     * @param dismissedAtMs when the card was acted on. Rows the child has since
     *   seen a NEWER miss of are not affected.
     */
    fun dismissCallback(rows: List<CallRecord>, nowMs: Long): List<CallRecord> {
        val target = callbackCard(rows, nowMs) ?: return rows
        return rows.map { row ->
            if (row.id == target.id) row.copy(dismissedAtMs = nowMs) else row
        }
    }

    const val CARD_WINDOW_MS = 24L * 60L * 60L * 1000L
}
