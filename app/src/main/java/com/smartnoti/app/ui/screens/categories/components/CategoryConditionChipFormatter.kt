package com.smartnoti.app.ui.screens.categories.components

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure formatter introduced by plan
 * `docs/plans/2026-04-24-categories-condition-chips.md` Task 2.
 *
 * Builds the inline "조건 → 액션" chip copy shown on Category cards, the
 * Detail summary, and the Editor preview. Compose-free so the entire copy
 * contract is unit-testable in isolation (see
 * [CategoryConditionChipFormatterTest]).
 *
 * The output is deliberately structural ([CategoryConditionChipText]) rather
 * than a single string: the composable still wants to render each token as a
 * separate chip (different surfaces, hit areas, future semantics) while the
 * tests and content descriptions consume the joined plain-string form via
 * [CategoryConditionChipText.toPlainString].
 */
object CategoryConditionChipFormatter {

    /**
     * Format [rules] (already filtered to this Category's membership) plus
     * the Category's [action] into chip copy.
     *
     * @param maxInline Maximum number of rule tokens to render before
     *   collapsing the remainder into "외 N개". Card mode passes a small
     *   value (1 or 2); editor / detail pass [Int.MAX_VALUE] to keep every
     *   condition visible.
     */
    fun format(
        rules: List<RuleUiModel>,
        action: CategoryAction,
        maxInline: Int,
        appLabelLookup: AppLabelLookup = AppLabelLookup.Identity,
    ): CategoryConditionChipText {
        val allTokens = rules.map { tokenFor(it, appLabelLookup) }
        val (visibleTokens, overflow) = if (allTokens.size > maxInline && maxInline >= 0) {
            val visible = allTokens.take(maxInline)
            val rest = allTokens.size - visible.size
            visible to "외 ${rest}개"
        } else {
            allTokens to null
        }

        val prefix = if (allTokens.isEmpty()) PREFIX_EMPTY else PREFIX_DEFAULT

        return CategoryConditionChipText(
            prefix = prefix,
            tokens = visibleTokens,
            overflowLabel = overflow,
            actionLabel = CategoryActionLabels.chipLabel(action),
        )
    }

    private fun tokenFor(rule: RuleUiModel, appLabelLookup: AppLabelLookup): String {
        val value = rule.matchValue.trim()
        val labelKey = when (rule.type) {
            RuleTypeUi.PERSON -> "보낸이"
            RuleTypeUi.APP -> "앱"
            RuleTypeUi.KEYWORD -> "키워드"
            RuleTypeUi.SCHEDULE -> "시간"
            RuleTypeUi.REPEAT_BUNDLE -> return "반복묶음"
        }
        if (value.isEmpty()) return labelKey
        // Plan `2026-04-25-category-chip-app-label-lookup.md` Task 2: only
        // APP tokens consult the lookup; resolved label wins when non-blank,
        // otherwise we fall back to the raw matchValue so the chip never
        // shows an empty value.
        val display = if (rule.type == RuleTypeUi.APP) {
            appLabelLookup.labelFor(value)?.takeIf { it.isNotBlank() } ?: value
        } else {
            value
        }
        return "$labelKey=$display"
    }

    internal const val PREFIX_DEFAULT = "조건:"
    internal const val PREFIX_EMPTY = "조건 없음"
    internal const val CONNECTOR = " 또는 "
    internal const val ARROW = " → "
}

/**
 * Structured chip copy returned by [CategoryConditionChipFormatter].
 *
 * Holds the parts separately so the composable can chip each token while
 * still exposing a flat [toPlainString] form for tests and accessibility.
 */
data class CategoryConditionChipText(
    val prefix: String,
    val tokens: List<String>,
    val overflowLabel: String?,
    val actionLabel: String,
) {
    /**
     * Joined string form: `<prefix> <token> 또는 <token> [외 N개] → <action>`.
     * The empty-rules defensive form drops the connector entirely:
     * `조건 없음 → <action>`.
     */
    fun toPlainString(): String {
        val builder = StringBuilder(prefix)
        if (tokens.isNotEmpty()) {
            builder.append(' ')
            builder.append(tokens.joinToString(CategoryConditionChipFormatter.CONNECTOR))
            if (overflowLabel != null) {
                builder.append(' ')
                builder.append(overflowLabel)
            }
        }
        builder.append(CategoryConditionChipFormatter.ARROW)
        builder.append(actionLabel)
        return builder.toString()
    }
}

/**
 * Single source of truth for the action labels used inside chips. Kept as
 * its own object (per plan Task 2 step 4) so the formatter copy and the
 * editor / badge surfaces never drift in isolation. The neighbouring
 * `CategoryActionBadge` keeps its own slightly more verbose labels for
 * historical reasons; the chip variants are intentionally terser to fit
 * inline.
 */
object CategoryActionLabels {
    fun chipLabel(action: CategoryAction): String = when (action) {
        CategoryAction.PRIORITY -> "즉시 전달"
        CategoryAction.DIGEST -> "모아서 알림"
        CategoryAction.SILENT -> "조용히"
        CategoryAction.IGNORE -> "무시"
    }
}
