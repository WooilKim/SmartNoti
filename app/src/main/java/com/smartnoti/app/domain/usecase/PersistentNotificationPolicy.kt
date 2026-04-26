package com.smartnoti.app.domain.usecase

class PersistentNotificationPolicy {
    fun shouldTreatAsPersistent(
        isOngoing: Boolean,
        isClearable: Boolean,
    ): Boolean {
        return isOngoing || !isClearable
    }

    fun shouldBypassPersistentHiding(
        packageName: String,
        title: String,
        body: String,
        protectCriticalPersistentNotifications: Boolean,
    ): Boolean {
        if (!protectCriticalPersistentNotifications) return false

        val normalizedText = listOf(packageName, title, body)
            .joinToString(" ")
            .lowercase()

        return BYPASS_KEYWORDS.any { keyword -> containsAsWord(normalizedText, keyword) }
    }

    /**
     * Returns true when [keyword] appears in [text] flanked by characters that are not part of
     * the same word. This avoids substring false positives such as "전화" matching inside
     * "전화번호" or "녹화" matching inside "녹화본".
     *
     * Multi-word keywords (e.g. "camera in use", "마이크 사용 중") are matched as a single
     * substring; only the outer edges are boundary-checked. Inter-token boundaries are not
     * inspected — see plan Risks for the documented limitation.
     */
    private fun containsAsWord(text: String, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        var fromIndex = 0
        while (true) {
            val idx = text.indexOf(keyword, fromIndex)
            if (idx < 0) return false
            val prev = text.getOrNull(idx - 1)
            val next = text.getOrNull(idx + keyword.length)
            if (isWordBoundary(prev) && isWordBoundary(next)) return true
            fromIndex = idx + 1
        }
    }

    /**
     * A character qualifies as a word boundary when it is null (string edge), not a Hangul
     * syllable / Jamo, and not an alphanumeric character. Whitespace, punctuation, and symbols
     * therefore count as boundaries.
     */
    private fun isWordBoundary(c: Char?): Boolean {
        if (c == null) return true
        if (isHangul(c)) return false
        if (c.isLetterOrDigit()) return false
        return true
    }

    private fun isHangul(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c) ?: return false
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
            block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A ||
            block == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B
    }

    companion object {
        private val BYPASS_KEYWORDS = setOf(
            "통화",
            "전화",
            "call",
            "dialer",
            "길안내",
            "내비",
            "navigation",
            "maps",
            "녹화",
            "recording",
            "screen record",
            "마이크 사용 중",
            "camera in use",
            "camera access",
            "microphone in use",
        )
    }
}
