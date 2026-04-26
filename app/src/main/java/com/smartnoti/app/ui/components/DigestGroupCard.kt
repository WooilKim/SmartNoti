package com.smartnoti.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    Box(
                        modifier = Modifier
                            .background(
                                DigestContainer,
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "${model.count}건",
                            style = MaterialTheme.typography.labelMedium,
                            color = DigestOnContainer,
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
                    model.items.take(previewState.visibleCount).forEach { item ->
                        NotificationCard(model = item, onClick = onNotificationClick)
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
