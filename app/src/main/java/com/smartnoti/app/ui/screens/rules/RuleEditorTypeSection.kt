package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatBundleThresholdEditor(
    value: String,
    presets: List<RepeatBundleThresholdPreset>,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onPresetClick: (RepeatBundleThresholdPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            title = "반복 기준",
            subtitle = "같은 알림이 몇 번 이상 반복되면 이 규칙을 적용할지 정해요.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDecrease) {
                Icon(Icons.Outlined.Remove, contentDescription = "반복 기준 낮추기")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("반복 횟수") },
                supportingText = { Text("예: 3회 이상 반복되면 적용") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onIncrease) {
                Icon(Icons.Outlined.Add, contentDescription = "반복 기준 높이기")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = value == preset.value,
                    onClick = { onPresetClick(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
        OutlinedButton(
            onClick = { onPresetClick(presets[1]) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("추천 기준으로 되돌리기")
        }
    }
}

@Composable
internal fun RuleEditorAppSuggestionRow(
    suggestion: RuleEditorAppSuggestion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = suggestion.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = suggestion.supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (selected) "선택됨" else "선택",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

internal fun typeLabel(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "사람"
    RuleTypeUi.APP -> "앱"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간"
    RuleTypeUi.REPEAT_BUNDLE -> "반복"
    // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 2 — natural-default copy. Task 6 owns RuleEditor wiring (dropdown
    // option enablement + dedicated test); this constant ensures the editor
    // already renders a sensible label when the dropdown iterates entries.
    RuleTypeUi.SENDER -> "발신자"
}

internal fun matchLabelFor(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "이름 또는 발신자"
    RuleTypeUi.APP -> "패키지명"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간 조건"
    RuleTypeUi.REPEAT_BUNDLE -> "반복 기준"
    // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 2 — natural-default match-value label.
    RuleTypeUi.SENDER -> "발신자 이름"
}
