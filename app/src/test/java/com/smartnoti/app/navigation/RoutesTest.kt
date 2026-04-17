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
}
