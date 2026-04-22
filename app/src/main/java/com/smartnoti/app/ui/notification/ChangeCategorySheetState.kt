package com.smartnoti.app.ui.notification

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * RED-phase skeleton for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 2.
 *
 * Pure state model that drives the "분류 변경" bottom sheet. See
 * `ChangeCategorySheetStateTest` for the exact contract Task 2 must
 * satisfy: rows ordered by `Category.order`, always-present "새 분류
 * 만들기" affordance, typed action factories for each user tap. The
 * factory methods throw [NotImplementedError] today so the RED tests
 * fail at runtime. Task 2 replaces them with the real implementation.
 */
@Suppress("UNUSED_PARAMETER")
data class ChangeCategorySheetState(
    val notification: NotificationUiModel,
    val existingRows: List<ExistingCategoryRow>,
    val canCreateNewCategory: Boolean,
) {

    fun onTapExisting(categoryId: String): ChangeCategorySheetAction {
        TODO("Plan task 2: produce ChangeCategorySheetAction.AssignToExisting(categoryId)")
    }

    fun onTapCreateNew(): ChangeCategorySheetAction {
        TODO("Plan task 2: produce ChangeCategorySheetAction.CreateNew")
    }

    data class ExistingCategoryRow(
        val categoryId: String,
        val name: String,
        val action: CategoryAction,
        val ruleCount: Int,
    )

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun from(
            notification: NotificationUiModel,
            categories: List<Category>,
        ): ChangeCategorySheetState {
            TODO("Plan task 2: order by Category.order, wrap each as ExistingCategoryRow, canCreateNewCategory = true")
        }
    }
}

/**
 * Typed action the sheet produces when the user taps a row. Viewmodel
 * dispatches each variant to the matching use case.
 */
sealed class ChangeCategorySheetAction {
    data class AssignToExisting(val categoryId: String) : ChangeCategorySheetAction()
    object CreateNew : ChangeCategorySheetAction()
}
