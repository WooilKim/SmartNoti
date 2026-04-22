package com.smartnoti.app.data.categories

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.RuleActionUi
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// Parallel DataStore handle that points at the SAME `smartnoti_rules` file
// [com.smartnoti.app.data.rules.RulesRepository] uses. Declared separately
// here because `preferencesDataStore` is per-file-per-module and we need a
// second reader for the one-shot legacy-action scan below.
private val Context.legacyRulesDataStore by preferencesDataStore(name = "smartnoti_rules")

/**
 * One-shot reader for the pre-P1 Rule payload that still has an `action`
 * column at index 4. Plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 4
 * ships a 7-column `RuleStorageCodec`; the Task 3 migration needs to
 * recover the legacy action so it can populate `Category.action` before
 * the 7-column codec rewrites (and loses) it.
 *
 * Returns `ruleId -> RuleActionUi` for every decodable legacy row. Rows
 * that already use the new 7-column shape produce no entry — they were
 * written post-migration and their action should be read from the
 * Category graph instead. Unknown action names fall through silently (no
 * crash) so unexpected upgrade payloads are tolerated.
 */
class LegacyRuleActionReader(private val context: Context) {

    suspend fun readRuleActions(): Map<String, RuleActionUi> {
        val raw = context.legacyRulesDataStore.data.first()[RULES_KEY] ?: return emptyMap()
        return parse(raw)
    }

    internal fun parse(encoded: String): Map<String, RuleActionUi> {
        if (encoded.isBlank()) return emptyMap()
        val out = mutableMapOf<String, RuleActionUi>()
        encoded.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split("|").map { it.unescape() }
                if (parts.size < 8) return@forEach
                val ruleId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@forEach
                val actionName = parts.getOrNull(4).orEmpty()
                runCatching { RuleActionUi.valueOf(actionName) }
                    .onSuccess { action -> out[ruleId] = action }
            }
        return out
    }

    private fun String.unescape(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())

    private companion object {
        private val RULES_KEY = stringPreferencesKey("rules_payload")
    }
}
