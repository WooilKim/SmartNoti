package com.smartnoti.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.ui.theme.BorderSubtle

/**
 * Presentation flavor for [RuleRow]. Defaults to [Base]; Phase C hierarchical
 * rendering passes [Override] for nested child rows and [BrokenOverride] when
 * the base can't be resolved (see plan `rules-ux-v2-inbox-restructure` Phase C
 * Task 3). Plan `2026-04-24-rule-editor-remove-action-dropdown.md` Task 6 adds
 * [Unassigned] for the 미분류 bucket — Rules that no Category claims, which
 * sit dormant until the user attaches them to a Category.
 *
 * Plan `2026-04-26-rule-explicit-draft-flag.md` Task 5 splits [Unassigned]
 * into two intent-aware tones via the [Unassigned.isParked] flag:
 *  - `isParked = false` (default) — "작업 필요" sub-bucket, accent-toned
 *    border-only chip + "분류에 추가되기 전까지 비활성" banner. Same loud
 *    treatment the historical 미분류 row had.
 *  - `isParked = true` — "보류" sub-bucket, quieter neutral border-only
 *    chip + "사용자가 보류함 — 필요 시 작업으로 끌어올리세요" banner so
 *    explicitly parked rows do not shout on every Rules screen mount.
 */
sealed interface RuleRowPresentation {
    data object Base : RuleRowPresentation
    data class Override(val baseTitle: String?) : RuleRowPresentation
    data class BrokenOverride(val reasonMessage: String) : RuleRowPresentation
    data class Unassigned(val isParked: Boolean = false) : RuleRowPresentation
}

