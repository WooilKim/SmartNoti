package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.ui.components.RuleRowPresentation
import com.smartnoti.app.ui.components.SectionLabel

@Composable
internal fun RuleOverrideEditorSection(
    overrideEnabled: Boolean,
    onOverrideEnabledChange: (Boolean) -> Unit,
    candidates: List<RuleUiModel>,
    selectedBaseId: String?,
    onBaseSelected: (String?) -> Unit,
    supersetWarningMessage: String?,
) {
    // Plan rules-ux-v2-inbox-restructure Phase C Task 4: "기존 규칙의 예외로
    // 만들기" toggle + base-rule dropdown. The section renders as a section
    // label + switch row, and only mounts the dropdown when the switch is on
    // so the dialog doesn't feel crowded for plain-rule creation.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            title = "예외 규칙",
            subtitle = "다른 규칙이 먼저 적용되어도 이 규칙이 우선하도록 예외로 설정할 수 있어요.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "기존 규칙의 예외로 만들기",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = overrideEnabled,
                onCheckedChange = onOverrideEnabledChange,
                enabled = candidates.isNotEmpty() || overrideEnabled,
            )
        }
        if (overrideEnabled) {
            if (candidates.isEmpty()) {
                Text(
                    text = "예외로 지정할 기준 규칙이 없어요. 먼저 기본 규칙을 만들어 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RuleOverrideBaseDropdown(
                    candidates = candidates,
                    selectedBaseId = selectedBaseId,
                    onBaseSelected = onBaseSelected,
                )
            }
            if (supersetWarningMessage != null) {
                Text(
                    text = supersetWarningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RuleOverrideBaseDropdown(
    candidates: List<RuleUiModel>,
    selectedBaseId: String?,
    onBaseSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = candidates.firstOrNull { it.id == selectedBaseId }?.title
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "어느 규칙의 예외인가요?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(selectedTitle ?: "규칙 선택")
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.title) },
                    onClick = {
                        onBaseSelected(candidate.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun supersetWarningMessage(reason: RuleOverrideSupersetValidator.Reason): String = when (reason) {
    RuleOverrideSupersetValidator.Reason.BASE_MISSING ->
        "기준 규칙을 찾을 수 없어요. 삭제됐거나 아직 저장되지 않은 규칙일 수 있어요."
    RuleOverrideSupersetValidator.Reason.TYPE_MISMATCH ->
        "기준 규칙과 타입이 달라요. 같은 타입으로 맞추면 더 정확하게 동작해요."
    RuleOverrideSupersetValidator.Reason.KEYWORD_NOT_SUPERSET ->
        "기준 규칙의 키워드를 모두 포함해야 예외가 정상적으로 동작해요."
    RuleOverrideSupersetValidator.Reason.VALUE_MISMATCH ->
        "기준 규칙과 조건 값이 달라서 예외가 적용되지 않을 수 있어요."
}

internal fun ruleRowPresentationFor(
    node: RuleListNode,
    baseTitle: String?,
): RuleRowPresentation {
    val broken = node.brokenReason
    if (broken != null) {
        return RuleRowPresentation.BrokenOverride(
            reasonMessage = when (broken) {
                RuleOverrideBrokenReason.SelfReference ->
                    "이 규칙은 자기 자신을 예외로 지정해 동작하지 않아요."
                is RuleOverrideBrokenReason.BaseMissing ->
                    "기준이 되는 규칙이 삭제돼 예외가 동작하지 않아요."
                is RuleOverrideBrokenReason.BaseIsOverride ->
                    "다른 예외의 예외로 지정돼 있어 동작하지 않아요."
            },
        )
    }
    return when (node.overrideState) {
        RuleOverrideState.Base -> RuleRowPresentation.Base
        is RuleOverrideState.Override -> RuleRowPresentation.Override(baseTitle = baseTitle)
    }
}
