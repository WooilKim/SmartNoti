package com.smartnoti.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.ui.screens.inbox.InboxCardLanguage
import com.smartnoti.app.ui.theme.BorderSubtle

/**
 * Maximum number of preview rows rendered inside a Digest group card before
 * the inline "전체 보기 · ${remaining}건 더" CTA gates the rest. Plan
 * `docs/plans/2026-04-26-inbox-bundle-preview-see-all.md` Task 2 — keeping
 * the constant file-private because no other component currently references
 * it.
 */
private const val PREVIEW_LIMIT = 3

@Composable
fun DigestGroupCard(
    model: DigestGroupUiModel,
    onNotificationClick: (String) -> Unit,
    collapsible: Boolean = false,
    bulkActions: (@Composable () -> Unit)? = null,
) {
    var expanded by rememberSaveable(model.id, collapsible) {
        mutableStateOf(!collapsible)
    }
    var showAll by rememberSaveable(model.id) {
        mutableStateOf(false)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "digestGroupCardChevronRotation",
    )

    val headerModifier = if (collapsible) {
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    } else {
        Modifier.fillMaxWidth()
    }

    val previewState = digestGroupCardPreviewState(
        itemsSize = model.items.size,
        showAll = showAll,
    )

    // Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
    // finding **F4** — "different card visual languages on one screen".
    // The outer DigestGroupCard surface is the [InboxCardLanguage.Subtle]
    // language: the surface-variant tint distinguishes a group container
    // from the [InboxCardLanguage.Primary] summary card without breaking
    // the shared rhythm (corner radius / border / padding all sourced from
    // the [InboxCardLanguage] constants). Same primitive is reused for
    // legacy `Routes.Digest` deep-link entries and for Hidden archived /
    // processed group cards (via [HiddenNotificationsScreen]).
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(InboxCardLanguage.CARD_CORNER_RADIUS_DP.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        border = BorderStroke(InboxCardLanguage.CARD_BORDER_WIDTH_DP.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(InboxCardLanguage.CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = headerModifier,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    model.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Plan
                    // `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
                    // finding F3: demote the count badge from a filled orange
                    // pill to a neutral outlined chip so the orange accent is
                    // reserved for selection / status signals (selected sub-tab
                    // indicator + the per-row [StatusBadge] on screens that
                    // actually need it). Style decisions live in the pure
                    // helper [digestGroupHeaderBadgeStyle] so a unit test can
                    // pin the contract without a Compose runtime.
                    val badgeStyle = digestGroupHeaderBadgeStyle()
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = BorderSubtle,
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            badgeStyle.formatCountLabel(model.count),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (collapsible) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (expanded) "접기" else "펼치기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }
                }
            }
            Text(
                model.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(
                        title = "최근 묶음 미리보기",
                        subtitle = "탭하면 원본 알림 상세를 확인할 수 있어요",
                    )
                    val previewItems = model.items.take(previewState.visibleCount)
                    previewItems.forEachIndexed { index, item ->
                        // Plan
                        // `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
                        // finding F3: hide the per-row orange `Digest` /
                        // `조용히 정리` StatusBadge inside group preview rows.
                        // The parent context (sub-tab + group card header)
                        // already declares status — repeating it on every
                        // preview row creates accent noise that competes with
                        // the actual content. Standalone NotificationCard
                        // call sites (Home, IgnoredArchive, Detail, Priority)
                        // keep the badge on by default.
                        //
                        // F1 (this PR): pass embeddedInCard = true so the
                        // preview row drops its own outlined card surface —
                        // the parent group card BE the only card boundary.
                        // Rows are visually separated by a 1dp BorderSubtle
                        // divider (the gap between rows below) instead of
                        // nested rectangles.
                        NotificationCard(
                            model = item,
                            onClick = onNotificationClick,
                            showStatusBadge = false,
                            embeddedInCard = true,
                        )
                        if (index != previewItems.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = BorderSubtle,
                            )
                        }
                    }
                    previewState.ctaCopy?.let { copy ->
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(copy)
                        }
                    }
                    if (bulkActions != null) {
                        bulkActions()
                    }
                }
            }
        }
    }
}

/**
 * Pure view-state for [DigestGroupCard] preview region. Extracted so that the
 * visibility / CTA-copy contract can be unit-tested without a Compose
 * runtime, mirroring the codebase pattern used by
 * `RuleRowDescriptionBuilder` and `HomePassthroughReviewCardState`.
 *
 * Contract (plan `2026-04-26-inbox-bundle-preview-see-all.md`):
 * - `itemsSize <= PREVIEW_LIMIT` → render every item, no CTA.
 * - `itemsSize > PREVIEW_LIMIT && !showAll` → render first PREVIEW_LIMIT,
 *   CTA reads "전체 보기 · ${itemsSize - PREVIEW_LIMIT}건 더".
 * - `itemsSize > PREVIEW_LIMIT && showAll` → render every item, CTA reads
 *   "최근만 보기".
 */
internal data class DigestGroupCardPreviewState(
    val visibleCount: Int,
    val ctaCopy: String?,
)

internal fun digestGroupCardPreviewState(
    itemsSize: Int,
    showAll: Boolean,
    previewLimit: Int = PREVIEW_LIMIT,
): DigestGroupCardPreviewState {
    val safeSize = itemsSize.coerceAtLeast(0)
    if (safeSize <= previewLimit) {
        return DigestGroupCardPreviewState(visibleCount = safeSize, ctaCopy = null)
    }
    return if (showAll) {
        DigestGroupCardPreviewState(visibleCount = safeSize, ctaCopy = "최근만 보기")
    } else {
        val remaining = safeSize - previewLimit
        DigestGroupCardPreviewState(
            visibleCount = previewLimit,
            ctaCopy = "전체 보기 · ${remaining}건 더",
        )
    }
}

/**
 * Pure-style descriptor for the [DigestGroupCard] header `${count}건` badge.
 *
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F3** (orange accent restraint on inbox-unified): the count badge moved
 * from a filled orange pill (`DigestContainer` / `DigestOnContainer`) to a
 * neutral outlined chip so the orange accent is reserved for selection /
 * status signals only — per `.claude/rules/ui-improvement.md` *"Use accent
 * color sparingly for selection, status, and primary actions"*.
 *
 * Extracted as a pure helper so the visual demotion contract is unit-pinned —
 * any future PR that re-introduces a filled-accent treatment trips
 * [DigestGroupHeaderBadgeStyleTest]. Mirrors the codebase pattern used by
 * [digestGroupCardPreviewState] and `RuleRowDescriptionBuilder`.
 */
internal data class DigestGroupHeaderBadgeStyle(
    val isFilledAccent: Boolean,
    val useOutlineBorder: Boolean,
) {
    fun formatCountLabel(count: Int): String = "${count}건"
}

internal fun digestGroupHeaderBadgeStyle(): DigestGroupHeaderBadgeStyle =
    DigestGroupHeaderBadgeStyle(
        isFilledAccent = false,
        useOutlineBorder = true,
    )
