package com.smartnoti.app.ui.screens.detail

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [DetailReclassifyConfirmationMessageBuilder].
 *
 * Plan `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` Task 1.
 *
 * Path A copy contract: `"<category-name> 분류로 옮겼어요"`.
 * Path B copy contract: `"새 분류 '<category-name>' 만들었어요"`.
 *
 * - Path A with unknown categoryId (race / stale flow) returns `null` so the
 *   host omits the snackbar entirely. Choosing `null` over a generic fallback
 *   avoids confusing the user when the actual destination cannot be named.
 * - Path B with a blank category name returns `null` for the same reason.
 */
class DetailReclassifyConfirmationMessageBuilderTest {

    private val builder = DetailReclassifyConfirmationMessageBuilder()

    private val promo = Category(
        id = "cat-promo",
        name = "프로모션",
        appPackageName = null,
        ruleIds = emptyList(),
        action = CategoryAction.DIGEST,
        order = 0,
    )
    private val priority = Category(
        id = "cat-priority",
        name = "중요 알림",
        appPackageName = null,
        ruleIds = emptyList(),
        action = CategoryAction.PRIORITY,
        order = 1,
    )

    @Test
    fun path_a_assigned_to_existing_category_returns_moved_copy() {
        val message = builder.build(
            outcome = DetailReclassifyOutcome.AssignedExisting(categoryId = "cat-promo"),
            categories = listOf(promo, priority),
        )

        assertEquals("프로모션 분류로 옮겼어요", message)
    }

    @Test
    fun path_a_with_unknown_category_id_returns_null() {
        val message = builder.build(
            outcome = DetailReclassifyOutcome.AssignedExisting(categoryId = "cat-missing"),
            categories = listOf(promo, priority),
        )

        assertNull(message)
    }

    @Test
    fun path_b_created_new_category_returns_created_copy() {
        val message = builder.build(
            outcome = DetailReclassifyOutcome.CreatedNew(categoryName = "새 분류"),
            categories = emptyList(),
        )

        assertEquals("새 분류 '새 분류' 만들었어요", message)
    }

    @Test
    fun path_b_with_blank_category_name_returns_null() {
        val message = builder.build(
            outcome = DetailReclassifyOutcome.CreatedNew(categoryName = "   "),
            categories = emptyList(),
        )

        assertNull(message)
    }
}
