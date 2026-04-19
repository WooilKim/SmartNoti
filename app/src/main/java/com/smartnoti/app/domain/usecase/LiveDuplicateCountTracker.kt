package com.smartnoti.app.domain.usecase

class LiveDuplicateCountTracker {
    private val recentPostTimesByKey = mutableMapOf<String, MutableMap<String, Long>>()

    @Synchronized
    fun recordAndCount(
        packageName: String,
        contentSignature: String,
        sourceEntryKey: String?,
        postedAtMillis: Long,
        windowStartMillis: Long,
        persistedDuplicateCount: Int,
    ): Int {
        pruneExpiredEntries(windowStartMillis)

        val key = "$packageName|$contentSignature"
        val entryKey = sourceEntryKey?.takeIf { it.isNotBlank() } ?: "$postedAtMillis"
        val postTimesByEntryKey = recentPostTimesByKey.getOrPut(key) { mutableMapOf() }
        postTimesByEntryKey[entryKey] = maxOf(postTimesByEntryKey[entryKey] ?: Long.MIN_VALUE, postedAtMillis)

        return maxOf(persistedDuplicateCount + 1, postTimesByEntryKey.size)
    }

    private fun pruneExpiredEntries(windowStartMillis: Long) {
        val iterator = recentPostTimesByKey.iterator()
        while (iterator.hasNext()) {
            val postTimesByEntryKey = iterator.next().value
            postTimesByEntryKey.entries.removeAll { (_, postTime) -> postTime < windowStartMillis }
            if (postTimesByEntryKey.isEmpty()) {
                iterator.remove()
            }
        }
    }
}
