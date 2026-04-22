package com.smartnoti.app.data.rules

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rulesDataStore by preferencesDataStore(name = "smartnoti_rules")

enum class RuleMoveDirection {
    UP,
    DOWN,
}

/**
 * Reorder [ruleId] within its tier.
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 5. A "tier" is defined as
 * the set of rules sharing the same [RuleUiModel.overrideOf] value — base rules
 * swap only with other bases, and overrides swap only with siblings of the same
 * base. Cross-tier neighbors are skipped so drag-to-reorder never promotes or
 * demotes a rule between base and override. Iteration order inside the tier is
 * preserved so `RuleConflictResolver` tie-break (earlier = higher priority)
 * stays meaningful after a swap.
 */
fun moveRule(
    rules: List<RuleUiModel>,
    ruleId: String,
    direction: RuleMoveDirection,
): List<RuleUiModel> {
    val currentIndex = rules.indexOfFirst { it.id == ruleId }
    if (currentIndex == -1) return rules

    val currentRule = rules[currentIndex]
    val tierKey = currentRule.overrideOf
    val step = when (direction) {
        RuleMoveDirection.UP -> -1
        RuleMoveDirection.DOWN -> 1
    }

    // Walk outwards in the requested direction, skipping non-tier rows. The
    // first same-tier rule we hit is our swap partner; if none exists, the
    // move is a no-op.
    var scan = currentIndex + step
    while (scan in rules.indices) {
        if (rules[scan].overrideOf == tierKey) {
            val mutable = rules.toMutableList()
            val item = mutable.removeAt(currentIndex)
            // After removing at [currentIndex], the partner sitting at [scan]
            // has shifted by -1 when scan > currentIndex. Inserting at [scan]
            // therefore places the item right after the partner when moving
            // DOWN, and right at the partner's position (displacing it by +1)
            // when moving UP — a clean swap in both directions.
            mutable.add(scan, item)
            return mutable.toList()
        }
        scan += step
    }
    return rules
}

fun resolveStoredRules(encodedPayload: String?): List<RuleUiModel> {
    return when {
        encodedPayload == null -> defaultRules()
        encodedPayload.isBlank() -> emptyList()
        else -> RuleStorageCodec.decode(encodedPayload)
    }
}

fun resolveConfiguredRules(encodedPayload: String?): List<RuleUiModel> {
    return when {
        encodedPayload == null -> emptyList()
        else -> resolveStoredRules(encodedPayload)
    }
}

class RulesRepository private constructor(
    private val context: Context,
    private val overrideValidator: RuleOverrideValidator = RuleOverrideValidator(),
) {
    fun observeRules(): Flow<List<RuleUiModel>> {
        return context.rulesDataStore.data.map { prefs ->
            resolveStoredRules(prefs[RULES])
        }
    }

    suspend fun currentRules(): List<RuleUiModel> = observeRules().first()

    suspend fun currentConfiguredRules(): List<RuleUiModel> {
        val encodedPayload = context.rulesDataStore.data.first()[RULES]
        return resolveConfiguredRules(encodedPayload)
    }

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        val updated = currentRules().map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
        persist(updated)
    }

    suspend fun upsertRule(rule: RuleUiModel) {
        val existing = currentRules().toMutableList()
        val index = existing.indexOfFirst {
            it.id == rule.id || (it.type == rule.type && it.matchValue == rule.matchValue)
        }
        val incoming = if (index >= 0) rule.copy(id = existing[index].id) else rule

        when (val verdict = overrideValidator.validate(incoming, existing)) {
            is RuleOverrideValidator.Result.Rejected -> {
                // Plan rules-ux-v2-inbox-restructure Phase C Task 1: circular
                // override references are dropped on the floor with an error
                // log rather than corrupting the persisted graph.
                Log.e(TAG, "Rejected override upsert for ${incoming.id}: ${verdict.reason}")
                return
            }
            is RuleOverrideValidator.Result.Accepted -> {
                if (index >= 0) {
                    existing[index] = verdict.rule
                } else {
                    existing += verdict.rule
                }
                persist(existing)
            }
        }
    }

    suspend fun replaceAllRules(rules: List<RuleUiModel>) {
        persist(rules)
    }

    suspend fun deleteRule(ruleId: String) {
        persist(currentRules().filterNot { it.id == ruleId })
    }

    suspend fun moveRule(ruleId: String, direction: RuleMoveDirection) {
        persist(moveRule(currentRules(), ruleId, direction))
    }

    private suspend fun persist(rules: List<RuleUiModel>) {
        context.rulesDataStore.edit { prefs ->
            prefs[RULES] = RuleStorageCodec.encode(rules)
        }
    }

    companion object {
        private const val TAG = "RulesRepository"
        private val RULES = stringPreferencesKey("rules_payload")

        @Volatile private var instance: RulesRepository? = null

        fun getInstance(context: Context): RulesRepository {
            return instance ?: synchronized(this) {
                instance ?: RulesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

private fun defaultRules(): List<RuleUiModel> = listOf(
    RuleUiModel(
        id = "default-person-mom",
        title = "엄마",
        subtitle = "항상 바로 보기",
        type = RuleTypeUi.PERSON,
        enabled = true,
        matchValue = "엄마",
    ),
    RuleUiModel(
        id = "default-app-coupang",
        title = "쿠팡",
        subtitle = "Digest로 묶기",
        type = RuleTypeUi.APP,
        enabled = true,
        matchValue = "com.coupang.mobile",
    ),
    RuleUiModel(
        id = "default-keyword-otp",
        title = "인증번호",
        subtitle = "즉시 전달",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "인증번호",
    ),
)
