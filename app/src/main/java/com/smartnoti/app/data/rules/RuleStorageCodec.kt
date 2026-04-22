package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Line-oriented codec for the `smartnoti_rules` DataStore payload.
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1
 * Task 4 removed `RuleUiModel.action`, so the persisted format shrinks from
 * 8 columns to 7. The decoder still tolerates legacy 8-column rows written
 * by pre-P1 builds — the 5th column (the old action string) is skipped so
 * existing installs upgrade in place without an explicit Room-style
 * migration. The action carried by those legacy rows is recovered via the
 * Task 3 Rules → Categories migration, which reads `RulesRepository` BEFORE
 * that repository was re-encoded (so `action` is still inspectable there).
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
            ).joinToString("|") { value -> value.escape() }
        }
    }

    fun decode(encoded: String): List<RuleUiModel> {
        if (encoded.isBlank()) return emptyList()

        return encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.unescape() }
                // Post-P1-Task-4: 7-column layout (id, title, subtitle, type,
                // enabled, matchValue, overrideOf). Pre-Task-4 lines have an
                // `action` column at index 4 — sometimes 8 columns including
                // `overrideOf`, sometimes 7 when the row was written before
                // Phase C added `overrideOf`. Disambiguate by checking index 4:
                //   - "true" / "false" -> new 7-column format, keep as-is.
                //   - anything else -> legacy, drop index 4 (action).
                val normalizedParts = when {
                    parts.size < 7 -> return@mapNotNull null
                    parts.size >= 8 -> parts.toMutableList().apply { removeAt(4) }
                    parts[4].equals("true", ignoreCase = true) ||
                        parts[4].equals("false", ignoreCase = true) -> parts
                    else -> parts.toMutableList().apply { removeAt(4) }
                }

                if (normalizedParts.size < 6) return@mapNotNull null
                val overrideOf = normalizedParts.getOrNull(6)?.let { raw ->
                    if (raw == NULL_OVERRIDE_OF || raw.isEmpty()) null else raw
                }

                RuleUiModel(
                    id = normalizedParts[0],
                    title = normalizedParts[1],
                    subtitle = normalizedParts[2],
                    type = RuleTypeUi.valueOf(normalizedParts[3]),
                    enabled = normalizedParts[4].toBoolean(),
                    matchValue = normalizedParts[5],
                    overrideOf = overrideOf,
                )
            }
            .toList()
    }

    private fun String.escape(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private fun String.unescape(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
}
