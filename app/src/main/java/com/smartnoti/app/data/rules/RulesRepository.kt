package com.smartnoti.app.data.rules

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.RuleActionUi
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

fun moveRule(
    rules: List<RuleUiModel>,
    ruleId: String,
    direction: RuleMoveDirection,
): List<RuleUiModel> {
    val currentIndex = rules.indexOfFirst { it.id == ruleId }
    if (currentIndex == -1) return rules

    val targetIndex = when (direction) {
        RuleMoveDirection.UP -> currentIndex - 1
        RuleMoveDirection.DOWN -> currentIndex + 1
    }
    if (targetIndex !in rules.indices) return rules

    val mutable = rules.toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(targetIndex, item)
    return mutable.toList()
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
        action = RuleActionUi.ALWAYS_PRIORITY,
        enabled = true,
        matchValue = "엄마",
    ),
    RuleUiModel(
        id = "default-app-coupang",
        title = "쿠팡",
        subtitle = "Digest로 묶기",
        type = RuleTypeUi.APP,
        action = RuleActionUi.DIGEST,
        enabled = true,
        matchValue = "com.coupang.mobile",
    ),
    RuleUiModel(
        id = "default-keyword-otp",
        title = "인증번호",
        subtitle = "즉시 전달",
        type = RuleTypeUi.KEYWORD,
        action = RuleActionUi.ALWAYS_PRIORITY,
        enabled = true,
        matchValue = "인증번호",
    ),
)
