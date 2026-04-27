package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.usecase.HomeReasonBreakdownItem
import com.smartnoti.app.domain.usecase.HomeReasonInsight
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.GreenAccent
import com.smartnoti.app.ui.theme.GreenContainer

@Composable
internal fun InsightCard(
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
internal fun ReasonBreakdownChart(
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
internal fun InsightLinkText(
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
