package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.usecase.SuppressionBreakdownItem
import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary
import com.smartnoti.app.ui.components.SettingsToggleRow
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle

@Composable
internal fun SuppressionManagementCard(
    settings: SmartNotiSettings,
    filteredCapturedApps: List<CapturedAppSelectionItem>,
    disclosureSummaryBuilder: SettingsDisclosureSummaryBuilder,
    insightSummaryBuilder: SettingsSuppressionInsightSummaryBuilder,
    summary: SuppressionInsightsSummary,
    breakdownItems: List<SuppressionBreakdownItem>,
    topAppRoute: String?,
    breakdownRoutesByAppName: Map<String, String>,
    onInsightClick: (String) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    appsExpanded: Boolean,
    onAppsExpandedChange: (Boolean) -> Unit,
    onSuppressSourceChange: (Boolean) -> Unit,
    onHidePersistentNotificationsChange: (Boolean) -> Unit,
    onHidePersistentSourceNotificationsChange: (Boolean) -> Unit,
    onProtectCriticalPersistentNotificationsChange: (Boolean) -> Unit,
    // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
    onReplacementAutoDismissEnabledChange: (Boolean) -> Unit,
    onReplacementAutoDismissMinutesChange: (Int) -> Unit,
    onSuppressedSourceAppToggle: (String, Boolean) -> Unit,
    onSuppressedSourceAppsReplace: (Set<String>) -> Unit,
    onSuppressedSourceAppsBulkExcludeChange: (Set<String>, Boolean) -> Unit,
) {
    val autoDismissPickerSpec = remember(
        settings.replacementAutoDismissEnabled,
        settings.replacementAutoDismissMinutes,
    ) {
        ReplacementAutoDismissPickerSpecBuilder().build(settings)
    }
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        val insightTokens = remember(settings.suppressSourceForDigestAndSilent, summary) {
            insightSummaryBuilder.buildTokens(
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                insights = summary,
            )
        }

        SettingsSubsection(
            title = "원본 알림 숨김 상태",
            subtitle = insightTokens.supportingMessage,
            isFirst = true,
        ) {
            if (insightTokens.metrics.isNotEmpty()) {
                SuppressionInsightMetricStrip(metrics = insightTokens.metrics)
            }
            if (breakdownItems.isNotEmpty()) {
                SuppressionBreakdownList(
                    items = breakdownItems,
                    routeByAppName = breakdownRoutesByAppName,
                    onInsightClick = onInsightClick,
                )
            }
            if (summary.appInsights.isNotEmpty()) {
                SuppressedAppInsightsList(
                    appInsights = summary.appInsights,
                    topAppRoute = topAppRoute,
                    onInsightClick = onInsightClick,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
            SettingsToggleRow(
                title = "Digest·조용히 알림의 원본 숨기기",
                checked = settings.suppressSourceForDigestAndSilent,
                onCheckedChange = onSuppressSourceChange,
                subtitle = if (settings.suppressSourceForDigestAndSilent) {
                    "선택한 앱의 Digest·조용히 알림에 대해 원본 숨김을 시도하고, SmartNoti 대체 알림으로 이어줘요. 기기/앱에 따라 원본이 남을 수 있어요."
                } else {
                    "먼저 이 옵션을 켜면 아래 고급 옵션과 앱별 선택이 활성화돼요."
                },
            )
        }
        ExpandableSettingsSubsection(
            title = "고급 숨김 옵션",
            subtitle = disclosureSummaryBuilder.buildSuppressionAdvancedSummary(settings),
            expanded = advancedExpanded,
            onExpandedChange = onAdvancedExpandedChange,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsToggleRow(
                    title = "지속 알림은 SmartNoti 목록에서 숨기기",
                    checked = settings.hidePersistentNotifications,
                    onCheckedChange = onHidePersistentNotificationsChange,
                    subtitle = if (settings.hidePersistentNotifications) {
                        "충전 중·정리 중 같은 고정 알림은 목록/인사이트 집계에서 제외해요."
                    } else {
                        "지속 알림도 일반 알림처럼 집계와 목록에 포함해요."
                    },
                )
                SettingsToggleRow(
                    title = "지속 알림은 시스템 알림센터에서도 숨기기",
                    checked = settings.hidePersistentSourceNotifications,
                    onCheckedChange = onHidePersistentSourceNotificationsChange,
                    subtitle = if (settings.hidePersistentSourceNotifications) {
                        "고정 시스템 알림이 올라오면 SmartNoti가 바로 감춰요. 중요한 시스템 알림까지 숨길 수 있어 주의가 필요해요."
                    } else {
                        "고정 시스템 알림은 기기 알림센터에 그대로 남겨둬요."
                    },
                )
                SettingsToggleRow(
                    title = "통화·길안내·녹화 중 알림은 항상 보이기",
                    checked = settings.protectCriticalPersistentNotifications,
                    onCheckedChange = onProtectCriticalPersistentNotificationsChange,
                    subtitle = if (settings.protectCriticalPersistentNotifications) {
                        "통화 중, 길안내 중, 화면 녹화/카메라·마이크 사용 중 알림은 숨김 예외로 보호해요."
                    } else {
                        "고정 알림 예외 보호를 끄면 중요한 live-state 알림도 일반 고정 알림처럼 숨겨질 수 있어요."
                    },
                )
                // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
                // Auto-dismiss controls live alongside the persistent-notification
                // tray toggles because both are about "what stays in the tray
                // and for how long". The picker stays present even when the
                // toggle is OFF (dimmed via `enabled = autoDismissPickerSpec.enabled`)
                // so the user understands the duration is still configured.
                SettingsToggleRow(
                    title = "SmartNoti 알림 자동 정리",
                    checked = autoDismissPickerSpec.enabled,
                    onCheckedChange = onReplacementAutoDismissEnabledChange,
                    subtitle = if (autoDismissPickerSpec.enabled) {
                        "Digest·조용히 대체 알림이 ${
                            ReplacementAutoDismissPickerSpecBuilder.labelFor(
                                autoDismissPickerSpec.selectedMinutes,
                            )
                        } 후 알림 트레이에서 자동으로 사라져요. 정리함 기록은 그대로 남아요."
                    } else {
                        "사용자가 직접 스와이프하기 전까지 SmartNoti 가 게시한 대체 알림이 트레이에 머물러요."
                    },
                )
                if (autoDismissPickerSpec.enabled) {
                    ReplacementAutoDismissDurationPicker(
                        spec = autoDismissPickerSpec,
                        onMinutesSelected = onReplacementAutoDismissMinutesChange,
                    )
                }
            }
        }
        ExpandableSettingsSubsection(
            title = "숨길 앱 선택",
            subtitle = disclosureSummaryBuilder.buildSuppressedAppsSummary(
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                selectedCount = settings.suppressedSourceApps.size,
                availableApps = filteredCapturedApps,
            ),
            expanded = appsExpanded,
            onExpandedChange = onAppsExpandedChange,
        ) {
            SuppressedSourceAppChips(
                filteredCapturedApps = filteredCapturedApps,
                suppressedSourceApps = settings.suppressedSourceApps,
                excludedSourceApps = settings.suppressedSourceAppsExcluded,
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                onSuppressedSourceAppToggle = onSuppressedSourceAppToggle,
                onSuppressedSourceAppsReplace = onSuppressedSourceAppsReplace,
                onSuppressedSourceAppsBulkExcludeChange = onSuppressedSourceAppsBulkExcludeChange,
            )
        }
    }
}

