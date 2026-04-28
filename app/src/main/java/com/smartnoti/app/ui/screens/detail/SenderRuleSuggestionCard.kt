package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 4.
 *
 * Notification-detail inline card that offers the user a one-tap
 * conversion of "지금 본 발신자" → SENDER rule attached to the PRIORITY
 * Category. Hosted as a single LazyColumn `item { }` directly above
 * `NotificationDetailReclassifyCta`; visibility is gated by
 * [SenderRuleSuggestionCardSpec.shouldShow] (caller's responsibility — when
 * `false`, the host should not emit this Composable at all).
 *
 * Visual rhythm matches [NotificationDetailReasonCard] — same surface
 * color, 20dp padding, Card defaults — so the suggestion does not read as
 * a foreign element on the Detail screen. A filled [Button] for accept and
 * an [OutlinedButton] for dismiss communicate the asymmetric intent
 * (accept is the recommended action; dismiss is opt-out).
 */
@Composable
internal fun SenderRuleSuggestionCard(
    title: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = SenderRuleSuggestionCardSpec.bodyFor(title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(SenderRuleSuggestionCardSpec.LABEL_ACCEPT, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(SenderRuleSuggestionCardSpec.LABEL_DISMISS, maxLines = 1)
                }
            }
        }
    }
}
