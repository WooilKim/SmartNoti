package com.smartnoti.app.domain.usecase

/**
 * Detects KCC-mandated Korean advertising prefixes at the head of a notification
 * body (or, as fallback, the title). When detected, [CategoryConflictResolver]
 * forces a non-PRIORITY Category to win over any IMPORTANT/PRIORITY Category
 * even when both KEYWORD rules match — closing the precedence regression
 * reported in issue #478 (3차 진단).
 *
 * Plan: `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 2 (Bug A fix).
 *
 * **Rationale.** South Korea's 정보통신망법 제50조의8 시행령 mandates that
 * promotional ads disclose themselves with a leading marker `(광고)` /
 * `[광고]` (Korean) or `(AD)` / `[AD]` (English-codepath equivalent). Apps that
 * must comply (AliExpress, NaverShopping, telco SMS gateways relaying card-issuer
 * promos, etc.) prepend the marker to the body. The marker is therefore an
 * unambiguous declaration that the notification is promotional — even when the
 * ad copy happens to mention an IMPORTANT keyword (배송 / 결제 / 대출).
 *
 * **Anchoring.** We only consider the first [HEAD_SCAN_LENGTH] characters of
 * the input string after trimming leading whitespace. This prevents false
 * positives from mid-body natural-language usages of the literal substring
 * "광고" (e.g. body=`"광고주 미팅이 곧 시작됩니다"`). Telco gateways prepend
 * `[Web발신]` and occasionally double the marker (`(광고) (광고)[하나카드] …`);
 * we accept those by allowing one optional `[Web발신]` token plus arbitrary
 * leading whitespace before the marker.
 *
 * **Title fallback.** Most apps put the marker in the body, but some pin it to
 * the title. [hasAdvertisingPrefix] checks body first, then title — both fields
 * are scanned with the same anchored regex.
 */
class KoreanAdvertisingPrefixDetector(
    private val patterns: List<Regex> = DEFAULT_PATTERNS,
) {

    /**
     * Returns true iff the start (after trim + optional `[Web발신]`) of either
     * [body] or [title] matches one of the configured advertising-prefix
     * patterns. Empty / blank inputs return false.
     */
    fun hasAdvertisingPrefix(body: String, title: String = ""): Boolean {
        if (matches(body)) return true
        return matches(title)
    }

    private fun matches(input: String): Boolean {
        if (input.isBlank()) return false
        val head = input.take(HEAD_SCAN_LENGTH)
        return patterns.any { it.containsMatchIn(head) }
    }

    companion object {
        /**
         * Cap how far into the input we scan. KCC markers always sit at the
         * very head; allowing more than ~32 chars would let mid-body natural
         * language slip through.
         */
        const val HEAD_SCAN_LENGTH: Int = 64

        /**
         * Default KCC marker set. Each regex anchors at start (`^`) of the
         * head window, accepts arbitrary leading whitespace, and tolerates an
         * optional telco gateway prefix (`[Web발신]`) before the marker.
         *
         * The four markers are `(광고)`, `[광고]`, `(AD)`, `[AD]`. The English
         * markers are case-insensitive.
         *
         * Future plan may surface this list as a user-editable setting; for
         * now it stays a code default.
         */
        val DEFAULT_PATTERNS: List<Regex> = listOf(
            // Optional `[Web발신]` (telco SMS gateway) + optional whitespace +
            // marker. The marker may also be doubled (e.g. `(광고) (광고)[하나카드]`)
            // — the first match still satisfies `containsMatchIn`.
            Regex("""^\s*(\[Web발신]\s*)?\(광고\)"""),
            Regex("""^\s*(\[Web발신]\s*)?\[광고]"""),
            Regex("""^\s*(\[Web발신]\s*)?\(AD\)""", RegexOption.IGNORE_CASE),
            Regex("""^\s*(\[Web발신]\s*)?\[AD]""", RegexOption.IGNORE_CASE),
        )
    }
}
