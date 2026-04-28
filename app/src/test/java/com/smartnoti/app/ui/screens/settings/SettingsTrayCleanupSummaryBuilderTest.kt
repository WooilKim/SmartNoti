package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.TrayOrphanCleanupRunner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Failing tests for plan
 * `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 3 — pure summary builder for the new "트레이 정리" Settings card.
 *
 * The builder maps `(PreviewResult, isListenerBound, appLabelLookup)` to
 * the single user-facing line the card renders under its header. The four
 * branches the plan calls out:
 *
 *   - listener not bound → "알림 권한 활성 후 다시 시도해 주세요"
 *   - candidateCount == 0 → "정리할 원본 알림이 없어요"
 *   - 1..3 packages       → "원본 알림 N건 정리 가능 (라벨1, 라벨2, 라벨3)"
 *   - >3 packages         → "원본 알림 N건 정리 가능 (라벨1, 라벨2, 라벨3 외 K개)"
 *
 * Labels are looked up via the plan's existing `AppLabelResolver` chain
 * (passed in as a `(packageName) -> String` lambda so this builder stays
 * pure and Android-free).
 *
 * RED signals (compile errors expected on `main`):
 *  - [SettingsTrayCleanupSummaryBuilder] — Task 3 introduces the builder
 *  - [TrayOrphanCleanupRunner.PreviewResult] — Task 2 introduces the data class
 */
class SettingsTrayCleanupSummaryBuilderTest {

    private val builder = SettingsTrayCleanupSummaryBuilder()

    @Test
    fun listener_not_bound_surfaces_permission_hint() {
        val summary = builder.build(
            preview = TrayOrphanCleanupRunner.PreviewResult(
                candidateCount = 7,
                candidatePackageNames = listOf("com.naver.android.search"),
            ),
            isListenerBound = false,
            appLabelLookup = { it },
        )

        assertEquals("알림 권한 활성 후 다시 시도해 주세요", summary)
    }

    @Test
    fun zero_candidates_surfaces_clean_state() {
        val summary = builder.build(
            preview = TrayOrphanCleanupRunner.PreviewResult(
                candidateCount = 0,
                candidatePackageNames = emptyList(),
            ),
            isListenerBound = true,
            appLabelLookup = { it },
        )

        assertEquals("정리할 원본 알림이 없어요", summary)
    }

    @Test
    fun three_packages_lists_each_label() {
        val labels = mapOf(
            "com.naver.android.search" to "네이버",
            "com.coupang.eats" to "쿠팡이츠",
            "com.kakao.talk" to "카카오톡",
        )
        val summary = builder.build(
            preview = TrayOrphanCleanupRunner.PreviewResult(
                candidateCount = 3,
                candidatePackageNames = listOf(
                    "com.naver.android.search",
                    "com.coupang.eats",
                    "com.kakao.talk",
                ),
            ),
            isListenerBound = true,
            appLabelLookup = { pkg -> labels[pkg] ?: pkg },
        )

        assertEquals("원본 알림 3건 정리 가능 (네이버, 쿠팡이츠, 카카오톡)", summary)
    }

    @Test
    fun more_than_three_packages_truncates_with_overflow_count() {
        val labels = mapOf(
            "com.naver.android.search" to "네이버",
            "com.coupang.eats" to "쿠팡이츠",
            "com.kakao.talk" to "카카오톡",
            "com.linkedin.android" to "LinkedIn",
        )
        val summary = builder.build(
            preview = TrayOrphanCleanupRunner.PreviewResult(
                candidateCount = 4,
                candidatePackageNames = listOf(
                    "com.naver.android.search",
                    "com.coupang.eats",
                    "com.kakao.talk",
                    "com.linkedin.android",
                ),
            ),
            isListenerBound = true,
            appLabelLookup = { pkg -> labels[pkg] ?: pkg },
        )

        assertEquals("원본 알림 4건 정리 가능 (네이버, 쿠팡이츠, 카카오톡 외 1개)", summary)
    }
}
