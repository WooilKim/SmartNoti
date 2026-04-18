package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEditorAppSuggestionBuilderTest {

    private val builder = RuleEditorAppSuggestionBuilder()

    @Test
    fun build_sorts_apps_by_capture_count_then_name_and_limits_to_top_six() {
        val result = builder.build(
            listOf(
                app("pkg.f", "플로우", 1, "방금"),
                app("pkg.b", "배민", 8, "1분 전"),
                app("pkg.a", "당근", 8, "방금"),
                app("pkg.c", "쿠팡", 5, "3분 전"),
                app("pkg.d", "토스", 4, "5분 전"),
                app("pkg.e", "슬랙", 3, "10분 전"),
                app("pkg.g", "지메일", 2, "15분 전"),
            )
        )

        assertEquals(listOf("당근", "배민", "쿠팡", "토스", "슬랙", "지메일"), result.map { it.appName })
    }

    @Test
    fun build_formats_operator_friendly_supporting_label() {
        val result = builder.build(
            listOf(app("pkg.coupang", "쿠팡", 5, "방금"))
        )

        assertEquals("알림 5건 · 방금", result.single().supportingLabel)
        assertEquals("pkg.coupang", result.single().packageName)
    }

    private fun app(
        packageName: String,
        appName: String,
        count: Long,
        lastSeenLabel: String,
    ) = CapturedAppSelectionItem(
        packageName = packageName,
        appName = appName,
        notificationCount = count,
        lastSeenLabel = lastSeenLabel,
    )
}
