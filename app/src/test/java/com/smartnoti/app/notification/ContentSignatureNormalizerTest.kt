package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 1 — RED tests for the not-yet-existing [ContentSignatureNormalizer].
 *
 * Issue #488 (Bug 2): 네이버페이 `[현장결제]` 포인트뽑기 알림은 본문이
 * `"8원이 적립되었어요"` / `"12원이 적립되었어요"` / `"28원이 적립되었어요"` 처럼
 * 금액 토큰만 다른 동일 템플릿이지만, 현재 [com.smartnoti.app.domain.usecase.DuplicateNotificationPolicy]
 * 의 lowercase + whitespace-collapse 시그니처는 매번 다른 hash 를 만들어
 * `repeat_bundle:3` rule 과 base `duplicateDigestThreshold` heuristic 모두 놓침.
 *
 * Normalizer 는 toggle (`enabled = true`) 일 때 다음을 collapse 한다:
 *   - 시간 토큰 `\b\d{1,2}:\d{2}\b` → `<time>`
 *   - 통화/숫자 토큰 (`8원`, `1,234원`, `$10`, `$10,000`) → `<num>`
 *   - 잔여 bare digit run → `<num>`
 *
 * `enabled = false` 일 때는 입력을 그대로 반환 (default-OFF safety guard).
 *
 * 정규화는 policy 가 이미 lowercase + whitespace-collapse 한 결과 위에서
 * 동작하기로 약속되어 있으므로, 입력 fixture 도 lowercase 형태로 작성한다.
 */
class ContentSignatureNormalizerTest {

    private val enabled = ContentSignatureNormalizer(enabled = true)
    private val disabled = ContentSignatureNormalizer(enabled = false)

    @Test
    fun naver_pay_point_pickup_amounts_collapse_to_same_signature() {
        val base = enabled.normalize("[현장결제] 8원이 적립되었어요")
        val twelve = enabled.normalize("[현장결제] 12원이 적립되었어요")
        val sixteen = enabled.normalize("[현장결제] 16원이 적립되었어요")
        val twentyEight = enabled.normalize("[현장결제] 28원이 적립되었어요")
        val commaThousands = enabled.normalize("[현장결제] 1,234원이 적립되었어요")

        assertEquals("8원 fixture must collapse to same shape as 12원", base, twelve)
        assertEquals("8원 fixture must collapse to same shape as 16원", base, sixteen)
        assertEquals("8원 fixture must collapse to same shape as 28원", base, twentyEight)
        assertEquals(
            "comma-thousands fixture must collapse to the same shape",
            base,
            commaThousands,
        )
    }

    @Test
    fun naver_pay_point_pickup_collapses_distinctly_from_no_amount_body() {
        val withAmount = enabled.normalize("[현장결제] 8원이 적립되었어요")
        val noAmount = enabled.normalize("[현장결제] 포인트가 사라졌어요")

        assertNotEquals(
            "Body without an amount token must NOT collapse with the 적립 bundle",
            withAmount,
            noAmount,
        )
    }

    @Test
    fun naver_pay_point_pickup_collapses_distinctly_from_payment_body() {
        // Same `[현장결제]` title + a number, but the verb stem differs (적립 vs 결제).
        // Normalizer must preserve verb-stem distinctions because that is the
        // signal users rely on to tell "포인트뽑기" apart from "결제 완료".
        val pickup = enabled.normalize("[현장결제] 8원이 적립되었어요")
        val payment = enabled.normalize("[현장결제] 5,000원이 결제되었습니다")

        assertNotEquals(
            "Different verb stems (적립 vs 결제) must NOT collapse together",
            pickup,
            payment,
        )
    }

    @Test
    fun time_of_day_tokens_collapse_to_same_signature() {
        val midnight = enabled.normalize("23:47에 알림")
        val morning = enabled.normalize("08:01에 알림")

        assertEquals("Different times-of-day must collapse to <time>", midnight, morning)
    }

    @Test
    fun bare_day_marker_does_not_collapse_with_time_of_day() {
        // `23일에 알림` lacks a colon so the time-of-day pattern must NOT match.
        // It will collapse via the bare-digit step instead, but the resulting
        // shape ("<num>일에 알림") differs from the time shape ("<time>에 알림"),
        // so the two must remain distinct signatures.
        val timeOfDay = enabled.normalize("23:47에 알림")
        val dayMarker = enabled.normalize("23일에 알림")

        assertNotEquals(
            "Bare day marker (no colon) must not match the time-of-day token",
            timeOfDay,
            dayMarker,
        )
    }

    @Test
    fun toggle_off_returns_input_unchanged_so_5_amounts_yield_5_distinct_signatures() {
        val bodies = listOf(
            "[현장결제] 8원이 적립되었어요",
            "[현장결제] 12원이 적립되었어요",
            "[현장결제] 16원이 적립되었어요",
            "[현장결제] 28원이 적립되었어요",
            "[현장결제] 1,234원이 적립되었어요",
        )
        val signatures = bodies.map { disabled.normalize(it) }

        assertEquals(
            "Toggle OFF must produce one distinct signature per amount fixture",
            5,
            signatures.toSet().size,
        )
        // Input is also returned verbatim — no accidental partial normalization.
        assertEquals(bodies, signatures)
    }

    @Test
    fun usd_amount_variants_collapse_acknowledged_over_bundling() {
        // Acknowledged trade-off (Risks section in plan): turning the toggle ON
        // collapses meaningfully-different USD amounts into the same signature.
        // The user opts into this when they enable the toggle.
        val small = enabled.normalize("\$10 paid")
        val large = enabled.normalize("\$10,000 paid")

        assertEquals(
            "USD amounts collapse together when the toggle is on (over-bundling by design)",
            small,
            large,
        )
    }
}
