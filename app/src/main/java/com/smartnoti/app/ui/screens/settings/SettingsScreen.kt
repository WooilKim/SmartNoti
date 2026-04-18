package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SettingsCardHeader
import com.smartnoti.app.ui.components.SettingsToggleRow
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentOnContainer
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onInsightClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val suppressionInsightsBuilder = remember { SuppressionInsightsBuilder() }
    val suppressionBreakdownBuilder = remember { SuppressionBreakdownChartModelBuilder() }
    val suppressionDrillDownTargetsBuilder = remember { SuppressionInsightDrillDownTargetsBuilder() }
    val settings by repository.observeSettings().collectAsState(initial = SmartNotiSettings())
    val filteredCapturedAppsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeCapturedAppsFiltered(settings.hidePersistentNotifications)
    }
    val filteredCapturedApps by filteredCapturedAppsFlow.collectAsState(initial = emptyList())
    val filteredNotificationsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredNotificationsFlow.collectAsState(initial = emptyList())
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
    val summaryBuilder = remember { SettingsDisclosureSummaryBuilder() }
    val suppressionSummaryBuilder = remember { SettingsSuppressionInsightSummaryBuilder() }
    val operationalSummaryBuilder = remember { SettingsOperationalSummaryBuilder() }
    val operationalSummary = remember(settings) { operationalSummaryBuilder.build(settings) }
    var deliveryProfilesExpanded by rememberSaveable { mutableStateOf(false) }
    var suppressionAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var suppressedAppsExpanded by rememberSaveable { mutableStateOf(false) }

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
                onQuietHoursEnabledChange = { enabled ->
                    scope.launch { repository.setQuietHoursEnabled(enabled) }
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
                onSuppressedSourceAppToggle = { packageName, enabled ->
                    scope.launch { repository.toggleSuppressedSourceApp(packageName, enabled) }
                },
            )
        }
        item {
            NotificationAccessCard()
        }
    }
}

@Composable
private fun OperationalSummaryCard(
    summary: SettingsOperationalSummary,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
) {
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
            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            OperationalSummaryRow(
                label = "Digest 시간",
                value = summary.digestSchedule,
                detail = summary.digestDetail,
            )
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
    onSuppressedSourceAppToggle: (String, Boolean) -> Unit,
) {
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
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                onSuppressedSourceAppToggle = onSuppressedSourceAppToggle,
            )
        }
    }
}

@Composable
private fun SuppressedSourceAppChips(
    filteredCapturedApps: List<CapturedAppSelectionItem>,
    suppressedSourceApps: Set<String>,
    suppressEnabled: Boolean,
    onSuppressedSourceAppToggle: (String, Boolean) -> Unit,
) {
    if (filteredCapturedApps.isEmpty()) {
        Text(
            text = "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val presentation = remember(filteredCapturedApps, suppressedSourceApps) {
        SettingsSuppressedAppPresentationBuilder().build(
            apps = filteredCapturedApps.map { app ->
                RawSuppressedAppState(
                    app = app,
                    isSelected = app.packageName in suppressedSourceApps,
                )
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = presentation.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
private fun NotificationAccessCard() {
    SmartSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
    ) {
        SettingsCardHeader(
            eyebrow = "알림 접근 권한",
            title = "시스템 설정 연결",
            subtitle = "시스템 설정에서 SmartNoti 알림 접근을 켜면 들어오는 알림을 홈 화면에 반영할 수 있어요.",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "한 번만 연결하면 들어오는 알림이 Home·Priority·Digest 흐름에 바로 반영돼요.",
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "설정 경로",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "설정 → 알림 → 기기 및 앱 알림 → 알림 읽기",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
                Text(
                    text = "SmartNoti를 켜면 실제 캡처된 알림만 홈 화면에 쌓이고, 추천 규칙 효과도 최근 데이터 기준으로 보여줘요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
