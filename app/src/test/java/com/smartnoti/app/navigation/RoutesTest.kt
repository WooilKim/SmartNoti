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

    @Test
    fun hidden_route_declares_optional_sender_and_package_query_params() {
        // AppNavHost registers the composable against this pattern. If the pattern changes,
        // NavHost param wiring must follow — pinning it here prevents silent drift.
        assertEquals(
            "hidden?sender={sender}&packageName={packageName}",
            Routes.Hidden.route,
        )
    }

    @Test
    fun hidden_create_without_filter_yields_bare_route() {
        assertEquals("hidden", Routes.Hidden.create())
    }

    @Test
    fun hidden_create_with_sender_encodes_sender_query() {
        assertEquals(
            "hidden?sender=%EC%97%84%EB%A7%88",
            Routes.Hidden.create(sender = "엄마"),
        )
    }

    @Test
    fun hidden_create_with_package_name_encodes_package_query() {
        assertEquals(
            "hidden?packageName=com.kakao.talk",
            Routes.Hidden.create(packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun hidden_create_prefers_sender_when_both_supplied() {
        // Product contract: a group key is either Sender or App, never both — so when the
        // caller accidentally supplies both, Sender wins (matches SilentGroupKey precedence).
        assertEquals(
            "hidden?sender=%EC%97%84%EB%A7%88",
            Routes.Hidden.create(sender = "엄마", packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun hidden_create_blank_sender_falls_back_to_package_filter() {
        assertEquals(
            "hidden?packageName=com.kakao.talk",
            Routes.Hidden.create(sender = "   ", packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun hidden_create_blank_sender_and_blank_package_yields_bare_route() {
        assertEquals("hidden", Routes.Hidden.create(sender = "   ", packageName = "  "))
    }
}
