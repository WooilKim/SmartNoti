package com.smartnoti.app.notification

/**
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 2.
 *
 * Pure-function normalizer applied AFTER
 * [com.smartnoti.app.domain.usecase.DuplicateNotificationPolicy.contentSignature]
 * has lower-cased + whitespace-collapsed the input. When [enabled] is `true`
 * the pipeline collapses three classes of "amount-only-差" tokens that today
 * cause `repeat_bundle:N` and the base duplicateDigestThreshold heuristic to
 * miss notifications such as 네이버페이 `[현장결제]` 포인트뽑기 (issue #488 Bug 2):
 *
 *   1. Time-of-day  `\b\d{1,2}:\d{2}\b`            → `<time>`
 *   2. Currency-suffixed digits (8원, 1,234원, $10) → `<num>`
 *   3. Bare digit runs                              → `<num>`
 *
 * When [enabled] is `false` the input is returned unchanged — this is the
 * default-OFF safety guarantee documented in the plan: collapsing meaningful
 * amount differences (e.g. `100원 결제` vs `100,000원 결제`) is a per-user
 * judgment call so existing installs see no behavior change until they opt in
 * via Settings → 중복 알림 묶기.
 *
 * Invariants the test matrix in `ContentSignatureNormalizerTest` pins:
 *   - 5 네이버페이 포인트뽑기 fixtures (8/12/16/28/1,234원) collapse to one shape.
 *   - Different verb stems (적립 vs 결제) stay distinct because only the digit
 *     tokens collapse — the surrounding tokens still carry the verb.
 *   - Time-of-day collapses (`23:47에 알림` ↔ `08:01에 알림`) but a bare day
 *     marker (`23일에 알림`) does NOT collapse with them because the `:` is
 *     absent in the day case so the time pattern misses it.
 *   - Toggle OFF round-trips the input verbatim — no accidental partial
 *     normalization (regression guard against an always-on bug).
 *
 * Implementation notes:
 *   - Three [Regex] instances are class fields so we don't re-compile per call.
 *     The instance is constructed per
 *     [com.smartnoti.app.notification.NotificationDuplicateContextBuilder.build]
 *     call today; if profiling shows allocation pressure we can hoist + inject.
 *   - All three patterns are linear-time on input length (no nested
 *     quantifiers), so ReDoS risk is bounded for the ~1KB title+body inputs
 *     this code sees in practice.
 *   - Currency-suffix step runs BEFORE the bare-digit step. The replacement
 *     intentionally drops the suffix (replaces `8원` → `<num>` rather than
 *     `<num>원`) to maximize bundling per fixture-driven assertions in the
 *     test matrix.
 *   - The class has no language awareness — the toggle is a global on/off,
 *     not a Korean-only feature.
 */
class ContentSignatureNormalizer(private val enabled: Boolean) {

    private val timeOfDayRegex = Regex("\\b\\d{1,2}:\\d{2}\\b")

    // Match priority (left → right): comma-thousands with optional unit,
    // bare-digit with unit, `$`-prefixed amount with optional comma-thousands.
    // The outer alternation is order-sensitive — `\d+(?:원|krw|usd)?` would
    // happily eat `1,234` as `1` if it ran first, leaving `,234` behind.
    private val currencySuffixedDigitsRegex = Regex(
        "\\d{1,3}(?:,\\d{3})+(?:원|krw|usd)?|\\d+(?:원|krw|usd)?|\\\$\\d+(?:,\\d{3})*"
    )

    private val bareDigitRunRegex = Regex("\\b\\d+\\b")

    fun normalize(signature: String): String {
        if (!enabled) return signature
        var out = signature
        out = timeOfDayRegex.replace(out, "<time>")
        out = currencySuffixedDigitsRegex.replace(out, "<num>")
        out = bareDigitRunRegex.replace(out, "<num>")
        return out
    }
}
