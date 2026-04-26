package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Implements plan
 * `docs/plans/2026-04-26-detail-reclassify-this-row-now.md` Tasks 2–3.
 *
 * After the user picks (or creates) a Category in Detail's "분류 변경"
 * sheet, the row they are looking at must update to reflect the new
 * mapping: the legacy 4-button layout did this synchronously via
 * `NotificationRepository.updateNotification`, and the redesign's
 * silent-success UX (Rule + Category write only) was a regression that
 * left users uncertain whether their tap took effect.
 *
 * This use case is the "row also moves" half of that recovery. It (a)
 * derives the new [com.smartnoti.app.domain.model.NotificationStatusUi]
 * from the destination [CategoryAction] via
 * [CategoryActionToNotificationStatusMapper], (b) appends the manual
 * [USER_CLASSIFIED_REASON_TAG] to `reasonTags` (dedup), and (c) preserves
 * every other field on the notification before delegating to a single
 * [Ports.updateNotification] write.
 *
 * The reason tag is a stable identifier (matching the `사용자 규칙` /
 * `사용자 복구` convention used by auto-rule + restore paths) so future
 * Detail visits can surface "왜 이렇게 처리됐나요?" → "사용자 분류" chip
 * and the user knows the routing was their own manual choice.
 *
 * The use case is **just a write** — it does not own UX consequences of
 * the new status (e.g. an IGNORE row vanishing from the default Home /
 * Inbox view). Callers (Detail) decide whether to leave the Detail
 * screen open, prompt for confirmation, or back out.
 *
 * The class takes a [Ports] interface instead of repository singletons
 * so unit tests can inject in-memory fakes. Production callers build a
 * [Ports] that delegates to `NotificationRepository.updateNotification`.
 */
class ApplyCategoryActionToNotificationUseCase(
    private val ports: Ports,
    private val mapper: CategoryActionToNotificationStatusMapper,
) {

    interface Ports {
        suspend fun updateNotification(notification: NotificationUiModel)
    }

    suspend fun apply(notification: NotificationUiModel, action: CategoryAction) {
        val newStatus = mapper.map(action)
        val newReasonTags = appendUserClassifiedTag(notification.reasonTags)
        ports.updateNotification(
            notification.copy(
                status = newStatus,
                reasonTags = newReasonTags,
            ),
        )
    }

    private fun appendUserClassifiedTag(existing: List<String>): List<String> {
        if (USER_CLASSIFIED_REASON_TAG in existing) return existing
        return existing + USER_CLASSIFIED_REASON_TAG
    }

    companion object {
        /**
         * Reason-tag label appended when this use case writes the row.
         * Matches the convention of `사용자 규칙` (auto-rule additions) and
         * `사용자 복구` (silent → priority restore) so Detail's reason-chip
         * surface stays uniform.
         */
        const val USER_CLASSIFIED_REASON_TAG: String = "사용자 분류"
    }
}
