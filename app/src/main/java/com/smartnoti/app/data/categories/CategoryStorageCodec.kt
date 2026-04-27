package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Line-oriented codec for the `smartnoti_categories` DataStore cell. Mirrors
 * the URL-escaped pipe-separated layout used by
 * [com.smartnoti.app.data.rules.RuleStorageCodec] so the two codecs read the
 * same in diffs — the Category layout adds `order` and `ruleIds` while
 * dropping the Rule-specific `enabled` / `title` / `subtitle` columns.
 *
 * Column order (frozen for backward compat):
 *
 *  0: id
 *  1: name
 *  2: appPackageName (sentinel [NULL_APP_PACKAGE] == null)
 *  3: action (CategoryAction.name)
 *  4: order (Int.toString)
 *  5: ruleIds (empty cell == empty list; otherwise comma-separated, each
 *     member URL-encoded individually so commas inside ids survive)
 *  6: userModifiedAction (`"1"` for true, `"0"` for false; missing column
 *     decoded as `false` for backward compat with pre-Task-3 payloads).
 *     Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`.
 */
object CategoryStorageCodec {

    /**
     * Sentinel written when `appPackageName` is null. Using the NUL char (same
     * pattern as RuleStorageCodec.NULL_OVERRIDE_OF) lets the decoder tell
     * "intentionally null" apart from "empty string".
     */
    private const val NULL_APP_PACKAGE = "\u0000"

    /** Separator inside the ruleIds cell. Comma works because every id is URL-encoded. */
    private const val RULE_IDS_SEPARATOR = ","

    fun encode(categories: List<Category>): String {
        return categories.joinToString("\n") { category ->
            listOf(
                category.id,
                category.name,
                category.appPackageName ?: NULL_APP_PACKAGE,
                category.action.name,
                category.order.toString(),
                category.ruleIds.joinToString(RULE_IDS_SEPARATOR) { it.escape() },
                if (category.userModifiedAction) "1" else "0",
            ).joinToString("|") { it.escape() }
        }
    }

    fun decode(encoded: String): List<Category> {
        if (encoded.isBlank()) return emptyList()

        return encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.unescape() }
                if (parts.size < 6) return@mapNotNull null

                val action = runCatching { CategoryAction.valueOf(parts[3]) }.getOrNull()
                    ?: return@mapNotNull null
                val order = parts[4].toIntOrNull() ?: return@mapNotNull null

                val appPackageName = parts[2].let { raw ->
                    if (raw == NULL_APP_PACKAGE || raw.isEmpty()) null else raw
                }

                val ruleIds = parts[5]
                    .split(RULE_IDS_SEPARATOR)
                    .filter { it.isNotEmpty() }
                    .map { it.unescape() }

                // Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
                // Task 3: backward-compat for pre-Task-3 payloads that
                // shipped 6 columns. Missing column 7 means "the user has
                // not been observed touching the action picker yet" so
                // the migration is allowed to bump the seeded default.
                val userModifiedAction = parts.getOrNull(6) == "1"

                Category(
                    id = parts[0],
                    name = parts[1],
                    appPackageName = appPackageName,
                    ruleIds = ruleIds,
                    action = action,
                    order = order,
                    userModifiedAction = userModifiedAction,
                )
            }
            .toList()
    }

    private fun String.escape(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private fun String.unescape(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
}
