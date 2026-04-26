package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.filterPersistent
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.usecase.InsightDrillDownBuilder
import com.smartnoti.app.domain.usecase.InsightContextBadgeModelBuilder
import com.smartnoti.app.domain.usecase.InsightContextBadgeTone
import com.smartnoti.app.domain.usecase.InsightDrillDownCopyBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownFilter
import com.smartnoti.app.domain.usecase.InsightDrillDownRange
import com.smartnoti.app.domain.usecase.InsightDrillDownReasonBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownReasonNavigationItem
import com.smartnoti.app.domain.usecase.InsightDrillDownReasonNavigationModelBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownSource
import com.smartnoti.app.domain.usecase.InsightDrillDownSummaryBuilder
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.ui.components.ContextBadge
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
    initialRange: String,
    source: String,
    onNotificationClick: (String) -> Unit,
    onInsightClick: (String) -> Unit,
    onBack: () -> Unit,
    // Plan 2026-04-26-insight-drilldown-range-state-survival Task 4:
    // optional savedStateHandle bridge so the user-selected range survives
    // even the back-stack lifecycle transitions where rememberSaveable's
    // bundle restore does not fire (e.g. STOPPED→STARTED on the same entry
    // after a Detail push). Defaults are no-ops so callers (including
    // existing tests) that do not wire it remain unaffected.
    savedRangeRouteValue: String? = null,
    onRangeSelected: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val drillDownBuilder = remember { InsightDrillDownBuilder() }
    val summaryBuilder = remember { InsightDrillDownSummaryBuilder() }
    val copyBuilder = remember { InsightDrillDownCopyBuilder() }
    val contextBadgeBuilder = remember { InsightContextBadgeModelBuilder() }
    val reasonBreakdownBuilder = remember { InsightDrillDownReasonBreakdownChartModelBuilder() }
    val reasonNavigationBuilder = remember { InsightDrillDownReasonNavigationModelBuilder() }
    val notifications by repository.observeAll().collectAsState(initial = emptyList())
    val settings by settingsRepository.observeSettings().collectAsState(initial = com.smartnoti.app.data.settings.SmartNotiSettings())
    // Plan 2026-04-26-insight-drilldown-range-state-survival Task 2 + Task 4:
    // hoist the chip selection into a pure holder whose `rememberSaveable`
    // key is static so URL-arg shake (Detail back-stack restore) does not
    // reset user choice. `onRouteArgsChanged` re-applies the URL arg only
    // while the user has not yet selected anything. The `savedRangeRouteValue`
    // bridge from `NavBackStackEntry.savedStateHandle` (wired by
    // `AppNavHost`) seeds the holder with `userOverridden = true` so a
    // post-back resume on the same entry restores the user's pick even when
    // `rememberSaveable`'s own bundle does not get reapplied for that
    // lifecycle transition.
    val rangeState = rememberSaveable(saver = InsightDrillDownRangeState.Saver) {
        InsightDrillDownRangeState.fromSeed(
            savedRangeRouteValue = savedRangeRouteValue,
            initialRouteValue = initialRange,
        )
    }
    LaunchedEffect(initialRange) {
        rangeState.onRouteArgsChanged(initialRouteValue = initialRange)
    }
    val selectedRange = rangeState.currentRange
    val filter = remember(filterType, filterValue) {
        when (filterType) {
            "app" -> InsightDrillDownFilter.App(appName = filterValue)
            else -> InsightDrillDownFilter.Reason(reasonTag = filterValue)
        }
    }
    val drillDownSource = remember(source) { InsightDrillDownSource.fromRouteValue(source) }
    val currentReasonTag = (filter as? InsightDrillDownFilter.Reason)?.reasonTag
    val currentRangeRouteValue = selectedRange.routeValue
    val visibleNotifications = remember(notifications, settings.hidePersistentNotifications) {
        notifications.filterPersistent(settings.hidePersistentNotifications)
    }
    val result = remember(visibleNotifications, filter, selectedRange) {
        drillDownBuilder.build(
            notifications = visibleNotifications,
            filter = filter,
            range = selectedRange,
        )
    }
    val summary = remember(result) {
        summaryBuilder.build(result.notifications)
    }
    val copy = remember(filter, drillDownSource, selectedRange, summary) {
        copyBuilder.build(
            filter = filter,
            source = drillDownSource,
            range = selectedRange,
            summary = summary,
        )
    }
    val contextBadge = remember(drillDownSource) {
        contextBadgeBuilder.build(drillDownSource)
    }
    val reasonBreakdownItems = remember(summary) {
        reasonBreakdownBuilder.build(summary.topReasons).items
    }
    val reasonNavigationItems = remember(reasonBreakdownItems, currentReasonTag) {
        reasonNavigationBuilder.build(
            items = reasonBreakdownItems,
            currentReasonTag = currentReasonTag,
        )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ScreenHeader(
                        eyebrow = "인사이트",
                        title = copy.title,
                        subtitle = copy.subtitle ?: result.subtitle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                }
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
                eyebrow = "인사이트",
                title = copy.title,
                subtitle = copy.subtitle ?: result.subtitle,
            )
        }
        item {
            SmartSurfaceCard {
                ContextBadge(
                    label = contextBadge.label,
                    containerColor = when (contextBadge.tone) {
                        InsightContextBadgeTone.GENERAL -> DigestContainer
                        InsightContextBadgeTone.SUPPRESSION -> SilentContainer
                    },
                    contentColor = when (contextBadge.tone) {
                        InsightContextBadgeTone.GENERAL -> DigestOnContainer
                        InsightContextBadgeTone.SUPPRESSION -> SilentOnContainer
                    },
                )
                Text(
                    text = copy.overview ?: "${selectedRange.label} 기준 정리 알림 ${result.notifications.size}건을 시간순으로 보여줘요.",
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val handleSelection: (InsightDrillDownRange) -> Unit = { picked ->
                        rangeState.select(picked)
                        onRangeSelected(picked.routeValue)
                    }
                    InsightRangeChip(
                        range = InsightDrillDownRange.RECENT_3_HOURS,
                        selectedRange = selectedRange,
                        onRangeSelected = handleSelection,
                    )
                    InsightRangeChip(
                        range = InsightDrillDownRange.RECENT_24_HOURS,
                        selectedRange = selectedRange,
                        onRangeSelected = handleSelection,
                    )
                    InsightRangeChip(
                        range = InsightDrillDownRange.ALL,
                        selectedRange = selectedRange,
                        onRangeSelected = handleSelection,
                    )
                }
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
                if (copy.topReasonText != null) {
                    Text(
                        text = copy.topReasonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reasonNavigationItems.isNotEmpty()) {
                    InsightReasonBreakdownChart(
                        items = reasonNavigationItems,
                        currentRangeRouteValue = currentRangeRouteValue,
                        drillDownSource = drillDownSource,
                        onInsightClick = onInsightClick,
                    )
                }
            }
        }
        items(result.notifications) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}

