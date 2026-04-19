package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDetailSourceSuppressionSummaryBuilderTest {

    private val builder = NotificationDetailSourceSuppressionSummaryBuilder()

    @Test
    fun cancel_attempt_summary_warns_that_oem_behavior_can_leave_source_visible() {
        val summary = builder.build(
            suppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
            replacementNotificationIssued = true,
        )

        assertEquals("원본 숨김 시도됨", summary.statusLabel)
        assertEquals("표시됨", summary.replacementLabel)
        assertEquals(
            "SmartNoti가 원본 알림 숨김을 시도했고 대체 알림도 표시했어요. 기기나 앱 정책에 따라 원본은 계속 남아있을 수 있어요.",
            summary.overview,
        )
    }

    @Test
    fun app_not_selected_summary_explains_selection_gap() {
        val summary = builder.build(
            suppressionState = SourceNotificationSuppressionState.APP_NOT_SELECTED,
            replacementNotificationIssued = false,
        )

        assertEquals("원본 유지됨", summary.statusLabel)
        assertEquals("원본 유지로 생략됨", summary.replacementLabel)
        assertEquals(
            "원본 알림 숨기기는 켜져 있지만 이 앱은 숨길 앱으로 선택되지 않아 원본을 그대로 남겨뒀어요.",
            summary.overview,
        )
    }

    @Test
    fun cancel_attempt_without_replacement_marks_it_as_skipped_for_silent_handling() {
        val summary = builder.build(
            suppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
            replacementNotificationIssued = false,
        )

        assertEquals("원본 숨김 시도됨", summary.statusLabel)
        assertEquals("조용히 보관되어 알림 센터 표시 없음", summary.replacementLabel)
        assertEquals(
            "SmartNoti가 원본 알림 숨김을 시도했고 조용히 처리된 알림은 앱 안에서만 보관했어요.",
            summary.overview,
        )
    }

    @Test
    fun priority_kept_summary_explains_why_replacement_is_skipped() {
        val summary = builder.build(
            suppressionState = SourceNotificationSuppressionState.PRIORITY_KEPT,
            replacementNotificationIssued = false,
        )

        assertEquals("원본 유지됨", summary.statusLabel)
        assertEquals("원본 유지로 생략됨", summary.replacementLabel)
        assertEquals(
            "이 알림은 중요 알림으로 분류돼 원본을 그대로 유지했어요.",
            summary.overview,
        )
    }

    @Test
    fun not_configured_state_hides_detail_card_when_no_replacement_exists() {
        assertFalse(
            SourceNotificationSuppressionState.NOT_CONFIGURED.shouldShowDetailCard(
                replacementNotificationIssued = false,
            )
        )
        assertTrue(
            SourceNotificationSuppressionState.CANCEL_ATTEMPTED.shouldShowDetailCard(
                replacementNotificationIssued = true,
            )
        )
    }
}
