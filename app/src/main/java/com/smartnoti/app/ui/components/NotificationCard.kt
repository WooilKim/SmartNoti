package com.smartnoti.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
    /**
     * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
     * finding F3 (orange accent restraint): when this card is rendered inside
     * a [DigestGroupCard] preview row, the per-row [StatusBadge] is
     * suppressed because the parent context (sub-tab + group header) already
     * declares status. Default `true` keeps the chip on for every standalone
     * call site (Home recent feed, IgnoredArchive, Detail-related list,
     * PriorityScreen) so cross-status feeds stay scannable.
     */
    showStatusBadge: Boolean = true,
    /**
     * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
     * finding F1 (cards-inside-cards collapse on inbox-unified Digest
     * sub-tab): when this card is rendered inside a parent group card the
     * outlined card surface (rounded outer container + status-tinted fill +
     * border) is suppressed so the parent card BE the only card boundary.
     * Preview rows render as flat row content; the caller provides the
     * divider/separation rhythm between rows. Default `false` keeps the
     * outlined surface for every standalone call site (Home recent feed,
     * IgnoredArchive, Detail-related list, PriorityScreen) so cross-status
     * feeds keep the per-row card affordance.
     */
    embeddedInCard: Boolean = false,
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

    val showSurface = notificationCardSurfaceVisible(embeddedInCard = embeddedInCard)

    if (showSurface) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(borderWidth, borderColor),
        ) {
            NotificationCardBody(
                model = model,
                showStatusBadge = showStatusBadge,
                contentPadding = PaddingValues(16.dp),
            )
        }
    } else {
        // Plan
        // `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
        // finding F1: embedded preview rows render as flat row content
        // (no Card surface, no status-tinted fill, no per-row border) so
        // the parent group card BE the only card boundary. The caller
        // (DigestGroupCard) provides the divider rhythm between rows. We
        // keep the same `clickModifier` so tap-to-Detail and PriorityScreen
        // multi-select long-press semantics carry over unchanged.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier),
        ) {
            NotificationCardBody(
                model = model,
                showStatusBadge = showStatusBadge,
                contentPadding = PaddingValues(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun NotificationCardBody(
    model: NotificationUiModel,
    showStatusBadge: Boolean,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier.padding(contentPadding),
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
        if (notificationCardStatusBadgeVisible(
                status = model.status,
                showStatusBadge = showStatusBadge,
            )
        ) {
            StatusBadge(model.status)
        }
        ReasonChipRow(model.reasonTags)
    }
}

/**
 * Pure visibility decision for the per-row [StatusBadge] inside
 * [NotificationCard].
 *
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F3** (orange accent restraint on inbox-unified). Extracted so the contract
 * "default on, callers opt out for nested-context rows" is unit-pinned by
 * [NotificationCardStatusBadgeVisibilityTest] without spinning up a Compose
 * runtime — mirrors the codebase pattern used by [digestGroupCardPreviewState].
 *
 * Today the only opt-out call site is [DigestGroupCard]'s preview rows, but
 * the helper is `status`-aware so a future caller (e.g. a hidden-group inline
 * preview) can extend the rule status-by-status without breaking the
 * standalone defaults.
 */
internal fun notificationCardStatusBadgeVisible(
    @Suppress("UNUSED_PARAMETER") status: NotificationStatusUi,
    showStatusBadge: Boolean,
): Boolean {
    // `status` is intentionally part of the signature so a future per-status
    // carve-out (e.g. always show PRIORITY chip even when caller opts out) is
    // a one-line change without a call-site refactor.
    return showStatusBadge
}

/**
 * Pure visibility decision for the outlined card surface (rounded outer
 * container + status-tinted fill + border) wrapped around the
 * [NotificationCard] body.
 *
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F1** (cards-inside-cards collapse on inbox-unified Digest sub-tab).
 * Extracted so the contract "default on, callers opt out for nested-context
 * rows" is unit-pinned by [NotificationCardSurfaceVisibilityTest] without
 * spinning up a Compose runtime — mirrors the codebase pattern used by
 * [notificationCardStatusBadgeVisible] and [digestGroupCardPreviewState].
 *
 * Today the only opt-out call site is [DigestGroupCard]'s preview rows; a
 * future caller (e.g. a hidden-group inline preview) can flip the same flag
 * without touching the renderer.
 */
internal fun notificationCardSurfaceVisible(embeddedInCard: Boolean): Boolean =
    !embeddedInCard
