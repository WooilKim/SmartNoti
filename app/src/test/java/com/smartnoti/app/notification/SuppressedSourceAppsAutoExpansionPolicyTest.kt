package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuppressedSourceAppsAutoExpansionPolicyTest {

    @Test
    fun digest_on_unseen_app_with_global_toggle_on_expands_list() {
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.testshop",
            currentApps = setOf("com.coupang.mobile"),
            excludedApps = emptySet(),
        )

        assertEquals(setOf("com.coupang.mobile", "com.testshop"), expanded)
    }

    @Test
    fun digest_on_app_already_in_list_returns_null() {
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.coupang.mobile",
            currentApps = setOf("com.coupang.mobile"),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    @Test
    fun global_toggle_off_never_expands() {
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = false,
            packageName = "com.testshop",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    @Test
    fun silent_decision_does_not_expand() {
        // SILENT 은 이미 opt-in 과 무관하게 항상 cancel 되므로 리스트 확장 불필요
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.SILENT,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.testshop",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    @Test
    fun priority_decision_does_not_expand() {
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.PRIORITY,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.testshop",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    @Test
    fun digest_on_unseen_app_with_empty_current_apps_does_not_expand() {
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 2 / Risks Q1: with empty `currentApps`, the suppression
        // policy already treats every package as opted-in (opt-out
        // semantics), so adding a single app here would silently shrink
        // the suppression scope to an allow-list-of-one. Stay empty until
        // the user explicitly narrows from Settings.
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.testshop",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    @Test
    fun blank_package_is_ignored() {
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "   ",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertNull(expanded)
    }

    // --- Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 1 ---

    @Test
    fun digest_on_excluded_app_returns_null_even_when_currentApps_nonempty() {
        // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md`:
        // 사용자가 Settings 에서 명시적으로 uncheck 한 앱은 sticky 하게
        // auto-expansion 에서 제외된다.
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.foo",
            currentApps = setOf("com.bar"),
            excludedApps = setOf("com.foo"),
        )

        assertNull(expanded)
    }

    @Test
    fun digest_on_non_excluded_app_still_expands_when_other_apps_excluded() {
        // excludedApps 는 다른 앱의 auto-expansion 에 영향이 없어야 한다.
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.baz",
            currentApps = setOf("com.bar"),
            excludedApps = setOf("com.foo"),
        )

        assertEquals(setOf("com.bar", "com.baz"), expanded)
    }

    @Test
    fun silent_decision_with_excluded_still_returns_null() {
        // 기존 fast-fail (decision != DIGEST) 의 우선순위는 변하지 않는다.
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.SILENT,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.foo",
            currentApps = setOf("com.bar"),
            excludedApps = setOf("com.foo"),
        )

        assertNull(expanded)
    }
}
