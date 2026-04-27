package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 1 (failing tests for Bug A — KCC `(광고)` prefix precedence).
 *
 * **Fixtures sourced from real Galaxy S24 (`R3CY2058DLJ`) DB rows** captured
 * during PR #486 diagnosis (issuecomment-4327574021). All three are notifications
 * the user reported as broken in issue #478: a `(광고)` body that ALSO contains
 * an IMPORTANT preset keyword (배송 / 결제 / 대출). Today the
 * [CategoryConflictResolver] picks IMPORTANT (PRIORITY) because both Categories
 * own KEYWORD-type rules and IMPORTANT has the lower drag `order`. The fix
 * (Tasks 2–3) introduces a KCC prefix detector + resolver precedence override
 * so PROMO wins whenever the body starts with `(광고)`.
 *
 * **This bundle (Task 1) ships the failing test only — assert NOT PRIORITY.**
 * Bug B2 (PROMO_QUIETING default action SILENT → DIGEST + migration) lands in
 * a subsequent bundle, so this file deliberately stays at the existing
 * semantic boundary: PROMO must beat IMPORTANT when the KCC `(광고)` marker is
 * present at the head of the body. The exact PROMO action (DIGEST today, per
 * [OnboardingQuickStartCategoryApplier]; future bundles may rewrite SILENT
 * installs) is not asserted here — only the negative claim "the user MUST NOT
 * see this in the priority tray".
 *
 * The Category graph, rule keywords, and drag `order` mirror the canonical
 * onboarding seeds: IMPORTANT_PRIORITY at `order=0` with keywords
 * `인증번호,결제,배송,출발`; PROMO_QUIETING at `order=1` with keywords
 * `광고,프로모션,쿠폰,세일,특가,이벤트,혜택`. Both presets ship enabled out of
 * the box for any user who picks PROMO_QUIETING during onboarding.
 */
class PromoPrefixRoutingFixturesTest {

    private val classifier = NotificationClassifier(
        vipSenders = emptySet(),
        priorityKeywords = emptySet(),
        shoppingPackages = emptySet(),
    )

    private val importantRule = RuleUiModel(
        id = "rule-onboarding-important_priority",
        title = "중요 알림",
        subtitle = "항상 바로 보기",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "인증번호,결제,배송,출발",
    )

    private val promoRule = RuleUiModel(
        id = "rule-onboarding-promo_quieting",
        title = "프로모션 알림",
        subtitle = "조용히 정리",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
    )

    private val importantCategory = Category(
        id = "cat-onboarding-important_priority",
        name = "중요 알림",
        appPackageName = null,
        ruleIds = listOf(importantRule.id),
        action = CategoryAction.PRIORITY,
        order = 0,
    )

    private val promoCategory = Category(
        id = "cat-onboarding-promo_quieting",
        name = "프로모션 알림",
        appPackageName = null,
        ruleIds = listOf(promoRule.id),
        action = CategoryAction.DIGEST,
        order = 1,
    )

    /**
     * Real DB row from `R3CY2058DLJ`: AliExpress promo arriving via Gmail's
     * sender bridge. The body is a textbook KCC-mandated `(광고)` prefix that
     * happens to mention `배송` (free delivery copy) — IMPORTANT_PRIORITY
     * matches `배송`, PROMO_QUIETING matches `광고`. Today the resolver picks
     * PRIORITY; the fix must keep this out of the priority tray.
     */
    @Test
    fun aliexpress_gmail_kcc_prefix_with_배송_keyword_must_not_be_priority() {
        val input = ClassificationInput(
            packageName = "com.google.android.gm",
            title = "AliExpress",
            body = "(광고) 상품을 무료로 배송받아보세요",
        )

        val result = classifier.classify(
            input = input,
            rules = listOf(importantRule, promoRule),
            categories = listOf(importantCategory, promoCategory),
        )

        assertNotEquals(
            "AliExpress (광고) ad must not route to PRIORITY just because '배송' " +
                "appears inside the ad copy. KCC `(광고)` prefix should win.",
            NotificationDecision.PRIORITY,
            result.decision,
        )
    }

    /**
     * Real DB row from `R3CY2058DLJ`: NaverShopping membership-day blast. Title
     * carries an alarm emoji + urgency copy; body opens with `(광고)` and lists
     * `무료배송` as one of the perks. IMPORTANT matches `배송`; PROMO matches
     * `광고`. Same precedence bug as the AliExpress case.
     */
    @Test
    fun navershopping_kcc_prefix_with_배송_keyword_must_not_be_priority() {
        val input = ClassificationInput(
            packageName = "com.nhn.android.shopping",
            title = "\uD83D\uDEA8컬리N마트 멤버십데이 곧 종료",
            body = "(광고)\u2460 인기 상품 6종 하나만 사도 무료배송 \u2461 적립 혜택",
        )

        val result = classifier.classify(
            input = input,
            rules = listOf(importantRule, promoRule),
            categories = listOf(importantCategory, promoCategory),
        )

        assertNotEquals(
            "NaverShopping 멤버십데이 (광고) ad must not route to PRIORITY just " +
                "because '배송' appears in the perks list.",
            NotificationDecision.PRIORITY,
            result.decision,
        )
    }

    /**
     * Real DB row from `R3CY2058DLJ`: SamsungMessaging-delivered SMS from
     * 하나카드. Telco gateways prepend `[Web발신]` before the KCC `(광고)` marker
     * (and some carriers double the marker). Body advertises `장기카드대출` —
     * IMPORTANT matches `대출` via the body content; PROMO matches `광고`.
     *
     * Note: '대출' is not literally in the IMPORTANT keyword set
     * `인증번호,결제,배송,출발`, but the user's installation also has standalone
     * keyword rules covering 대출 (per the issue #478 reproducer thread). We
     * exercise the same precedence path here by giving the IMPORTANT preset a
     * superset that includes 대출 — the test still asserts the precedence
     * contract: KCC `(광고)` prefix must not be steam-rolled by an IMPORTANT
     * keyword match no matter how the user composed their priority keywords.
     */
    @Test
    fun samsung_messaging_kcc_prefix_with_대출_keyword_must_not_be_priority() {
        val importantWith대출 = importantRule.copy(
            matchValue = importantRule.matchValue + ",대출",
        )

        val input = ClassificationInput(
            packageName = "com.samsung.android.messaging",
            title = "\u2068하나카드\u2069",
            body = "[Web발신] (광고) (광고)[하나카드] 장기카드대출 이용 안내",
        )

        val result = classifier.classify(
            input = input,
            rules = listOf(importantWith대출, promoRule),
            categories = listOf(importantCategory, promoCategory),
        )

        assertNotEquals(
            "SamsungMessaging 하나카드 (광고) ad must not route to PRIORITY just " +
                "because '대출' appears inside the ad copy. KCC `(광고)` prefix " +
                "should win even when the carrier prepends `[Web발신]` or doubles " +
                "the marker.",
            NotificationDecision.PRIORITY,
            result.decision,
        )
    }
}
