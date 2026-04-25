package com.smartnoti.app.ui.screens.categories.components

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the chip copy contract introduced by plan
 * `docs/plans/2026-04-24-categories-condition-chips.md` Task 1.
 *
 * The formatter is the i18n entry point: copy lives here so the three
 * Categories surfaces (list / detail / editor) share one source of truth.
 *
 * Copy contract (Korean):
 * - Prefix: `조건:` (or `조건 없음` defensively when no rules)
 * - Connector between tokens: ` 또는 `
 * - Arrow before action: ` → `
 * - Token formats: `보낸이=<v>`, `앱=<v>`, `키워드=<v>`, `시간=<v>`, `반복묶음`
 * - Action labels: PRIORITY=`즉시 전달`, DIGEST=`모아서 알림`,
 *   SILENT=`조용히`, IGNORE=`무시`.
 *
 * Overflow contract: when [maxInline] is exceeded, the overflow label is
 * `외 N개` (N = remaining tokens). Editor mode passes `Int.MAX_VALUE` to
 * disable truncation. APP type renders the raw matchValue in v1 — pretty
 * label resolution is deferred per the plan's open question.
 */
class CategoryConditionChipFormatterTest {

    @Test
    fun `empty rule list yields defensive 조건 없음 prefix`() {
        val result = CategoryConditionChipFormatter.format(
            rules = emptyList(),
            action = CategoryAction.DIGEST,
            maxInline = 2,
        )
        assertEquals("조건 없음", result.prefix)
        assertEquals(emptyList<String>(), result.tokens)
        assertNull(result.overflowLabel)
        assertEquals("모아서 알림", result.actionLabel)
        assertEquals("조건 없음 → 모아서 알림", result.toPlainString())
    }

