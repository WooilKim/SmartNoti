package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * RED-phase skeleton for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 3.
 *
 * This stub exists so Task 1's RED tests compile. Every method body throws
 * [NotImplementedError] — Task 3 replaces them with the real derivation
 * logic. Do not wire this into the UI until Task 3 lands.
 */
class AssignNotificationToCategoryUseCase(
    @Suppress("UNUSED_PARAMETER") private val ports: Ports,
) {

    interface Ports {
        suspend fun upsertRule(rule: RuleUiModel)
        suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun assignToExisting(
        notification: NotificationUiModel,
        categoryId: String,
    ) {
        TODO("Plan task 3: derive auto-rule + upsert + append to Category.ruleIds")
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun buildPrefillForNewCategory(
            notification: NotificationUiModel,
            currentCategoryAction: CategoryAction?,
        ): CategoryEditorPrefill {
            TODO("Plan task 3: derive prefill with dynamic-opposite default action")
        }
    }
}

/**
 * RED-phase skeleton for the "새 분류 만들기" editor prefill payload. Task 3
 * finalizes the exact shape; the fields here are what Task 1's tests
 * assert against so future changes must stay compatible.
 */
data class CategoryEditorPrefill(
    val name: String,
    val appPackageName: String?,
    val pendingRule: RuleUiModel,
    val defaultAction: CategoryAction,
)
