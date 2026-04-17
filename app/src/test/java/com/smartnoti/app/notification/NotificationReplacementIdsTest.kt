package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationReplacementIdsTest {

    @Test
    fun same_package_and_decision_produce_stable_id() {
        val first = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )
        val second = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )

        assertEquals(first, second)
    }

    @Test
    fun different_decisions_produce_different_ids() {
        val digestId = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )
        val silentId = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.SILENT,
        )

        assertNotEquals(digestId, silentId)
    }
}
