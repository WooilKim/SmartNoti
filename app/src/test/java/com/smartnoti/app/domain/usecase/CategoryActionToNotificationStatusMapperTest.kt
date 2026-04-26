package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-26-detail-reclassify-this-row-now.md` Task 1.
 *
 * The "이 알림도 지금 재분류" CTA needs to translate the destination
 * Category's [CategoryAction] into the [NotificationStatusUi] value the
 * row will be persisted with. The codebase already has
 * `CategoryAction.toDecision()` (in `Category.kt`) and
 * `NotificationDecision.toUiStatus()` (in
 * `NotificationDeliveryProfileMetadata.kt`) — composing them gives the
 * mapping we need, but Detail call sites should not have to learn that
 * indirection. A single-purpose mapper keeps the four-case `when`
 * exhaustive and pin-tested in one place so future additions to
 * [CategoryAction] (forbidden by `CategoryTest.category_action_enum_has_exactly_four_values`
 * but worth defending) cannot silently miss this surface.
 *
 * The mapper symbol does not exist yet — Task 3 turns this RED test
 * green by introducing
 * [com.smartnoti.app.domain.usecase.CategoryActionToNotificationStatusMapper].
 */
class CategoryActionToNotificationStatusMapperTest {

    private val mapper = CategoryActionToNotificationStatusMapper()

    @Test
    fun priority_action_maps_to_priority_status() {
        assertEquals(
            NotificationStatusUi.PRIORITY,
            mapper.map(CategoryAction.PRIORITY),
        )
    }

    @Test
    fun digest_action_maps_to_digest_status() {
        assertEquals(
            NotificationStatusUi.DIGEST,
            mapper.map(CategoryAction.DIGEST),
        )
    }

    @Test
    fun silent_action_maps_to_silent_status() {
        assertEquals(
            NotificationStatusUi.SILENT,
            mapper.map(CategoryAction.SILENT),
        )
    }

    @Test
    fun ignore_action_maps_to_ignore_status() {
        assertEquals(
            NotificationStatusUi.IGNORE,
            mapper.map(CategoryAction.IGNORE),
        )
    }
}