    @Test
    fun `single keyword rule with PRIORITY action`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.KEYWORD, "세일")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
        )
        assertEquals("조건:", result.prefix)
        assertEquals(listOf("키워드=세일"), result.tokens)
        assertNull(result.overflowLabel)
        assertEquals("즉시 전달", result.actionLabel)
        assertEquals("조건: 키워드=세일 → 즉시 전달", result.toPlainString())
    }

    @Test
    fun `two rules render with 또는 connector`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(
                rule("r1", RuleTypeUi.KEYWORD, "세일"),
                rule("r2", RuleTypeUi.PERSON, "홍길동"),
            ),
            action = CategoryAction.DIGEST,
            maxInline = 2,
        )
        assertEquals(listOf("키워드=세일", "보낸이=홍길동"), result.tokens)
        assertNull(result.overflowLabel)
        assertEquals(
            "조건: 키워드=세일 또는 보낸이=홍길동 → 모아서 알림",
            result.toPlainString(),
        )
    }

    @Test
    fun `three rules with maxInline=1 collapse with 외 N개`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(
                rule("r1", RuleTypeUi.KEYWORD, "세일"),
                rule("r2", RuleTypeUi.PERSON, "홍길동"),
                rule("r3", RuleTypeUi.APP, "com.kakao.talk"),
            ),
            action = CategoryAction.SILENT,
            maxInline = 1,
        )
        assertEquals(listOf("키워드=세일"), result.tokens)
        assertEquals("외 2개", result.overflowLabel)
        assertEquals("조용히", result.actionLabel)
        assertEquals("조건: 키워드=세일 외 2개 → 조용히", result.toPlainString())
    }

    @Test
    fun `editor mode keeps every token expanded`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(
                rule("r1", RuleTypeUi.KEYWORD, "세일"),
                rule("r2", RuleTypeUi.PERSON, "홍길동"),
                rule("r3", RuleTypeUi.APP, "com.kakao.talk"),
            ),
            action = CategoryAction.IGNORE,
            maxInline = Int.MAX_VALUE,
        )
        assertEquals(
            listOf("키워드=세일", "보낸이=홍길동", "앱=com.kakao.talk"),
            result.tokens,
        )
        assertNull(result.overflowLabel)
        assertEquals("무시", result.actionLabel)
        assertEquals(
            "조건: 키워드=세일 또는 보낸이=홍길동 또는 앱=com.kakao.talk → 무시",
            result.toPlainString(),
        )
    }

    @Test
    fun `SCHEDULE rule renders with 시간 label`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.SCHEDULE, "22:00-07:00")),
            action = CategoryAction.SILENT,
            maxInline = 2,
        )
        assertEquals(listOf("시간=22:00-07:00"), result.tokens)
        assertEquals("조건: 시간=22:00-07:00 → 조용히", result.toPlainString())
    }

    @Test
    fun `REPEAT_BUNDLE rule omits matchValue and uses bare label`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.REPEAT_BUNDLE, "ignored")),
            action = CategoryAction.DIGEST,
            maxInline = 2,
        )
        assertEquals(listOf("반복묶음"), result.tokens)
        assertEquals("조건: 반복묶음 → 모아서 알림", result.toPlainString())
    }

    @Test
    fun `APP rule renders raw matchValue per v1 deferred lookup`() {
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "com.kakao.talk")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
        )
        assertEquals(listOf("앱=com.kakao.talk"), result.tokens)
    }

    // --- Plan `2026-04-25-category-chip-app-label-lookup.md` Task 1 ---------
    // APP rule lookup contract: when `appLabelLookup` resolves the
    // packageName to a non-blank label, the chip uses the label; otherwise
    // (null / blank / non-APP token) the existing raw-matchValue behaviour
    // wins. lookup is plumbed only — formatter stays Compose-free.

    @Test
    fun `APP rule uses appLabelLookup result when label is non-blank`() {
        val lookup = AppLabelLookup { pkg -> if (pkg == "com.kakao.talk") "카카오톡" else null }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "com.kakao.talk")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
            appLabelLookup = lookup,
        )
        assertEquals(listOf("앱=카카오톡"), result.tokens)
    }

    @Test
    fun `APP rule falls back to raw matchValue when lookup returns null`() {
        val lookup = AppLabelLookup { _ -> null }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "com.kakao.talk")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
            appLabelLookup = lookup,
        )
        assertEquals(listOf("앱=com.kakao.talk"), result.tokens)
    }

    @Test
    fun `APP rule falls back to raw matchValue when lookup returns blank`() {
        val lookup = AppLabelLookup { _ -> "   " }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "com.kakao.talk")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
            appLabelLookup = lookup,
        )
        assertEquals(listOf("앱=com.kakao.talk"), result.tokens)
    }

    @Test
    fun `APP rule with blank matchValue keeps bare 앱 label without invoking lookup`() {
        var invoked = false
        val lookup = AppLabelLookup { _ ->
            invoked = true
            "should-not-appear"
        }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "   ")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
            appLabelLookup = lookup,
        )
        assertEquals(listOf("앱"), result.tokens)
        assertEquals(false, invoked)
    }

    @Test
    fun `PERSON rule ignores appLabelLookup mapping`() {
        val lookup = AppLabelLookup { _ -> "카카오톡" }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.PERSON, "엄마")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
            appLabelLookup = lookup,
        )
        assertEquals(listOf("보낸이=엄마"), result.tokens)
    }

    @Test
    fun `KEYWORD SCHEDULE REPEAT_BUNDLE rules ignore appLabelLookup`() {
        val lookup = AppLabelLookup { _ -> "안녕" }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(
                rule("r1", RuleTypeUi.KEYWORD, "세일"),
                rule("r2", RuleTypeUi.SCHEDULE, "22:00-07:00"),
                rule("r3", RuleTypeUi.REPEAT_BUNDLE, "ignored"),
            ),
            action = CategoryAction.DIGEST,
            maxInline = Int.MAX_VALUE,
            appLabelLookup = lookup,
        )
        assertEquals(
            listOf("키워드=세일", "시간=22:00-07:00", "반복묶음"),
            result.tokens,
        )
    }

    @Test
    fun `mixed rules apply lookup only to APP token`() {
        val lookup = AppLabelLookup { pkg -> if (pkg == "com.kakao.talk") "카카오톡" else null }
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(
                rule("r1", RuleTypeUi.APP, "com.kakao.talk"),
                rule("r2", RuleTypeUi.PERSON, "엄마"),
                rule("r3", RuleTypeUi.KEYWORD, "세일"),
            ),
            action = CategoryAction.DIGEST,
            maxInline = Int.MAX_VALUE,
            appLabelLookup = lookup,
        )
        assertEquals(
            listOf("앱=카카오톡", "보낸이=엄마", "키워드=세일"),
            result.tokens,
        )
    }

    @Test
    fun `default formatter call without lookup keeps raw APP packageName`() {
        // Backward-compat: existing call sites that don't pass a lookup
        // still see the raw matchValue, identical to v1 behaviour.
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.APP, "com.kakao.talk")),
            action = CategoryAction.PRIORITY,
            maxInline = 2,
        )
        assertEquals(listOf("앱=com.kakao.talk"), result.tokens)
    }

    @Test
    fun `KEYWORD rule with blank matchValue falls back to bare 키워드 token`() {
        // Defensive: validators normally block empty matchValue, but the
        // formatter must not produce trailing `=` if it ever receives one.
        val result = CategoryConditionChipFormatter.format(
            rules = listOf(rule("r1", RuleTypeUi.KEYWORD, "   ")),
            action = CategoryAction.DIGEST,
            maxInline = 2,
        )
        assertEquals(listOf("키워드"), result.tokens)
    }

    private fun rule(id: String, type: RuleTypeUi, matchValue: String): RuleUiModel =
        RuleUiModel(
            id = id,
            title = "",
            subtitle = "",
            type = type,
            enabled = true,
            matchValue = matchValue,
        )
}
