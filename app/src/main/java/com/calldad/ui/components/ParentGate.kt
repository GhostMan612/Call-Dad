// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/components/ParentGate.kt — grown-ups-only check before pairing
// Location: app/src/main/java/com/calldad/ui/components/ParentGate.kt
package com.calldad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Parent gate (RULES §1.7: contact changes are never kid self-service).
 * A two-digit multiplication a 6-year-old cannot guess, typed on the
 * number pad. Three wrong answers close the gate. No dependency, works on
 * phones with no screen lock (common on kid devices).
 */
@Composable
fun ParentGate(
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a = rememberSaveable { Random.nextInt(6, 10) }
    val b = rememberSaveable { Random.nextInt(12, 20) }
    var answer by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Grown-ups only",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pairing changes who this phone can call. What is $a × $b?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = wrong > 0,
            label = { Text(if (wrong > 0) "Not quite. Try again." else "Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GiantButton(
                label = "Cancel",
                icon = Icons.Filled.Close,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                minHeight = 96.dp,
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            GiantButton(
                label = "Continue",
                icon = Icons.Filled.Lock,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                minHeight = 96.dp,
                onClick = {
                    if (answer.toIntOrNull() == a * b) {
                        onUnlocked()
                    } else {
                        wrong += 1
                        answer = ""
                        if (wrong >= 3) onCancel()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
