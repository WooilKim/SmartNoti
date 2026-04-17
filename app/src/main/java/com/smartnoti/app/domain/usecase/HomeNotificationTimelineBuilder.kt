package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import kotlin.math.max

class HomeNotificationTimelineBuilder {
    fun build(
        notifications: List<NotificationUiModel>,
        nowMillis: Long = System.currentTimeMillis(),
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        bucketSizeMillis: Long = DEFAULT_BUCKET_SIZE_MILLIS,
    ): HomeNotificationTimeline {
        val windowStart = nowMillis - windowMillis
        val bucketCount = max(1, (windowMillis / bucketSizeMillis).toInt())
        val buckets = MutableList(bucketCount) { index ->
            HomeTimelineBucket(
                label = bucketLabel(bucketCount - index - 1),
                filteredCount = 0,
                priorityCount = 0,
                isPeakFilteredBucket = false,
            )
        }

        notifications.forEach { notification ->
            val postedAtMillis = notification.id.substringAfterLast(':').toLongOrNull() ?: return@forEach
            if (postedAtMillis < windowStart || postedAtMillis > nowMillis) return@forEach

            val bucketIndex = ((postedAtMillis - windowStart) / bucketSizeMillis)
                .toInt()
                .coerceIn(0, bucketCount - 1)
            val current = buckets[bucketIndex]
            buckets[bucketIndex] = current.copy(
                filteredCount = current.filteredCount + if (notification.status.isFiltered()) 1 else 0,
                priorityCount = current.priorityCount + if (notification.status == NotificationStatusUi.PRIORITY) 1 else 0,
            )
        }

        val nonEmptyBuckets = buckets.filter { bucket ->
            bucket.filteredCount > 0 || bucket.priorityCount > 0
        }
        if (nonEmptyBuckets.isEmpty()) {
            return HomeNotificationTimeline(totalFilteredCount = 0, buckets = emptyList())
        }

        val peakFilteredCount = nonEmptyBuckets.maxOf(HomeTimelineBucket::filteredCount)
        val normalizedBuckets = nonEmptyBuckets.map { bucket ->
            bucket.copy(isPeakFilteredBucket = peakFilteredCount > 0 && bucket.filteredCount == peakFilteredCount)
        }

        return HomeNotificationTimeline(
            totalFilteredCount = normalizedBuckets.sumOf(HomeTimelineBucket::filteredCount),
            buckets = normalizedBuckets,
        )
    }

    private fun bucketLabel(offsetFromLatest: Int): String = when (offsetFromLatest) {
        0 -> "방금 전"
        else -> "${offsetFromLatest}시간 전"
    }

    private fun NotificationStatusUi.isFiltered(): Boolean {
        return this == NotificationStatusUi.DIGEST || this == NotificationStatusUi.SILENT
    }

    private companion object {
        const val DEFAULT_WINDOW_MILLIS = 3 * 60 * 60 * 1000L
        const val DEFAULT_BUCKET_SIZE_MILLIS = 60 * 60 * 1000L
    }
}

data class HomeNotificationTimeline(
    val totalFilteredCount: Int,
    val buckets: List<HomeTimelineBucket>,
)

data class HomeTimelineBucket(
    val label: String,
    val filteredCount: Int,
    val priorityCount: Int,
    val isPeakFilteredBucket: Boolean,
)
