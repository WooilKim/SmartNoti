package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveDuplicateCountTrackerTest {

    private val tracker = LiveDuplicateCountTracker()

    @Test
    fun rapid_identical_notifications_increment_count_when_entry_keys_are_distinct() {
        val first = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "배달 상태 업데이트 라이더 위치가 갱신됐어요",
            sourceEntryKey = "0|com.smartnoti.testnotifier|100|repeat-1|10193",
            postedAtMillis = 1_700_000_000_000L,
            windowStartMillis = 1_700_000_000_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )
        val second = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "배달 상태 업데이트 라이더 위치가 갱신됐어요",
            sourceEntryKey = "0|com.smartnoti.testnotifier|101|repeat-2|10193",
            postedAtMillis = 1_700_000_000_500L,
            windowStartMillis = 1_700_000_000_500L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )
        val third = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "배달 상태 업데이트 라이더 위치가 갱신됐어요",
            sourceEntryKey = "0|com.smartnoti.testnotifier|102|repeat-3|10193",
            postedAtMillis = 1_700_000_001_000L,
            windowStartMillis = 1_700_000_001_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(3, third)
    }

    @Test
    fun repeated_callback_for_same_source_entry_key_does_not_increment_count() {
        val first = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "배달 상태 업데이트 라이더 위치가 갱신됐어요",
            sourceEntryKey = "0|com.smartnoti.testnotifier|100|repeat-1|10193",
            postedAtMillis = 1_700_000_000_000L,
            windowStartMillis = 1_700_000_000_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )
        val repeated = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "배달 상태 업데이트 라이더 위치가 갱신됐어요",
            sourceEntryKey = "0|com.smartnoti.testnotifier|100|repeat-1|10193",
            postedAtMillis = 1_700_000_000_500L,
            windowStartMillis = 1_700_000_000_500L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )

        assertEquals(1, first)
        assertEquals(1, repeated)
    }

    @Test
    fun persisted_duplicate_count_still_applies_when_tracker_is_empty() {
        val count = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "보안 인증 안내 인증번호 482913",
            sourceEntryKey = "0|com.smartnoti.testnotifier|200|important-1|10193",
            postedAtMillis = 1_700_000_000_000L,
            windowStartMillis = 1_700_000_000_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 2,
        )

        assertEquals(3, count)
    }

    @Test
    fun notifications_outside_the_window_are_pruned_before_counting() {
        tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "오늘만 특가 안내 쿠폰 도착",
            sourceEntryKey = "0|com.smartnoti.testnotifier|0|promo-1|10193",
            postedAtMillis = 1_700_000_000_000L,
            windowStartMillis = 1_700_000_000_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )

        val count = tracker.recordAndCount(
            packageName = "com.smartnoti.testnotifier",
            contentSignature = "오늘만 특가 안내 쿠폰 도착",
            sourceEntryKey = "0|com.smartnoti.testnotifier|0|promo-2|10194",
            postedAtMillis = 1_700_000_700_000L,
            windowStartMillis = 1_700_000_700_000L - 10 * 60 * 1000L,
            persistedDuplicateCount = 0,
        )

        assertEquals(1, count)
    }
}
