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
}
