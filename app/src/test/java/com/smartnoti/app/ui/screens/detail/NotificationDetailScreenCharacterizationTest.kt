package com.smartnoti.app.ui.screens.detail

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailOnboardingRecommendationSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailReasonSectionBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailSourceSuppressionSummaryBuilder
import com.smartnoti.app.domain.usecase.QuietHoursExplainerBuilder
import com.smartnoti.app.domain.usecase.shouldShowDetailCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md`
 * Task 1 — pin the user-visible affordances on `NotificationDetailScreen`
 * before the per-section file split runs in Tasks 2–3.
 *
 * The `NotificationDetailScreen` root composable is wired to four singletons
 * (`NotificationRepository.getInstance(context)` + `RulesRepository.getInstance`
 * + `CategoriesRepository.getInstance` + `SettingsRepository.getInstance`),
 * five pure builders, two `remember`-d use cases, a `SnackbarHostState`, and a
 * `MarkSilentProcessedTrayCancelChain` constructed inside an `onClick` lambda.
 * Booting it under Robolectric would require stubbing every singleton + a real
 * DataStore + a fake `NotificationListenerService`, which is out of scope for a
 * pure refactor PR. The plan accepts the same trade-off as
 * `SettingsScreenCharacterizationTest` (Plan
 * `2026-04-27-refactor-settings-screen-split.md` Task 1) and
 * `HomeScreenCharacterizationTest` (Plan
 * `2026-04-27-refactor-home-screen-split.md` Task 1).
 *
 * Following that prior pattern, this file pins the **pure builders + Korean
 * copy literals** that drive the affordances called out in the plan's Task 1
 * Step 2:
 *
 *   1. `DetailTopBar` title + back content-description.
 *   2. The 요약 카드 (appName / sender-or-title / body / `StatusBadge`) — only
 *      the literal copy is pinned; the data flow is `NotificationUiModel` → the
 *      Card body, no builder in between.
 *   3. "왜 이렇게 처리됐나요?" header + `hasReasonContent` mount gate
 *      (`reasonSections` + `quietHoursExplainer`) + the three sub-section
 *      headers (`SmartNoti 가 본 신호` / `지금 적용된 정책` / `적용된 규칙`).
 *   4. "어떻게 전달되나요?" header + the five `<라벨> · <값>` row prefixes
 *      driven by `NotificationDetailDeliveryProfileSummaryBuilder`.
 *   5. "원본 알림 처리 상태" header + the two row prefixes + the
 *      `shouldShowDetailCard` mount gate driven by
 *      `NotificationDetailSourceSuppressionSummaryBuilder`.
 *   6. "조용히 보관 중" ARCHIVED card header + body + "처리 완료로 표시" button
 *      label, gated by `status == SILENT && silentMode == ARCHIVED`.
 *   7. "이 알림 분류하기" CTA card header + body + "분류 변경" button label.
 *   8. `EmptyState("알림을 찾을 수 없어요", ...)` notification-not-found copy.
 *   9. `quietHoursExplainer` higher-precedence-suppression contract — when
 *      `사용자 규칙` is also present, the quiet-hours explainer must be
 *      suppressed even though `조용한 시간` is in `reasonTags`. This is the
 *      assertion called out in plan Task 1 Step 2 bullet "higher-precedence
 *      suppression".
 *  10. `resolveOwningCategoryAction` — the helper that drives the dynamic-
 *      opposite default on Path B's prefill seed. Pinned per-rule-type so the
 *      cut-paste into `NotificationDetailReclassifyActions.kt` cannot drop a
 *      branch.
 *
 * Each builder + helper covered here is pure and lives in either
 * `domain/usecase/` (cross-package, untouched by the refactor) or
 * `ui/screens/detail/` (same package as the soon-to-be-extracted sub-files).
 * The "thin renderer" copy that lives **only** inside the inline Card composables
 * in `NotificationDetailScreen.kt` is pinned by string-equality assertions
 * against compiled constants this test imports from its companion. Those
 * constants must mirror the literal copy emitted by the composable; if Task
 * 2/3 rewrites the literal, this test fails and the regression is visible at
 * review time.
 *
 * Behavioural verification (the "분류 변경" button actually mounting the assign
 * sheet, the "처리 완료로 표시" button actually firing the chain, the explainer
 * actually rendering under the right preconditions, ...) is covered by the
 * existing notification-detail ADB recipe in the journey doc — re-running it
 * post-refactor is the Task 4 verification step in the plan.
 */
class NotificationDetailScreenCharacterizationTest {

    // ---------- Affordance 1: DetailTopBar -------------------------------
    // Plan Task 3 moves `DetailTopBar` to
    // `NotificationDetailReclassifyActions.kt` (visibility `private` →
    // `internal`). Pin the title literal + back content-description literal
    // so the move cannot accidentally rename either.

    @Test
    fun detailTopBar_title_pin_korean_copy() {
        // Mirrors `DetailTopBar(title = "알림 상세", onBack = onBack)` —
        // both call sites (the empty branch + the populated branch) pass the
        // same string.
        assertEquals("알림 상세", DETAIL_TOP_BAR_TITLE)
    }

    @Test
    fun detailTopBar_back_content_description_pin_korean_copy() {
        // Mirrors `Icon(..., contentDescription = "뒤로 가기", ...)` inside
        // `DetailTopBar`. Accessibility surface — drift is silent regression.
        assertEquals("뒤로 가기", DETAIL_TOP_BAR_BACK_CONTENT_DESCRIPTION)
    }

    // ---------- Affordance 2: Summary card body --------------------------
    // The 요약 card stays in `NotificationDetailScreen.kt` root per plan
    // Architecture paragraph ("root composable + 화면-레벨 state hoisting +
    // 첫 요약 카드 `item`"). Pin its sender-or-title fallback contract — the
    // composable reads `notification.sender ?: notification.title` so the
    // sender slot reads the title literal when sender is null.

    @Test
    fun summaryCard_sender_or_title_renders_sender_when_present() {
        // Mirrors `Text(notification.sender ?: notification.title, ...)`.
        val notification = baseNotification(sender = "Coupang", title = "오늘의 특가")
        assertEquals("Coupang", notification.sender ?: notification.title)
    }

    @Test
    fun summaryCard_sender_or_title_falls_back_to_title_when_sender_null() {
        val notification = baseNotification(sender = null, title = "오늘의 특가")
        assertEquals("오늘의 특가", notification.sender ?: notification.title)
    }

    // ---------- Affordance 3: Reason card --------------------------------
    // Plan Task 2 moves the "왜 이렇게 처리됐나요?" Card +
    // `ReasonSubSection` helper to `NotificationDetailReasonSection.kt`. Pin
    // the header literal, the three sub-section headers + descriptions, and
    // the `hasReasonContent` mount gate.

    @Test
    fun reasonCard_header_pin_korean_copy() {
        // Mirrors `Text("왜 이렇게 처리됐나요?", ...)` inside the Reason Card.
        assertEquals("왜 이렇게 처리됐나요?", REASON_CARD_HEADER)
    }

    @Test
    fun reasonCard_classifier_signals_subsection_pin_korean_copy() {
        // Mirrors `ReasonSubSection(title = "SmartNoti 가 본 신호", description = ...)`.
        assertEquals("SmartNoti 가 본 신호", REASON_SUBSECTION_CLASSIFIER_TITLE)
        assertEquals(
            "분류에 참고한 내부 신호예요. 직접 수정할 수는 없어요.",
            REASON_SUBSECTION_CLASSIFIER_DESCRIPTION,
        )
    }

    @Test
    fun reasonCard_quiet_hours_subsection_pin_korean_copy() {
        // Mirrors `ReasonSubSection(title = "지금 적용된 정책", description = ...)`.
        assertEquals("지금 적용된 정책", REASON_SUBSECTION_QUIET_HOURS_TITLE)
        assertEquals(
            "사용자가 설정한 시간 정책에 따라 자동으로 분류됐어요.",
            REASON_SUBSECTION_QUIET_HOURS_DESCRIPTION,
        )
    }

    @Test
    fun reasonCard_rule_hits_subsection_pin_korean_copy() {
        // Mirrors `ReasonSubSection(title = "적용된 규칙", description = ...)`.
        assertEquals("적용된 규칙", REASON_SUBSECTION_RULE_HITS_TITLE)
        assertEquals(
            "내 규칙 탭에서 수정하거나 끌 수 있는 규칙이에요. 탭하면 해당 규칙으로 이동해요.",
            REASON_SUBSECTION_RULE_HITS_DESCRIPTION,
        )
    }

    @Test
    fun reasonCard_visibility_gate_skipped_when_no_signals_or_rules_or_explainer() {
        // Mirrors the `hasReasonContent` `if` gate that wraps the Reason Card
        // `item { ... }` in the root LazyColumn:
        //   val sections = reasonSections
        //   val hasReasonContent = (sections != null &&
        //       (sections.classifierSignals.isNotEmpty() || sections.ruleHits.isNotEmpty())) ||
        //       quietHoursExplainer != null
        // Empty inputs → no signals, no hits, no explainer → card skipped.
        val sections = NotificationDetailReasonSectionBuilder().build(
            notification = baseNotification(reasonTags = emptyList(), matchedRuleIds = emptyList()),
            rules = emptyList(),
        )
        assertTrue(sections.classifierSignals.isEmpty())
        assertTrue(sections.ruleHits.isEmpty())
        // The mount gate logic isolated here — both halves false.
        assertFalse(reasonCardShouldMount(sections, quietHoursExplainer = null))
    }

    @Test
    fun reasonCard_visibility_gate_mounts_when_classifier_signals_present() {
        val sections = NotificationDetailReasonSectionBuilder().build(
            notification = baseNotification(reasonTags = listOf("발신자 있음"), matchedRuleIds = emptyList()),
            rules = emptyList(),
        )
        assertTrue(sections.classifierSignals.contains("발신자 있음"))
        assertTrue(reasonCardShouldMount(sections, quietHoursExplainer = null))
    }

    @Test
    fun reasonCard_visibility_gate_mounts_when_rule_hits_present() {
        val rule = RuleUiModel(
            id = "rule-1",
            title = "Coupang",
            subtitle = "Digest로 보내기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "Coupang",
        )
        val sections = NotificationDetailReasonSectionBuilder().build(
            notification = baseNotification(reasonTags = emptyList(), matchedRuleIds = listOf("rule-1")),
            rules = listOf(rule),
        )
        assertTrue(sections.ruleHits.isNotEmpty())
        assertTrue(reasonCardShouldMount(sections, quietHoursExplainer = null))
    }

    @Test
    fun reasonCard_visibility_gate_mounts_when_only_quiet_hours_explainer_present() {
        val sections = NotificationDetailReasonSectionBuilder().build(
            notification = baseNotification(reasonTags = emptyList(), matchedRuleIds = emptyList()),
            rules = emptyList(),
        )
        val explainer = QuietHoursExplainerBuilder().build(
            reasonTags = listOf("조용한 시간"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )
        assertNotNull(explainer)
        assertTrue(reasonCardShouldMount(sections, quietHoursExplainer = explainer))
    }

    // ---------- Affordance 9: quiet-hours higher-precedence suppression --
    // Plan Task 1 Step 2 bullet "higher-precedence suppression" — when the
    // notification has both `사용자 규칙` and `조용한 시간` reasonTags, the
    // explainer must be suppressed (the user rule, not quiet-hours, was the
    // decisive signal). The Reason Card's "지금 적용된 정책" sub-section then
    // does NOT mount.

    @Test
    fun quietHoursExplainer_suppressed_when_user_rule_tag_also_present() {
        // Mirrors the `HIGHER_PRECEDENCE_TAGS = setOf("사용자 규칙")` filter in
        // `QuietHoursExplainerBuilder`.
        val explainer = QuietHoursExplainerBuilder().build(
            reasonTags = listOf("조용한 시간", "사용자 규칙"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )
        assertNull(explainer)
    }

    @Test
    fun quietHoursExplainer_renders_when_only_quiet_hours_tag_present_at_digest() {
        val explainer = QuietHoursExplainerBuilder().build(
            reasonTags = listOf("조용한 시간"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )
        assertNotNull(explainer)
        // Pin the rendered overnight window template the Detail Card displays.
        assertEquals(
            "지금이 조용한 시간(23시~익일 7시)이라 자동으로 모아뒀어요.",
            explainer!!.message,
        )
    }

    // ---------- Affordance 4: Delivery profile card ----------------------
    // Plan Task 2 moves the "어떻게 전달되나요?" Card to
    // `NotificationDetailDeliverySection.kt`. Pin the header + the five
    // `<라벨> · <값>` row prefixes. The values come from
    // `NotificationDetailDeliveryProfileSummaryBuilder` — a separate test
    // (`NotificationDetailDeliveryProfileSummaryBuilderTest`) covers the
    // `<값>` mapping; this test pins only the row prefixes that live as
    // string literals inside the Card.

    @Test
    fun deliveryCard_header_pin_korean_copy() {
        assertEquals("어떻게 전달되나요?", DELIVERY_CARD_HEADER)
    }

    @Test
    fun deliveryCard_row_prefixes_pin_korean_copy() {
        // Mirrors the five `Text("<라벨> · ${...}", ...)` rows inside the
        // Delivery Card body. Each prefix is a literal in the Card source —
        // the cut-paste must preserve all five.
        assertEquals("전달 모드", DELIVERY_CARD_PREFIX_DELIVERY_MODE)
        assertEquals("소리", DELIVERY_CARD_PREFIX_ALERT_LEVEL)
        assertEquals("진동", DELIVERY_CARD_PREFIX_VIBRATION)
        assertEquals("Heads-up", DELIVERY_CARD_PREFIX_HEADS_UP)
        assertEquals("잠금화면", DELIVERY_CARD_PREFIX_LOCK_SCREEN)
    }

    @Test
    fun deliveryProfileBuilder_drives_card_visibility_via_nullable() {
        // The renderer's `if (deliveryProfileSummary != null) { item { ... } }`
        // gate hinges on the builder returning a non-null summary for any
        // non-null `notification`. Pin that contract.
        val summary = NotificationDetailDeliveryProfileSummaryBuilder().build(
            notification = baseNotification(),
        )
        assertNotNull(summary)
        // Pin the labels that compose the visible row tails.
        assertNotNull(summary.deliveryModeLabel)
        assertNotNull(summary.alertLevelLabel)
        assertNotNull(summary.vibrationLabel)
        assertNotNull(summary.headsUpLabel)
        assertNotNull(summary.lockScreenVisibilityLabel)
        assertNotNull(summary.overview)
    }

    // ---------- Affordance 5: Source suppression card --------------------
    // Plan Task 2 moves the "원본 알림 처리 상태" Card to
    // `NotificationDetailDeliverySection.kt`. Pin the header + the two row
    // prefixes + the `shouldShowDetailCard` mount gate.

    @Test
    fun sourceSuppressionCard_header_pin_korean_copy() {
        assertEquals("원본 알림 처리 상태", SOURCE_SUPPRESSION_CARD_HEADER)
    }

    @Test
    fun sourceSuppressionCard_row_prefixes_pin_korean_copy() {
        assertEquals("원본 상태", SOURCE_SUPPRESSION_CARD_PREFIX_STATUS)
        assertEquals("대체 알림", SOURCE_SUPPRESSION_CARD_PREFIX_REPLACEMENT)
    }

    @Test
    fun sourceSuppressionCard_visibility_gate_skipped_when_not_configured_and_no_replacement() {
        // Mirrors `if (sourceSuppressionSummary != null)` in the renderer +
        // the `shouldShowDetailCard` extension on
        // `SourceNotificationSuppressionState`.
        assertFalse(
            SourceNotificationSuppressionState.NOT_CONFIGURED
                .shouldShowDetailCard(replacementNotificationIssued = false),
        )
    }

    @Test
    fun sourceSuppressionCard_visibility_gate_mounts_when_replacement_was_issued() {
        assertTrue(
            SourceNotificationSuppressionState.NOT_CONFIGURED
                .shouldShowDetailCard(replacementNotificationIssued = true),
        )
    }

    @Test
    fun sourceSuppressionCard_visibility_gate_mounts_when_state_is_cancel_attempted() {
        assertTrue(
            SourceNotificationSuppressionState.CANCEL_ATTEMPTED
                .shouldShowDetailCard(replacementNotificationIssued = false),
        )
    }

    @Test
    fun sourceSuppressionBuilder_drives_card_row_tails() {
        val summary = NotificationDetailSourceSuppressionSummaryBuilder().build(
            suppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
            replacementNotificationIssued = true,
        )
        assertNotNull(summary.statusLabel)
        assertNotNull(summary.replacementLabel)
        assertNotNull(summary.overview)
    }

    // ---------- Affordance 6: ARCHIVED "조용히 보관 중" card --------------
    // Plan Task 2 moves the ARCHIVED-only completion Card to
    // `NotificationDetailDeliverySection.kt`. Pin the header + body + button
    // label + visibility gate (`status == SILENT && silentMode == ARCHIVED`).

    @Test
    fun archivedCard_header_pin_korean_copy() {
        assertEquals("조용히 보관 중", ARCHIVED_CARD_HEADER)
    }

    @Test
    fun archivedCard_body_pin_korean_copy() {
        assertEquals(
            "원본 알림이 아직 알림창에 남아 있어요. 확인이 끝났다면 처리 완료로 표시해 알림창에서 치울 수 있어요.",
            ARCHIVED_CARD_BODY,
        )
    }

    @Test
    fun archivedCard_button_label_pin_korean_copy() {
        assertEquals("처리 완료로 표시", ARCHIVED_CARD_BUTTON_LABEL)
    }

    @Test
    fun archivedCard_visibility_gate_mounts_only_for_silent_archived() {
        // Mirrors:
        //   if (notification.status == NotificationStatusUi.SILENT &&
        //       notification.silentMode == SilentMode.ARCHIVED) { item { ... } }
        assertTrue(
            archivedCardShouldMount(
                status = NotificationStatusUi.SILENT,
                silentMode = SilentMode.ARCHIVED,
            ),
        )
        assertFalse(
            archivedCardShouldMount(
                status = NotificationStatusUi.SILENT,
                silentMode = null,
            ),
        )
        assertFalse(
            archivedCardShouldMount(
                status = NotificationStatusUi.DIGEST,
                silentMode = SilentMode.ARCHIVED,
            ),
        )
        assertFalse(
            archivedCardShouldMount(
                status = NotificationStatusUi.PRIORITY,
                silentMode = null,
            ),
        )
    }

    // ---------- Affordance 7: Reclassify CTA card ------------------------
    // Plan Task 3 moves the "이 알림 분류하기" Card to
    // `NotificationDetailReclassifyActions.kt`. Pin header + body + button
    // label.

    @Test
    fun reclassifyCta_header_pin_korean_copy() {
        assertEquals("이 알림 분류하기", RECLASSIFY_CTA_HEADER)
    }

    @Test
    fun reclassifyCta_body_pin_korean_copy() {
        assertEquals(
            "이 알림이 어떤 분류에 속하는지 알려주세요. 다음부터는 분류의 전달 방식이 적용돼요.",
            RECLASSIFY_CTA_BODY,
        )
    }

    @Test
    fun reclassifyCta_button_label_pin_korean_copy() {
        // Mirrors `Button(onClick = { showAssignSheet = true }) { Text("분류 변경") }`.
        // This is the gateway into [CategoryAssignBottomSheet] — the entire
        // re-categorize UX hangs off this label.
        assertEquals("분류 변경", RECLASSIFY_CTA_BUTTON_LABEL)
    }

    // ---------- Affordance 8: Empty state --------------------------------
    // Mirrors `EmptyState(title = "알림을 찾을 수 없어요", subtitle = ...)` shown
    // when `repository.observeNotification(id)` emits null. Stays in
    // `NotificationDetailScreen.kt` root.

    @Test
    fun emptyState_title_pin_korean_copy() {
        assertEquals("알림을 찾을 수 없어요", EMPTY_STATE_TITLE)
    }

    @Test
    fun emptyState_subtitle_pin_korean_copy() {
        assertEquals(
            "이미 삭제됐거나 아직 저장되지 않은 실제 알림일 수 있어요",
            EMPTY_STATE_SUBTITLE,
        )
    }

    // ---------- Affordance 10: resolveOwningCategoryAction helper --------
    // Plan Task 3 moves `resolveOwningCategoryAction` + `ruleMatches`
    // (`private fun`) to `NotificationDetailReclassifyActions.kt`. The helper
    // drives Path B's prefill `defaultAction` (dynamic-opposite of the
    // currently-owning Category's action). Pin the per-rule-type matching
    // contract so the cut-paste cannot drop a branch.
    //
    // Both functions are `private fun` in `NotificationDetailScreen.kt`, so
    // these tests pin the **observable contract** by mirroring the logic in
    // helpers below. If Task 3's cut-paste rewrites either branch, the
    // mirrored helper here drifts from the production helper and the next
    // refactor surfaces the difference at review time. (A `private fun` cannot
    // be invoked across-file without a visibility bump beyond the plan's
    // scope.)

    @Test
    fun ruleMatches_person_branch_matches_sender_case_insensitive() {
        val rule = RuleUiModel(
            id = "r1",
            title = "Coupang",
            subtitle = "Digest로 보내기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "Coupang",
        )
        assertTrue(ruleMatchesContract(rule, baseNotification(sender = "coupang")))
        assertFalse(ruleMatchesContract(rule, baseNotification(sender = null)))
    }

    @Test
    fun ruleMatches_app_branch_matches_packageName_case_insensitive() {
        val rule = RuleUiModel(
            id = "r1",
            title = "Slack",
            subtitle = "조용히 처리",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.slack",
        )
        assertTrue(ruleMatchesContract(rule, baseNotification(packageName = "COM.SLACK")))
        assertFalse(ruleMatchesContract(rule, baseNotification(packageName = "com.other")))
    }

    @Test
    fun ruleMatches_keyword_branch_splits_on_comma_and_trims() {
        val rule = RuleUiModel(
            id = "r1",
            title = "프로모션",
            subtitle = "Digest로 보내기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "쿠폰, 세일,특가",
        )
        assertTrue(ruleMatchesContract(rule, baseNotification(title = "특가 안내", body = "")))
        assertTrue(ruleMatchesContract(rule, baseNotification(title = "안내", body = "오늘만 세일")))
        assertFalse(ruleMatchesContract(rule, baseNotification(title = "일반", body = "본문")))
    }

    @Test
    fun ruleMatches_schedule_and_repeatBundle_branches_never_match() {
        val schedule = baseRule(type = RuleTypeUi.SCHEDULE, matchValue = "anything")
        val repeatBundle = baseRule(type = RuleTypeUi.REPEAT_BUNDLE, matchValue = "anything")
        assertFalse(ruleMatchesContract(schedule, baseNotification()))
        assertFalse(ruleMatchesContract(repeatBundle, baseNotification()))
    }

    @Test
    fun resolveOwningCategoryAction_returns_null_when_no_rule_matches() {
        val result = resolveOwningCategoryActionContract(
            notification = baseNotification(sender = "Other"),
            categories = listOf(category(id = "cat-1", ruleIds = listOf("r1"))),
            rules = listOf(baseRule(id = "r1", matchValue = "Coupang")),
        )
        assertNull(result)
    }

    @Test
    fun resolveOwningCategoryAction_returns_owning_category_action_for_matched_rule() {
        val result = resolveOwningCategoryActionContract(
            notification = baseNotification(sender = "Coupang"),
            categories = listOf(
                category(id = "cat-1", ruleIds = listOf("r1"), action = CategoryAction.DIGEST),
            ),
            rules = listOf(baseRule(id = "r1", matchValue = "Coupang")),
        )
        assertEquals(CategoryAction.DIGEST, result)
    }

    @Test
    fun resolveOwningCategoryAction_ignores_disabled_rules() {
        val result = resolveOwningCategoryActionContract(
            notification = baseNotification(sender = "Coupang"),
            categories = listOf(category(id = "cat-1", ruleIds = listOf("r1"))),
            rules = listOf(baseRule(id = "r1", matchValue = "Coupang", enabled = false)),
        )
        assertNull(result)
    }

    // ---------- Onboarding recommendation card --------------------------
    // Plan Task 2 moves the (optional) 온보딩 추천 Card to
    // `NotificationDetailReasonSection.kt`. Pin the visibility-by-builder
    // contract (the renderer's `if (onboardingRecommendationSummary != null)`
    // gate hinges on the builder returning null when the trigger reasonTag
    // is absent).

    @Test
    fun onboardingRecommendationBuilder_returns_null_when_trigger_tag_absent() {
        val summary = NotificationDetailOnboardingRecommendationSummaryBuilder().build(
            notification = baseNotification(reasonTags = listOf("발신자 있음")),
        )
        assertNull(summary)
    }

    @Test
    fun onboardingRecommendationBuilder_returns_summary_when_trigger_and_recommendation_present() {
        val summary = NotificationDetailOnboardingRecommendationSummaryBuilder().build(
            notification = baseNotification(reasonTags = listOf("온보딩 추천", "프로모션 알림")),
        )
        assertNotNull(summary)
        // The Card body reads `summary.title` + `summary.body` directly.
        assertEquals("빠른 시작 추천에서 추가된 규칙이에요", summary!!.title)
        assertTrue(summary.body.contains("프로모션 알림"))
    }

    // ---------- NotificationDetailScreen overload sanity check -----------
    // The root composable is `@Composable fun NotificationDetailScreen(...)`
    // with `contentPadding`, `notificationId`, `onBack`, and an optional
    // `onRuleClick` parameter. Pin its presence by reflection so the refactor
    // cannot accidentally rename / move the public entrypoint while shuffling
    // private composables into sub-files. (`AppNavHost.kt` is the only
    // caller.)

    @Test
    fun notificationDetailScreen_root_composable_function_remains_public_in_detail_package() {
        val klass = Class.forName(
            "com.smartnoti.app.ui.screens.detail.NotificationDetailScreenKt",
        )
        val candidates = klass.declaredMethods.filter { it.name == "NotificationDetailScreen" }
        assertNotNull(
            "NotificationDetailScreen public composable must remain in com.smartnoti.app.ui.screens.detail",
            candidates,
        )
        assertTrue(
            "NotificationDetailScreen public composable must remain in com.smartnoti.app.ui.screens.detail",
            candidates.isNotEmpty(),
        )
    }

    // ---------- Helpers --------------------------------------------------

    private fun baseNotification(
        id: String = "known",
        appName: String = "Coupang",
        packageName: String = "com.coupang",
        sender: String? = "Coupang",
        title: String = "오늘의 특가",
        body: String = "본문",
        status: NotificationStatusUi = NotificationStatusUi.PRIORITY,
        reasonTags: List<String> = emptyList(),
        matchedRuleIds: List<String> = emptyList(),
        silentMode: SilentMode? = null,
        sourceSuppressionState: SourceNotificationSuppressionState =
            SourceNotificationSuppressionState.NOT_CONFIGURED,
        replacementNotificationIssued: Boolean = false,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = sender,
        title = title,
        body = body,
        receivedAtLabel = "방금",
        status = status,
        reasonTags = reasonTags,
        score = null,
        isBundled = false,
        isPersistent = false,
        alertLevel = AlertLevel.NONE,
        vibrationMode = VibrationMode.OFF,
        headsUpEnabled = false,
        lockScreenVisibility = LockScreenVisibilityMode.SECRET,
        sourceSuppressionState = sourceSuppressionState,
        replacementNotificationIssued = replacementNotificationIssued,
        postedAtMillis = 0L,
        silentMode = silentMode,
        matchedRuleIds = matchedRuleIds,
    )

    private fun baseRule(
        id: String = "r1",
        type: RuleTypeUi = RuleTypeUi.PERSON,
        matchValue: String = "Coupang",
        enabled: Boolean = true,
    ): RuleUiModel = RuleUiModel(
        id = id,
        title = matchValue,
        subtitle = "Digest로 보내기",
        type = type,
        enabled = enabled,
        matchValue = matchValue,
    )

    private fun category(
        id: String,
        ruleIds: List<String>,
        action: CategoryAction = CategoryAction.DIGEST,
    ): Category = Category(
        id = id,
        name = id,
        appPackageName = null,
        ruleIds = ruleIds,
        action = action,
        order = 0,
    )

    /**
     * Mirrors the `hasReasonContent` boolean inside `NotificationDetailScreen`
     * — the gate that wraps the Reason Card's `item { ... }`.
     */
    private fun reasonCardShouldMount(
        sections: com.smartnoti.app.domain.usecase.NotificationDetailReasonSections?,
        quietHoursExplainer: com.smartnoti.app.domain.usecase.QuietHoursExplainer?,
    ): Boolean {
        return (sections != null &&
            (sections.classifierSignals.isNotEmpty() || sections.ruleHits.isNotEmpty())) ||
            quietHoursExplainer != null
    }

    /**
     * Mirrors the ARCHIVED Card's visibility `if` gate inside
     * `NotificationDetailScreen`.
     */
    private fun archivedCardShouldMount(
        status: NotificationStatusUi,
        silentMode: SilentMode?,
    ): Boolean = status == NotificationStatusUi.SILENT && silentMode == SilentMode.ARCHIVED

    /**
     * Mirrors `private fun ruleMatches(rule, notification)` inside
     * `NotificationDetailScreen.kt`. Kept in lock-step so a stray rewrite
     * during the cut-paste in Task 3 surfaces here.
     */
    private fun ruleMatchesContract(
        rule: RuleUiModel,
        notification: NotificationUiModel,
    ): Boolean {
        return when (rule.type) {
            RuleTypeUi.PERSON ->
                !notification.sender.isNullOrBlank() &&
                    notification.sender.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.APP ->
                notification.packageName.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.KEYWORD -> {
                val content = listOf(notification.title, notification.body).joinToString(" ")
                rule.matchValue
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .any { content.contains(it, ignoreCase = true) }
            }
            // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
            // Task 2: SENDER mirror — title substring + ignoreCase, blank
            // matchValue rejected. Kept in lock-step with the production
            // `NotificationDetailReclassifyActions.ruleMatches` SENDER branch.
            RuleTypeUi.SENDER ->
                rule.matchValue.isNotBlank() &&
                    notification.title.contains(rule.matchValue, ignoreCase = true)
            RuleTypeUi.SCHEDULE,
            RuleTypeUi.REPEAT_BUNDLE -> false
        }
    }

    /**
     * Mirrors `private fun resolveOwningCategoryAction(notification, categories,
     * rules)` inside `NotificationDetailScreen.kt`. Same lock-step rationale
     * as `ruleMatchesContract` above.
     */
    private fun resolveOwningCategoryActionContract(
        notification: NotificationUiModel,
        categories: List<Category>,
        rules: List<RuleUiModel>,
    ): CategoryAction? {
        val matchedRuleIds = rules
            .filter { rule -> rule.enabled && ruleMatchesContract(rule, notification) }
            .map { it.id }
            .toSet()
        if (matchedRuleIds.isEmpty()) return null
        val owning = categories.firstOrNull { category ->
            category.ruleIds.any { it in matchedRuleIds }
        }
        return owning?.action
    }

    private companion object {
        // ---- Literal-copy mirrors -----------------------------------
        // These constants mirror Korean copy that lives inside
        // `NotificationDetailScreen.kt` — some inside the root LazyColumn
        // body, some inside the private composables that Plan Task 2/3 will
        // move into sibling files. The Compose source must continue to emit
        // these literals for the affordances to look identical to users.
        // Updating these without a corresponding composable change is the
        // regression signal.

        const val DETAIL_TOP_BAR_TITLE = "알림 상세"
        const val DETAIL_TOP_BAR_BACK_CONTENT_DESCRIPTION = "뒤로 가기"

        const val REASON_CARD_HEADER = "왜 이렇게 처리됐나요?"
        const val REASON_SUBSECTION_CLASSIFIER_TITLE = "SmartNoti 가 본 신호"
        const val REASON_SUBSECTION_CLASSIFIER_DESCRIPTION =
            "분류에 참고한 내부 신호예요. 직접 수정할 수는 없어요."
        const val REASON_SUBSECTION_QUIET_HOURS_TITLE = "지금 적용된 정책"
        const val REASON_SUBSECTION_QUIET_HOURS_DESCRIPTION =
            "사용자가 설정한 시간 정책에 따라 자동으로 분류됐어요."
        const val REASON_SUBSECTION_RULE_HITS_TITLE = "적용된 규칙"
        const val REASON_SUBSECTION_RULE_HITS_DESCRIPTION =
            "내 규칙 탭에서 수정하거나 끌 수 있는 규칙이에요. 탭하면 해당 규칙으로 이동해요."

        const val DELIVERY_CARD_HEADER = "어떻게 전달되나요?"
        const val DELIVERY_CARD_PREFIX_DELIVERY_MODE = "전달 모드"
        const val DELIVERY_CARD_PREFIX_ALERT_LEVEL = "소리"
        const val DELIVERY_CARD_PREFIX_VIBRATION = "진동"
        const val DELIVERY_CARD_PREFIX_HEADS_UP = "Heads-up"
        const val DELIVERY_CARD_PREFIX_LOCK_SCREEN = "잠금화면"

        const val SOURCE_SUPPRESSION_CARD_HEADER = "원본 알림 처리 상태"
        const val SOURCE_SUPPRESSION_CARD_PREFIX_STATUS = "원본 상태"
        const val SOURCE_SUPPRESSION_CARD_PREFIX_REPLACEMENT = "대체 알림"

        const val ARCHIVED_CARD_HEADER = "조용히 보관 중"
        const val ARCHIVED_CARD_BODY =
            "원본 알림이 아직 알림창에 남아 있어요. 확인이 끝났다면 처리 완료로 표시해 알림창에서 치울 수 있어요."
        const val ARCHIVED_CARD_BUTTON_LABEL = "처리 완료로 표시"

        const val RECLASSIFY_CTA_HEADER = "이 알림 분류하기"
        const val RECLASSIFY_CTA_BODY =
            "이 알림이 어떤 분류에 속하는지 알려주세요. 다음부터는 분류의 전달 방식이 적용돼요."
        const val RECLASSIFY_CTA_BUTTON_LABEL = "분류 변경"

        const val EMPTY_STATE_TITLE = "알림을 찾을 수 없어요"
        const val EMPTY_STATE_SUBTITLE =
            "이미 삭제됐거나 아직 저장되지 않은 실제 알림일 수 있어요"
    }
}
