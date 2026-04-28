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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.ui.components.SettingsCardHeader
import com.smartnoti.app.ui.components.SettingsToggleRow
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle

@Composable
internal fun AdvancedRulesEntryCard(onClick: () -> Unit) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardHeader(
            eyebrow = "고급",
            title = "고급 규칙 편집",
            subtitle = "분류에 묶이기 전의 개별 매처 규칙을 직접 다룹니다. 일반적으로는 '분류' 탭에서 편집하세요.",
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("고급 규칙 편집 열기")
        }
    }
}

@Composable
internal fun IgnoredArchiveSettingsCard(
    showIgnoredArchive: Boolean,
    onShowIgnoredArchiveChange: (Boolean) -> Unit,
    onOpenIgnoredArchive: (() -> Unit)? = null,
) {
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6. The toggle is the
    // sole way for users to surface IGNORE rows — rows are persisted
    // regardless, but the archive screen is only reachable when this is on.
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardHeader(
            eyebrow = "무시됨",
            title = "무시된 알림 보기",
            subtitle = "IGNORE 규칙으로 즉시 정리한 알림은 기본 뷰에서 제외돼요. 필요할 때만 켜서 아카이브를 확인하세요.",
        )
        SettingsToggleRow(
            title = "무시된 알림 아카이브 표시",
            checked = showIgnoredArchive,
            onCheckedChange = onShowIgnoredArchiveChange,
            subtitle = if (showIgnoredArchive) {
                "아래 버튼으로 아카이브 화면을 열 수 있어요."
            } else {
                "켜면 설정 화면에 아카이브 진입 버튼이 나타나요. 알림 분류 동작은 바뀌지 않아요."
            },
        )
        if (showIgnoredArchive && onOpenIgnoredArchive != null) {
            OutlinedButton(
                onClick = onOpenIgnoredArchive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("무시됨 아카이브 열기")
            }
        }
    }
}

/**
 * Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 5 — Settings card for the master toggle that gates the
 * `SenderRuleSuggestionCard` surfaced on the notification Detail screen.
 * Default ON matches the plan's "learning acceleration" intent — fresh
 * installs see the card immediately so the cohort can convert noisy
 * SILENT messenger DMs to PRIORITY in one tap. Power users that find the
 * card intrusive can flip this OFF here. The toggle does not alter the
 * classifier or the rule storage — disabling only hides the suggestion
 * card; existing SENDER rules continue to match.
 */
@Composable
internal fun SenderSuggestionSettingsCard(
    senderSuggestionEnabled: Boolean,
    onSenderSuggestionEnabledChange: (Boolean) -> Unit,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardHeader(
            eyebrow = "발신자",
            title = "발신자 분류 제안",
            subtitle = "알림 상세 화면에서 같은 발신자의 알림을 항상 [중요]로 분류할지 제안받아요.",
        )
        SettingsToggleRow(
            title = "발신자 분류 제안",
            checked = senderSuggestionEnabled,
            onCheckedChange = onSenderSuggestionEnabledChange,
            subtitle = if (senderSuggestionEnabled) {
                "알림 상세 화면에서 발신자별 중요 분류를 제안받아요. 기존 규칙은 영향을 받지 않아요."
            } else {
                "켜면 알림 상세 화면에 발신자 기반의 한 번에 중요 분류 제안 카드가 나타나요."
            },
        )
    }
}