@Composable
fun RuleRow(
    rule: RuleUiModel,
    action: RuleActionUi,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUpClick: () -> Unit = {},
    onMoveDownClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    presentation: RuleRowPresentation = RuleRowPresentation.Base,
) {
    val description = rememberRuleRowDescription(rule, action)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RuleRowPresentationBanner(presentation = presentation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        rule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description.primaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuleMetaChip(typeLabel(rule.type))
                        // Plan 2026-04-24-rule-editor-remove-action-dropdown
                        // Task 6: Unassigned rows replace the action chip with
                        // a border-only "미분류" chip so users can spot the
                        // dormant state at a glance. IGNORE keeps its
                        // border-only treatment (Plan 2026-04-21-ignore-tier
                        // Task 5 step 5) for the lowest-presence destructive
                        // tier.
                        if (presentation is RuleRowPresentation.Unassigned) {
                            // Plan `2026-04-26-rule-explicit-draft-flag` Task 5
                            // — parked rules (explicit "보류") get a quieter
                            // "보류" chip, action-needed drafts keep the
                            // historical "미분류" label so existing users
                            // recognize the loud sub-bucket.
                            RuleMetaChip(
                                text = if (presentation.isParked) "보류" else "미분류",
                                style = RuleMetaChipStyle.BorderOnly,
                            )
                        } else {
                            RuleMetaChip(
                                text = actionLabel(action),
                                style = if (action == RuleActionUi.IGNORE) {
                                    RuleMetaChipStyle.BorderOnly
                                } else {
                                    RuleMetaChipStyle.Filled
                                },
                            )
                        }
                        if (!description.emphasisLabel.isNullOrBlank()) {
                            RuleMetaChip(description.emphasisLabel)
                        }
                    }
                    Text(
                        text = description.secondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.9f))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Drag handle. Long-press + vertical drag repeatedly invokes
                // the same move callbacks as the arrow buttons, so storage
                // stays tier-aware (plan rules-ux-v2-inbox-restructure Phase
                // C Task 5 — see `moveRule` in RulesRepository).
                RuleRowDragHandle(
                    onMoveUp = onMoveUpClick,
                    onMoveDown = onMoveDownClick,
                )
                CompactIconButton(onClick = onMoveUpClick) {
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "위로",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompactIconButton(onClick = onMoveDownClick) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "아래로",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompactIconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "수정",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompactIconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRowPresentationBanner(presentation: RuleRowPresentation) {
    when (presentation) {
        RuleRowPresentation.Base -> Unit
        is RuleRowPresentation.Unassigned -> {
            val bannerText = if (presentation.isParked) {
                "사용자가 보류함 · 필요 시 작업으로 끌어올리세요"
            } else {
                "분류에 추가되기 전까지 비활성"
            }
            Text(
                text = bannerText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        is RuleRowPresentation.Override -> {
            val baseSuffix = presentation.baseTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { " · $it" }
                .orEmpty()
            Text(
                text = "이 규칙의 예외$baseSuffix",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        is RuleRowPresentation.BrokenOverride -> {
            Text(
                text = presentation.reasonMessage,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Visual style hook for [RuleMetaChip]. [Filled] is the historical pill look;
 * [BorderOnly] drops the surface fill and paints a thin subtle border, which
 * the IGNORE action chip uses to signal a destructive-but-low-presence tier
 * (plan `2026-04-21-ignore-tier-fourth-decision` Task 5 step 5).
 */
private enum class RuleMetaChipStyle {
    Filled,
    BorderOnly,
}

@Composable
private fun RuleMetaChip(
    text: String,
    style: RuleMetaChipStyle = RuleMetaChipStyle.Filled,
) {
    val shape = RoundedCornerShape(999.dp)
    val baseModifier = Modifier
        .let { mod ->
            when (style) {
                RuleMetaChipStyle.Filled -> mod.background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = shape,
                )
                RuleMetaChipStyle.BorderOnly -> mod.border(
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = shape,
                )
            }
        }
        .padding(horizontal = 8.dp, vertical = 4.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = baseModifier,
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier,
    ) {
        content()
    }
}

/**
 * Long-press-and-drag handle for tier-aware reordering (plan
 * `rules-ux-v2-inbox-restructure` Phase C Task 5). The handle runs the same
 * `onMoveUp` / `onMoveDown` callbacks as the arrow buttons, which in turn hit
 * `RulesRepository.moveRule` — so tier guards live in one place and drag can
 * never split a base from its overrides or reparent an override into another
 * base's group.
 *
 * Gesture model:
 *  - Long-press to start (gives haptic-adjacent feel, avoids competing with
 *    LazyColumn scroll).
 *  - Each [DRAG_STEP_DP] of vertical travel triggers one move in the
 *    corresponding direction — cumulative drags ratchet the row through the
 *    list tier-by-tier.
 */
@Composable
private fun RuleRowDragHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { DRAG_STEP_DP.dp.toPx() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "드래그해서 순서 바꾸기" }
            .pointerInput(onMoveUp, onMoveDown) {
                var accumulated = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount.y
                        while (accumulated <= -stepPx) {
                            onMoveUp()
                            accumulated += stepPx
                        }
                        while (accumulated >= stepPx) {
                            onMoveDown()
                            accumulated -= stepPx
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Each full-step vertical drag swaps the row with its same-tier neighbor.
// 48dp roughly matches a card's vertical rhythm — small enough for precise
// control, large enough that accidental jitter doesn't ratchet.
private const val DRAG_STEP_DP = 48

private fun typeLabel(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "사람"
    RuleTypeUi.APP -> "앱"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간"
    RuleTypeUi.REPEAT_BUNDLE -> "반복"
    // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 2 — natural-default chip label. Task 6 owns the editor / dropdown
    // copy, this stays in sync as the row chip.
    RuleTypeUi.SENDER -> "발신자"
}

private fun actionLabel(action: RuleActionUi): String = when (action) {
    RuleActionUi.ALWAYS_PRIORITY -> "즉시 전달"
    RuleActionUi.DIGEST -> "Digest"
    RuleActionUi.SILENT -> "조용히"
    RuleActionUi.CONTEXTUAL -> "상황별"
    // Final copy landed in Task 5 of plan
    // `2026-04-21-ignore-tier-fourth-decision`; Task 2 uses the short label
    // so existing rule rows render without crashes if a test fixture
    // includes an IGNORE rule.
    RuleActionUi.IGNORE -> "무시"
}

@Composable
private fun rememberRuleRowDescription(
    rule: RuleUiModel,
    action: RuleActionUi,
): RuleRowDescription {
    val builder = remember { RuleRowDescriptionBuilder() }
    return remember(rule, action) { builder.build(rule, action) }
}
