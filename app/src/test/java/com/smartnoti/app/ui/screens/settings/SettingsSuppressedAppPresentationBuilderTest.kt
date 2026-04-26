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

    @Test
    fun summary_is_zeroed_when_no_apps_are_captured() {
        val presentation = builder.build(apps = emptyList())

        assertEquals("선택 0개 · 추가 가능 0개", presentation.summary)
        assertEquals(emptyList<SettingsSuppressedAppGroup>(), presentation.groups)
    }

    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 7:
    // smoke tests for the (capturedApps, suppressedSourceApps, excludedApps)
    // overload — pin the effective `isSelected` rule that bridges the
    // persisted sets to the row checkmark.

    @Test
    fun excluded_app_renders_as_unselected_when_suppressed_set_is_empty() {
        val presentation = builder.build(
            capturedApps = listOf(captured("토스"), captured("쿠팡")),
            suppressedSourceApps = emptySet(),
            excludedApps = setOf("pkg.쿠팡"),
        )

        // 쿠팡 was unchecked → excluded → must surface in "추가로 숨길 수 있는 앱".
        assertEquals(
            listOf(
                SettingsSuppressedAppGroup(
                    title = "이미 선택한 앱",
                    items = listOf(SettingsSuppressedAppItem("pkg.토스", "토스", 0, "방금", true)),
                ),
                SettingsSuppressedAppGroup(
                    title = "추가로 숨길 수 있는 앱",
                    items = listOf(SettingsSuppressedAppItem("pkg.쿠팡", "쿠팡", 0, "방금", false)),
                ),
            ),
            presentation.groups,
        )
    }

    @Test
    fun empty_excluded_and_empty_suppressed_marks_every_row_selected() {
        val presentation = builder.build(
            capturedApps = listOf(captured("토스"), captured("쿠팡")),
            suppressedSourceApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertEquals(
            listOf(
                SettingsSuppressedAppGroup(
                    title = "이미 선택한 앱",
                    items = listOf(
                        SettingsSuppressedAppItem("pkg.토스", "토스", 0, "방금", true),
                        SettingsSuppressedAppItem("pkg.쿠팡", "쿠팡", 0, "방금", true),
                    ),
                ),
            ),
            presentation.groups,
        )
    }

    @Test
    fun excluded_set_overrides_explicit_membership_in_suppressed_set() {
        // Conflict resolution per plan Risks Q3: excluded wins.
        val presentation = builder.build(
            capturedApps = listOf(captured("토스")),
            suppressedSourceApps = setOf("pkg.토스"),
            excludedApps = setOf("pkg.토스"),
        )

        assertEquals(
            listOf(
                SettingsSuppressedAppGroup(
                    title = "추가로 숨길 수 있는 앱",
                    items = listOf(SettingsSuppressedAppItem("pkg.토스", "토스", 0, "방금", false)),
                ),
            ),
            presentation.groups,
        )
    }

    @Test
    fun non_empty_suppressed_set_acts_as_whitelist() {
        val presentation = builder.build(
            capturedApps = listOf(captured("토스"), captured("쿠팡")),
            suppressedSourceApps = setOf("pkg.토스"),
            excludedApps = emptySet(),
        )

        assertEquals(
            listOf(
                SettingsSuppressedAppGroup(
                    title = "이미 선택한 앱",
                    items = listOf(SettingsSuppressedAppItem("pkg.토스", "토스", 0, "방금", true)),
                ),
                SettingsSuppressedAppGroup(
                    title = "추가로 숨길 수 있는 앱",
                    items = listOf(SettingsSuppressedAppItem("pkg.쿠팡", "쿠팡", 0, "방금", false)),
                ),
            ),
            presentation.groups,
        )
    }

    @Test
    fun is_effectively_selected_helper_matches_overload_rule() {
        // excluded wins
        assertEquals(
            false,
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "pkg.foo",
                suppressedSourceApps = setOf("pkg.foo"),
                excludedApps = setOf("pkg.foo"),
            ),
        )
        // empty + not excluded → selected (default opt-in)
        assertEquals(
            true,
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "pkg.foo",
                suppressedSourceApps = emptySet(),
                excludedApps = emptySet(),
            ),
        )
        // explicit list, not in list → unselected
        assertEquals(
            false,
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "pkg.foo",
                suppressedSourceApps = setOf("pkg.bar"),
                excludedApps = emptySet(),
            ),
        )
    }

    private fun app(name: String, count: Long, selected: Boolean) = RawSuppressedAppState(
        app = captured(name, count),
        isSelected = selected,
    )

    private fun captured(name: String, count: Long = 0L) = CapturedAppSelectionItem(
        packageName = "pkg.$name",
        appName = name,
        notificationCount = count,
        lastSeenLabel = "방금",
    )
}
