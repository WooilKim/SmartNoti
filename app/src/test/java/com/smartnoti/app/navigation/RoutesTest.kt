package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun insight_create_for_app_encodes_argument() {
        assertEquals(
            "insight/app/%EC%BF%A0%ED%8C%A1",
            Routes.Insight.createForApp("쿠팡"),
        )
    }

    @Test
    fun insight_create_for_reason_encodes_argument() {
        assertEquals(
            "insight/reason/%EC%87%BC%ED%95%91%20%EC%95%B1",
            Routes.Insight.createForReason("쇼핑 앱"),
        )
    }

    @Test
    fun insight_create_for_reason_with_range_encodes_argument_and_query() {
        assertEquals(
            "insight/reason/%EC%87%BC%ED%95%91%20%EC%95%B1?range=recent_3_hours",
            Routes.Insight.createForReason("쇼핑 앱", "recent_3_hours"),
        )
    }

    @Test
    fun insight_create_for_app_with_source_appends_source_query() {
        assertEquals(
            "insight/app/%EC%BF%A0%ED%8C%A1?source=suppression",
            Routes.Insight.createForApp("쿠팡", source = "suppression"),
        )
    }

    @Test
    fun insight_create_for_app_with_range_and_source_appends_both_queries() {
        assertEquals(
            "insight/app/%EC%BF%A0%ED%8C%A1?range=recent_24_hours&source=suppression",
            Routes.Insight.createForApp("쿠팡", range = "recent_24_hours", source = "suppression"),
        )
    }
}
