package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateNotificationPolicyTest {

    private val policy = DuplicateNotificationPolicy(windowMillis = 10 * 60 * 1000L)

    @Test
    fun similar_content_is_normalized_into_same_signature() {
        val first = policy.contentSignature(
            title = "속보   알림",
            body = "중요 뉴스가   도착했어요"
        )
        val second = policy.contentSignature(
            title = "속보 알림",
            body = "중요   뉴스가 도착했어요   "
        )

        assertEquals(first, second)
    }

    @Test
    fun window_start_is_calculated_from_post_time() {
        val postedAtMillis = 1_700_000_000_000L

        assertEquals(postedAtMillis - 10 * 60 * 1000L, policy.windowStart(postedAtMillis))
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
     *
     * The policy's `windowMillis` parameter is now caller-injected — there is
     * no companion `DEFAULT_WINDOW_MILLIS` to lean on. This pins that an
     * explicit 5-minute window subtracts exactly 5 minutes from the posted
     * timestamp, regardless of any historical default.
     */
    @Test
    fun window_start_reflects_caller_injected_window_millis() {
        val fiveMinutePolicy = DuplicateNotificationPolicy(windowMillis = 5 * 60 * 1000L)
        val postedAtMillis = 1_700_000_000_000L

        assertEquals(postedAtMillis - 5 * 60 * 1000L, fiveMinutePolicy.windowStart(postedAtMillis))
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
     *
     * Same shape with a 30-minute window — guards that the policy actually
     * multiplies through the configured value rather than collapsing to a
     * hidden default.
     */
    @Test
    fun window_start_reflects_thirty_minute_window() {
        val thirtyMinutePolicy = DuplicateNotificationPolicy(windowMillis = 30 * 60 * 1000L)
        val postedAtMillis = 1_700_000_000_000L

        assertEquals(postedAtMillis - 30 * 60 * 1000L, thirtyMinutePolicy.windowStart(postedAtMillis))
    }
}
