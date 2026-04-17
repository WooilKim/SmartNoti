package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTimelineBarChartModelBuilderTest {

    private val builder = HomeTimelineBarChartModelBuilder()

    @Test
    fun scales_bar_fill_against_peak_filtered_bucket() {
        val model = builder.build(
            HomeNotificationTimeline(
                range = HomeTimelineRange.RECENT_3_HOURS,
                totalFilteredCount = 5,
                buckets = listOf(
                    HomeTimelineBucket(label = "2시간 전", filteredCount = 1, priorityCount = 0, isPeakFilteredBucket = false),
                    HomeTimelineBucket(label = "1시간 전", filteredCount = 3, priorityCount = 1, isPeakFilteredBucket = true),
                    HomeTimelineBucket(label = "방금 전", filteredCount = 0, priorityCount = 2, isPeakFilteredBucket = false),
                ),
            )
        )

        assertEquals(listOf(0.33f, 1.0f, 0.0f), model.bars.map { bar -> bar.fillFraction })
        assertEquals(listOf(false, true, false), model.bars.map { bar -> bar.isPeak })
    }

    @Test
    fun returns_zero_fill_when_all_filtered_counts_are_zero() {
        val model = builder.build(
            HomeNotificationTimeline(
                range = HomeTimelineRange.RECENT_24_HOURS,
                totalFilteredCount = 0,
                buckets = listOf(
                    HomeTimelineBucket(label = "18시간 전", filteredCount = 0, priorityCount = 1, isPeakFilteredBucket = false),
                ),
            )
        )

        assertEquals(listOf(0.0f), model.bars.map { bar -> bar.fillFraction })
    }
}
