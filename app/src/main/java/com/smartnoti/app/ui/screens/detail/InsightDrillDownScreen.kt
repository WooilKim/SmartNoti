package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.usecase.InsightDrillDownBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownFilter
import com.smartnoti.app.domain.usecase.InsightDrillDownReasonBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownReasonBreakdownItem
import com.smartnoti.app.domain.usecase.InsightDrillDownSummaryBuilder
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

@Composable
fun InsightDrillDownScreen(
    contentPadding: PaddingValues,
    filterType: String,
    filterValue: String,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val drillDownBuilder = remember { InsightDrillDownBuilder() }
    val summaryBuilder = remember { InsightDrillDownSummaryBuilder() }
    val reasonBreakdownBuilder = remember { InsightDrillDownReasonBreakdownChartModelBuilder() }
    val notifications by repository.observeAll().collectAsState(initial = emptyList())
    val filter = remember(filterType, filterValue) {
        when (filterType) {
            "app" -> InsightDrillDownFilter.App(appName = filterValue)
            else -> InsightDrillDownFilter.Reason(reasonTag = filterValue)
        }
    }
    val result = remember(notifications, filter) {
        drillDownBuilder.build(notifications = notifications, filter = filter)
    }
    val summary = remember(result) {
        summaryBuilder.build(result.notifications)
    }
    val reasonBreakdownItems = remember(summary) {
        reasonBreakdownBuilder.build(summary.topReasons).items
    }

    if (result.notifications.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Insight",
                    title = result.title,
                    subtitle = result.subtitle,
                )
            }
            item {
                EmptyState(
                    title = "아직 해당 인사이트에 맞는 알림이 없어요",
                    subtitle = "새 알림이 쌓이면 여기서 바로 이유와 내용을 함께 볼 수 있어요.",
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Insight",
                title = result.title,
                subtitle = result.subtitle,
            )
        }
        item {
            SmartSurfaceCard {
                Text(
                    text = "선택한 인사이트에 연결된 정리 알림 ${result.notifications.size}건을 시간순으로 보여줘요.",
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill(
                        label = "Digest",
                        count = summary.digestCount,
                        containerColor = DigestContainer,
                        contentColor = DigestOnContainer,
                    )
                    SummaryPill(
                        label = "조용히",
                        count = summary.silentCount,
                        containerColor = SilentContainer,
                        contentColor = SilentOnContainer,
                    )
                }
                if (summary.topReasonTag != null) {
                    Text(
                        text = "가장 많이 보인 이유는 '${summary.topReasonTag}'예요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reasonBreakdownItems.isNotEmpty()) {
                    InsightReasonBreakdownChart(items = reasonBreakdownItems)
                }
            }
        }
        items(result.notifications) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    count: Int,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun InsightReasonBreakdownChart(items: List<InsightDrillDownReasonBreakdownItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${item.tag} · ${item.count}건",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