@Composable
private fun InsightRangeChip(
    range: InsightDrillDownRange,
    selectedRange: InsightDrillDownRange,
    onRangeSelected: (InsightDrillDownRange) -> Unit,
) {
    FilterChip(
        selected = range == selectedRange,
        onClick = { onRangeSelected(range) },
        label = { Text(range.label) },
    )
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
private fun InsightReasonBreakdownChart(
    items: List<InsightDrillDownReasonNavigationItem>,
    currentRangeRouteValue: String,
    drillDownSource: InsightDrillDownSource,
    onInsightClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = if (item.isClickable) {
                    Modifier.clickable {
                        onInsightClick(
                            Routes.Insight.createForReason(
                                reasonTag = item.tag,
                                range = currentRangeRouteValue,
                                source = drillDownSource.routeValue.takeIf {
                                    drillDownSource != InsightDrillDownSource.GENERAL
                                },
                            )
                        )
                    }
                } else {
                    Modifier
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
                            text = "${item.tag} · ${item.count}건",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isCurrentReason) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = item.hintLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isClickable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (item.showChevron) {
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
                                color = when {
                                    item.isCurrentReason -> MaterialTheme.colorScheme.primary
                                    item.isTopReason -> GreenAccent
                                    else -> DigestOnContainer
                                },
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }
        }
    }
}
