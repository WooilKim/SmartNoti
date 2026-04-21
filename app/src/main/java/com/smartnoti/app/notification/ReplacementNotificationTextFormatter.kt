package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

internal object ReplacementNotificationTextFormatter {
    private val nonExplanatoryReasonTags = setOf("발신자 있음")

    fun explanationText(
        decision: NotificationDecision,
        reasonTags: List<String>,
    ): String {
        val handlingSummary = when (decision) {
            NotificationDecision.DIGEST -> "원본 알림 숨김을 시도하고 Digest에 모아뒀어요"
            NotificationDecision.SILENT -> "원본 알림 숨김을 시도하고 조용히 보관했어요"
            NotificationDecision.PRIORITY -> "원본 알림을 바로 확인할 수 있게 유지했어요"
            // IGNORE never posts a replacement notification (Task 4 of plan
            // `2026-04-21-ignore-tier-fourth-decision`), so this formatter
            // never runs for IGNORE rows. Defensive copy in case it does.
            NotificationDecision.IGNORE -> "사용자 규칙에 따라 무시 처리했어요"
        }
        val highlightedReasons = highlightReasons(reasonTags)

        return if (highlightedReasons.isEmpty()) {
            handlingSummary
        } else {
            "$handlingSummary · ${highlightedReasons.joinToString(" · ")}"
        }
    }

    internal fun highlightReasons(reasonTags: List<String>): List<String> {
        return reasonTags
            .map(String::trim)
            .filter { tag -> tag.isNotEmpty() && tag !in nonExplanatoryReasonTags }
            .distinct()
            .take(2)
    }
}