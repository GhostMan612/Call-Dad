// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PhotoScreen.kt — the picture half of the thread (BP-04, §2.4)
// Location: app/src/main/java/com/calldad/ui/screens/PhotoScreen.kt
package com.calldad.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calldad.consent.ConsentScope
import com.calldad.photos.PhotoMessage
import com.calldad.ui.theme.ChatBubbleTheirs

/**
 * Pictures, in the same conversation as the messages.
 *
 * KID-SAFY, and the two rules that matter:
 *
 *  - **An unverified photo is never rendered.** [PhotoMessage.verified] gates the
 *    decode, and a failure shows SENTENCES. A truncated photo of a child rendered
 *    as if it were whole is worse than saying "that didn't arrive whole", and a
 *    parent has no other way to tell a corrupt transfer from a bug.
 *  - **Decode is off the main thread and cached.** A 1080px WEBP decode is
 *    ~20MB of ARGB; doing it in a composable body is an ANR on a cheap phone.
 *    So the bitmap is remembered per id and the failure path produces no bitmap
 *    at all.
 *
 * No save-to-gallery, no share, no link. There is nowhere in this screen for a
 * picture to become a file the device's gallery can show other apps, and
 * `ChatKidSafetyTest`'s "no ACTION_VIEW anywhere" rule is the reason.
 */
@Composable
fun PhotoScreen(
    onBackHome: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel = rememberPhotoViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackHome, modifier = Modifier.size(TOUCH_TARGET_DP.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to home",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Pictures",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            when {
                !state.isPaired ->
                    Notice("Pair the phones first. (Grown-ups: the gear button.)")
                ConsentScope.PHOTO !in state.scopes ->
                    Notice("Pictures are turned off right now. Ask a grown-up.")
                state.photos.isEmpty() -> Notice("No pictures yet.")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        PhotoRow(photo)
                    }
                }
            }

            state.problem?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::clearProblem)
                        .padding(8.dp)
                        .heightIn(min = TOUCH_TARGET_DP.dp)
                )
            }

            // The ONLY affordance. Capture is a separate, explicitly-launched
            // system activity handed back as a bitmap, so this screen never holds
            // a camera and a child can only reach the gallery by being handed a
            // pick from the grown-up's own flow.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isSending, onClick = onPickPhoto)
                    .heightIn(min = 120.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(
                        text = if (state.isSending) "Sending…" else "Send a picture",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(photo: PhotoMessage) {
    // Decoded ONCE per id, off the composition, and only for a verified photo.
    val bitmap: ImageBitmap? = remember(photo.id, photo.verified, photo.bytes?.size) {
        val bytes = photo.bytes
        if (!photo.verified || bytes == null) {
            null
        } else {
            // runCatching because decodeByteArray returns null on malformed
            // input, and an exception here would crash on a child's screen.
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull()?.asImageBitmap()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "A picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .background(ChatBubbleTheirs)
                    .padding(8.dp)
                    .semantics { contentDescription = "A picture from your grown-up" }
            )
        } else {
            // The failure path, and it says what to do rather than what went
            // wrong. A child cannot act on "checksum mismatch".
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            ) {
                Text(
                    text = photo.failure ?: "That picture didn't arrive.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }
}

private const val TOUCH_TARGET_DP = 96
