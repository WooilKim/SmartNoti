package com.smartnoti.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.IgnoreContainer
import com.smartnoti.app.ui.theme.IgnoreOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

/**
 * Notification card. Plan
 * `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` Task 5 added the
 * optional [onLongClick] + [isSelected] parameters so PriorityScreen can wire
 * its long-press → multi-select gesture without breaking the four other call
 * sites (Home, Inbox digest preview, Inbox hidden archive, Detail-related list)
 * that still want the simple tap-only behavior.
 *
 * - When [onLongClick] is non-null we attach `combinedClickable`; otherwise we
 *   keep the cheaper `clickable` so the rest of the app keeps the existing
 *   accessibility / ripple semantics.
 * - [isSelected] only paints an accent border — the selection state itself
 *   lives in [com.smartnoti.app.ui.screens.priority.PriorityScreenMultiSelectState].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationCard(
    model: NotificationUiModel,
    onClick: (String) -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
) {
    val (containerColor, baseBorderColor) = when (model.status) {
        NotificationStatusUi.PRIORITY -> PriorityContainer.copy(alpha = 0.42f) to PriorityOnContainer.copy(alpha = 0.35f)
        NotificationStatusUi.DIGEST -> DigestContainer.copy(alpha = 0.34f) to DigestOnContainer.copy(alpha = 0.28f)
        NotificationStatusUi.SILENT -> SilentContainer.copy(alpha = 0.92f) to SilentOnContainer.copy(alpha = 0.22f)
        // IGNORE rows surface only in the opt-in 무시됨 아카이브 (plan
        // `2026-04-21-ignore-tier-fourth-decision` Task 6). Neutral-gray token
        // at a lower opacity — the archive row should feel flatter than
        // SILENT without disappearing entirely.
        NotificationStatusUi.IGNORE -> IgnoreContainer.copy(alpha = 0.75f) to IgnoreOnContainer.copy(alpha = 0.20f)
    }

    // Plan `2026-04-26-priority-inbox-bulk-reclassify.md` Task 5 step 3 —
    // selected rows in PriorityScreen multi-select mode draw an accent
    // border so the user can see what is in their bulk action set.
    val selectionAccent = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) selectionAccent else baseBorderColor,
        animationSpec = tween(durationMillis = 200),
        label = "notificationCardBorder",
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick(model.id) },
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable { onClick(model.id) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    model.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    model.receivedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                model.sender ?: model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(model.status)
            ReasonChipRow(model.reasonTags)
        }
    }
}
