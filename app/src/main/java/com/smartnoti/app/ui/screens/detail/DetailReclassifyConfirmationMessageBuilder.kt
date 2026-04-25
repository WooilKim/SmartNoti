package com.smartnoti.app.ui.screens.detail

import com.smartnoti.app.domain.model.Category

/**
 * The two outcomes a Detail "분류 변경" interaction can produce, both of which
 * the host wants to acknowledge with a short snackbar so the silent-success
 * UX gap (notification-detail Known gaps / rules-feedback-loop Known gaps)
 * goes away.
 *
 * Plan `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md`.
 */
sealed interface DetailReclassifyOutcome {
    /** Path A — user picked an existing Category from the assign sheet. */
    data class AssignedExisting(val categoryId: String) : DetailReclassifyOutcome

    /** Path B — user saved a freshly created Category in the editor. */
    data class CreatedNew(val categoryName: String) : DetailReclassifyOutcome
}

/**
 * Pure mapper from a reclassify outcome + the current Categories snapshot to
 * the user-visible snackbar string. Returning `null` tells the host to show
 * no snackbar (the data needed to name the destination is missing — better
 * silent than misleading).
 *
 * Path A copy contract: `"<category-name> 분류로 옮겼어요"`. We look up the
 * Category by id from the snapshot the host already collected, so the name
 * does not need to round-trip through the use case.
 *
 * Path B copy contract: `"새 분류 '<category-name>' 만들었어요"`. The editor
 * passes the saved Category's name in directly because by the time the
 * snackbar fires the new Category may not yet have surfaced in the host's
 * `categories` Flow snapshot.
 */
class DetailReclassifyConfirmationMessageBuilder {

    fun build(
        outcome: DetailReclassifyOutcome,
        categories: List<Category>,
    ): String? = when (outcome) {
        is DetailReclassifyOutcome.AssignedExisting -> {
            val name = categories.firstOrNull { it.id == outcome.categoryId }?.name
            if (name.isNullOrBlank()) null else "$name 분류로 옮겼어요"
        }
        is DetailReclassifyOutcome.CreatedNew -> {
            val name = outcome.categoryName.trim()
            if (name.isEmpty()) null else "새 분류 '$name' 만들었어요"
        }
    }
}
