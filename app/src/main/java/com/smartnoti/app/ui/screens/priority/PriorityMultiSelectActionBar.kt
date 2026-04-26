package com.smartnoti.app.ui.screens.priority

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
 * Sticky action bar that surfaces the bulk-reclassify affordances when
 * [PriorityScreenMultiSelectState] is in selection mode (plan
 * `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` Task 5).
 *
 * Mounted just below `ScreenHeader` (and the "검토 대기 N건" SmartSurfaceCard)
 * inside the same LazyColumn flow — keeps content padding and scroll
 * behavior consistent with the rest of PriorityScreen, mirrors the
 * [com.smartnoti.app.ui.screens.rules.RulesMultiSelectActionBar] pattern from
 * `2026-04-26-rules-bulk-assign-unassigned.md`.
 */
@Composable
fun PriorityMultiSelectActionBar(
    selectedCount: Int,
    onSendToDigest: () -> Unit,
    onSilence: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSendToDigest,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("→ Digest")
                }
                OutlinedButton(
                    onClick = onSilence,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("→ 조용히")
                }
            }
        }
    }
}
