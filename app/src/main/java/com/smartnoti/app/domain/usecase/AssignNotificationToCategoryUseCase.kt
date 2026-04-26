package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Implements plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 3.
 *
 * The user taps a row in the "분류 변경" bottom sheet. If they picked an
 * existing Category, [assignToExisting] derives an auto-rule from the
 * notification, upserts it, and appends the id to the Category's
 * `ruleIds`. If they picked "새 분류 만들기", the caller first reads
 * [buildPrefillForNewCategory] to seed the editor and later persists the
 * new Category via the editor's own save path (not this class).
 *
 * Assign is idempotent: if the Category already references the same
 * derived rule id, the upsert refreshes the Rule and the append is a
 * no-op (dedup handled by the [Ports.appendRuleIdToCategory]
 * implementation).
 *
 * The class takes a [Ports] interface instead of repository singletons so
 * unit tests can inject in-memory fakes. Production callers build a
 * [Ports] that delegates to `RulesRepository.upsertRule` +
 * `CategoriesRepository.appendRuleIdToCategory`.
 */
class AssignNotificationToCategoryUseCase(
    private val ports: Ports,
) {

    interface Ports {
        suspend fun upsertRule(rule: RuleUiModel)
        suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String)
    }

    suspend fun assignToExisting(
        notification: NotificationUiModel,
        categoryId: String,
    ) {
        val rule = deriveAutoRule(notification)
        ports.upsertRule(rule)
        ports.appendRuleIdToCategory(categoryId, rule.id)
    }

    companion object {
        /**
         * Derive the Rule that represents this notification's sender/app.
         *
         * - Non-blank sender → PERSON rule matching the sender, id `PERSON:<sender>`.
         * - Blank sender → APP rule matching the packageName, id `APP:<package>`.
         *
         * The id is deterministic so two notifications with the same sender
         * always produce the same rule id. Repeat assignments to the same
         * Category are then idempotent (dedup at append site). A short
         * human-readable title is used for reason-chip surfacing.
         */
        fun deriveAutoRule(notification: NotificationUiModel): RuleUiModel {
            val usesSender = !notification.sender.isNullOrBlank()
            return if (usesSender) {
                val sender = notification.sender!!
                RuleUiModel(
                    id = "PERSON:$sender",
                    title = sender,
                    subtitle = "",
                    type = RuleTypeUi.PERSON,
                    enabled = true,
                    matchValue = sender,
                )
            } else {
                RuleUiModel(
                    id = "APP:${notification.packageName}",
                    title = notification.appName,
                    subtitle = "",
                    type = RuleTypeUi.APP,
                    enabled = true,
                    matchValue = notification.packageName,
                )
            }
        }

        /**
         * Build a prefill payload for the "새 분류 만들기" editor flow.
         *
         * `name` uses the sender when present, else the app name. When the
         * derived rule is APP the Category is pinned to that app package
         * (keyword / person categories stay app-unpinned so they can match
         * across apps). `defaultAction` is the **dynamic-opposite** of
         * [currentCategoryAction]: DIGEST/SILENT/IGNORE → PRIORITY;
         * PRIORITY → DIGEST; null → PRIORITY. The user can still override
         * in the editor before saving.
         */
        fun buildPrefillForNewCategory(
            notification: NotificationUiModel,
            currentCategoryAction: CategoryAction?,
        ): CategoryEditorPrefill {
            val rule = deriveAutoRule(notification)
            val appPackageName = if (rule.type == RuleTypeUi.APP) {
                notification.packageName
            } else {
                null
            }
            return CategoryEditorPrefill(
                name = rule.title,
                appPackageName = appPackageName,
                pendingRule = rule,
                defaultAction = dynamicOppositeAction(currentCategoryAction),
            )
        }

        /**
         * Dynamic-opposite mapping documented in plan Risks (b), 2026-04-22:
         * swap PRIORITY ↔ DIGEST so creating a new Category for a currently
         * DIGEST-routed notification defaults to PRIORITY (the user is
         * escalating it). SILENT and IGNORE both escalate to PRIORITY too,
         * because creating a dedicated Category strongly signals "I want
         * to notice this".
         */
        private fun dynamicOppositeAction(current: CategoryAction?): CategoryAction {
            return when (current) {
                CategoryAction.PRIORITY -> CategoryAction.DIGEST
                CategoryAction.DIGEST,
                CategoryAction.SILENT,
                CategoryAction.IGNORE,
                null -> CategoryAction.PRIORITY
            }
        }
    }
}

/**
 * Payload the "분류 변경" → "새 분류 만들기" entry point hands to the
 * `CategoryEditor` composable. The editor consumes these fields on first
 * composition to seed its draft state; no Rule is persisted until the
 * user saves.
 *
 * `seedExistingRuleIds` (plan
 * `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Task 6) seeds
 * the editor's pre-selected `draftSelectedRuleIds` with rule ids that
 * already exist in the Rules DataStore — used by the RulesScreen
 * bulk-assign "새 분류 만들기" path so the user lands in the editor
 * with the N selected unassigned rules already checked. Empty for the
 * Detail single-rule path (which uses [pendingRule] instead).
 */
data class CategoryEditorPrefill(
    val name: String,
    val appPackageName: String?,
    val pendingRule: RuleUiModel?,
    val defaultAction: CategoryAction,
    val seedExistingRuleIds: List<String> = emptyList(),
)
