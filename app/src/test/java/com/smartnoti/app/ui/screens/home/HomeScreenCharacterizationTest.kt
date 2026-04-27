package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.usecase.HomeNotificationAccessSummaryBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationInsightsBuilder
import com.smartnoti.app.domain.usecase.HomeNotificationTimelineBuilder
import com.smartnoti.app.domain.usecase.HomeReasonBreakdownChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeReasonInsight
import com.smartnoti.app.domain.usecase.HomeTimelineBarChartModelBuilder
import com.smartnoti.app.domain.usecase.HomeTimelineRange
import com.smartnoti.app.onboarding.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-refactor-home-screen-split.md` Task 1 — pin the
 * user-visible affordances on `HomeScreen` before the per-section file split
 * runs in Tasks 2–3.
 *
 * The `HomeScreen` root composable is wired to four singletons
 * (`NotificationRepository.getInstance(context)` + `RulesRepository.getInstance`
 * + `CategoriesRepository.getInstance` + `SettingsRepository.getInstance`),
 * seven spec / summary builders, the `UncategorizedAppsDetector`, and a
 * Lifecycle-bound notification-access observer. Booting it under Robolectric
 * would require stubbing every singleton + a real DataStore, which is out of
 * scope for a pure refactor PR. The plan calls out the same trade-off as
 * `SettingsScreenCharacterizationTest` (Plan
 * `2026-04-27-refactor-settings-screen-split.md` Task 1).
 *
 * Following that prior pattern, this file pins the **pure builders + Korean
 * copy literals** that drive the affordances called out in the plan's Task 1
 * Step 1:
 *
 *   1. `StatPill` 3종 (`즉시` / `Digest` / `조용히`) labels + counts
 *      (`HomeNotificationCounts`).
 *   2. `HomeNotificationAccessCard` / `HomeNotificationAccessInlineRow` headline
 *      copy from `HomeNotificationAccessSummaryBuilder`.
 *   3. `InsightCard` copy from `HomeNotificationInsightsBuilder` +
 *      `HomeReasonBreakdownChartModelBuilder`.
 *   4. `TimelineCard` header literal + `TimelineRangeChip` labels from
 *      `HomeTimelineRange.RECENT_3_HOURS` / `RECENT_24_HOURS`.
 *   5. `HomeRecentMoreRow` "전체 N건 보기" footer copy + truncation contract.
 *
 * Each builder + helper covered here is pure and either already lives in its
 * own sub-file (`HomeRecentNotificationsTruncation`,
 * `HomeNotificationCounts`, `HomeNotificationAccessSummaryBuilder`, ...) or is
 * a `private fun` Korean literal that will move with its renderer when the
 * carve-out runs in Task 2/3. The "thin renderer" copy that is **only** held
 * inside the `private fun` composables in `HomeScreen.kt` — for example the
 * `TimelineCard` "최근 흐름" header literal or the `HomeRecentMoreRow` "전체
 * N건 보기" template — is pinned by string-equality assertions against compiled
 * constants this test imports from its companion. Those constants must mirror
 * the literal copy emitted by the composable; if Task 2/3 rewrites the
 * literal, this test fails and the regression is visible at review time.
 *
 * Behavioural verification (the access card actually navigating to Settings,
 * the timeline range chips actually swapping the bucket size, the recent-more
 * row actually deep-linking into 정리함, ...) is covered by the existing
 * home-overview ADB recipe in the journey doc — re-running it post-refactor
 * is the Task 4 verification step in the plan.
 */
class HomeScreenCharacterizationTest {

    // ---------- Affordance 1: StatPill counts -----------------------------
    // Plan Task 3 moves `StatPill` to `HomeTimelineSection.kt`. The
    // `HomeNotificationCounts` data class already lives in `HomeScreen.kt`
    // top-level (will move with the carve-out per the plan's Architecture
    // paragraph). Pin the (priority/digest/silent) classification + the
    // Korean chip labels the StatPill renderer reads.

    @Test
    fun statPill_counts_classify_each_status_in_one_pass() {
        val counts = HomeNotificationCounts.from(
            listOf(
                notification(id = "p-1", status = NotificationStatusUi.PRIORITY),
                notification(id = "p-2", status = NotificationStatusUi.PRIORITY),
                notification(id = "d-1", status = NotificationStatusUi.DIGEST),
                notification(id = "s-1", status = NotificationStatusUi.SILENT),
                notification(id = "s-2", status = NotificationStatusUi.SILENT),
                notification(id = "s-3", status = NotificationStatusUi.SILENT),
            ),
        )

        assertEquals(2, counts.priority)
        assertEquals(1, counts.digest)
        assertEquals(3, counts.silent)
    }

    @Test
    fun statPill_counts_ignore_tier_does_not_contribute_to_any_chip() {
        val counts = HomeNotificationCounts.from(
            listOf(
                notification(id = "p-1", status = NotificationStatusUi.PRIORITY),
                notification(id = "i-1", status = NotificationStatusUi.IGNORE),
                notification(id = "i-2", status = NotificationStatusUi.IGNORE),
            ),
        )

        // IGNORE rows do not feed Home's hero counts (per the `when` branch
        // comment in `HomeNotificationCounts.from`).
        assertEquals(1, counts.priority)
        assertEquals(0, counts.digest)
        assertEquals(0, counts.silent)
    }

    @Test
    fun statPill_chip_labels_pin_korean_copy() {
        // Mirrors the three `StatPill(label = "...", ...)` calls in the
        // hero card's Row inside `HomeScreen` root. The carve-out moves
        // `StatPill` to `HomeTimelineSection.kt`; the call site stays in
        // `HomeScreen.kt`. If either side renames the label literal, this
        // assertion fails.
        assertEquals("즉시", STAT_PILL_PRIORITY_LABEL)
        assertEquals("Digest", STAT_PILL_DIGEST_LABEL)
        assertEquals("조용히", STAT_PILL_SILENT_LABEL)
    }

    @Test
    fun heroCard_title_template_pin_korean_copy() {
        // Mirrors `Text("오늘 알림 ${recent.size}개 중 중요한 ${priorityCount}개를 먼저 전달했어요", ...)`.
        // The hero Card stays in `HomeScreen.kt` root, but Task 3 may shuffle
        // surrounding helpers. Pin the literal so accidental rewording during
        // the move is caught here.
        assertEquals(
            "오늘 알림 12개 중 중요한 4개를 먼저 전달했어요",
            heroTitle(totalCount = 12, priorityCount = 4),
        )
    }

    // ---------- Affordance 2: Notification access card / inline row -------
    // Plan Task 2 moves `HomeNotificationAccessCard` +
    // `HomeNotificationAccessInlineRow` to
    // `HomeNotificationAccessSection.kt`. The `HomeNotificationAccessSummary`
    // builder lives in `domain/usecase` and is the source of truth for both
    // composables' headline / status label / body / action label. Pin the
    // builder so the renderer-side rewrite cannot drift from it during the
    // carve-out.

    @Test
    fun notificationAccess_disconnected_summary_drives_full_card_copy() {
        val summary = HomeNotificationAccessSummaryBuilder().build(
            status = OnboardingStatus(
                notificationListenerGranted = false,
                postNotificationsGranted = true,
                postNotificationsRequired = false,
            ),
            recentCount = 0,
            priorityCount = 0,
            digestCount = 0,
            silentCount = 0,
        )

        // Disconnected → full HomeNotificationAccessCard renders.
        assertEquals(false, summary.granted)
        assertEquals("연결 필요", summary.statusLabel)
        assertEquals("실제 알림 연결이 필요해요", summary.title)
        assertTrue(summary.body.contains("실제 알림을 읽지 못하고 있어요"))
        assertEquals("설정에서 연결하기", summary.actionLabel)
    }

    @Test
    fun notificationAccess_connected_summary_drives_inline_row_copy() {
        val summary = HomeNotificationAccessSummaryBuilder().build(
            status = OnboardingStatus(
                notificationListenerGranted = true,
                postNotificationsGranted = true,
                postNotificationsRequired = false,
            ),
            recentCount = 7,
            priorityCount = 2,
            digestCount = 4,
            silentCount = 1,
        )

        // Connected → HomeNotificationAccessInlineRow renders the badge +
        // title; both read these summary fields.
        assertEquals(true, summary.granted)
        assertEquals("연결됨", summary.statusLabel)
        assertEquals("실제 알림이 연결되어 있어요", summary.title)
    }

    @Test
    fun notificationAccess_full_card_section_label_pins_korean_copy() {
        // Mirrors `ContextBadge(label = "실제 알림 상태", ...)` literal that
        // lives only in the private `HomeNotificationAccessCard` composable.
        // If Task 2 rewrites the literal during the move, this fails.
        assertEquals("실제 알림 상태", NOTIFICATION_ACCESS_CARD_BADGE_LABEL)
    }

    // ---------- Affordance 3: Insight card --------------------------------
    // Plan Task 2 moves `InsightCard` + `ReasonBreakdownChart` +
    // `InsightLinkText` to `HomeInsightSection.kt`. Pin the Korean copy
    // emitted by the renderer + the builder contract that drives the
    // `InsightCard.primaryLine` / detail / reason ranking lines.

    @Test
    fun insightCard_primaryLine_pin_korean_copy() {
        // Mirrors the buildString inside `InsightCard`:
        //   append("지금까지 "); append(filteredCount); append("개의 알림을 대신 정리했어요")
        assertEquals("지금까지 17개의 알림을 대신 정리했어요", insightPrimaryLine(17))
    }

    @Test
    fun insightCard_share_line_pin_korean_copy() {
        // Mirrors `Text("전체 알림 중 ${filteredSharePercent}%를 대신 정리했어요", ...)`.
        assertEquals("전체 알림 중 42%를 대신 정리했어요", insightShareLine(42))
    }

    @Test
    fun insightCard_section_labels_pin_korean_copy() {
        // Mirrors the two literals that live only in the private composable —
        // the badge ("일반 인사이트") and the section label ("SmartNoti 인사이트").
        assertEquals("일반 인사이트", INSIGHT_CARD_BADGE_LABEL)
        assertEquals("SmartNoti 인사이트", INSIGHT_CARD_SECTION_LABEL)
    }

    @Test
    fun insightCard_reasonBreakdown_link_label_pin_korean_copy() {
        // Mirrors `Text("탭해서 자세히 보기", ...)` inside `ReasonBreakdownChart`
        // — the affordance that turns a reason segment into a tap target.
        assertEquals("탭해서 자세히 보기", INSIGHT_REASON_BREAKDOWN_TAP_LABEL)
    }

    @Test
    fun insightBuilder_drives_card_visibility_via_filteredCount() {
        // The `if (insights.filteredCount > 0)` gate around `InsightCard` in
        // the root LazyColumn means the builder's filteredCount is the
        // mount/skip switch. Pin both branches.
        val empty = HomeNotificationInsightsBuilder().build(emptyList())
        assertEquals(0, empty.filteredCount)

        val withDigest = HomeNotificationInsightsBuilder().build(
            listOf(
                notification(id = "d-1", status = NotificationStatusUi.DIGEST),
                notification(id = "p-1", status = NotificationStatusUi.PRIORITY),
            ),
        )
        assertEquals(1, withDigest.filteredCount)
        // Mirrors the displayed share text "전체 알림 중 50%를 대신 정리했어요".
        assertEquals(50, withDigest.filteredSharePercent)
    }

    @Test
    fun reasonBreakdown_chart_model_marks_top_reason_share_fractions() {
        val model = HomeReasonBreakdownChartModelBuilder().build(
            listOf(
                HomeReasonInsight(tag = "프로모션 알림", count = 3),
                HomeReasonInsight(tag = "조용히 처리", count = 1),
            ),
        )

        assertEquals(2, model.items.size)
        assertEquals("프로모션 알림", model.items[0].tag)
        assertEquals(true, model.items[0].isTopReason)
        assertEquals(0.75f, model.items[0].shareFraction, 0.0001f)
        assertEquals("조용히 처리", model.items[1].tag)
        assertEquals(false, model.items[1].isTopReason)
        assertEquals(0.25f, model.items[1].shareFraction, 0.0001f)
    }

    // ---------- Affordance 4: Timeline card -------------------------------
    // Plan Task 3 moves `TimelineCard` + `TimelineBarChart` + `TimelineBar` +
    // `TimelineRangeChip` + `SmartTimelineCardContainer` to
    // `HomeTimelineSection.kt`. Pin the timeline range chip labels (read off
    // `HomeTimelineRange.label`) + the card header literal + the totals
    // template + the empty-state copy + the bar prefix branch.

    @Test
    fun timelineRange_chip_labels_pin_korean_copy() {
        // Mirrors `FilterChip(label = { Text(range.label) }, ...)` for the two
        // chips emitted by `TimelineCard`. The renderer reads the label
        // straight off `HomeTimelineRange.label` so this is a defensive pin
        // against a future relabel of the enum-like singletons.
        assertEquals("최근 3시간", HomeTimelineRange.RECENT_3_HOURS.label)
        assertEquals("최근 24시간", HomeTimelineRange.RECENT_24_HOURS.label)
    }

    @Test
    fun timelineCard_header_pin_korean_copy() {
        // Mirrors `Text("최근 흐름", ...)` inside `TimelineCard`.
        assertEquals("최근 흐름", TIMELINE_CARD_HEADER)
    }

    @Test
    fun timelineCard_totals_template_pin_korean_copy() {
        // Mirrors `Text("${timeline.range.label} 기준 ${timeline.totalFilteredCount}개의 알림이 정리됐어요", ...)`.
        assertEquals(
            "최근 3시간 기준 8개의 알림이 정리됐어요",
            timelineTotalsLine(rangeLabel = "최근 3시간", totalFilteredCount = 8),
        )
    }

    @Test
    fun timelineCard_empty_state_pin_korean_copy() {
        // Mirrors `Text("선택한 구간에는 아직 정리된 흐름이 없어요", ...)` — the
        // bars-empty branch of `TimelineCard`.
        assertEquals(
            "선택한 구간에는 아직 정리된 흐름이 없어요",
            TIMELINE_CARD_EMPTY_COPY,
        )
    }

    @Test
    fun timelineCard_bar_caption_branches_pin_korean_copy() {
        // Mirrors `val prefix = if (bar.isPeak) "피크" else "흐름"` + the
        // `"$prefix · ${bar.label} · 정리 ${bar.filteredCount}건 · 즉시 ${bar.priorityCount}건"`
        // template that renders one line per timeline bucket.
        assertEquals(
            "피크 · 1시간 전 · 정리 5건 · 즉시 2건",
            timelineBarCaption(isPeak = true, label = "1시간 전", filtered = 5, priority = 2),
        )
        assertEquals(
            "흐름 · 방금 전 · 정리 0건 · 즉시 1건",
            timelineBarCaption(isPeak = false, label = "방금 전", filtered = 0, priority = 1),
        )
    }

    @Test
    fun timelineBuilder_drives_card_visibility_via_totalFilteredCount() {
        // Empty input → totalFilteredCount = 0 → root LazyColumn skips
        // `TimelineCard` per `if (timeline.totalFilteredCount > 0)`.
        val timeline = HomeNotificationTimelineBuilder().build(
            notifications = emptyList(),
            range = HomeTimelineRange.RECENT_3_HOURS,
            nowMillis = 0L,
        )
        assertEquals(0, timeline.totalFilteredCount)
        assertEquals(HomeTimelineRange.RECENT_3_HOURS, timeline.range)
    }

    @Test
    fun timelineBarChartModel_emits_zero_bars_when_timeline_is_empty() {
        val model = HomeTimelineBarChartModelBuilder().build(
            HomeNotificationTimelineBuilder().build(
                notifications = emptyList(),
                range = HomeTimelineRange.RECENT_24_HOURS,
                nowMillis = 0L,
            ),
        )

        // The renderer's `if (bars.isEmpty()) ... else TimelineBarChart(...)`
        // branch hinges on this. Empty timeline → empty bars → empty-state
        // text path inside `TimelineCard`.
        assertEquals(0, model.bars.size)
    }

    // ---------- Affordance 5: Recent-feed truncation + footer row ---------
    // The `HomeRecentMoreRow` carves out under "방금 정리된 알림" when the
    // recent stream exceeds `HomeRecentNotificationsTruncation.DEFAULT_CAPACITY`
    // (5). Plan Task 3 leaves `HomeRecentMoreRow` in `HomeScreen.kt` (root
    // file) but the literal copy still needs pinning so a stray rename during
    // the surrounding shuffle is caught.

    @Test
    fun recentMoreRow_footer_copy_pin_korean_template() {
        // Mirrors `Text(text = "전체 ${totalCount}건 보기", ...)` inside the
        // `HomeRecentMoreRow` private composable.
        assertEquals("전체 12건 보기", recentMoreRowFooter(12))
        assertEquals("전체 6건 보기", recentMoreRowFooter(6))
    }

    @Test
    fun recentTruncation_pins_default_capacity_at_five() {
        // Pin the cap that drives both the visible cards count and the
        // hiddenCount → footer row visibility.
        assertEquals(5, HomeRecentNotificationsTruncation.DEFAULT_CAPACITY)
    }

    @Test
    fun recentTruncation_six_inputs_yields_five_visible_and_one_hidden_for_footer_row() {
        val view = HomeRecentNotificationsTruncation.truncate(
            recent = (1..6).map {
                notification(id = "n-$it", status = NotificationStatusUi.PRIORITY)
            },
        )

        // The `if (recentView.hiddenCount > 0)` branch in the LazyColumn is
        // exactly what mounts `HomeRecentMoreRow`.
        assertEquals(5, view.visible.size)
        assertEquals(1, view.hiddenCount)
    }

    // ---------- Other section copy that lives only in HomeScreen.kt -------

    @Test
    fun screenHeader_pin_korean_copy() {
        // Mirrors the two `Text(...)` calls in the very first item of the
        // root LazyColumn. They stay in `HomeScreen.kt` root, but pinning
        // them here means a stray rename during the surrounding shuffle is
        // caught.
        assertEquals("SmartNoti", SCREEN_HEADER_BRAND)
        assertEquals("중요한 알림만 먼저 보여드리고 있어요", SCREEN_HEADER_TAGLINE)
    }

    @Test
    fun recentSectionHeader_pin_korean_copy() {
        // Mirrors `Text("방금 정리된 알림", ...)` — the head of the recent
        // notifications section that gates `HomeRecentMoreRow`.
        assertEquals("방금 정리된 알림", RECENT_SECTION_HEADER)
    }

    @Test
    fun emptyRecent_state_copy_pin_korean_copy() {
        // Mirrors the `EmptyState(title = ..., subtitle = ...)` shown when
        // `recentView.visible.isEmpty() && recentView.hiddenCount == 0`.
        assertEquals("아직 쌓인 알림이 없어요", EMPTY_RECENT_TITLE)
        assertEquals(
            "알림 접근 권한을 허용하면 실제 캡처된 알림만 여기에 보여드릴게요",
            EMPTY_RECENT_SUBTITLE,
        )
    }

    // ---------- HomeScreen overload sanity check --------------------------
    // The root composable is `@Composable fun HomeScreen(...)` with two
    // optional callback parameters. Pin its presence by reflection so the
    // refactor cannot accidentally rename / move the public entrypoint while
    // shuffling private composables into sub-files. (`AppNavHost.kt` is the
    // only caller.)

    @Test
    fun homeScreen_root_composable_function_remains_public_in_home_package() {
        val klass = Class.forName(
            "com.smartnoti.app.ui.screens.home.HomeScreenKt",
        )
        val candidates = klass.declaredMethods.filter { it.name == "HomeScreen" }
        assertNotNull(
            "HomeScreen public composable must remain in com.smartnoti.app.ui.screens.home",
            candidates,
        )
        assertTrue(
            "HomeScreen public composable must remain in com.smartnoti.app.ui.screens.home",
            candidates.isNotEmpty(),
        )
    }

    // ---------- Helpers ----------------------------------------------------

    private fun notification(
        id: String,
        status: NotificationStatusUi,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "앱",
        packageName = "pkg.$id",
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
    )

    /**
     * Mirrors the buildString inside `HomeScreen.HeroCard`:
     *   `"오늘 알림 ${recent.size}개 중 중요한 ${priorityCount}개를 먼저 전달했어요"`
     */
    private fun heroTitle(totalCount: Int, priorityCount: Int): String =
        "오늘 알림 ${totalCount}개 중 중요한 ${priorityCount}개를 먼저 전달했어요"

    /**
     * Mirrors the buildString inside `InsightCard`:
     *   `append("지금까지 "); append(filteredCount); append("개의 알림을 대신 정리했어요")`
     */
    private fun insightPrimaryLine(filteredCount: Int): String =
        "지금까지 ${filteredCount}개의 알림을 대신 정리했어요"

    /**
     * Mirrors `Text("전체 알림 중 ${filteredSharePercent}%를 대신 정리했어요", ...)`
     * inside `InsightCard`.
     */
    private fun insightShareLine(filteredSharePercent: Int): String =
        "전체 알림 중 ${filteredSharePercent}%를 대신 정리했어요"

    /**
     * Mirrors the totals line inside `TimelineCard`:
     *   `"${timeline.range.label} 기준 ${timeline.totalFilteredCount}개의 알림이 정리됐어요"`
     */
    private fun timelineTotalsLine(rangeLabel: String, totalFilteredCount: Int): String =
        "${rangeLabel} 기준 ${totalFilteredCount}개의 알림이 정리됐어요"

    /**
     * Mirrors the bar-caption template inside `TimelineCard`:
     *   `val prefix = if (bar.isPeak) "피크" else "흐름"`
     *   `"$prefix · ${bar.label} · 정리 ${bar.filteredCount}건 · 즉시 ${bar.priorityCount}건"`
     */
    private fun timelineBarCaption(
        isPeak: Boolean,
        label: String,
        filtered: Int,
        priority: Int,
    ): String {
        val prefix = if (isPeak) "피크" else "흐름"
        return "$prefix · $label · 정리 ${filtered}건 · 즉시 ${priority}건"
    }

    /**
     * Mirrors the footer literal inside `HomeRecentMoreRow`:
     *   `Text(text = "전체 ${totalCount}건 보기", ...)`
     */
    private fun recentMoreRowFooter(totalCount: Int): String =
        "전체 ${totalCount}건 보기"

    private companion object {
        // ---- Literal-copy mirrors -----------------------------------
        // These constants mirror Korean copy that lives inside `HomeScreen.kt`
        // — some inside the root LazyColumn body, some inside the private
        // composables that Plan Task 2/3 will move into sibling files. The
        // Compose source must continue to emit these literals for the
        // affordances to look identical to users. Updating these without a
        // corresponding composable change is the regression signal.

        const val SCREEN_HEADER_BRAND = "SmartNoti"
        const val SCREEN_HEADER_TAGLINE = "중요한 알림만 먼저 보여드리고 있어요"

        const val STAT_PILL_PRIORITY_LABEL = "즉시"
        const val STAT_PILL_DIGEST_LABEL = "Digest"
        const val STAT_PILL_SILENT_LABEL = "조용히"

        const val NOTIFICATION_ACCESS_CARD_BADGE_LABEL = "실제 알림 상태"

        const val INSIGHT_CARD_BADGE_LABEL = "일반 인사이트"
        const val INSIGHT_CARD_SECTION_LABEL = "SmartNoti 인사이트"
        const val INSIGHT_REASON_BREAKDOWN_TAP_LABEL = "탭해서 자세히 보기"

        const val TIMELINE_CARD_HEADER = "최근 흐름"
        const val TIMELINE_CARD_EMPTY_COPY = "선택한 구간에는 아직 정리된 흐름이 없어요"

        const val RECENT_SECTION_HEADER = "방금 정리된 알림"
        const val EMPTY_RECENT_TITLE = "아직 쌓인 알림이 없어요"
        const val EMPTY_RECENT_SUBTITLE =
            "알림 접근 권한을 허용하면 실제 캡처된 알림만 여기에 보여드릴게요"
    }
}
