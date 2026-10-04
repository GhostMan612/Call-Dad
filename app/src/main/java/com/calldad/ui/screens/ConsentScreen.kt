// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/ConsentScreen.kt — the parent-side grant/revoke (BP-05 §2, ADR-017)
// Location: app/src/main/java/com/calldad/ui/screens/ConsentScreen.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.consent.ConsentScope
import com.calldad.consent.ConsentStore
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.ui.components.ParentGate
import com.calldad.ui.theme.HangUpRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ConsentUiState(
    val pair: FamilyPair? = null,
    val held: Set<ConsentScope> = emptySet(),
    val highestSeq: Int = 0,
    val isWorking: Boolean = false,
    val message: String? = null
)

class ConsentViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ConsentStore()

    private val _state = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = _state.asStateFlow()

    init {
        store.start(application, viewModelScope)
        viewModelScope.launch {
            FamilySession.pair(getApplication()).collect { p ->
                _state.value = _state.value.copy(pair = p)
            }
        }
        viewModelScope.launch {
            // Merge the derived scopes with the cert tick. They are two different
            // signals and BOTH are required: `scopes` carries what is permitted,
            // while the tick fires when a cert changes without changing the
            // derived set — which is every renewal on the grantor side, since the
            // grantor's scope set is always "everything" from the moment it
            // becomes the grantor. Watching only `scopes` left `highestSeq`
            // frozen at its first value, so the second "Allow everything"
            // rewrote seq 1 over seq 1 and the rules denied it.
            combine(store.scopes, store.certTick) { held, _ -> held }
                .collect { held ->
                    _state.value = _state.value.copy(
                        held = held,
                        highestSeq = store.highestObservedSeq()
                    )
                }
        }
    }

    /** Grants exactly [scopes], with a sequence above every existing grant. */
    fun grant(scopes: Set<ConsentScope>) = act {
        val p = requirePair() ?: return@act
        val next = _state.value.highestSeq + 1
        store.grant(p, p.peerUid, scopes, grantSeq = next)
            .onSuccess {
                _state.value = _state.value.copy(
                    message = "Allowed for the next month."
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    message = "Couldn't save that. Check the connection and try again."
                )
            }
    }

    /**
     * The kill switch. Revokes through the HIGHEST sequence seen, so every
     * existing grant is cancelled in one append-only write — and a later grant
     * must carry a higher sequence to take effect again.
     *
     * REFUSES to act when no grant has been observed yet. `grants` is populated
     * only from a non-cached snapshot, so on a cold start — or on the flaky
     * network a parent reaches for the kill switch *because of* — `highestSeq` is
     * 0, and a revocation of seq 0 cancels nothing while the UI cheerfully
     * reports "Turned off." That is precisely the "a kill switch believed to be
     * off and is not" failure this screen exists to prevent, and it is the worst
     * possible moment to invent it: a parent in a hurry, on bad signal, trying to
     * stop something.
     */
    fun revoke() = act {
        val p = requirePair() ?: return@act
        val through = _state.value.highestSeq
        if (through < 1) {
            _state.value = _state.value.copy(
                message = "Can't turn it off yet — still checking. Try again in a moment."
            )
            return@act
        }
        store.revoke(p, p.peerUid, throughGrantSeq = through)
            .onSuccess {
                _state.value = _state.value.copy(
                    message = "Turned off. It stays off until you allow it again."
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    message = "Couldn't turn it off. Check the connection and try again."
                )
            }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private fun requirePair(): FamilyPair? {
        val p = _state.value.pair
        if (p == null) {
            _state.value = _state.value.copy(message = "Pair the phones first.")
        }
        return p
    }

    private fun act(block: suspend () -> Unit) {
        _state.value = _state.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            block()
            _state.value = _state.value.copy(isWorking = false)
        }
    }
}

class ConsentViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ConsentViewModel(application) as T
}

@Composable
private fun rememberConsentViewModel(): ConsentViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = ConsentViewModelFactory(activity.application)
    )
}

