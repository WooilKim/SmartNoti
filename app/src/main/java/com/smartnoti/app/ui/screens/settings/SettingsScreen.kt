package com.smartnoti.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.SuppressionBreakdownItem
import com.smartnoti.app.domain.usecase.SuppressionInsightDrillDownTargets
import com.smartnoti.app.domain.usecase.SuppressionInsightDrillDownTargetsBuilder
import com.smartnoti.app.domain.usecase.SuppressionInsightsBuilder
import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SettingsCardHeader
import com.smartnoti.app.ui.components.SettingsToggleRow
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.notificationaccess.notificationAccessLifecycleObserver
import com.smartnoti.app.ui.notificationaccess.openNotificationAccessSettings
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentOnContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onInsightClick: (String) -> Unit,
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: non-null only
    // when the `showIgnoredArchive` toggle is on — Settings surfaces the
    // archive entry row conditionally on this callback. AppNavHost owns the
    // route registration so this screen does not depend on nav routes
    // directly.
    onOpenIgnoredArchive: (() -> Unit)? = null,
    // Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
    // Task 11 demotes the Rules tab from BottomNav to a Settings sub-menu
    // ("고급 규칙 편집"). The callback is optional — when null (e.g. from a
    // future preview that doesn't wire nav) the row simply doesn't render.
    onOpenAdvancedRules: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val suppressionInsightsBuilder = remember { SuppressionInsightsBuilder() }
    val suppressionBreakdownBuilder = remember { SuppressionBreakdownChartModelBuilder() }
    val suppressionDrillDownTargetsBuilder = remember { SuppressionInsightDrillDownTargetsBuilder() }
    val settings by repository.observeSettings().collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    val filteredCapturedAppsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeCapturedAppsFiltered(settings.hidePersistentNotifications)
    }
    val filteredCapturedApps by filteredCapturedAppsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredNotificationsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredNotificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val suppressionInsights = remember(filteredCapturedApps, filteredNotifications, settings.suppressedSourceApps) {
        suppressionInsightsBuilder.build(
            capturedApps = filteredCapturedApps,
            notifications = filteredNotifications,
            suppressedPackages = settings.suppressedSourceApps,
        )
    }
    val suppressionBreakdownItems = remember(suppressionInsights) {
        suppressionBreakdownBuilder.build(suppressionInsights.appInsights).items
    }
    val suppressionDrillDownTargets = remember(suppressionInsights, suppressionBreakdownItems) {
        suppressionDrillDownTargetsBuilder.build(
            summary = suppressionInsights,
            breakdownItems = suppressionBreakdownItems,
        )
    }
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val summaryBuilder = remember { SettingsDisclosureSummaryBuilder() }
    val suppressionSummaryBuilder = remember { SettingsSuppressionInsightSummaryBuilder() }
    val operationalSummaryBuilder = remember { SettingsOperationalSummaryBuilder() }
    val quietHoursPickerSpecBuilder = remember { QuietHoursWindowPickerSpecBuilder() }
    // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
    // Pure spec builder for the "조용한 시간 대상 앱" sub-row + picker —
    // mirrors the start/end hour picker spec so the Compose code stays a
    // thin renderer and visibility / count / empty-warning live in
    // unit-testable territory.
    val quietHoursPackagesPickerSpecBuilder = remember { QuietHoursPackagesPickerSpecBuilder() }
    // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 5.
    // Pure spec builder for the "중복 알림 묶기" editor row — keeps option
    // lists / labels in one place so the Compose code stays a thin renderer.
    val duplicateThresholdEditorSpecBuilder = remember { DuplicateThresholdEditorSpecBuilder() }
    val notificationAccessSummaryBuilder = remember { SettingsNotificationAccessSummaryBuilder() }
    var notificationAccessStatus by remember { mutableStateOf(OnboardingPermissions.currentStatus(context)) }
    val operationalSummary = remember(settings) { operationalSummaryBuilder.build(settings) }
    val quietHoursPickerSpec = remember(settings) { quietHoursPickerSpecBuilder.build(settings) }
    val quietHoursPackagesPickerSpec = remember(settings, filteredCapturedApps) {
        quietHoursPackagesPickerSpecBuilder.build(
            settings = settings,
            capturedApps = filteredCapturedApps,
        )
    }
    val duplicateThresholdEditorSpec = remember(settings) {
        duplicateThresholdEditorSpecBuilder.build(settings)
    }
    val notificationAccessSummary = remember(notificationAccessStatus) {
        notificationAccessSummaryBuilder.build(notificationAccessStatus)
    }
    var deliveryProfilesExpanded by rememberSaveable { mutableStateOf(false) }
    var suppressionAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var suppressedAppsExpanded by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = notificationAccessLifecycleObserver(
            statusProvider = { OnboardingPermissions.currentStatus(context) },
            onStatusChanged = { notificationAccessStatus = it },
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val priorityCallbacks = remember(repository, scope) {
        DeliveryProfileCallbacks(
            onAlertLevelChange = { value -> scope.launch { repository.setPriorityAlertLevel(value) } },
            onVibrationModeChange = { value -> scope.launch { repository.setPriorityVibrationMode(value) } },
            onHeadsUpChange = { enabled -> scope.launch { repository.setPriorityHeadsUpEnabled(enabled) } },
            onLockScreenVisibilityChange = { value ->
                scope.launch { repository.setPriorityLockScreenVisibility(value) }
            },
        )
    }
    val digestCallbacks = remember(repository, scope) {
        DeliveryProfileCallbacks(
            onAlertLevelChange = { value -> scope.launch { repository.setDigestAlertLevel(value) } },
            onVibrationModeChange = { value -> scope.launch { repository.setDigestVibrationMode(value) } },
            onHeadsUpChange = { enabled -> scope.launch { repository.setDigestHeadsUpEnabled(enabled) } },
            onLockScreenVisibilityChange = { value ->
                scope.launch { repository.setDigestLockScreenVisibility(value) }
            },
        )
    }
    val silentCallbacks = remember(repository, scope) {
        DeliveryProfileCallbacks(
            onAlertLevelChange = { value -> scope.launch { repository.setSilentAlertLevel(value) } },
            onVibrationModeChange = { value -> scope.launch { repository.setSilentVibrationMode(value) } },
            onHeadsUpChange = { enabled -> scope.launch { repository.setSilentHeadsUpEnabled(enabled) } },
            onLockScreenVisibilityChange = { value ->
                scope.launch { repository.setSilentLockScreenVisibility(value) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "설정",
                title = "설정",
                subtitle = "알림 분류 동작을 운영 도구처럼 명확하게 조정할 수 있어요.",
            )
        }
        item {
            OperationalSummaryCard(
                summary = operationalSummary,
                quietHoursPickerSpec = quietHoursPickerSpec,
                quietHoursPackagesPickerSpec = quietHoursPackagesPickerSpec,
                duplicateThresholdEditorSpec = duplicateThresholdEditorSpec,
                onQuietHoursEnabledChange = { enabled ->
                    scope.launch { repository.setQuietHoursEnabled(enabled) }
                },
                onQuietHoursStartHourChange = { hour ->
                    scope.launch { repository.setQuietHoursStartHour(hour) }
                },
                onQuietHoursEndHourChange = { hour ->
                    scope.launch { repository.setQuietHoursEndHour(hour) }
                },
                // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
                // Task 6 callbacks. The picker bottom sheet calls add/remove
                // for single-row interactions and the "앱 추가" affordance can
                // commit a multi-select result via setQuietHoursPackages.
                onQuietHoursPackageAdd = { packageName ->
                    scope.launch { repository.addQuietHoursPackage(packageName) }
                },
                onQuietHoursPackageRemove = { packageName ->
                    scope.launch { repository.removeQuietHoursPackage(packageName) }
                },
                onQuietHoursPackagesReplace = { packageNames ->
                    scope.launch { repository.setQuietHoursPackages(packageNames) }
                },
                onDuplicateThresholdChange = { value ->
                    scope.launch { repository.setDuplicateDigestThreshold(value) }
                },
                onDuplicateWindowMinutesChange = { value ->
                    scope.launch { repository.setDuplicateWindowMinutes(value) }
                },
            )
        }
        item {
            DeliveryProfileSettingsCard(
                settings = settings,
                priorityCallbacks = priorityCallbacks,
                digestCallbacks = digestCallbacks,
                silentCallbacks = silentCallbacks,
                summaryBuilder = summaryBuilder,
                expanded = deliveryProfilesExpanded,
                onExpandedChange = { deliveryProfilesExpanded = it },
            )
        }
        item {
            SuppressionManagementCard(
                settings = settings,
                filteredCapturedApps = filteredCapturedApps,
                disclosureSummaryBuilder = summaryBuilder,
                insightSummaryBuilder = suppressionSummaryBuilder,
                summary = suppressionInsights,
                breakdownItems = suppressionBreakdownItems,
                topAppRoute = suppressionDrillDownTargets.topAppRoute,
                breakdownRoutesByAppName = suppressionDrillDownTargets.breakdownRoutesByAppName,
                onInsightClick = onInsightClick,
                advancedExpanded = suppressionAdvancedExpanded,
                onAdvancedExpandedChange = { suppressionAdvancedExpanded = it },
                appsExpanded = suppressedAppsExpanded,
                onAppsExpandedChange = { suppressedAppsExpanded = it },
                onSuppressSourceChange = { enabled ->
                    scope.launch { repository.setSuppressSourceForDigestAndSilent(enabled) }
                },
                onHidePersistentNotificationsChange = { enabled ->
                    scope.launch { repository.setHidePersistentNotifications(enabled) }
                },
                onHidePersistentSourceNotificationsChange = { enabled ->
                    scope.launch { repository.setHidePersistentSourceNotifications(enabled) }
                },
                onProtectCriticalPersistentNotificationsChange = { enabled ->
                    scope.launch { repository.setProtectCriticalPersistentNotifications(enabled) }
                },
                // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
                // Two callbacks for the auto-dismiss controls — toggle the
                // master switch + persist the selected duration. The notifier
                // reads the latest snapshot via the listener's settings flow,
                // so the next post after the write picks up the change.
                onReplacementAutoDismissEnabledChange = { enabled ->
                    scope.launch { repository.setReplacementAutoDismissEnabled(enabled) }
                },
                onReplacementAutoDismissMinutesChange = { minutes ->
                    scope.launch { repository.setReplacementAutoDismissMinutes(minutes) }
                },
                onSuppressedSourceAppToggle = { packageName, enabled ->
                    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 6.
                    // Toggling OFF must persist sticky-exclude intent so the
                    // next DIGEST notification cannot re-add the package via
                    // auto-expansion. Toggling ON clears the exclude entry
                    // AND adds the package to `suppressedSourceApps` so the
                    // user's explicit opt-in is recorded even when the list
                    // was previously empty (default opt-out semantic).
                    scope.launch {
                        if (enabled) {
                            repository.setSuppressedSourceAppExcluded(packageName, excluded = false)
                            repository.toggleSuppressedSourceApp(packageName, true)
                        } else {
                            repository.setSuppressedSourceAppExcluded(packageName, excluded = true)
                        }
                    }
                },
                onSuppressedSourceAppsReplace = { packageNames ->
                    scope.launch { repository.setSuppressedSourceApps(packageNames) }
                },
                onSuppressedSourceAppsBulkExcludeChange = { packageNames, excluded ->
                    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 6.4.
                    // Bulk variant for the "모두 선택 / 모두 해제" buttons.
                    scope.launch {
                        repository.setSuppressedSourceAppsExcludedBulk(packageNames, excluded)
                    }
                },
            )
        }
        item {
            IgnoredArchiveSettingsCard(
                showIgnoredArchive = settings.showIgnoredArchive,
                onShowIgnoredArchiveChange = { enabled ->
                    scope.launch { repository.setShowIgnoredArchive(enabled) }
                },
                onOpenIgnoredArchive = onOpenIgnoredArchive,
            )
        }
        if (onOpenAdvancedRules != null) {
            item {
                AdvancedRulesEntryCard(onClick = onOpenAdvancedRules)
            }
        }
        item {
            NotificationAccessCard(
                summary = notificationAccessSummary,
                onOpenSettings = {
                    openNotificationAccessSettings(context)
                },
            )
        }
    }
}

/**
 * "고급 규칙 편집" entry card — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11.
 * Rules are no longer a top-level BottomNav tab; power users who still want to
 * edit matcher rules directly enter through Settings. The copy deliberately
 * frames it as advanced so the default flow goes through 분류.
 */
@Composable
private fun AdvancedRulesEntryCard(onClick: () -> Unit) {
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
private fun IgnoredArchiveSettingsCard(
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

@Composable
private fun OperationalSummaryCard(
    summary: SettingsOperationalSummary,
    quietHoursPickerSpec: QuietHoursWindowPickerSpec,
    quietHoursPackagesPickerSpec: QuietHoursPackagesPickerSpec,
    duplicateThresholdEditorSpec: DuplicateThresholdEditorSpec,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursStartHourChange: (Int) -> Unit,
    onQuietHoursEndHourChange: (Int) -> Unit,
    onQuietHoursPackageAdd: (String) -> Unit,
    onQuietHoursPackageRemove: (String) -> Unit,
    onQuietHoursPackagesReplace: (Set<String>) -> Unit,
    onDuplicateThresholdChange: (Int) -> Unit,
    onDuplicateWindowMinutesChange: (Int) -> Unit,
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
                onThresholdSelected = onDuplicateThresholdChange,
                onWindowMinutesSelected = onDuplicateWindowMinutesChange,
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

@Composable
private fun DuplicateThresholdEditorRow(
    spec: DuplicateThresholdEditorSpec,
    onThresholdSelected: (Int) -> Unit,
    onWindowMinutesSelected: (Int) -> Unit,
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

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
 *
 * Duration picker for the auto-dismiss timeout. Mirrors the
 * `DuplicateWindowPicker` AssistChip + DropdownMenu pattern so the visual tone
 * stays consistent with the other Settings dropdowns.
 */
@Composable
private fun ReplacementAutoDismissDurationPicker(
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
private fun QuietHoursWindowPickerRow(
    spec: QuietHoursWindowPickerSpec,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietHoursHourPicker(
                label = "시작",
                selectedHour = spec.startHour,
                options = spec.hourOptions,
                onHourSelected = onStartHourChange,
                modifier = Modifier.weight(1f),
            )
            QuietHoursHourPicker(
                label = "종료",
                selectedHour = spec.endHour,
                options = spec.hourOptions,
                onHourSelected = onEndHourChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (spec.sameValueWarning != null) {
            Text(
                text = spec.sameValueWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QuietHoursHourPicker(
    label: String,
    selectedHour: Int,
    options: List<QuietHoursWindowPickerSpec.HourOption>,
    onHourSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.hour == selectedHour }?.label
        ?: "%02d:00".format(selectedHour)
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
                            onHourSelected(option.hour)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Sub-row for the new "조용한 시간 대상 앱" picker. Mirrors the visual tone
 * of the surrounding QuietHours editor row — a labelled count + tap target
 * that opens the bottom-sheet picker. Empty-set warning is rendered inline
 * so the user sees the silently-no-op signal even before opening the sheet.
 */
@Composable
private fun QuietHoursPackagesPickerRow(
    spec: QuietHoursPackagesPickerSpec,
    onOpenPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPicker),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.ROW_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.rowSummary(spec.selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = spec.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "조용한 시간 대상 앱 편집",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (spec.emptyWarning != null) {
            Text(
                text = spec.emptyWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Modal bottom sheet body for editing the `quietHoursPackages` set. The
 * sheet has two stacked sections:
 *   - Currently-selected rows with a per-row Close affordance that calls
 *     `onRemovePackage`. Empty state surfaces the same warning copy as the
 *     row above so the user gets a consistent signal.
 *   - Candidate rows (captured apps not yet in the set) with an Add
 *     affordance. The plan intentionally limits candidates to apps that
 *     have actually posted notifications during this session — wiring a
 *     full installed-app picker is Out of scope per the plan's Scope/Out.
 *
 * The sheet does not close on add/remove so the user can perform multiple
 * edits in one session; "닫기" returns to the Settings card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursPackagesPickerSheet(
    spec: QuietHoursPackagesPickerSpec,
    onDismiss: () -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (spec.emptyWarning != null) {
                Text(
                    text = spec.emptyWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            QuietHoursPackagesSelectedSection(
                items = spec.items,
                onRemovePackage = onRemovePackage,
            )
            QuietHoursPackagesCandidateSection(
                candidates = spec.candidates,
                onAddPackage = onAddPackage,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    enabled = spec.selectedCount > 0,
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("모두 비우기")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("닫기")
                }
            }
        }
    }
}

@Composable
private fun QuietHoursPackagesSelectedSection(
    items: List<QuietHoursPackagesPickerSpec.Item>,
    onRemovePackage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "현재 선택된 앱",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (items.isEmpty()) {
            Text(
                text = QuietHoursPackagesPickerSpecBuilder.NO_TARGETS_SUMMARY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        SmartSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.supporting ?: item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onRemovePackage(item.packageName) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "${item.displayName} 제거",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietHoursPackagesCandidateSection(
    candidates: List<CapturedAppSelectionItem>,
    onAddPackage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = QuietHoursPackagesPickerSpecBuilder.ADD_BUTTON_LABEL,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (candidates.isEmpty()) {
            Text(
                text = QuietHoursPackagesPickerSpecBuilder.NO_CANDIDATES_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        SmartSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            candidates.forEachIndexed { index, candidate ->
                if (index > 0) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = candidate.appName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${candidate.notificationCount}건 · ${candidate.lastSeenLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onAddPackage(candidate.packageName) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "${candidate.appName} 추가",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationalSummaryRow(
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

private data class DeliveryProfileCallbacks(
    val onAlertLevelChange: (String) -> Unit,
    val onVibrationModeChange: (String) -> Unit,
    val onHeadsUpChange: (Boolean) -> Unit,
    val onLockScreenVisibilityChange: (String) -> Unit,
)

@Composable
private fun DeliveryProfileSettingsCard(
    settings: SmartNotiSettings,
    priorityCallbacks: DeliveryProfileCallbacks,
    digestCallbacks: DeliveryProfileCallbacks,
    silentCallbacks: DeliveryProfileCallbacks,
    summaryBuilder: SettingsDisclosureSummaryBuilder,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val priorityTokens = remember(
        settings.priorityAlertLevel,
        settings.priorityVibrationMode,
        settings.priorityHeadsUpEnabled,
        settings.priorityLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.priorityAlertLevel,
            vibrationMode = settings.priorityVibrationMode,
            headsUpEnabled = settings.priorityHeadsUpEnabled,
            lockScreenVisibility = settings.priorityLockScreenVisibility,
        )
    }
    val digestTokens = remember(
        settings.digestAlertLevel,
        settings.digestVibrationMode,
        settings.digestHeadsUpEnabled,
        settings.digestLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.digestAlertLevel,
            vibrationMode = settings.digestVibrationMode,
            headsUpEnabled = settings.digestHeadsUpEnabled,
            lockScreenVisibility = settings.digestLockScreenVisibility,
        )
    }
    val silentTokens = remember(
        settings.silentAlertLevel,
        settings.silentVibrationMode,
        settings.silentHeadsUpEnabled,
        settings.silentLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.silentAlertLevel,
            vibrationMode = settings.silentVibrationMode,
            headsUpEnabled = settings.silentHeadsUpEnabled,
            lockScreenVisibility = settings.silentLockScreenVisibility,
        )
    }

    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        ExpandableSettingsSectionHeader(
            title = "대체 알림 전달 방식",
            subtitle = if (expanded) {
                "설정한 값은 SmartNoti가 원본 알림을 대신 보여줄 때만 적용돼요."
            } else {
                "Priority·Digest·Silent 현재 상태를 먼저 확인하고, 필요할 때만 세부 제약을 펼쳐 조정해요."
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeliveryProfileSummaryRow(
                title = "Priority",
                modeLabel = "즉시 대응",
                accentColor = PriorityOnContainer,
                tokens = priorityTokens,
            )
            DeliveryProfileSummaryRow(
                title = "Digest",
                modeLabel = "묶음 확인",
                accentColor = DigestOnContainer,
                tokens = digestTokens,
            )
            DeliveryProfileSummaryRow(
                title = "Silent",
                modeLabel = "비침습 모드",
                accentColor = SilentOnContainer,
                tokens = silentTokens,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeliveryProfileEditorSection(
                    title = "Priority",
                    subtitle = "중요 알림은 필요할 때 강하게 알리되, 조용한 시간이나 반복 상황에서는 자동으로 낮아질 수 있어요.",
                    accentColor = PriorityOnContainer,
                    summaryTokens = priorityTokens,
                    selectedAlertLevel = settings.priorityAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.LOUD, AlertLevel.SOFT, AlertLevel.QUIET),
                    onAlertLevelChange = priorityCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.priorityVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.STRONG, VibrationMode.LIGHT, VibrationMode.OFF),
                    onVibrationModeChange = priorityCallbacks.onVibrationModeChange,
                    headsUpEnabled = settings.priorityHeadsUpEnabled,
                    headsUpEnabledAllowed = true,
                    headsUpDescription = "Heads-up 표시",
                    onHeadsUpChange = priorityCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.priorityLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PUBLIC,
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = priorityCallbacks.onLockScreenVisibilityChange,
                )
                DeliveryProfileEditorSection(
                    title = "Digest",
                    subtitle = "Digest는 조용히 다시 확인하는 용도라서 loud·강한 진동·heads-up은 허용하지 않아요.",
                    accentColor = DigestOnContainer,
                    summaryTokens = digestTokens,
                    selectedAlertLevel = settings.digestAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.SOFT, AlertLevel.QUIET, AlertLevel.NONE),
                    onAlertLevelChange = digestCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.digestVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.LIGHT, VibrationMode.OFF),
                    onVibrationModeChange = digestCallbacks.onVibrationModeChange,
                    headsUpEnabled = false,
                    headsUpEnabledAllowed = false,
                    headsUpDescription = "Digest는 heads-up을 사용하지 않음",
                    onHeadsUpChange = digestCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.digestLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = digestCallbacks.onLockScreenVisibilityChange,
                )
                DeliveryProfileEditorSection(
                    title = "Silent",
                    subtitle = "Silent는 항상 비침습적으로 유지돼요. 소리·진동·heads-up은 사용할 수 없어요.",
                    accentColor = SilentOnContainer,
                    summaryTokens = silentTokens,
                    selectedAlertLevel = settings.silentAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.NONE, AlertLevel.QUIET),
                    onAlertLevelChange = silentCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.silentVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.OFF),
                    onVibrationModeChange = silentCallbacks.onVibrationModeChange,
                    headsUpEnabled = false,
                    headsUpEnabledAllowed = false,
                    headsUpDescription = "Silent는 heads-up을 사용하지 않음",
                    onHeadsUpChange = silentCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.silentLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = silentCallbacks.onLockScreenVisibilityChange,
                )
            }
        }
    }
}

@Composable
private fun DeliveryProfileSummaryRow(
    title: String,
    modeLabel: String,
    accentColor: Color,
    tokens: DeliveryProfileSummaryTokens,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = accentColor, shape = RoundedCornerShape(999.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = tokens.toSummaryLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

@Composable
private fun DeliveryProfileEditorSection(
    title: String,
    subtitle: String,
    accentColor: Color,
    summaryTokens: DeliveryProfileSummaryTokens,
    selectedAlertLevel: String,
    allowedAlertLevels: List<AlertLevel>,
    onAlertLevelChange: (String) -> Unit,
    selectedVibrationMode: String,
    allowedVibrationModes: List<VibrationMode>,
    onVibrationModeChange: (String) -> Unit,
    headsUpEnabled: Boolean,
    headsUpEnabledAllowed: Boolean,
    headsUpDescription: String,
    onHeadsUpChange: (Boolean) -> Unit,
    selectedLockScreenVisibility: String,
    allowedLockScreenModes: List<LockScreenVisibilityMode>,
    onLockScreenVisibilityChange: (String) -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = accentColor, shape = RoundedCornerShape(999.dp)),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "현재 ${summaryTokens.toSummaryLine()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DeliveryProfileControlGroup(title = "신호") {
            DeliveryProfileOptionRow(
                label = "알림",
                selected = selectedAlertLevel,
                options = allowedAlertLevels.map { it.name to it.toKoreanLabel() },
                onSelected = onAlertLevelChange,
            )
            DeliveryProfileOptionRow(
                label = "진동",
                selected = selectedVibrationMode,
                options = allowedVibrationModes.map { it.name to it.toKoreanLabel() },
                onSelected = onVibrationModeChange,
            )
        }
        HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
        DeliveryProfileControlGroup(title = "표시와 보호") {
            DeliveryProfileHeadsUpRow(
                headsUpEnabled = headsUpEnabled,
                headsUpEnabledAllowed = headsUpEnabledAllowed,
                headsUpDescription = headsUpDescription,
                onHeadsUpChange = onHeadsUpChange,
            )
            DeliveryProfileOptionRow(
                label = "잠금 화면",
                selected = selectedLockScreenVisibility,
                options = allowedLockScreenModes.map { it.name to it.toKoreanLabel() },
                onSelected = onLockScreenVisibilityChange,
            )
        }
    }
}

@Composable
private fun DeliveryProfileControlGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryProfileOptionRow(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.first == selected,
                    onClick = { onSelected(option.first) },
                    label = { Text(option.second) },
                )
            }
        }
    }
}

@Composable
private fun DeliveryProfileHeadsUpRow(
    headsUpEnabled: Boolean,
    headsUpEnabledAllowed: Boolean,
    headsUpDescription: String,
    onHeadsUpChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Heads-up",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = headsUpDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!headsUpEnabledAllowed) {
                Text(
                    text = "안전한 전달을 위해 고정됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = headsUpEnabled,
            enabled = headsUpEnabledAllowed,
            onCheckedChange = onHeadsUpChange,
        )
    }
}

@Composable
private fun ExpandableSettingsSectionHeader(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                rotationZ = if (expanded) 90f else 0f
            },
        )
    }
}

private fun DeliveryProfileSummaryTokens.toSummaryLine(): String =
    listOf(alertLabel, vibrationLabel, headsUpLabel, lockScreenLabel).joinToString(" · ")

@Composable
private fun SettingsSubsection(
    title: String,
    subtitle: String? = null,
    isFirst: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isFirst) {
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun ExpandableSettingsSubsection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
        ExpandableSettingsSectionHeader(
            title = title,
            subtitle = subtitle,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SuppressionInsightMetricStrip(metrics: List<SuppressionInsightMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowMetrics.forEach { metric ->
                    SuppressionInsightMetricCard(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowMetrics.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuppressionInsightMetricCard(
    metric: SuppressionInsightMetric,
    modifier: Modifier = Modifier,
) {
    SmartSurfaceCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = metric.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SuppressionBreakdownList(
    items: List<SuppressionBreakdownItem>,
    routeByAppName: Map<String, String>,
    onInsightClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            SuppressionBreakdownRow(
                item = item,
                route = routeByAppName[item.appName],
                onInsightClick = onInsightClick,
            )
        }
    }
}

@Composable
private fun SuppressionBreakdownRow(
    item: SuppressionBreakdownItem,
    route: String?,
    onInsightClick: (String) -> Unit,
) {
    Column(
        modifier = if (route != null) {
            Modifier.clickable { onInsightClick(route) }
        } else {
            Modifier
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${item.filteredCount}건 정리 · ${(item.shareFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (route != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(999.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(item.shareFraction.coerceIn(0f, 1f))
                    .background(
                        color = if (item.isTopApp) GreenAccent else DigestOnContainer,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

@Composable
private fun SuppressedAppInsightsList(
    appInsights: List<SuppressedAppInsight>,
    topAppRoute: String?,
    onInsightClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        appInsights.take(3).forEachIndexed { index, appInsight ->
            if (index > 0) {
                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            }
            SuppressedAppInsightRow(
                appInsight = appInsight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                onClick = if (index == 0 && topAppRoute != null) {
                    { onInsightClick(topAppRoute) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SuppressionManagementCard(
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
private fun SuppressedSourceAppChips(
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

@Composable
private fun AppSelectionGroup(
    group: SettingsSuppressedAppGroup,
    suppressEnabled: Boolean,
    onSuppressedSourceAppToggle: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SmartSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            group.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                }
                AppSelectionRow(
                    item = item,
                    enabled = suppressEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onToggle = { packageName, selected ->
                        onSuppressedSourceAppToggle(packageName, selected)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppSelectionRow(
    item: SettingsSuppressedAppItem,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (String, Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(
                            color = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${item.notificationCount}건 · ${item.lastSeenLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FilterChip(
            selected = item.isSelected,
            enabled = enabled,
            onClick = { onToggle(item.packageName, !item.isSelected) },
            label = { Text(if (item.isSelected) "선택됨" else "선택") },
        )
    }
}

@Composable
private fun NotificationAccessCard(
    summary: SettingsNotificationAccessSummary,
    onOpenSettings: () -> Unit,
) {
    val accentColor = if (summary.granted) GreenAccent else MaterialTheme.colorScheme.primary
    val impactTitle = if (summary.granted) "현재 반영 효과" else "켜면 생기는 변화"
    SmartSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SettingsCardHeader(
                eyebrow = "알림 접근",
                title = "실제 알림 연결 상태",
                subtitle = summary.headline,
                modifier = Modifier.weight(1f),
            )
            ContextBadge(
                label = summary.statusLabel,
                containerColor = accentColor.copy(alpha = 0.18f),
                contentColor = accentColor,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = summary.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "설정 경로",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = summary.pathDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
                Text(
                    text = impactTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
                Text(
                    text = summary.impactDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = summary.actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SuppressedAppInsightRow(
    appInsight: SuppressedAppInsight,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val prefix = if (appInsight.isSuppressed) "선택됨" else "관찰 중"
    Row(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(
                            color = if (appInsight.isSuppressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = appInsight.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$prefix · ${appInsight.filteredCount}건 정리 · ${appInsight.filteredSharePercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = appInsight.lastSeenLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
