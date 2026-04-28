package com.smartnoti.app.ui.screens.inbox

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.HighVolumeAppCandidate
import com.smartnoti.app.ui.theme.BorderSubtle

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 6.
 *
 * Inbox `Digest` sub-tab inline-suggestion card for the noisiest source app.
 * Above the first DigestGroupCard. Three-button row (`예, 묶을게요` / `나중에` /
 * `무시`) maps 1:1 to [InboxSuggestionAcceptCallbacks]; the host (InboxScreen)
 * decides what each button does behind the surface.
 *
 * Visual rhythm follows [InboxCardLanguage.Primary] — same 16dp corner / 1dp
 * border / 16dp padding as the rest of the inbox-unified surfaces so the card
 * does not read as a foreign element. Source app icon uses the Issue #510
 * `AppIconResolver` (best-effort: passing `null` falls back to a generic
 * Notifications icon so the row layout stays stable).
 */
@Composable
fun InboxSuggestionCard(
    candidate: HighVolumeAppCandidate,
    sourceIcon: Bitmap?,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(InboxCardLanguage.CARD_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(InboxCardLanguage.CARD_BORDER_WIDTH_DP.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(InboxCardLanguage.CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceIconSlot(sourceIcon)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = InboxSuggestionCardSpec.eyebrowFor(candidate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = InboxSuggestionCardSpec.bodyFor(candidate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(0.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(InboxSuggestionCardSpec.LABEL_ACCEPT, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(InboxSuggestionCardSpec.LABEL_SNOOZE, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(InboxSuggestionCardSpec.LABEL_DISMISS, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SourceIconSlot(bitmap: Bitmap?) {
    val slot = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(8.dp))
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = slot,
        )
    } else {
        Box(
            modifier = slot,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
