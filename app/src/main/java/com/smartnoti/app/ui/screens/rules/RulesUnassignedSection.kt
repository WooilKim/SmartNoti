package com.smartnoti.app.ui.screens.rules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.ui.components.RuleRow
import com.smartnoti.app.ui.components.RuleRowPresentation
import com.smartnoti.app.ui.components.SmartSurfaceCard

/**
 * Compose slot that renders a single 미분류 row in either the "작업 필요" or
 * "보류" sub-section. Plan
 * `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 5.
 *
 * Rendered as a [Column] (not a `Card`) so the existing [RuleRow] card +
 * accent-border highlight overlay continue to compose cleanly. When
 * [isParked] is true an extra small `TextButton` sits below the row offering
 * "작업으로 끌어올리기" so users can promote a parked rule back to the loud
 * "작업 필요" sub-bucket without having to walk through the post-save sheet
 * a second time.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnassignedRuleRowSlot(
    rule: RuleUiModel,
    isParked: Boolean,
    isHighlighted: Boolean,
    onRowTap: () -> Unit,
    onPromoteToActionNeeded: (() -> Unit)?,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 400),
        label = "ruleHighlight",
    )
    // Plan `2026-04-26-rules-bulk-assign-unassigned.md` Task 5 step 3 —
    // selected rows in multi-select mode draw an accent border so the
    // user can see what is in their bulk action set. Drawn on the slot
    // wrapper (not on RuleRow itself) so the existing `RuleRow` visual
    // contract stays intact.
    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "ruleSelectionBorder",
    )
    Column(
        modifier = Modifier
            .let { mod ->
                if (isHighlighted) {
                    mod.border(
                        width = 2.dp,
                        color = highlightColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                } else if (isSelected) {
                    mod.border(
                        width = 2.dp,
                        color = selectionBorderColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                } else {
                    mod
                }
            }
            .combinedClickable(
                onClick = onRowTap,
                onLongClick = onLongPress,
            ),
    ) {
        RuleRow(
            rule = rule,
            action = RuleActionUi.SILENT,
            onCheckedChange = onCheckedChange,
            onMoveUpClick = onMoveUp,
            onMoveDownClick = onMoveDown,
            onEditClick = onEdit,
            onDeleteClick = onDelete,
            presentation = RuleRowPresentation.Unassigned(isParked = isParked),
        )
        if (isParked && onPromoteToActionNeeded != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onPromoteToActionNeeded) {
                    Text("작업으로 끌어올리기")
                }
            }
        }
    }
}

@Composable
internal fun CategoryAssignSheetContent(
    rule: RuleUiModel,
    categories: List<Category>,
    onCategorySelected: (String) -> Unit,
    onParkRule: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "이 규칙을 어떤 분류에 추가하시겠어요?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "“${rule.title.ifBlank { rule.matchValue }}” 규칙은 분류에 추가되기 전까지 어떤 알림도 분류하지 않아요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (categories.isEmpty()) {
            Text(
                text = "아직 만들어진 분류가 없어요. 분류 탭에서 새 분류를 만든 뒤 다시 규칙을 추가해 보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    CategoryAssignRow(
                        category = category,
                        onClick = { onCategorySelected(category.id) },
                    )
                }
            }
        }
        // Plan `2026-04-26-rule-explicit-draft-flag.md` Task 4 — split the
        // single "나중에 분류에 추가" CTA into two intent-aware buttons:
        //   - "작업 목록에 두기" (default) leaves draft = true so the rule
        //     surfaces in the loud "작업 필요" sub-bucket on RulesScreen.
        //   - "분류 없이 보류" flips draft = false so the rule moves to the
        //     quieter "보류" sub-bucket — explicit "intentionally
        //     unassigned" intent.
        // Both buttons dismiss the sheet; neither auto-reopens. Closing the
        // sheet via outside-tap / system back keeps the rule's current
        // draft state untouched (default branch via onDismiss).
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onParkRule) {
                    Text("분류 없이 보류")
                }
                TextButton(onClick = onDismiss) {
                    Text("작업 목록에 두기")
                }
            }
        }
    }
}

@Composable
private fun CategoryAssignRow(
    category: Category,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.name.ifBlank { "분류" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "규칙 ${category.ruleIds.size}개 · ${categoryActionLabel(category.action)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "추가",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun categoryActionLabel(action: CategoryAction): String = when (action) {
    CategoryAction.PRIORITY -> "즉시 전달"
    CategoryAction.DIGEST -> "Digest"
    CategoryAction.SILENT -> "조용히"
    CategoryAction.IGNORE -> "무시"
}
