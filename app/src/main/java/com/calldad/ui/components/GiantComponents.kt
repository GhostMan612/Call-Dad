// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/components/GiantComponents.kt
// Location: app/src/main/java/com/calldad/ui/components/GiantComponents.kt
package com.calldad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The primary call-to-action tile used on Home.
 * Guarantees: >= 140dp tall, 72dp icon, whole surface is one single-tap target.
 */
@Composable
fun GiantActionCard(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .heightIn(min = 140.dp)
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(12.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/**
 * Wide, labelled action button. Minimum height is 100dp by default,
 * per the child-centric design directive.
 */
@Composable
fun GiantButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    minHeight: Dp = 100.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .shadow(elevation = if (enabled) 6.dp else 0.dp, shape = shape)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = contentColor
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

/** Square icon-only button. Used for "Home" escapes and the camera toggle. */
@Composable
fun GiantIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    size: Dp = 100.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = contentColor
        )
    }
}
