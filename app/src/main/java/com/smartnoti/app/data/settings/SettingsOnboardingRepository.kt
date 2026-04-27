package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Sibling repository carved out of [SettingsRepository] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Owns every
 * one-shot onboarding / first-launch flag plus the Categories migration
 * gate and Home card acknowledgement timestamps. Domain coherence is
 * weaker than the other siblings (3 sub-clusters), but the keys all share
 * the "first-launch behavior" lifecycle and split further only when the
 * façade size becomes problematic.
 */
internal class SettingsOnboardingRepository(
    private val dataStore: DataStore<Preferences>,
) {
    fun observeOnboardingCompleted(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] ?: false
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return observeOnboardingCompleted().first()
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun requestOnboardingActiveNotificationBootstrap(): Boolean {
        var requested = false
        dataStore.edit { prefs ->
            val completed = prefs[Keys.BOOTSTRAP_COMPLETED] ?: false
            val pending = prefs[Keys.BOOTSTRAP_PENDING] ?: false
            if (!completed && !pending) {
                prefs[Keys.BOOTSTRAP_PENDING] = true
                requested = true
            }
        }
        return requested
    }

    /**
     * Reader-only poll for the onboarding bootstrap pending flag.
     * Lets the listener-reconnect sweep coordinator defer while the bootstrap
     * path still owns the first pass over active notifications.
     */
    suspend fun isOnboardingBootstrapPending(): Boolean {
        return dataStore.data.map { prefs ->
            prefs[Keys.BOOTSTRAP_PENDING] ?: false
        }.first()
    }

    suspend fun consumeOnboardingActiveNotificationBootstrapRequest(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            val completed = prefs[Keys.BOOTSTRAP_COMPLETED] ?: false
            val pending = prefs[Keys.BOOTSTRAP_PENDING] ?: false
            when {
                pending && !completed -> {
                    prefs[Keys.BOOTSTRAP_PENDING] = false
                    prefs[Keys.BOOTSTRAP_COMPLETED] = true
                    consumed = true
                }
                completed -> {
                    prefs[Keys.BOOTSTRAP_PENDING] = false
                }
            }
        }
        return consumed
    }

    /**
     * First-launch-post-upgrade gate for the Rules → Categories migration
     * pass (plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
     * Phase P1 Task 3). False until the migration has successfully run at
     * least once; once flipped, the [com.smartnoti.app.data.categories.RuleToCategoryMigration]
     * caller short-circuits and never scans again.
     *
     * The flag complements the idempotent `cat-from-rule-<ruleId>` id scheme
     * — even if a crash tore down the app mid-migration we would re-run
     * safely, but this flag avoids the repeated scan on every cold start.
     */
    fun observeRulesToCategoriesMigrated(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[Keys.RULES_TO_CATEGORIES_MIGRATED] ?: false
        }
    }

    suspend fun isRulesToCategoriesMigrated(): Boolean {
        return observeRulesToCategoriesMigrated().first()
    }

    suspend fun setRulesToCategoriesMigrated(migrated: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.RULES_TO_CATEGORIES_MIGRATED] = migrated
        }
    }

    /**
     * Epoch-millis deadline for the Home "새 앱 분류 유도 카드" snooze (plan
     * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task
     * 10). Zero means "never snoozed". Tapping "나중에" on the card writes
     * `now + 24h` here; the [UncategorizedAppsDetector] consults this when
     * deciding whether to re-emit a Prompt.
     */
    fun observeUncategorizedPromptSnoozeUntilMillis(): Flow<Long> {
        return dataStore.data.map { prefs ->
            prefs[Keys.UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS] ?: 0L
        }
    }

    suspend fun setUncategorizedPromptSnoozeUntilMillis(millis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS] = millis
        }
    }

    /**
     * Tracks when the Home `HomeQuickStartAppliedCard` was last acknowledged
     * so the Task 10 Home declutter can drop the card after 7 days or after an
     * explicit tap-to-ack. Zero means "not acknowledged yet".
     */
    fun observeQuickStartAppliedCardAcknowledgedAtMillis(): Flow<Long> {
        return dataStore.data.map { prefs ->
            prefs[Keys.QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS] ?: 0L
        }
    }

    suspend fun setQuickStartAppliedCardAcknowledgedAtMillis(millis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS] = millis
        }
    }

    /**
     * One-shot gate for the Task 11 categories-migration announcement modal:
     * false until the user dismisses the "규칙은 이제 '분류' 안에서 편집합니다"
     * notice, after which it flips permanently.
     */
    fun observeCategoriesMigrationAnnouncementSeen(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[Keys.CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN] ?: false
        }
    }

    suspend fun setCategoriesMigrationAnnouncementSeen(seen: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN] = seen
        }
    }

    internal object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BOOTSTRAP_PENDING =
            booleanPreferencesKey("onboarding_active_notification_bootstrap_pending")
        val BOOTSTRAP_COMPLETED =
            booleanPreferencesKey("onboarding_active_notification_bootstrap_completed")
        val RULES_TO_CATEGORIES_MIGRATED =
            booleanPreferencesKey("rules_to_categories_migrated")
        val UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS =
            longPreferencesKey("uncategorized_prompt_snooze_until_millis")
        val QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS =
            longPreferencesKey("quick_start_applied_card_acknowledged_at_millis")
        val CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN =
            booleanPreferencesKey("categories_migration_announcement_seen")
    }
}
