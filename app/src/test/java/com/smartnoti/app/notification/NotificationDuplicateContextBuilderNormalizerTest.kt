package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.usecase.LiveDuplicateCountTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 1 — RED end-to-end through the listener seam.
 *
 * Pins the contract that Task 3's wiring lands later: when
 * [SmartNotiSettings.normalizeNumericTokensInSignature] is true, calling
 * [NotificationDuplicateContextBuilder.build] five times with the same title
 * and five amount-mutating bodies (the canonical 네이버페이 포인트뽑기 fixtures
 * from issue #488) yields `duplicateCount >= 5` because all five collapse into
 * the same `contentSignature`. With the toggle false, each call is a unique
 * signature so `duplicateCount == 1` per call.
 *
 * The persistent-suffix is NOT applied here (`isPersistent = false`) so the
 * normalized signature is the only thing the tracker keys on.
 */
class NotificationDuplicateContextBuilderNormalizerTest {

    private val pointPickupBodies = listOf(
        "8원이 적립되었어요",
        "12원이 적립되었어요",
        "16원이 적립되었어요",
        "28원이 적립되었어요",
        "1,234원이 적립되었어요",
    )

    @Test
    fun five_naver_pay_pickup_fixtures_collapse_when_normalizer_setting_on() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val settingsOn = SmartNotiSettings(
            duplicateWindowMinutes = 10,
            normalizeNumericTokensInSignature = true,
        )

        val signatures = mutableSetOf<String>()
        var lastDuplicateCount = 0
        pointPickupBodies.forEachIndexed { index, body ->
            val ctx = builder.build(
                packageName = "com.nhnent.payapp",
                notificationId = 1_000 + index,
                sourceEntryKey = "k-$index",
                postTimeMillis = 1_700_000_000_000L + index,
                title = "[현장결제]",
                body = body,
                settings = settingsOn,
                isPersistent = false,
            )
            signatures += ctx.contentSignature
            lastDuplicateCount = ctx.duplicateCount
        }

        assertEquals(
            "All 5 포인트뽑기 fixtures must collapse to one normalized signature",
            1,
            signatures.size,
        )
        assertTrue(
            "Tracker must report duplicateCount >= 5 (got $lastDuplicateCount) when all 5 share a signature",
            lastDuplicateCount >= 5,
        )
    }

    @Test
    fun five_naver_pay_pickup_fixtures_stay_distinct_when_normalizer_setting_off() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val settingsOff = SmartNotiSettings(
            duplicateWindowMinutes = 10,
            normalizeNumericTokensInSignature = false,
        )

        val signatures = mutableSetOf<String>()
        val perCallCounts = mutableListOf<Int>()
        pointPickupBodies.forEachIndexed { index, body ->
            val ctx = builder.build(
                packageName = "com.nhnent.payapp",
                notificationId = 2_000 + index,
                sourceEntryKey = "k-off-$index",
                postTimeMillis = 1_700_000_000_000L + index,
                title = "[현장결제]",
                body = body,
                settings = settingsOff,
                isPersistent = false,
            )
            signatures += ctx.contentSignature
            perCallCounts += ctx.duplicateCount
        }

        assertEquals(
            "Toggle OFF must keep historical per-amount uniqueness",
            5,
            signatures.size,
        )
        assertTrue(
            "Each distinct-signature call must report duplicateCount == 1, got $perCallCounts",
            perCallCounts.all { it == 1 },
        )
    }
}