@Composable
internal fun SuppressedSourceAppChips(
    filteredCapturedApps: List<CapturedAppSelectionItem>,
    suppressedSourceApps: Set<String>,
    excludedSourceApps: Set<String>,
    suppressEnabled: Boolean,
    onSuppressedSourceAppToggle: (String, Boolean) -> Unit,
    onSuppressedSourceAppsReplace: (Set<String>) -> Unit,
    onSuppressedSourceAppsBulkExcludeChange: (Set<String>, Boolean) -> Unit,
) {
    if (filteredCapturedApps.isEmpty()) {
        Text(
            text = "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 6.1.
    // Effective `isSelected` is no longer a single membership check — the
    // sticky-exclude set must subtract from the displayed selection even
    // when `suppressedSourceApps` is empty. The builder owns that rule.
    val presentation = remember(filteredCapturedApps, suppressedSourceApps, excludedSourceApps) {
        SettingsSuppressedAppPresentationBuilder().build(
            capturedApps = filteredCapturedApps,
            suppressedSourceApps = suppressedSourceApps,
            excludedApps = excludedSourceApps,
        )
    }
    val allCandidatePackages = remember(filteredCapturedApps) {
        filteredCapturedApps.map(CapturedAppSelectionItem::packageName).toSet()
    }
    val allEffectivelySelected = remember(allCandidatePackages, suppressedSourceApps, excludedSourceApps) {
        allCandidatePackages.isNotEmpty() && allCandidatePackages.all { pkg ->
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = pkg,
                suppressedSourceApps = suppressedSourceApps,
                excludedApps = excludedSourceApps,
            )
        }
    }
    val anyEffectivelyExcludedAmongVisible = remember(allCandidatePackages, suppressedSourceApps, excludedSourceApps) {
        // "모두 해제" should remain enabled while at least one visible app
        // could still be excluded — either it's currently selected, or it's
        // already in the excluded set (idempotent re-press is harmless but
        // we hide it once nothing is left to do).
        allCandidatePackages.any { pkg ->
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = pkg,
                suppressedSourceApps = suppressedSourceApps,
                excludedApps = excludedSourceApps,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = presentation.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = suppressEnabled && !allEffectivelySelected,
                onClick = {
                    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md`
                    // Task 6.4. "모두 선택" clears the exclude entries for every
                    // visible app AND adds them back to `suppressedSourceApps`
                    // so the user's intent persists symmetrically with the
                    // single-row toggle.
                    onSuppressedSourceAppsBulkExcludeChange(allCandidatePackages, false)
                    onSuppressedSourceAppsReplace(suppressedSourceApps + allCandidatePackages)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("모두 선택")
            }
            OutlinedButton(
                enabled = suppressEnabled && anyEffectivelyExcludedAmongVisible,
                onClick = {
                    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md`
                    // Task 6.4. "모두 해제" persists sticky-exclude intent for
                    // every visible app so subsequent DIGEST notifications
                    // cannot re-add them via auto-expansion. The bulk helper
                    // also strips them from `suppressedSourceApps` atomically
                    // — no separate replace call is required.
                    onSuppressedSourceAppsBulkExcludeChange(allCandidatePackages, true)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("모두 해제")
            }
        }

        presentation.groups.forEach { group ->
            AppSelectionGroup(
                group = group,
                suppressEnabled = suppressEnabled,
                onSuppressedSourceAppToggle = onSuppressedSourceAppToggle,
            )
        }
    }
}
