package com.smartnoti.app.domain.usecase

class DuplicateNotificationPolicy(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    fun contentSignature(title: String, body: String): String {
        return listOf(title, body)
            .joinToString(" ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun windowStart(postedAtMillis: Long): Long = postedAtMillis - windowMillis

    companion object {
        private const val DEFAULT_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}