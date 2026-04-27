package com.smartnoti.app.ui.screens.ignored

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * Sticky action bar that surfaces the bulk-delete affordance when
 * [IgnoredArchiveMultiSelectState] is in selection mode (plan
 * `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Task 5).
 *
 * Mounted just below the `SmartSurfaceCard` summary inside the same LazyColumn
 * flow — keeps content padding and scroll behavior consistent with the rest
 * of the screen, mirrors the
 * [com.smartnoti.app.ui.screens.priority.PriorityMultiSelectActionBar]
 * pattern from `2026-04-26-priority-inbox-bulk-reclassify.md`.
 *
 * Only "모두 지우기" + "취소" are exposed here — multi-select restore was left
 * out of the plan's In scope (the per-row "PRIORITY 로 복구" TextButton covers
 * single-row recovery; bulk restore lives on the header card).
 */
@Composable
fun IgnoredArchiveActionBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            }
            OutlinedButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("모두 지우기")
            }
        }
    }
}
