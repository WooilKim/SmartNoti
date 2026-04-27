package com.smartnoti.app.domain.usecase

import com.smartnoti.app.notification.ContentSignatureNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 1 — RED integration between [DuplicateNotificationPolicy] and the
 * not-yet-existing [ContentSignatureNormalizer].
 *
 * Pins the contract that wiring (Task 3) lands later: the policy emits its
 * lowercase + whitespace-collapsed signature first, the normalizer applies
 * the digit/currency/time pipeline second. With the toggle ON, the five
 * 네이버페이 포인트뽑기 fixtures must produce one identical signature.
 *
 * Calling code is the same shape the production wiring will use:
 *   `normalizer.normalize(policy.contentSignature(title, body))`
 *
 * No persistent suffix is appended here — that piece lives in
 * [com.smartnoti.app.notification.NotificationDuplicateContextBuilder] and is
 * exercised by `NotificationDuplicateContextBuilderNormalizerTest`.
 */
class DuplicateNotificationPolicyNormalizerIntegrationTest {

    private val policy = DuplicateNotificationPolicy(windowMillis = 10 * 60 * 1000L)
    private val normalizerOn = ContentSignatureNormalizer(enabled = true)
    private val normalizerOff = ContentSignatureNormalizer(enabled = false)

    @Test
    fun five_naver_pay_pickup_fixtures_share_one_signature_when_normalizer_on() {
        val fixtures = listOf("8", "12", "16", "28", "1,234")
            .map { amount ->
                normalizerOn.normalize(
                    policy.contentSignature(
                        title = "[현장결제]",
                        body = "${amount}원이 적립되었어요",
                    )
                )
            }

        assertEquals(
            "All five 포인트뽑기 fixtures must collapse into one signature",
            1,
            fixtures.toSet().size,
        )
    }

    @Test
    fun toggle_off_keeps_each_amount_distinct() {
        val fixtures = listOf("8", "12", "16", "28", "1,234")
            .map { amount ->
                normalizerOff.normalize(
                    policy.contentSignature(
                        title = "[현장결제]",
                        body = "${amount}원이 적립되었어요",
                    )
                )
            }

        assertEquals(
            "Toggle OFF must preserve historic per-amount uniqueness",
            5,
            fixtures.toSet().size,
        )
    }

    @Test
    fun normalized_pickup_signature_differs_from_normalized_payment_signature() {
        val pickup = normalizerOn.normalize(
            policy.contentSignature(
                title = "[현장결제]",
                body = "8원이 적립되었어요",
            )
        )
        val payment = normalizerOn.normalize(
            policy.contentSignature(
                title = "[현장결제]",
                body = "5,000원이 결제되었습니다",
            )
        )

        assertNotEquals(
            "적립 vs 결제 verb stems must remain distinct after normalization",
            pickup,
            payment,
        )
    }
}
