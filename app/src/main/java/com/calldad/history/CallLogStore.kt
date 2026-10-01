// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// history/CallLogStore.kt — DataStore persistence for the call log
// Location: app/src/main/java/com/calldad/history/CallLogStore.kt
package com.calldad.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.callLogStore: DataStore<Preferences> by
    preferencesDataStore("call_log")

/**
 * The call log, on-device only.
 *
 * Serialised as a ``-joined record list rather than one preference key per
 * column, because DataStore preferences are a flat map and a per-column layout
 * would need a migration every time a column is added. The wire format is
 * deliberately dull and versioned by [SCHEMA]: fields are positional, so an
 * unknown future field is skipped rather than shifting every later one.
 *
 * No encryption here, for the same reason as `SecurePeerStore`: a two-person
 * sideloaded family app on a non-rooted device with allowBackup="false" does not
 * have a realistic at-rest threat, and the content is a log of "we called, they
 * answered" with no message bodies. ADR-018 records when that changes.
 */
class CallLogStore(private val context: Context) {

    fun observe(): Flow<List<CallRecord>> =
        context.callLogStore.data.map { prefs ->
            decode(prefs[ROWS].orEmpty())
        }

    suspend fun record(record: CallRecord) {
        context.callLogStore.edit { prefs ->
            val rows = CallLog.add(decode(prefs[ROWS].orEmpty()), record)
            prefs[ROWS] = encode(rows)
        }
    }

    /** Dismisses the callback card without erasing history. */
    suspend fun dismissCallback(nowMs: Long) {
        context.callLogStore.edit { prefs ->
            val rows = CallLog.clearCallbacks(decode(prefs[ROWS].orEmpty()), nowMs)
            prefs[ROWS] = encode(rows)
        }
    }

    suspend fun clear() {
        context.callLogStore.edit { it.clear() }
    }

    private fun encode(rows: List<CallRecord>): String =
        rows.joinToString(SEP) { r ->
            listOf(
                r.id.replace(SEP, "_").replace(FIELD, "_"),
                r.startedAtMs.toString(),
                r.durationMs.toString(),
                r.outcome.name,
                if (r.wasOutgoing) "1" else "0"
            ).joinToString(FIELD)
        }

    private fun decode(raw: String): List<CallRecord> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEP).mapNotNull { line ->
            val f = line.split(FIELD)
            if (f.size < 5) return@mapNotNull null
            val outcome = runCatching { CallOutcome.valueOf(f[3]) }.getOrNull()
                ?: return@mapNotNull null
            CallRecord(
                id = f[0],
                startedAtMs = f[1].toLongOrNull() ?: return@mapNotNull null,
                durationMs = f[2].toLongOrNull() ?: return@mapNotNull null,
                outcome = outcome,
                wasOutgoing = f[4] == "1"
            )
        }
    }

    private companion object {
        /**
         * Bumped only if the field ORDER changes, so a future column lands in a
         * new preference key instead of silently shifting every existing row.
         */
        const val SCHEMA = 1
        val ROWS = stringPreferencesKey("rows_v$SCHEMA")

        /**
         * ASCII record (0x1E) and unit (0x1F) separators, built from Char() on
         * purpose. Written as `"\u001F"` they are one keystroke from being
         * stripped by an editor or a formatter, and a stripped escape turns
         * `split(SEP)` into `split("")`, which throws at runtime instead of
         * failing the build. Neither byte can occur in a generated id, so field
         * values need no escaping beyond the id itself.
         */
        val SEP: String = Char(0x1E).toString()
        val FIELD: String = Char(0x1F).toString()
    }
}