/**
 * The parent-side consent controls.
 *
 * ## Why this screen is behind the gate, and why that is the whole design
 *
 * `BP-05 §2` is "revocation blocks all comms", and a revocation only means
 * something if a GROWN-UP can perform it. So this screen is the kill switch's
 * only trigger, and it is unreachable from the child's flow: the destination is
 * inside a [ParentGate], and `unlocked` is `remember` — not `rememberSaveable` —
 * so a process death while on this screen restores the child to the GATE, never
 * into the controls. That was already the pattern for pairing and it is the
 * pattern here, for the same reason: the gate must not survive a restart.
 *
 * ## The two-button shape is deliberate
 *
 * "Allow everything" and "Turn everything off" are the only two actions. NOT a
 * per-scope checkbox grid, even though the domain supports `[call,text,photo]`
 * independently. A parent standing on a child's phone in a hurry needs two
 * unambiguous choices; four checkboxes is how you ship a consent screen nobody
 * reads, and a half-read consent screen is worse than a coarse one. Scope is
 * still real underneath — `ConsentScope` decides what each grant authorises, and
 * the emulator suite pins that a CALL grant does not authorise PHOTO.
 *
 * ## The revoke wording is a promise the code keeps
 *
 * "It stays off until you allow it again" is literally true: revocation is an
 * append-only document nothing can edit or delete (ADR-017), and re-allowing
 * requires a strictly higher sequence. An earlier draft said "turned off" and
 * implied a toggle; a parent who believes it is a toggle will not check whether
 * it took effect, and a kill switch that is believed to be off and is not is the
 * exact failure this feature exists to prevent.
 */
@Composable
fun ConsentScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsentViewModel = rememberConsentViewModel()
) {
    var unlocked by remember { mutableStateOf(false) }
    if (!unlocked) {
        ParentGate(
            onUnlocked = { unlocked = true },
            onCancel = onBackHome
        )
        return
    }
    ConsentContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onGrant = { viewModel.grant(ConsentScope.entries.toSet()) },
        onRevoke = viewModel::revoke,
        onAcknowledge = viewModel::clearMessage,
        onBackHome = onBackHome,
        modifier = modifier
    )
}

@Composable
private fun ConsentContent(
    state: ConsentUiState,
    onGrant: () -> Unit,
    onRevoke: () -> Unit,
    onAcknowledge: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackHome, modifier = Modifier.size(96.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to home",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "Grown-ups",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                // "this phone" was wrong on the child device, which is a TABLET (Q8K)
                // as of 2026-10-03. This screen is the grown-ups' own, so getting
                // the noun wrong here is exactly what makes them doubt they are on
                // the right device.
                text = "What is allowed on this device",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Calls, video, messages, the walkie talkie and photos. " +
                    "Nothing is allowed until you allow it here, and nothing can " +
                    // Two more "phone" literals, found by KidNamesRegressionTest rather than by
                    // eye: the child device is a 600x1024 TABLET, and this is the
                    // grown-ups' own screen, so a wrong noun here is what makes
                    // someone doubt they are holding the right device.
                    "reach anyone but the one paired device.",
                style = MaterialTheme.typography.titleMedium
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.held.isEmpty()) {
                            "Currently: nothing"
                        } else {
                            "Currently: ${state.held.joinToString { it.name.lowercase() }}"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (state.pair == null) {
                            "Not paired to a device yet."
                        } else {
                            "Allowances last 30 days."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // A result banner, so a parent who taps "Turn everything off" can SEE
            // that it took effect instead of assuming. That makes it an
            // interactive target, not a label, so it carries the same 96dp floor
            // as the buttons rather than the 48dp a decorative card would need.
            state.message?.let { m ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAcknowledge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onGrant,
                enabled = !state.isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
            ) {
                Text("Allow everything", style = MaterialTheme.typography.headlineSmall)
            }

            OutlinedButton(
                onClick = onRevoke,
                enabled = !state.isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
            ) {
                Text(
                    "Turn everything off",
                    style = MaterialTheme.typography.headlineSmall,
                    color = HangUpRed
                )
            }
        }
    }
}