@Composable
internal fun OperationalSummaryCard(
    summary: SettingsOperationalSummary,
    quietHoursPickerSpec: QuietHoursWindowPickerSpec,
    quietHoursPackagesPickerSpec: QuietHoursPackagesPickerSpec,
    duplicateThresholdEditorSpec: DuplicateThresholdEditorSpec,
    normalizeNumericTokensInSignature: Boolean,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursStartHourChange: (Int) -> Unit,
    onQuietHoursEndHourChange: (Int) -> Unit,
    onQuietHoursPackageAdd: (String) -> Unit,
    onQuietHoursPackageRemove: (String) -> Unit,
    onQuietHoursPackagesReplace: (Set<String>) -> Unit,
    onDuplicateThresholdChange: (Int) -> Unit,
    onDuplicateWindowMinutesChange: (Int) -> Unit,
    onNormalizeNumericTokensInSignatureChange: (Boolean) -> Unit,
) {
    // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
    // Task 6: bottom-sheet visibility for the picker is owned by the card so
    // tap-state lives close to the row and the LazyColumn parent stays
    // declarative. rememberSaveable so an OS rotation doesn't reopen the
    // sheet unintentionally.
    var packagesPickerOpen by rememberSaveable { mutableStateOf(false) }
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardHeader(
            eyebrow = "운영 상태",
            title = "지금 동작 중인 전달 상태",
            subtitle = "현재 모드·Quiet Hours·Digest 시간을 한눈에 확인해요.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OperationalSummaryRow(
                label = "현재 모드",
                value = summary.modeTitle,
                detail = summary.modeDetail,
            )
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            OperationalSummaryRow(
                label = "Quiet Hours",
                value = summary.quietHoursWindow,
                detail = summary.quietHoursState,
                trailing = {
                    Switch(
                        checked = summary.quietHoursEnabled,
                        onCheckedChange = onQuietHoursEnabledChange,
                    )
                },
            )
            // Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 4.
            // Pickers appear only when the master Switch is ON — see
            // QuietHoursWindowPickerSpecBuilder for the full rationale and the
            // same-value warning contract.
            if (quietHoursPickerSpec.visible) {
                QuietHoursWindowPickerRow(
                    spec = quietHoursPickerSpec,
                    onStartHourChange = onQuietHoursStartHourChange,
                    onEndHourChange = onQuietHoursEndHourChange,
                )
            }
            // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
            // Task 6. Sub-row visibility tracks the master Switch — when OFF
            // the picker is also no-op so we hide it (consistent with the
            // start/end hour picker row above).
            if (quietHoursPackagesPickerSpec.visible) {
                QuietHoursPackagesPickerRow(
                    spec = quietHoursPackagesPickerSpec,
                    onOpenPicker = { packagesPickerOpen = true },
                )
            }
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 5.
            // The "중복 알림 묶기" row mirrors the QuietHours editor pattern —
            // two AssistChip + DropdownMenu selectors so the user can shorten
            // ("더 자주 묶기") or lengthen ("거의 묶지 말기") the duplicate-burst
            // base heuristic. The label copy matches the plan's design
            // direction (반복 N회 / 최근 N분).
            DuplicateThresholdEditorRow(
                spec = duplicateThresholdEditorSpec,
                normalizeNumericTokens = normalizeNumericTokensInSignature,
                onThresholdSelected = onDuplicateThresholdChange,
                onWindowMinutesSelected = onDuplicateWindowMinutesChange,
                onNormalizeNumericTokensChange = onNormalizeNumericTokensInSignatureChange,
            )
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            OperationalSummaryRow(
                label = "Digest 시간",
                value = summary.digestSchedule,
                detail = summary.digestDetail,
            )
        }
    }
    if (packagesPickerOpen) {
        QuietHoursPackagesPickerSheet(
            spec = quietHoursPackagesPickerSpec,
            onDismiss = { packagesPickerOpen = false },
            onAddPackage = onQuietHoursPackageAdd,
            onRemovePackage = onQuietHoursPackageRemove,
            onClearAll = { onQuietHoursPackagesReplace(emptySet()) },
        )
    }
}

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
 *
 * Duration picker for the auto-dismiss timeout. Mirrors the
 * `DuplicateWindowPicker` AssistChip + DropdownMenu pattern so the visual tone
 * stays consistent with the other Settings dropdowns.
 */
@Composable
internal fun ReplacementAutoDismissDurationPicker(
    spec: ReplacementAutoDismissPickerSpec,
    onMinutesSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = spec.options.firstOrNull { it.minutes == spec.selectedMinutes }?.label
        ?: ReplacementAutoDismissPickerSpecBuilder.labelFor(spec.selectedMinutes)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "자동 정리 시간",
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
                spec.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onMinutesSelected(option.minutes)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun OperationalSummaryRow(
    label: String,
    value: String,
    detail: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (trailing != null) Alignment.Top else Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
