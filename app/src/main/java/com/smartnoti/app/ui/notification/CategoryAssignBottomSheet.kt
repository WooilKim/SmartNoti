package com.smartnoti.app.ui.notification

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
import com.smartnoti.app.ui.screens.categories.CategoryActionBadge

/**
 * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 2.
 *
 * Bottom sheet surface for the Detail screen's "분류 변경" CTA. Driven
 * by [ChangeCategorySheetState]: existing Category rows render in
 * `order` ascending, followed by a terminal "새 분류 만들기" row that
 * produces the Create-New navigation action.
 *
 * Tap routing: row tap → `state.onTapExisting(id)` dispatched via
 * [onAssignToExisting]; create row tap → `state.onTapCreateNew()` via
 * [onCreateNewCategory]. Both paths dismiss the sheet through
 * [onDismiss] so the caller can execute the follow-up side effect
 * (assign use case / editor navigation) on the caller's scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAssignBottomSheet(
    state: ChangeCategorySheetState,
    onDismiss: () -> Unit,
    onAssignToExisting: (String) -> Unit,
    onCreateNewCategory: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = "분류 변경",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "이 알림이 속할 분류를 고르거나 새 분류를 만들어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.existingRows.isNotEmpty()) {
                Text(
                    text = "기존 분류에 포함",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.existingRows.forEach { row ->
                        ExistingCategoryRowItem(
                            row = row,
                            onClick = {
                                onAssignToExisting(row.categoryId)
                            },
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            if (state.canCreateNewCategory) {
                CreateNewCategoryRow(
                    onClick = onCreateNewCategory,
                )
            }
        }
    }
}

@Composable
private fun ExistingCategoryRowItem(
    row: ChangeCategorySheetState.ExistingCategoryRow,
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
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "규칙 ${row.ruleCount}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CategoryActionBadge(action = row.action)
        }
    }
}

@Composable
private fun CreateNewCategoryRow(onClick: () -> Unit) {
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

