package com.smartnoti.app.ui.notification

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1
 * (sheet state model contract).
 *
 * The "분류 변경" bottom sheet is driven by a pure state model
 * `ChangeCategorySheetState` that lists:
 *   - every existing Category (ordered by `order`, with action + ruleIds
 *     size surfaced for the row chip + caption)
 *   - a terminal "새 분류 만들기" row that is always present
 *
 * When the user taps a row the sheet produces a strongly-typed
 * `ChangeCategorySheetAction` that the viewmodel hands directly to the
 * corresponding use case:
 *   - `AssignToExisting(categoryId)` → routes to
 *     `AssignNotificationToCategoryUseCase.assignToExisting`
 *   - `CreateNew` → routes to the editor-prefill entry point
 *
 * None of these symbols exist yet — Task 2 introduces them. This file is
 * expected to compile-fail in RED.
 */
class ChangeCategorySheetStateTest {

    @Test
    fun state_lists_existing_categories_ordered_by_order_field() {
        val categories = listOf(
            category(id = "cat-b", name = "B", action = CategoryAction.DIGEST, order = 2),
            category(id = "cat-a", name = "A", action = CategoryAction.PRIORITY, order = 0),
            category(id = "cat-c", name = "C", action = CategoryAction.SILENT, order = 1),
        )

        val state = ChangeCategorySheetState.from(
            notification = notification(),
            categories = categories,
        )

        // Rows are ordered by the Category `order` field, not the input
        // list order. The "create new" row is always last.
        val rowIds = state.existingRows.map { it.categoryId }
        assertEquals(listOf("cat-a", "cat-c", "cat-b"), rowIds)
    }

    @Test
    fun state_exposes_action_chip_and_rule_count_per_existing_row() {
        val categories = listOf(
            category(
                id = "cat-a",
                name = "업무",
                action = CategoryAction.PRIORITY,
                order = 0,
                ruleIds = listOf("r1", "r2", "r3"),
            ),
        )

        val state = ChangeCategorySheetState.from(
            notification = notification(),
            categories = categories,
        )

        val row = state.existingRows.single()
        assertEquals("업무", row.name)
        assertEquals(CategoryAction.PRIORITY, row.action)
        assertEquals(3, row.ruleCount)
    }

    @Test
    fun state_always_includes_create_new_row_even_when_no_categories() {
        val state = ChangeCategorySheetState.from(
            notification = notification(),
            categories = emptyList(),
        )

        assertTrue(state.existingRows.isEmpty())
        // "새 분류 만들기" row is an implicit terminal affordance — the
        // state exposes it as a flag rather than a fake Category entry so
        // the UI can render it with its own styling.
        assertTrue(state.canCreateNewCategory)
    }

    @Test
    fun tapping_existing_row_produces_assign_to_existing_action() {
        val categories = listOf(
            category(id = "cat-a", name = "A", action = CategoryAction.PRIORITY, order = 0),
        )
        val state = ChangeCategorySheetState.from(
            notification = notification(),
            categories = categories,
        )

        val action = state.onTapExisting(categoryId = "cat-a")

        assertEquals(
            ChangeCategorySheetAction.AssignToExisting(categoryId = "cat-a"),
            action,
        )
    }

    @Test
    fun tapping_create_new_produces_create_new_action() {
        val state = ChangeCategorySheetState.from(
            notification = notification(),
            categories = emptyList(),
        )

        val action = state.onTapCreateNew()

        assertEquals(ChangeCategorySheetAction.CreateNew, action)
    }

    private fun notification() = NotificationUiModel(
        id = "n-1",
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "Alice",
        title = "Alice",
        body = "내용",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
    )

    private fun category(
        id: String,
        name: String,
        action: CategoryAction,
        order: Int,
        ruleIds: List<String> = emptyList(),
    ) = Category(
        id = id,
        name = name,
        appPackageName = null,
        ruleIds = ruleIds,
        action = action,
        order = order,
    )
}
