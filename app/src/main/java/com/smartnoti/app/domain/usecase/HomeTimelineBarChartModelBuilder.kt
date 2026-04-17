package com.smartnoti.app.domain.usecase

import kotlin.math.roundToInt

class HomeTimelineBarChartModelBuilder {
    fun build(timeline: HomeNotificationTimeline): HomeTimelineBarChartModel {
        val peakFilteredCount = timeline.buckets.maxOfOrNull(HomeTimelineBucket::filteredCount) ?: 0
        return HomeTimelineBarChartModel(
            bars = timeline.buckets.map { bucket ->
                val fillFraction = if (peakFilteredCount == 0) {
                    0f
                } else {
                    ((bucket.filteredCount.toFloat() / peakFilteredCount.toFloat()) * 100)
                        .roundToInt() / 100f
                }
                HomeTimelineBar(
                    label = bucket.label,
                    fillFraction = fillFraction,
                    filteredCount = bucket.filteredCount,
                    priorityCount = bucket.priorityCount,
                    isPeak = bucket.isPeakFilteredBucket,
                )
            }
        )
    }
}

data class HomeTimelineBarChartModel(
    val bars: List<HomeTimelineBar>,
)

data class HomeTimelineBar(
    val label: String,
    val fillFraction: Float,
    val filteredCount: Int,
    val priorityCount: Int,
    val isPeak: Boolean,
)
