package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightDrillDownCopyBuilderTest {

    private val builder = InsightDrillDownCopyBuilder()

    @Test
    fun builds_general_app_copy() {
        val copy = builder.build(
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            source = InsightDrillDownSource.GENERAL,
            range = InsightDrillDownRange.RECENT_24_HOURS,
            summary = InsightDrillDownSummary(
                totalCount = 3,
                topReasonTag = "쇼핑 앱",
                topReasons = listOf(HomeReasonInsight(tag = "쇼핑 앱", count = 2)),
            ),
        )

        assertEquals("쿠팡 인사이트", copy.title)
        assertEquals("쿠팡 알림 3건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.", copy.subtitle)
        assertEquals("최근 24시간 기준 쿠팡에서 정리된 알림 3건을 시간순으로 보여줘요.", copy.overview)
        assertEquals("이 앱에서 가장 많이 보인 이유는 '쇼핑 앱'이에요.", copy.topReasonText)
    }

    @Test
    fun builds_suppression_app_copy() {
        val copy = builder.build(
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            source = InsightDrillDownSource.SUPPRESSION,
            range = InsightDrillDownRange.RECENT_24_HOURS,
            summary = InsightDrillDownSummary(
                totalCount = 4,
                topReasonTag = "반복 알림",
                topReasons = listOf(HomeReasonInsight(tag = "반복 알림", count = 3)),
            ),
        )

        assertEquals("쿠팡 숨김 인사이트", copy.title)
        assertEquals("원본 알림을 숨긴 상태에서 쿠팡 알림 4건이 SmartNoti에 어떻게 남았는지 보여줘요.", copy.subtitle)
        assertEquals("최근 24시간 기준 원본 알림이 숨겨진 쿠팡 알림 4건을 모아봤어요.", copy.overview)
        assertEquals("이 앱에서 숨김 처리된 알림은 '반복 알림' 이유가 가장 많아요.", copy.topReasonText)
    }

    @Test
    fun builds_reason_copy_using_co_occurring_reason_when_available() {
        val copy = builder.build(
            filter = InsightDrillDownFilter.Reason(reasonTag = "쇼핑 앱"),
            source = InsightDrillDownSource.GENERAL,
            range = InsightDrillDownRange.RECENT_3_HOURS,
            summary = InsightDrillDownSummary(
                totalCount = 2,
                topReasonTag = "쇼핑 앱",
                topReasons = listOf(
                    HomeReasonInsight(tag = "쇼핑 앱", count = 2),
                    HomeReasonInsight(tag = "반복 알림", count = 1),
                ),
            ),
        )

        assertEquals("쇼핑 앱 이유", copy.title)
        assertEquals("'쇼핑 앱' 이유로 정리된 알림 2건을 모아봤어요.", copy.subtitle)
        assertEquals("최근 3시간 기준 '쇼핑 앱' 이유로 정리된 알림 2건을 시간순으로 보여줘요.", copy.overview)
        assertEquals("'쇼핑 앱'와 함께 많이 보인 이유는 '반복 알림'이에요.", copy.topReasonText)
    }

    @Test
    fun returns_null_top_reason_text_when_reason_has_no_secondary_signal() {
        val copy = builder.build(
            filter = InsightDrillDownFilter.Reason(reasonTag = "사용자 규칙"),
            source = InsightDrillDownSource.GENERAL,
            range = InsightDrillDownRange.ALL,
            summary = InsightDrillDownSummary(
                totalCount = 1,
                topReasonTag = "사용자 규칙",
                topReasons = listOf(HomeReasonInsight(tag = "사용자 규칙", count = 1)),
            ),
        )

        assertNull(copy.topReasonText)
    }

    @Test
    fun parses_unknown_source_as_general() {
        assertEquals(InsightDrillDownSource.GENERAL, InsightDrillDownSource.fromRouteValue("unknown"))
        assertEquals(InsightDrillDownSource.SUPPRESSION, InsightDrillDownSource.fromRouteValue("suppression"))
    }
}
