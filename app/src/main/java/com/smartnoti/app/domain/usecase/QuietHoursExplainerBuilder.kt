package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi

/**
 * Plain-language explainer for the "왜 이 알림이 정리함으로 갔는지" Detail card,
 * specifically the quiet-hours branch of [NotificationClassifier].
 *
 * Plan: `docs/plans/2026-04-26-quiet-hours-explainer-copy.md`. Surfaced under
 * the reasonTag chip row as a "지금 적용된 정책" sub-section so users do not
 * have to infer the meaning of the `조용한 시간` chip on its own.
 */
data class QuietHoursExplainer(val message: String)

class QuietHoursExplainerBuilder {

    /**
     * Returns a non-null explainer only when:
     *  - `reasonTags` contains [QUIET_HOURS_TAG] (capture pipeline attached the tag), AND
     *  - `status` is [NotificationStatusUi.DIGEST] (classifier branch actually fired), AND
     *  - no higher-precedence signal (e.g. [USER_RULE_TAG]) is also present —
     *    in that case the user rule, not quiet-hours, was decisive so explaining
     *    quiet-hours would mislead.
     *
     * `startHour == endHour` falls through to overnight phrasing but in practice
     * the quiet-hours branch never fires for an empty window, so the tag will
     * not be present and this method returns null upstream.
     */
    fun build(
        reasonTags: List<String>,
        status: NotificationStatusUi,
        startHour: Int,
        endHour: Int,
    ): QuietHoursExplainer? {
        if (status != NotificationStatusUi.DIGEST) return null
        if (QUIET_HOURS_TAG !in reasonTags) return null
        if (HIGHER_PRECEDENCE_TAGS.any { it in reasonTags }) return null

        val isOvernight = startHour >= endHour
        val window = if (isOvernight) {
            "${startHour}시~익일 ${endHour}시"
        } else {
            "${startHour}시~${endHour}시"
        }
        return QuietHoursExplainer(
            message = "지금이 조용한 시간(${window})이라 자동으로 모아뒀어요.",
        )
    }

    private companion object {
        const val QUIET_HOURS_TAG = "조용한 시간"

        /**
         * Tags that, when also present, indicate the classifier reached DIGEST
         * for a reason other than quiet-hours. Keeping this tight (only the
         * user-rule tag for now) so we still explain quiet-hours when it is
         * paired with weaker context signals (e.g. duplicate-suppression).
         */
        val HIGHER_PRECEDENCE_TAGS = setOf("사용자 규칙")
    }
}
