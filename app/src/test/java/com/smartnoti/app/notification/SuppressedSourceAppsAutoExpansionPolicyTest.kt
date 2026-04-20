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
        )

        assertNull(expanded)
    }
}
