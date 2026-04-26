package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.model.toUiStatus

/**
 * Translates a destination [CategoryAction] into the persisted
 * [NotificationStatusUi] value the row should carry after the user
 * picks (or creates) a Category in Detail's "분류 변경" sheet.
 *
 * Plan `docs/plans/2026-04-26-detail-reclassify-this-row-now.md` Task 3.
 *
 * The mapping is the existing [CategoryAction.toDecision] +
 * [com.smartnoti.app.domain.model.NotificationDecision.toUiStatus]
 * composition, but kept as its own injectable seam so:
 *
 * - Detail call sites do not have to learn the two-hop indirection.
 * - The four-case `when` is pin-tested in
 *   [CategoryActionToNotificationStatusMapperTest] in one place,
 *   defending against silent gaps if [CategoryAction] ever grows
 *   (currently fixed at four by `CategoryTest.category_action_enum_has_exactly_four_values`).
 *
 * The class is dependency-free and trivially constructible — Detail
 * builds a singleton at composition time. A function would do too;
 * the class shape mirrors sibling builders in this package and gives
 * future stateful variants (e.g. quiet-hours-aware) a place to land.
 */
class CategoryActionToNotificationStatusMapper {
    fun map(action: CategoryAction): NotificationStatusUi = action.toDecision().toUiStatus()
}
