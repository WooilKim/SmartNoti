package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp

import android.content.Intent
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.diagnostic.DiagnosticLogExporter
import com.smartnoti.app.diagnostic.DiagnosticLoggerProvider
import com.smartnoti.app.diagnostic.DiagnosticLoggingPreferences
import com.smartnoti.app.domain.usecase.SuppressionBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.SuppressionInsightDrillDownTargetsBuilder
import com.smartnoti.app.domain.usecase.SuppressionInsightsBuilder
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.notificationaccess.notificationAccessLifecycleObserver
import com.smartnoti.app.ui.notificationaccess.openNotificationAccessSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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
    // Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
    // Task 3 — diagnostic logging preferences + export wiring. Both
    // singletons resolved via `remember(context)` so they survive recomposition
    // without re-initialising the DataStore-backed cache.
    val diagnosticPreferences = remember(context) {
        DiagnosticLoggingPreferences.getInstance(context)
    }
    val diagnosticExporter = remember(context) {
        // The provider creates the logger AND the diagnostic/ subfolder; we
        // point the exporter at the same subfolder so URIs resolve under the
        // FileProvider authority declared in AndroidManifest.xml.
        DiagnosticLoggerProvider.getInstance(context) // ensure subfolder exists
        DiagnosticLogExporter(
            File(context.filesDir, DiagnosticLoggerProvider.DIAGNOSTIC_DIR_NAME),
        )
    }
    val diagnosticLoggingEnabled by diagnosticPreferences.observeEnabled()
        .collectAsStateWithLifecycle(initialValue = false)
    val diagnosticRawTitleBody by diagnosticPreferences.observeRawTitleBody()
        .collectAsStateWithLifecycle(initialValue = false)
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
        item {
            // Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
            // Task 3 — Settings → "진단" section. Default OFF; opt-in
            // toggles wire through `DiagnosticLoggingPreferences` which the
            // logger reads on every call.
            SettingsDiagnosticSection(
                state = SettingsDiagnosticSectionState(
                    loggingEnabled = diagnosticLoggingEnabled,
                    rawTitleBodyEnabled = diagnosticRawTitleBody,
                ),
                onLoggingEnabledChange = { enabled ->
                    scope.launch { diagnosticPreferences.setEnabled(enabled) }
                },
                onRawTitleBodyEnabledChange = { enabled ->
                    scope.launch { diagnosticPreferences.setRawTitleBodyEnabled(enabled) }
                },
                onExportClick = {
                    val intent = diagnosticExporter.buildShareIntent(context)
                    val chooser = Intent.createChooser(intent, "진단 로그 공유")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                },
            )
        }
    }
}

