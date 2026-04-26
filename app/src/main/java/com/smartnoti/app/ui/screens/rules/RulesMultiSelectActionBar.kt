package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartnoti.app.ui.components.SmartSurfaceCard

/**
 * Sticky action bar that surfaces the bulk-assign affordances when
 * [RulesScreenMultiSelectState] is in selection mode (plan
 * `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Task 5).
 *
 * Mounted just below `ScreenHeader` so it stays inside the LazyColumn flow
 * — that keeps content padding and scroll behavior consistent with the
 * rest of RulesScreen without introducing a Floating overlay.
 */
@Composable
fun RulesMultiSelectActionBar(
    selectedCount: Int,
    onAssignToCategoryClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selectedCount}개 선택됨",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancelClick) {
                Text("취소")
            }
            OutlinedButton(
                onClick = onAssignToCategoryClick,
                enabled = selectedCount > 0,
            ) {
                Text("분류 추가")
            }
        }
    }
}
