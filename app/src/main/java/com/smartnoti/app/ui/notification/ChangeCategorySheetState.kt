package com.smartnoti.app.ui.notification

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 2.
 *
 * Pure state model that drives the "분류 변경" bottom sheet. Rows render
 * in `Category.order` ascending so the user's drag-ordering in the 분류
 * tab governs sheet ordering too. The "새 분류 만들기" affordance is an
 * implicit terminal row — exposed as a flag so the composable can style
 * it differently from an existing-Category row.
 */
data class ChangeCategorySheetState(
    val notification: NotificationUiModel,
    val existingRows: List<ExistingCategoryRow>,
    val canCreateNewCategory: Boolean,
) {

    fun onTapExisting(categoryId: String): ChangeCategorySheetAction {
        return ChangeCategorySheetAction.AssignToExisting(categoryId = categoryId)
    }

    fun onTapCreateNew(): ChangeCategorySheetAction {
        return ChangeCategorySheetAction.CreateNew
    }

    data class ExistingCategoryRow(
        val categoryId: String,
        val name: String,
        val action: CategoryAction,
        val ruleCount: Int,
    )

    companion object {
        fun from(
            notification: NotificationUiModel,
            categories: List<Category>,
        ): ChangeCategorySheetState {
            val rows = categories
                .sortedBy { it.order }
                .map { category ->
                    ExistingCategoryRow(
                        categoryId = category.id,
                        name = category.name,
                        action = category.action,
                        ruleCount = category.ruleIds.size,
                    )
                }
            return ChangeCategorySheetState(
                notification = notification,
                existingRows = rows,
                canCreateNewCategory = true,
            )
        }
    }
}

/**
 * Typed action the sheet produces when the user taps a row. Viewmodel
 * dispatches each variant to the matching use case (AssignToExisting →
 * `AssignNotificationToCategoryUseCase.assignToExisting`, CreateNew →
 * editor navigation with prefill).
 */
sealed class ChangeCategorySheetAction {
    data class AssignToExisting(val categoryId: String) : ChangeCategorySheetAction()
    object CreateNew : ChangeCategorySheetAction()
}
