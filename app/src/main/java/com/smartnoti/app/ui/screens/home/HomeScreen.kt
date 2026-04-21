package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.usecase.HomeNotificationAccessSummary
import com.smartnoti.app.domain.usecase.HomeNotificationAccessSummaryBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationInsightsBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationTimeline
import com.smartnoti.app.domain.usecase.HomeNotificationTimelineBuilder
import com.smartnoti.app.domain.usecase.HomeQuickStartAppliedSummaryBuilder
import com.smartnoti.app.domain.usecase.HomeReasonBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeReasonBreakdownItem
import com.smartnoti.app.domain.usecase.HomeReasonInsight
import com.smartnoti.app.domain.usecase.HomeTimelineBar
import com.smartnoti.app.domain.usecase.HomeTimelineBarChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeTimelineRange
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.ui.notificationaccess.notificationAccessLifecycleObserver
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.HomePassthroughReviewCard
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
    onNotificationAccessClick: () -> Unit,
    onRulesClick: () -> Unit,
    onInsightClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val settingsRepository = remember(context) { com.smartnoti.app.data.settings.SettingsRepository.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val insightsBuilder = remember { HomeNotificationInsightsBuilder() }
    val quickStartAppliedSummaryBuilder = remember { HomeQuickStartAppliedSummaryBuilder() }
    val notificationAccessSummaryBuilder = remember { HomeNotificationAccessSummaryBuilder() }
    val reasonBreakdownBuilder = remember { HomeReasonBreakdownChartModelBuilder() }
    val timelineBuilder = remember { HomeNotificationTimelineBuilder() }
    val timelineBarChartBuilder = remember { HomeTimelineBarChartModelBuilder() }
    var selectedTimelineRange by remember { mutableStateOf(HomeTimelineRange.RECENT_3_HOURS) }
    var notificationAccessStatus by remember { mutableStateOf(OnboardingPermissions.currentStatus(context)) }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val recentFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by rulesRepository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val notificationCounts = remember(recent) { HomeNotificationCounts.from(recent) }
    val priorityCount = notificationCounts.priority
    val digestCount = notificationCounts.digest
    val silentCount = notificationCounts.silent
    val insights = remember(recent) { insightsBuilder.build(recent) }
    val quickStartAppliedSummary = remember(rules, recent) {
        quickStartAppliedSummaryBuilder.build(
            rules = rules,
            notifications = recent,
        )
    }
    val notificationAccessSummary = remember(notificationAccessStatus, recent) {
        notificationAccessSummaryBuilder.build(
            status = notificationAccessStatus,
            recentCount = recent.size,
            priorityCount = priorityCount,
            digestCount = digestCount,
            silentCount = silentCount,
        )
    }
    val reasonBreakdownItems = remember(insights) {
        reasonBreakdownBuilder.build(insights.topReasons).items
    }
    val timeline = remember(recent, selectedTimelineRange) {
        timelineBuilder.build(
            notifications = recent,
            range = selectedTimelineRange,
        )
    }
    val timelineBars = remember(timeline) {
        timelineBarChartBuilder.build(timeline).bars
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = notificationAccessLifecycleObserver(
            statusProvider = { OnboardingPermissions.currentStatus(context) },
            onStatusChanged = { notificationAccessStatus = it },
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            HomePassthroughReviewCard(
                count = priorityCount,
                onReviewClick = onPriorityClick,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeNotificationAccessCard(
                    summary = notificationAccessSummary,
                    onClick = onNotificationAccessClick,
                )
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
        if (quickStartAppliedSummary != null) {
            item {
                HomeQuickStartAppliedCard(
                    summary = quickStartAppliedSummary,
                    onClick = onRulesClick,
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
                    reasonBreakdownItems = reasonBreakdownItems,
                    onInsightClick = onInsightClick,
                )
            }
        }
        item {
            TimelineCard(
                timeline = timeline,
                bars = timelineBars,
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
            items(recent, key = { it.id }) { notification ->
                NotificationCard(model = notification, onClick = onNotificationClick)
            }
        }
    }
}

internal data class HomeNotificationCounts(
    val priority: Int,
    val digest: Int,
    val silent: Int,
) {
    companion object {
        fun from(notifications: List<NotificationUiModel>): HomeNotificationCounts {
            var priority = 0
            var digest = 0
            var silent = 0

            notifications.forEach { notification ->
                when (notification.status) {
                    NotificationStatusUi.PRIORITY -> priority += 1
                    NotificationStatusUi.DIGEST -> digest += 1
                    NotificationStatusUi.SILENT -> silent += 1
                    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6
                    // filters IGNORE rows out of Home's source collection, so
                    // this branch should not run in practice. It exists only
                    // to keep the `when` exhaustive; IGNORE rows do not
                    // contribute to the Home hero counts.
                    NotificationStatusUi.IGNORE -> Unit
                }
            }

            return HomeNotificationCounts(
                priority = priority,
                digest = digest,
                silent = silent,
            )
        }
    }
}

@Composable
private fun HomeNotificationAccessCard(
    summary: HomeNotificationAccessSummary,
    onClick: () -> Unit,
) {
    val accentColor = if (summary.granted) GreenAccent else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContextBadge(
                        label = "실제 알림 상태",
                        containerColor = accentColor.copy(alpha = 0.16f),
                        contentColor = accentColor,
                    )
                    ContextBadge(
                        label = summary.statusLabel,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
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
    reasonBreakdownItems: List<HomeReasonBreakdownItem>,
    onInsightClick: (String) -> Unit,
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
            ContextBadge(
                label = "일반 인사이트",
                containerColor = DigestContainer,
                contentColor = DigestOnContainer,
            )
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
            InsightLinkText(
                text = detailLine,
                enabled = topFilteredAppName != null,
                onClick = {
                    topFilteredAppName?.let { appName ->
                        onInsightClick(Routes.Insight.createForApp(appName))
                    }
                },
            )
            if (reasonRankingLine.isNotBlank()) {
                Text(
                    text = "주요 이유: $reasonRankingLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reasonBreakdownItems.isNotEmpty()) {
                ReasonBreakdownChart(
                    items = reasonBreakdownItems,
                    onReasonClick = { reasonTag ->
                        onInsightClick(Routes.Insight.createForReason(reasonTag))
                    },
                )
            }
        }
    }
}

@Composable
private fun ReasonBreakdownChart(
    items: List<HomeReasonBreakdownItem>,
    onReasonClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onReasonClick(item.tag) },
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
                            text = "${item.tag} · ${item.count}건",
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
                                color = if (item.isTopReason) GreenAccent else DigestOnContainer,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightLinkText(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun TimelineCard(
    timeline: HomeNotificationTimeline,
    bars: List<HomeTimelineBar>,
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
        if (bars.isEmpty()) {
            Text(
                text = "선택한 구간에는 아직 정리된 흐름이 없어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TimelineBarChart(bars = bars)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bars.forEach { bar ->
                    val prefix = if (bar.isPeak) "피크" else "흐름"
                    Text(
                        text = "$prefix · ${bar.label} · 정리 ${bar.filteredCount}건 · 즉시 ${bar.priorityCount}건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineBarChart(bars: List<HomeTimelineBar>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bars.forEach { bar ->
            TimelineBar(bar = bar)
        }
    }
}

@Composable
private fun TimelineBar(bar: HomeTimelineBar) {
    Column(
        modifier = Modifier.width(56.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(bar.fillFraction.coerceIn(0f, 1f))
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(
                        color = if (bar.isPeak) GreenAccent else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    ),
            )
        }
        Text(
            text = bar.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
