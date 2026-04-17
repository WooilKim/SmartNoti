package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.smartnoti.app.domain.usecase.SuppressionInsightsBuilder
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val suppressionInsightsBuilder = remember { SuppressionInsightsBuilder() }
    val settings by repository.observeSettings().collectAsState(
        initial = com.smartnoti.app.data.settings.SmartNotiSettings()
    )
    val capturedApps by notificationRepository.observeCapturedApps().collectAsState(initial = emptyList())
    val notifications by notificationRepository.observeAll().collectAsState(initial = emptyList())
    val suppressionInsights = remember(capturedApps, notifications, settings.suppressedSourceApps) {
        suppressionInsightsBuilder.build(
            capturedApps = capturedApps,
            notifications = notifications,
            suppressedPackages = settings.suppressedSourceApps,
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
                title = "소스 알림 처리",
                subtitle = "Digest·조용히 결정 시 원본 알림을 숨길지 선택할 수 있어요.",
            )
        }
        item {
            SuppressionInsightsCard(
                suppressEnabled = settings.suppressSourceForDigestAndSilent,
                summary = suppressionInsights,
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
                if (capturedApps.isEmpty()) {
                    Text(
                        "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    capturedApps.forEach { app ->
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
private fun SuppressionInsightsCard(
    suppressEnabled: Boolean,
    summary: com.smartnoti.app.domain.usecase.SuppressionInsightsSummary,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
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
        )
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
private fun SuppressedAppInsightRow(appInsight: SuppressedAppInsight) {
    val prefix = if (appInsight.isSuppressed) "선택됨" else "관찰 중"
    Text(
        text = "$prefix · ${appInsight.appName} · ${appInsight.filteredCount}건 정리 · ${appInsight.filteredSharePercent}% · ${appInsight.lastSeenLabel}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
