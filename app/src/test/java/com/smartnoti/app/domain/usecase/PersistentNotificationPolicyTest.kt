package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentNotificationPolicyTest {

    private val policy = PersistentNotificationPolicy()

    @Test
    fun treats_ongoing_notifications_as_persistent() {
        assertTrue(policy.shouldTreatAsPersistent(isOngoing = true, isClearable = false))
    }

    @Test
    fun treats_non_clearable_notifications_as_persistent() {
        assertTrue(policy.shouldTreatAsPersistent(isOngoing = false, isClearable = false))
    }

    @Test
    fun ignores_normal_clearable_notifications() {
        assertFalse(policy.shouldTreatAsPersistent(isOngoing = false, isClearable = true))
    }

    @Test
    fun keeps_call_related_persistent_notifications_visible() {
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.android.dialer",
                title = "통화 중",
                body = "00:31",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun keeps_recording_and_navigation_persistent_notifications_visible() {
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.google.android.apps.maps",
                title = "길안내 중",
                body = "다음 교차로에서 우회전",
                protectCriticalPersistentNotifications = true,
            )
        )
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.android.systemui",
                title = "화면 녹화 중",
                body = "탭하여 중지",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun allows_charging_notifications_to_be_hidden() {
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "android",
                title = "충전 중",
                body = "배터리 보호",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun disables_bypass_when_critical_persistent_protection_is_turned_off() {
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "com.android.dialer",
                title = "통화 중",
                body = "00:31",
                protectCriticalPersistentNotifications = false,
            )
        )
    }

    // --- Regression: substring false-positive cases (plan 2026-04-26-persistent-bypass-keyword-word-boundary) ---

    @Test
    fun does_not_bypass_when_phone_keyword_appears_inside_compound_word() {
        // "전화번호 변경 안내" — "전화" appears inside the compound noun "전화번호".
        // Old substring matcher would bypass; the word-boundary matcher must not.
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.marketing",
                title = "전화번호 변경 안내",
                body = "고객님의 전화번호가 변경되었습니다",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun does_not_bypass_when_recording_keyword_appears_inside_compound_word() {
        // "녹화본" — "녹화" is buried inside a compound noun describing a recorded artifact, not an active recording.
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.meeting",
                title = "오늘 회의 녹화본 업로드 완료",
                body = "공유 링크를 확인하세요",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun does_not_bypass_when_call_keyword_appears_inside_compound_word() {
        // "통화기록" — "통화" inside a compound noun. Direct following Hangul char must invalidate the match.
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.usage",
                title = "통화기록을 정리할 수 있어요",
                body = "이번 달 사용량 안내",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun does_not_bypass_when_navigation_keyword_appears_inside_compound_word() {
        // "내비게이션" — "내비" inside a compound noun (marketing copy for a nav app).
        // Body intentionally omits standalone "길안내" tokens to keep the assertion focused on
        // the title-side compound-word false-positive.
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.nav",
                title = "새로운 내비게이션 앱 출시",
                body = "실시간 교통 정보 제공",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun still_bypasses_standalone_english_keyword_documenting_known_limitation() {
        // Limitation: the word-boundary matcher cannot semantically separate marketing copy that
        // happens to use a single-token English keyword (e.g. "Maps") as a standalone word.
        // Tracked in plan 2026-04-26-persistent-bypass-keyword-word-boundary Risks for follow-up.
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.maps_promotion",
                title = "Maps Pro 출시 이벤트",
                body = "오늘만 50% 할인",
                protectCriticalPersistentNotifications = true,
            )
        )
    }

    @Test
    fun still_bypasses_multiword_english_keyword_documenting_known_limitation() {
        // Limitation: multi-word English keywords ("camera in use") are matched whole; the matcher
        // only checks word boundaries at the keyword's outer edges, not between tokens. A marketing
        // headline that embeds the exact phrase still bypasses. Tracked in plan Risks for follow-up.
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.example.blog",
                title = "Product update",
                body = "new camera in use feature launched today",
                protectCriticalPersistentNotifications = true,
            )
        )
    }
}
