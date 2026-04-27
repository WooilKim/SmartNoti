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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionBreakdownItem
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent

@Composable
internal fun SuppressionInsightMetricStrip(metrics: List<SuppressionInsightMetric>) {
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
internal fun SuppressionInsightMetricCard(
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
internal fun SuppressionBreakdownList(
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
internal fun SuppressionBreakdownRow(
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
internal fun SuppressedAppInsightsList(
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
internal fun SuppressedAppInsightRow(
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
