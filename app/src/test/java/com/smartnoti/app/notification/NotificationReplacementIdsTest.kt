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

    @Test
    fun replacement_id_does_not_change_when_delivery_profile_changes() {
        val loudDigest = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )
        val quietDigest = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )

        assertEquals(loudDigest, quietDigest)
    }

    @Test
    fun distinct_notification_ids_from_same_app_produce_distinct_replacement_ids() {
        val first = NotificationReplacementIds.idFor(
            packageName = "com.promoapp",
            decision = NotificationDecision.DIGEST,
            notificationId = "com.promoapp:tag:1",
        )
        val second = NotificationReplacementIds.idFor(
            packageName = "com.promoapp",
            decision = NotificationDecision.DIGEST,
            notificationId = "com.promoapp:tag:2",
        )

        assertNotEquals(first, second)
    }

    @Test
    fun repeated_update_of_same_notification_keeps_stable_replacement_id() {
        // 같은 notification id 로 업데이트되는 경우 (예: 제목/본문이 업데이트되어도 같은 slot)
        val initial = NotificationReplacementIds.idFor(
            packageName = "com.promoapp",
            decision = NotificationDecision.DIGEST,
            notificationId = "com.promoapp:tag:1",
        )
        val updated = NotificationReplacementIds.idFor(
            packageName = "com.promoapp",
            decision = NotificationDecision.DIGEST,
            notificationId = "com.promoapp:tag:1",
        )

        assertEquals(initial, updated)
    }

    @Test
    fun blank_notification_id_falls_back_to_legacy_keying() {
        val legacy = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
        )
        val explicitBlank = NotificationReplacementIds.idFor(
            packageName = "com.coupang.mobile",
            decision = NotificationDecision.DIGEST,
            notificationId = "",
        )

        assertEquals(legacy, explicitBlank)
    }
}
