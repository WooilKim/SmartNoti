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

@Composable
internal fun ExpandableSettingsSectionHeader(
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
