package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSuppressedAppPresentationBuilderTest {

    private val builder = SettingsSuppressedAppPresentationBuilder()

    @Test
    fun groups_selected_apps_before_available_apps_and_sorts_each_group_by_count() {
        val presentation = builder.build(
            apps = listOf(
                app("토스", 3, selected = false),
                app("쿠팡", 8, selected = true),
                app("슬랙", 1, selected = false),
                app("카카오톡", 5, selected = true),
            ),
        )

        assertEquals(
            listOf(
                SettingsSuppressedAppGroup(
                    title = "이미 선택한 앱",
                    items = listOf(
                        SettingsSuppressedAppItem("pkg.쿠팡", "쿠팡", 8, "방금", true),
                        SettingsSuppressedAppItem("pkg.카카오톡", "카카오톡", 5, "방금", true),
                    ),
                ),
                SettingsSuppressedAppGroup(
                    title = "추가로 숨길 수 있는 앱",
                    items = listOf(
                        SettingsSuppressedAppItem("pkg.토스", "토스", 3, "방금", false),
                        SettingsSuppressedAppItem("pkg.슬랙", "슬랙", 1, "방금", false),
                    ),
                ),
            ),
            presentation.groups,
        )
    }

    @Test
    fun summary_counts_selected_and_available_apps() {
        val presentation = builder.build(
            apps = listOf(
                app("쿠팡", 8, selected = true),
                app("토스", 3, selected = false),
                app("지메일", 2, selected = false),
            ),
        )

        assertEquals("선택 1개 · 추가 가능 2개", presentation.summary)
    }

    private fun app(name: String, count: Long, selected: Boolean) = RawSuppressedAppState(
        app = CapturedAppSelectionItem(
            packageName = "pkg.$name",
            appName = name,
            notificationCount = count,
            lastSeenLabel = "방금",
        ),
        isSelected = selected,
    )
}
