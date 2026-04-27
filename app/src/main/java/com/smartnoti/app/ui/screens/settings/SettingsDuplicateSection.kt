package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.smartnoti.app.ui.components.SettingsToggleRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DuplicateThresholdEditorRow(
    spec: DuplicateThresholdEditorSpec,
    normalizeNumericTokens: Boolean,
    onThresholdSelected: (Int) -> Unit,
    onWindowMinutesSelected: (Int) -> Unit,
    onNormalizeNumericTokensChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "중복 알림 묶기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "같은 내용이 짧은 시간 안에 반복되면 자동으로 모아둬요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuplicateThresholdPicker(
                label = "반복 횟수",
                selectedValue = spec.selectedThreshold,
                options = spec.thresholdOptions,
                onSelected = onThresholdSelected,
                modifier = Modifier.weight(1f),
            )
            DuplicateWindowPicker(
                label = "시간 창",
                selectedMinutes = spec.selectedWindowMinutes,
                options = spec.windowOptions,
                onSelected = onWindowMinutesSelected,
                modifier = Modifier.weight(1f),
            )
        }
        // Plan `2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
        // Task 4. Opt-in normalizer toggle. Default OFF — over-bundling risk
        // (e.g. `100원 결제` collapsing with `100,000원 결제`) is documented in
        // the helper text so the user opts into the trade-off knowingly.
        SettingsToggleRow(
            title = "비슷한 알림 묶기 (숫자/시각 차이 무시)",
            checked = normalizeNumericTokens,
            onCheckedChange = onNormalizeNumericTokensChange,
            subtitle = if (normalizeNumericTokens) {
                "‘8원/28원 적립’처럼 숫자만 다른 알림을 같은 알림으로 묶어요. 결제 금액 차이도 같이 묶일 수 있어요. 곧 들어올 알림부터 적용돼요."
            } else {
                "켜면 ‘8원/28원 적립’처럼 숫자만 다른 알림을 같은 알림으로 인식해 묶음 처리해요. 결제 금액 차이도 같이 묶일 수 있어요."
            },
        )
    }
}

@Composable
private fun DuplicateThresholdPicker(
    label: String,
    selectedValue: Int,
    options: List<DuplicateThresholdEditorSpec.ThresholdOption>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: "반복 ${selectedValue}회"
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(selectedLabel) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateWindowPicker(
    label: String,
    selectedMinutes: Int,
    options: List<DuplicateThresholdEditorSpec.WindowOption>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.minutes == selectedMinutes }?.label
        ?: "최근 ${selectedMinutes}분"
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(selectedLabel) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.minutes)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
