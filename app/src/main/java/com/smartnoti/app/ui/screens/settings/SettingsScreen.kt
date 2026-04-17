package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.SuppressionBreakdownItem
import com.smartnoti.app.domain.usecase.SuppressionInsightDrillDownTargetsBuilder
import com.smartnoti.app.domain.usecase.SuppressionInsightsBuilder
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val settings by repository.observeSettings().collectAsState(
        initial = com.smartnoti.app.data.settings.SmartNotiSettings()
    )
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

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "Settings",
                title = "설정",
                subtitle = "알림 분류 동작을 운영 도구처럼 명확하게 조정할 수 있어요.",
            )
        }
        item {
            SectionLabel(
                title = "현재 모드",
                subtitle = "SmartNoti가 지금 어떤 기준으로 알림을 다루는지 보여줘요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (settings.quietHoursEnabled) "조용한 시간 자동 적용" else "항상 즉시 분류",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (settings.quietHoursEnabled) {
                        "지정한 시간대에는 덜 급한 알림을 정리함 중심으로 다뤄요."
                    } else {
                        "모든 시간대에 동일한 기준으로 바로 분류해요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel(
                title = "Quiet Hours",
                subtitle = "자동 완화 시간대를 확인하고 즉시 켜거나 끌 수 있어요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${settings.quietHoursStartHour}:00 ~ ${settings.quietHoursEndHour}:00",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "조용한 시간 사용",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setQuietHoursEnabled(enabled)
                            }
                        }
                    )
                }
            }
        }
        item {
            SectionLabel(
                title = "알림 전달 방식",
                subtitle = "Priority/Digest/Silent 대체 알림의 주목도를 안전한 범위 안에서 조절해요.",
            )
        }
        item {
            DeliveryProfileSettingsCard(
                settings = settings,
                onPriorityAlertLevelChange = { value -> scope.launch { repository.setPriorityAlertLevel(value) } },
                onPriorityVibrationChange = { value -> scope.launch { repository.setPriorityVibrationMode(value) } },
                onPriorityHeadsUpChange = { enabled -> scope.launch { repository.setPriorityHeadsUpEnabled(enabled) } },
                onPriorityLockScreenChange = { value -> scope.launch { repository.setPriorityLockScreenVisibility(value) } },
                onDigestAlertLevelChange = { value -> scope.launch { repository.setDigestAlertLevel(value) } },
                onDigestVibrationChange = { value -> scope.launch { repository.setDigestVibrationMode(value) } },
                onDigestHeadsUpChange = { enabled -> scope.launch { repository.setDigestHeadsUpEnabled(enabled) } },
                onDigestLockScreenChange = { value -> scope.launch { repository.setDigestLockScreenVisibility(value) } },
                onSilentAlertLevelChange = { value -> scope.launch { repository.setSilentAlertLevel(value) } },
                onSilentVibrationChange = { value -> scope.launch { repository.setSilentVibrationMode(value) } },
                onSilentHeadsUpChange = { enabled -> scope.launch { repository.setSilentHeadsUpEnabled(enabled) } },
                onSilentLockScreenChange = { value -> scope.launch { repository.setSilentLockScreenVisibility(value) } },
            )
        }
        item {
            SectionLabel(
                title = "소스 알림 처리",
                subtitle = "Digest·조용히 결정 시 원본 알림을 숨길지 선택할 수 있어요.",
            )
        }
        item {
            SuppressionInsightsCard(
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                summary = suppressionInsights,
                breakdownItems = suppressionBreakdownItems,
                topAppRoute = suppressionDrillDownTargets.topAppRoute,
                breakdownRoutesByAppName = suppressionDrillDownTargets.breakdownRoutesByAppName,
                onInsightClick = onInsightClick,
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Digest/조용히 결정 시 원본 알림 숨기기",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "켜면 선택한 앱의 중요하지 않은 알림은 SmartNoti에만 남기고, 기기 알림창의 원본 알림은 바로 감춰요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "원본 알림 숨기기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.suppressSourceForDigestAndSilent,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setSuppressSourceForDigestAndSilent(enabled)
                            }
                        }
                    )
                }
                Text(
                    if (settings.suppressSourceForDigestAndSilent) {
                        "선택한 앱만 조용히 숨기고 SmartNoti 대체 알림으로 남겨요."
                    } else {
                        "먼저 원본 알림 숨기기를 켜면 앱별 선택이 활성화돼요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "지속 알림은 SmartNoti 목록에서 숨기기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.hidePersistentNotifications,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setHidePersistentNotifications(enabled)
                            }
                        }
                    )
                }
                Text(
                    if (settings.hidePersistentNotifications) {
                        "충전 중·정리 중 같은 고정 알림은 목록/인사이트 집계에서 제외해요."
                    } else {
                        "지속 알림도 일반 알림처럼 집계와 목록에 포함해요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "지속 알림은 시스템 알림센터에서도 숨기기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.hidePersistentSourceNotifications,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setHidePersistentSourceNotifications(enabled)
                            }
                        }
                    )
                }
                Text(
                    if (settings.hidePersistentSourceNotifications) {
                        "고정 시스템 알림이 올라오면 SmartNoti가 바로 감춰요. 중요한 시스템 알림까지 숨길 수 있어 주의가 필요해요."
                    } else {
                        "고정 시스템 알림은 기기 알림센터에 그대로 남겨둬요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "통화·길안내·녹화 중 알림은 항상 보이기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.protectCriticalPersistentNotifications,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setProtectCriticalPersistentNotifications(enabled)
                            }
                        }
                    )
                }
                Text(
                    if (settings.protectCriticalPersistentNotifications) {
                        "통화 중, 길안내 중, 화면 녹화/카메라·마이크 사용 중 알림은 숨김 예외로 보호해요."
                    } else {
                        "고정 알림 예외 보호를 끄면 중요한 live-state 알림도 일반 고정 알림처럼 숨겨질 수 있어요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredCapturedApps.isEmpty()) {
                    Text(
                        "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    filteredCapturedApps.forEach { app ->
                        FilterChip(
                            selected = app.packageName in settings.suppressedSourceApps,
                            enabled = settings.suppressSourceForDigestAndSilent,
                            onClick = {
                                if (settings.suppressSourceForDigestAndSilent) {
                                    val enabled = app.packageName !in settings.suppressedSourceApps
                                    scope.launch {
                                        repository.toggleSuppressedSourceApp(app.packageName, enabled)
                                    }
                                }
                            },
                            label = {
                                Text("${app.appName} · ${app.notificationCount}건 · ${app.lastSeenLabel}")
                            },
                        )
                    }
                }
            }
        }
        item {
            SectionLabel(
                title = "Digest 시간",
                subtitle = "덜 중요한 알림을 묶어 보여줄 정리 시점을 확인해요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    settings.digestHours.joinToString(" · ") { "$it:00" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "반복되거나 덜 급한 알림은 이 시점에 맞춰 Digest로 다시 확인할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel(
                title = "알림 접근 설정",
                subtitle = "실시간 반영을 위해 필요한 시스템 권한 위치를 안내해요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "시스템 설정에서 SmartNoti 알림 접근을 켜면 들어오는 알림을 홈 화면에 반영할 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "경로: 설정 → 알림 → 기기 및 앱 알림 → 알림 읽기",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryProfileSettingsCard(
    settings: com.smartnoti.app.data.settings.SmartNotiSettings,
    onPriorityAlertLevelChange: (String) -> Unit,
    onPriorityVibrationChange: (String) -> Unit,
    onPriorityHeadsUpChange: (Boolean) -> Unit,
    onPriorityLockScreenChange: (String) -> Unit,
    onDigestAlertLevelChange: (String) -> Unit,
    onDigestVibrationChange: (String) -> Unit,
    onDigestHeadsUpChange: (Boolean) -> Unit,
    onDigestLockScreenChange: (String) -> Unit,
    onSilentAlertLevelChange: (String) -> Unit,
    onSilentVibrationChange: (String) -> Unit,
    onSilentHeadsUpChange: (Boolean) -> Unit,
    onSilentLockScreenChange: (String) -> Unit,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "대체 알림 전달 방식",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "설정한 값은 SmartNoti가 원본 알림을 대신 보여줄 때만 적용돼요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeliveryProfileEditorSection(
            title = "Priority",
            subtitle = "중요 알림은 필요할 때 강하게 알리되, 조용한 시간이나 반복 상황에서는 자동으로 낮아질 수 있어요.",
            selectedAlertLevel = settings.priorityAlertLevel,
            allowedAlertLevels = listOf(AlertLevel.LOUD, AlertLevel.SOFT, AlertLevel.QUIET),
            onAlertLevelChange = onPriorityAlertLevelChange,
            selectedVibrationMode = settings.priorityVibrationMode,
            allowedVibrationModes = listOf(VibrationMode.STRONG, VibrationMode.LIGHT, VibrationMode.OFF),
            onVibrationModeChange = onPriorityVibrationChange,
            headsUpEnabled = settings.priorityHeadsUpEnabled,
            headsUpEnabledAllowed = true,
            headsUpDescription = "Heads-up 표시",
            onHeadsUpChange = onPriorityHeadsUpChange,
            selectedLockScreenVisibility = settings.priorityLockScreenVisibility,
            allowedLockScreenModes = listOf(
                LockScreenVisibilityMode.PUBLIC,
                LockScreenVisibilityMode.PRIVATE,
                LockScreenVisibilityMode.SECRET,
            ),
            onLockScreenVisibilityChange = onPriorityLockScreenChange,
        )
        DeliveryProfileEditorSection(
            title = "Digest",
            subtitle = "Digest는 조용히 다시 확인하는 용도라서 loud/강한 진동/heads-up은 허용하지 않아요.",
            selectedAlertLevel = settings.digestAlertLevel,
            allowedAlertLevels = listOf(AlertLevel.SOFT, AlertLevel.QUIET, AlertLevel.NONE),
            onAlertLevelChange = onDigestAlertLevelChange,
            selectedVibrationMode = settings.digestVibrationMode,
            allowedVibrationModes = listOf(VibrationMode.LIGHT, VibrationMode.OFF),
            onVibrationModeChange = onDigestVibrationChange,
            headsUpEnabled = false,
            headsUpEnabledAllowed = false,
            headsUpDescription = "Digest는 heads-up을 사용하지 않음",
            onHeadsUpChange = onDigestHeadsUpChange,
            selectedLockScreenVisibility = settings.digestLockScreenVisibility,
            allowedLockScreenModes = listOf(
                LockScreenVisibilityMode.PRIVATE,
                LockScreenVisibilityMode.SECRET,
            ),
            onLockScreenVisibilityChange = onDigestLockScreenChange,
        )
        DeliveryProfileEditorSection(
            title = "Silent",
            subtitle = "Silent는 항상 비침습적으로 유지돼요. 소리·진동·heads-up은 사용할 수 없어요.",
            selectedAlertLevel = settings.silentAlertLevel,
            allowedAlertLevels = listOf(AlertLevel.NONE, AlertLevel.QUIET),
            onAlertLevelChange = onSilentAlertLevelChange,
            selectedVibrationMode = settings.silentVibrationMode,
            allowedVibrationModes = listOf(VibrationMode.OFF),
            onVibrationModeChange = onSilentVibrationChange,
            headsUpEnabled = false,
            headsUpEnabledAllowed = false,
            headsUpDescription = "Silent는 heads-up을 사용하지 않음",
            onHeadsUpChange = onSilentHeadsUpChange,
            selectedLockScreenVisibility = settings.silentLockScreenVisibility,
            allowedLockScreenModes = listOf(
                LockScreenVisibilityMode.PRIVATE,
                LockScreenVisibilityMode.SECRET,
            ),
            onLockScreenVisibilityChange = onSilentLockScreenChange,
        )
    }
}

@Composable
private fun DeliveryProfileEditorSection(
    title: String,
    subtitle: String,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeliveryProfileOptionChips(
            label = "알림 강도",
            selected = selectedAlertLevel,
            options = allowedAlertLevels.map { it.name to it.toKoreanLabel() },
            onSelected = onAlertLevelChange,
        )
        DeliveryProfileOptionChips(
            label = "진동",
            selected = selectedVibrationMode,
            options = allowedVibrationModes.map { it.name to it.toKoreanLabel() },
            onSelected = onVibrationModeChange,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headsUpDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        DeliveryProfileOptionChips(
            label = "잠금 화면 공개 범위",
            selected = selectedLockScreenVisibility,
            options = allowedLockScreenModes.map { it.name to it.toKoreanLabel() },
            onSelected = onLockScreenVisibilityChange,
        )
    }
}

@Composable
private fun DeliveryProfileOptionChips(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

private fun AlertLevel.toKoreanLabel(): String = when (this) {
    AlertLevel.LOUD -> "강함"
    AlertLevel.SOFT -> "보통"
    AlertLevel.QUIET -> "조용함"
    AlertLevel.NONE -> "없음"
}

private fun VibrationMode.toKoreanLabel(): String = when (this) {
    VibrationMode.STRONG -> "강하게"
    VibrationMode.LIGHT -> "가볍게"
    VibrationMode.OFF -> "끔"
}

private fun LockScreenVisibilityMode.toKoreanLabel(): String = when (this) {
    LockScreenVisibilityMode.PUBLIC -> "전체 공개"
    LockScreenVisibilityMode.PRIVATE -> "내용 숨김"
    LockScreenVisibilityMode.SECRET -> "숨김"
}

@Composable
private fun SuppressionInsightsCard(
    suppressEnabled: Boolean,
    summary: com.smartnoti.app.domain.usecase.SuppressionInsightsSummary,
    breakdownItems: List<SuppressionBreakdownItem>,
    topAppRoute: String?,
    breakdownRoutesByAppName: Map<String, String>,
    onInsightClick: (String) -> Unit,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        ContextBadge(
            label = "숨김 인사이트",
            containerColor = SilentContainer,
            contentColor = SilentOnContainer,
        )
        Text(
            text = "원본 알림 숨김 인사이트",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val headline = when {
            !suppressEnabled -> "아직 원본 알림 숨기기가 꺼져 있어요"
            summary.selectedAppCount == 0 -> "숨기기 대상 앱을 아직 고르지 않았어요"
            else -> "선택한 ${summary.selectedAppCount}개 앱에서 ${summary.selectedFilteredCount}개 알림을 SmartNoti가 대신 정리했어요"
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val detail = when {
            !suppressEnabled -> "기능을 켜고 앱을 선택하면 Settings에서 실제 숨김 효과를 바로 확인할 수 있어요."
            summary.selectedAppCount == 0 -> "아래 앱 목록에서 숨기고 싶은 앱을 선택하면 요약이 여기에 표시돼요."
            summary.topSelectedAppName != null -> "선택한 앱 알림 중 ${summary.selectedFilteredSharePercent}%가 정리됐고, ${summary.topSelectedAppName}에서 ${summary.topSelectedAppFilteredCount}건이 가장 많이 정리됐어요."
            else -> "선택한 앱의 숨김 효과를 계속 집계하고 있어요."
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (topAppRoute != null) {
                Modifier.clickable { onInsightClick(topAppRoute) }
            } else {
                Modifier
            },
        )
        if (breakdownItems.isNotEmpty()) {
            SuppressionBreakdownChart(
                items = breakdownItems,
                routeByAppName = breakdownRoutesByAppName,
                onInsightClick = onInsightClick,
            )
        }
        if (summary.appInsights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.appInsights.take(3).forEach { appInsight ->
                    SuppressedAppInsightRow(appInsight)
                }
            }
        }
    }
}

@Composable
private fun SuppressionBreakdownChart(
    items: List<SuppressionBreakdownItem>,
    routeByAppName: Map<String, String>,
    onInsightClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable {
                    routeByAppName[item.appName]?.let(onInsightClick)
                },
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
                            text = "${item.appName} · ${item.filteredCount}건",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "탭해서 자세히 보기",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
    }
}

@Composable
private fun SuppressedAppInsightRow(appInsight: SuppressedAppInsight) {
    val prefix = if (appInsight.isSuppressed) "선택됨" else "관찰 중"
    Text(
        text = "$prefix · ${appInsight.appName} · ${appInsight.filteredCount}건 정리 · ${appInsight.filteredSharePercent}% · ${appInsight.lastSeenLabel}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
