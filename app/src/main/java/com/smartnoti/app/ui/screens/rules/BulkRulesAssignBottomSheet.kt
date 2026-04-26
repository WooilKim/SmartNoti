package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.ui.screens.categories.CategoryActionBadge

/**
 * Plan `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Task 6.
 *
 * Bulk-assign sibling of [com.smartnoti.app.ui.notification.CategoryAssignBottomSheet].
 * Presented when the RulesScreen multi-select ActionBar's "분류 추가" CTA
 * is tapped — lists every existing Category plus a terminal "+ 새 분류
 * 만들기" row. The single-rule sheet ([RulesScreen#CategoryAssignSheetContent])
 * is intentionally untouched: this sheet has no rule-specific copy and no
 * "park" CTA because bulk assign has no source rule context to anchor
 * those affordances to.
 *
 * Tap routing: existing-row tap → [onAssignToExisting]; terminal row tap →
 * [onCreateNewCategory]. Both paths dismiss via [onDismiss] so the caller
 * runs the follow-up side effect (bulk use case / editor open) on its own
 * coroutine scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRulesAssignBottomSheet(
    categories: List<Category>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onAssignToExisting: (String) -> Unit,
    onCreateNewCategory: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedCategories = categories.sortedBy { it.order }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "선택한 규칙 ${selectedCount}개를 어디에 추가할까요?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "기존 분류에 합류시키거나, 선택한 규칙을 묶어 새 분류를 만들 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (sortedCategories.isNotEmpty()) {
                Text(
                    text = "기존 분류에 포함",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sortedCategories.forEach { category ->
                        BulkExistingCategoryRow(
                            category = category,
                            onClick = { onAssignToExisting(category.id) },
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            BulkCreateNewCategoryRow(onClick = onCreateNewCategory)
        }
    }
}

@Composable
private fun BulkExistingCategoryRow(
    category: Category,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.name.ifBlank { "분류" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "규칙 ${category.ruleIds.size}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CategoryActionBadge(action = category.action)
        }
    }
}

@Composable
private fun BulkCreateNewCategoryRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = "+ 새 분류 만들기",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
