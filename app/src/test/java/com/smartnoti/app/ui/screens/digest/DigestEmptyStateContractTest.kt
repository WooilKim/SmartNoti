package com.smartnoti.app.ui.screens.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract: the Digest 서브탭의 empty-state copy + inline CTA label live in a
 * single source of truth (`DigestEmptyStateAction`) so the suppress opt-in
 * 유도 카피와 "숨길 앱 선택하기" 버튼 라벨이 미래 PR 에서 silently 변경되거나
 * 두 진입 경로 (`InboxScreen` Digest 서브탭 + 레거시 `Routes.Digest` deep-link)
 * 사이에서 drift 하지 않게 한다.
 *
 * Plan: `docs/plans/2026-04-27-digest-empty-state-suppress-opt-in-cta.md`
 * Task 1.
 */
class DigestEmptyStateContractTest {

    @Test
    fun `title preserves the existing empty-state heading`() {
        assertEquals("아직 정리된 알림이 없어요", DigestEmptyStateAction.TITLE)
    }

    @Test
    fun `subtitle explains suppress opt-in semantics in one short line`() {
        assertEquals(
            "숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요",
            DigestEmptyStateAction.SUBTITLE,
        )
        assertTrue(
            "subtitle should fit under 50 chars to keep the empty-state quiet (was ${DigestEmptyStateAction.SUBTITLE.length})",
            DigestEmptyStateAction.SUBTITLE.length <= 50,
        )
    }

    @Test
    fun `CTA label matches Settings 의 숨길 앱 선택 어휘`() {
        assertEquals("숨길 앱 선택하기", DigestEmptyStateAction.CTA_LABEL)
    }
}
