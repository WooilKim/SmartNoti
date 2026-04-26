package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Line-oriented codec for the `smartnoti_rules` DataStore payload.
 *
 * Layout history (per row, `|`-separated, every cell URL-encoded):
 *
 *  - Pre-P1: 8 columns — `id | title | subtitle | type | action | enabled |
 *    matchValue | overrideOf`. Some early rows are 7 columns (no `overrideOf`).
 *  - Post-P1-Task-4 (plan `2026-04-22-categories-split-rules-actions`):
 *    7 columns — `id | title | subtitle | type | enabled | matchValue |
 *    overrideOf`. The `action` column at index 4 was dropped; the action
 *    moved onto the owning [com.smartnoti.app.domain.model.Category]. The
 *    pre-P1 action is recovered exactly once by [LegacyRuleActionReader]
 *    during the Rules → Categories migration before this codec rewrites
 *    storage.
 *  - Post-Task-2 (plan `2026-04-26-rule-explicit-draft-flag`): 8 columns —
 *    same as the 7-column layout plus a trailing boolean `draft` cell.
 *    Disambiguation against the pre-P1 8-column layout is by index 4:
 *    pre-P1 stored an `RuleActionUi.name` there ("ALWAYS_PRIORITY" /
 *    "DIGEST" / "SILENT" / "CONTEXTUAL" / "IGNORE"), the new layout stores
 *    `enabled` ("true" / "false"). A boolean string at index 4 means "this
 *    is a post-P1 row" and the trailing cell is the new `draft` flag.
 *
 * Decoder migration policy: rows missing the `draft` cell decode as
 * `draft = false` (the quieter "보류" sub-bucket). Rows with a garbled
 * 8th cell also decode as `draft = false`. The only intentional spelling of
 * `draft = true` is "true" written by the encoder for a freshly saved
 * rule.
 */
object RuleStorageCodec {
    /**
     * Sentinel written to the storage cell when `overrideOf` is null. We need a
     * sentinel (rather than an empty string) so that a decoded line can
     * distinguish "override missing" from any other empty-string field.
     */
    private const val NULL_OVERRIDE_OF = "\u0000"

    fun encode(rules: List<RuleUiModel>): String {
        return rules.joinToString("\n") { rule ->
            listOf(
                rule.id,
                rule.title,
                rule.subtitle,
                rule.type.name,
                rule.enabled.toString(),
                rule.matchValue,
                rule.overrideOf ?: NULL_OVERRIDE_OF,
                rule.draft.toString(),
            ).joinToString("|") { value -> value.escape() }
        }
    }

    fun decode(encoded: String): List<RuleUiModel> {
        if (encoded.isBlank()) return emptyList()

        return encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.unescape() }
                if (parts.size < 7) return@mapNotNull null

                // Disambiguate post-Task-2 (boolean at index 4) vs pre-P1
                // (action name at index 4). The 4th cell of the post-P1
                // layouts is `enabled` so it is always the literal "true"
                // or "false"; the pre-P1 layouts stored an `RuleActionUi`
                // enum name there, so a non-boolean string indicates a
                // legacy row that needs its action column stripped.
                val isPostP1Layout = parts[4].equals("true", ignoreCase = true) ||
                    parts[4].equals("false", ignoreCase = true)
                val normalizedParts = if (isPostP1Layout) {
                    parts
                } else {
                    parts.toMutableList().apply { removeAt(4) }
                }

                if (normalizedParts.size < 6) return@mapNotNull null

                val overrideOf = normalizedParts.getOrNull(6)?.let { raw ->
                    if (raw == NULL_OVERRIDE_OF || raw.isEmpty()) null else raw
                }
                // `draft` lives in the trailing cell of the post-Task-2
                // layout (index 7). Anything else — missing cell, garbled
                // value, legacy row that never carried a draft column —
                // decodes as `draft = false` so we route the rule into the
                // quieter "보류" sub-bucket on RulesScreen.
                val draft = normalizedParts.getOrNull(7)?.toBooleanStrictOrNull() ?: false

                RuleUiModel(
                    id = normalizedParts[0],
                    title = normalizedParts[1],
                    subtitle = normalizedParts[2],
                    type = RuleTypeUi.valueOf(normalizedParts[3]),
                    enabled = normalizedParts[4].toBoolean(),
                    matchValue = normalizedParts[5],
                    overrideOf = overrideOf,
                    draft = draft,
                )
            }
            .toList()
    }

    private fun String.escape(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private fun String.unescape(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
}
