package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.usecase.HomeNotificationAccessSummaryBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationInsightsBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationTimelineBuilder
import com.smartnoti.app.domain.usecase.HomeQuickStartAppliedSummaryBuilder
import com.smartnoti.app.domain.usecase.HomeReasonBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeTimelineBarChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeTimelineRange
import com.smartnoti.app.domain.usecase.UncategorizedAppsDetection
import com.smartnoti.app.domain.usecase.UncategorizedAppsDetector
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.ui.notificationaccess.notificationAccessLifecycleObserver
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.HomePassthroughReviewCard
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer
import kotlinx.coroutines.launch

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 10 declutter:
 * - Keep: StatPill hero summary.
 * - Keep conditional (count > 0): [HomePassthroughReviewCard].
 * - Demote: notification access card to inline 1-row when CONNECTED; full card
 *   only when there's an issue.
 * - Hide: the two [com.smartnoti.app.ui.components.QuickActionCard] entries —
 *   the BottomNav already exposes both destinations.
 * - Demote: [HomeQuickStartAppliedCard] with 7-day TTL + tap-to-ack stored on
 *   SettingsRepository.
 * - Keep conditional: InsightCard (when filteredCount > 0), TimelineCard (when
 *   totalFiltered > 0).
 * - Keep: "방금 정리된 알림" list.
 *
 * The new "새 앱 분류 유도 카드" sits at the **top** of the list above the
 * StatPill hero when [UncategorizedAppsDetector] returns a Prompt.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onPriorityClick: () -> Unit,
    onNotificationAccessClick: () -> Unit,
    onRulesClick: () -> Unit,
    onInsightClick: (String) -> Unit,
    onCreateCategoryClick: (prefillPackage: String?, prefillLabel: String?) -> Unit = { _, _ -> },
    onSeeAllRecent: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val settingsRepository = remember(context) { com.smartnoti.app.data.settings.SettingsRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val insightsBuilder = remember { HomeNotificationInsightsBuilder() }
    val quickStartAppliedSummaryBuilder = remember { HomeQuickStartAppliedSummaryBuilder() }
    val notificationAccessSummaryBuilder = remember { HomeNotificationAccessSummaryBuilder() }
    val reasonBreakdownBuilder = remember { HomeReasonBreakdownChartModelBuilder() }
    val timelineBuilder = remember { HomeNotificationTimelineBuilder() }
    val timelineBarChartBuilder = remember { HomeTimelineBarChartModelBuilder() }
    val uncategorizedAppsDetector = remember { UncategorizedAppsDetector() }
    var selectedTimelineRange by remember { mutableStateOf(HomeTimelineRange.RECENT_3_HOURS) }
    var notificationAccessStatus by remember { mutableStateOf(OnboardingPermissions.currentStatus(context)) }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val recentFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Plan `2026-04-22-inbox-denest-and-home-recent-truncate` Task 1: cap the
    // Home recent feed to a small head; the remainder surfaces as a single
    // "전체 N건 보기 →" footer row that deep-links into 정리함.
    val recentView = remember(recent) {
        HomeRecentNotificationsTruncation.truncate(recent)
    }
    val rules by rulesRepository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by categoriesRepository.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val snoozeUntil by settingsRepository
        .observeUncategorizedPromptSnoozeUntilMillis()
        .collectAsStateWithLifecycle(initialValue = 0L)
    val quickStartAcknowledgedAt by settingsRepository
        .observeQuickStartAppliedCardAcknowledgedAtMillis()
        .collectAsStateWithLifecycle(initialValue = 0L)
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
    val shouldShowQuickStart = remember(quickStartAppliedSummary, quickStartAcknowledgedAt) {
        HomeQuickStartAppliedVisibility.shouldShow(
            hasSummary = quickStartAppliedSummary != null,
            acknowledgedAtMillis = quickStartAcknowledgedAt,
            nowMillis = System.currentTimeMillis(),
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
    // Plan `2026-04-27-uncategorized-prompt-app-rule-coverage` Task 3: pass the
    // collected rules so APP-type rules referenced by some Category's ruleIds
    // also count toward coverage (not just Category.appPackageName).
    val uncategorizedDetection = remember(recent, categories, rules, snoozeUntil) {
        uncategorizedAppsDetector.detect(
            notifications = recent,
            categories = categories,
            rules = rules,
            nowMillis = System.currentTimeMillis(),
            snoozeUntilMillis = snoozeUntil,
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
        // 새 앱 분류 유도 카드 sits above everything else.
        val detection = uncategorizedDetection
        if (detection is UncategorizedAppsDetection.Prompt) {
            item {
                HomeUncategorizedAppsPromptCard(
                    prompt = detection,
                    onCreateCategory = {
                        // Plan `2026-04-26-uncategorized-prompt-editor-autoopen`
                        // Task 6: extract the first uncovered sample (newest-first
                        // per detector contract) so the Categories tab can
                        // auto-open the editor pre-populated with that app. The
                        // pure helper centralises the "blank package == no
                        // prefill" rule for this and any future entry point.
                        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(detection)
                        onCreateCategoryClick(prefill?.packageName, prefill?.label)
                    },
                    onSnooze = {
                        coroutineScope.launch {
                            val deadline = System.currentTimeMillis() + HomeUncategorizedPromptSnooze.TWENTY_FOUR_HOURS_MILLIS
                            settingsRepository.setUncategorizedPromptSnoozeUntilMillis(deadline)
                        }
                    },
                )
            }
        }
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
        // StatPill hero — kept as-is.
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
        // PassthroughReview — only when there is something to review.
        if (priorityCount > 0) {
            item {
                HomePassthroughReviewCard(
                    count = priorityCount,
                    onReviewClick = onPriorityClick,
                )
            }
        }
        // Access card — inline 1-row when CONNECTED; full card only on issue.
        // The two QuickActionCard entries are dropped (BottomNav covers them).
        item {
            if (notificationAccessSummary.granted) {
                HomeNotificationAccessInlineRow(
                    summary = notificationAccessSummary,
                    onClick = onNotificationAccessClick,
                )
            } else {
                HomeNotificationAccessCard(
                    summary = notificationAccessSummary,
                    onClick = onNotificationAccessClick,
                )
            }
        }
        if (quickStartAppliedSummary != null && shouldShowQuickStart) {
            item {
                HomeQuickStartAppliedCard(
                    summary = quickStartAppliedSummary,
                    onClick = {
                        coroutineScope.launch {
                            settingsRepository.setQuickStartAppliedCardAcknowledgedAtMillis(
                                System.currentTimeMillis(),
                            )
                        }
                        onRulesClick()
                    },
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
        if (timeline.totalFilteredCount > 0) {
            item {
                TimelineCard(
                    timeline = timeline,
                    bars = timelineBars,
                    selectedRange = selectedTimelineRange,
                    onRangeSelected = { range -> selectedTimelineRange = range },
                )
            }
        }
        item {
            Text(
                "방금 정리된 알림",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (recentView.visible.isEmpty() && recentView.hiddenCount == 0) {
            item {
                EmptyState(
                    title = "아직 쌓인 알림이 없어요",
                    subtitle = "알림 접근 권한을 허용하면 실제 캡처된 알림만 여기에 보여드릴게요",
                )
            }
        } else {
            items(recentView.visible, key = { it.id }) { notification ->
                NotificationCard(model = notification, onClick = onNotificationClick)
            }
            if (recentView.hiddenCount > 0) {
                item {
                    HomeRecentMoreRow(
                        totalCount = recent.size,
                        onClick = onSeeAllRecent,
                    )
                }
            }
        }
    }
}

/**
 * Pure-function gate for the `HomeQuickStartAppliedCard` visibility. Plan Task
 * 10: demoted to a 7-day TTL — after the user acknowledges (taps the card) we
 * timestamp it on SettingsRepository and suppress the card until seven days
 * have elapsed, at which point the card may re-appear if the quick-start
 * summary is still present.
 */
internal object HomeQuickStartAppliedVisibility {
    const val TTL_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    fun shouldShow(
        hasSummary: Boolean,
        acknowledgedAtMillis: Long,
        nowMillis: Long,
    ): Boolean {
        if (!hasSummary) return false
        if (acknowledgedAtMillis <= 0L) return true
        return nowMillis - acknowledgedAtMillis >= TTL_MILLIS
    }
}

/**
 * 24-hour snooze constant for the Home "나중에" action. Shared between
 * [HomeScreen] and tests.
 */
internal object HomeUncategorizedPromptSnooze {
    const val TWENTY_FOUR_HOURS_MILLIS: Long = 24L * 60L * 60L * 1000L
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

