package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.usecase.HomeNotificationInsightsBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationTimeline
import com.smartnoti.app.domain.usecase.HomeNotificationTimelineBuilder
import com.smartnoti.app.domain.usecase.HomeReasonInsight
import com.smartnoti.app.domain.usecase.HomeTimelineRange
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.QuickActionCard
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onPriorityClick: () -> Unit,
    onDigestClick: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val insightsBuilder = remember { HomeNotificationInsightsBuilder() }
    val timelineBuilder = remember { HomeNotificationTimelineBuilder() }
    var selectedTimelineRange by remember { mutableStateOf(HomeTimelineRange.RECENT_3_HOURS) }
    val recent by repository.observeAll().collectAsState(initial = emptyList())
    val priorityCount = recent.count { it.status == NotificationStatusUi.PRIORITY }
    val digestCount = recent.count { it.status == NotificationStatusUi.DIGEST }
    val silentCount = recent.count { it.status == NotificationStatusUi.SILENT }
    val insights = remember(recent) { insightsBuilder.build(recent) }
    val timeline = remember(recent, selectedTimelineRange) {
        timelineBuilder.build(
            notifications = recent,
            range = selectedTimelineRange,
        )
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "SmartNoti",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "중요한 알림만 먼저 보여드리고 있어요",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "오늘 알림 ${recent.size}개 중 중요한 ${priorityCount}개를 먼저 전달했어요",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("즉시", priorityCount, PriorityContainer, PriorityOnContainer)
                        StatPill("Digest", digestCount, DigestContainer, DigestOnContainer)
                        StatPill("조용히", silentCount, SilentContainer, SilentOnContainer)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionCard(
                    title = "중요 알림",
                    subtitle = "지금 봐야 할 알림 ${priorityCount}개",
                    onClick = onPriorityClick,
                )
                QuickActionCard(
                    title = "정리함",
                    subtitle = "묶인 알림 ${digestCount}개",
                    onClick = onDigestClick,
                )
            }
        }
        if (insights.filteredCount > 0) {
            item {
                InsightCard(
                    filteredCount = insights.filteredCount,
                    filteredSharePercent = insights.filteredSharePercent,
                    topFilteredAppName = insights.topFilteredAppName,
                    topFilteredAppCount = insights.topFilteredAppCount,
                    topReasonTag = insights.topReasonTag,
                    topReasons = insights.topReasons,
                )
            }
        }
        item {
            TimelineCard(
                timeline = timeline,
                selectedRange = selectedTimelineRange,
                onRangeSelected = { range -> selectedTimelineRange = range },
            )
        }
        item {
            Text(
                "방금 정리된 알림",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "아직 쌓인 알림이 없어요",
                    subtitle = "알림 접근 권한을 허용하면 실제 캡처된 알림만 여기에 보여드릴게요",
                )
            }
        } else {
            items(recent) { notification ->
                NotificationCard(model = notification, onClick = onNotificationClick)
            }
        }
    }
}

@Composable
private fun InsightCard(
    filteredCount: Int,
    filteredSharePercent: Int,
    topFilteredAppName: String?,
    topFilteredAppCount: Int,
    topReasonTag: String?,
    topReasons: List<HomeReasonInsight>,
) {
    val primaryLine = buildString {
        append("지금까지 ")
        append(filteredCount)
        append("개의 알림을 대신 정리했어요")
    }
    val detailLine = when {
        topFilteredAppName != null && topReasonTag != null -> {
            "$topFilteredAppName 알림 ${topFilteredAppCount}개가 가장 많이 정리됐고, 주된 이유는 '$topReasonTag'예요"
        }
        topFilteredAppName != null -> {
            "$topFilteredAppName 알림 ${topFilteredAppCount}개가 가장 많이 정리됐어요"
        }
        topReasonTag != null -> {
            "가장 자주 적용된 정리 이유는 '$topReasonTag'예요"
        }
        else -> {
            "정리된 알림 패턴을 계속 학습하고 있어요"
        }
    }
    val reasonRankingLine = topReasons.joinToString(" · ") { reason ->
        "${reason.tag} ${reason.count}건"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GreenContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SmartNoti 인사이트",
                style = MaterialTheme.typography.labelLarge,
                color = GreenAccent,
            )
            Text(
                text = primaryLine,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "전체 알림 중 ${filteredSharePercent}%를 대신 정리했어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detailLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reasonRankingLine.isNotBlank()) {
                Text(
                    text = "주요 이유: $reasonRankingLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(
    timeline: HomeNotificationTimeline,
    selectedRange: HomeTimelineRange,
    onRangeSelected: (HomeTimelineRange) -> Unit,
) {
    SmartTimelineCardContainer {
        Text(
            text = "최근 흐름",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimelineRangeChip(
                range = HomeTimelineRange.RECENT_3_HOURS,
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected,
            )
            TimelineRangeChip(
                range = HomeTimelineRange.RECENT_24_HOURS,
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected,
            )
        }
        Text(
            text = "${timeline.range.label} 기준 ${timeline.totalFilteredCount}개의 알림이 정리됐어요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (timeline.buckets.isEmpty()) {
            Text(
                text = "선택한 구간에는 아직 정리된 흐름이 없어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                timeline.buckets.forEach { bucket ->
                    val prefix = if (bucket.isPeakFilteredBucket) "피크" else "흐름"
                    Text(
                        text = "$prefix · ${bucket.label} · 정리 ${bucket.filteredCount}건 · 즉시 ${bucket.priorityCount}건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRangeChip(
    range: HomeTimelineRange,
    selectedRange: HomeTimelineRange,
    onRangeSelected: (HomeTimelineRange) -> Unit,
) {
    FilterChip(
        selected = range == selectedRange,
        onClick = { onRangeSelected(range) },
        label = {
            Text(range.label)
        },
    )
}

@Composable
private fun SmartTimelineCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun StatPill(label: String, count: Int, bgColor: Color, fgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
        )
    }
}
