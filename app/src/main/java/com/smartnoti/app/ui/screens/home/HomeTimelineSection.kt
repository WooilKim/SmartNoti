package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.usecase.HomeNotificationTimeline
import com.smartnoti.app.domain.usecase.HomeTimelineBar
import com.smartnoti.app.domain.usecase.HomeTimelineRange
import com.smartnoti.app.ui.theme.GreenAccent

@Composable
internal fun TimelineCard(
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
internal fun TimelineBarChart(bars: List<HomeTimelineBar>) {
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
internal fun TimelineBar(bar: HomeTimelineBar) {
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
                    .align(Alignment.BottomCenter)
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
internal fun TimelineRangeChip(
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
internal fun SmartTimelineCardContainer(content: @Composable ColumnScope.() -> Unit) {
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
internal fun StatPill(label: String, count: Int, bgColor: Color, fgColor: Color) {
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
