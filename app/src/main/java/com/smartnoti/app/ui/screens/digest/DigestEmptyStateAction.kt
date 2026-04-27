package com.smartnoti.app.ui.screens.digest

/**
 * Single source of truth for the user-facing copy on the Digest 서브탭's
 * empty-state surface. Used by [DigestScreen] when `groups.isEmpty()` to
 * render the new suppress-opt-in 유도 카피 + inline "숨길 앱 선택하기"
 * CTA wired through [com.smartnoti.app.ui.components.EmptyState]'s `action`
 * slot.
 *
 * The Digest 서브탭 is reused by two entry points (the InboxScreen Digest
 * sub-tab body + the legacy `Routes.Digest` replacement-notification
 * deep-link target). Pinning all three strings here prevents the two call
 * sites — and any future preview / test surfaces — from silently drifting
 * apart when copy is later adjusted.
 *
 * The CTA label intentionally mirrors the Settings vocabulary "숨길 앱 선택"
 * so users who arrive at Settings via the button immediately recognise the
 * destination.
 *
 * Plan: `docs/plans/2026-04-27-digest-empty-state-suppress-opt-in-cta.md`.
 */
object DigestEmptyStateAction {
    const val TITLE = "아직 정리된 알림이 없어요"
    const val SUBTITLE = "숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요"
    const val CTA_LABEL = "숨길 앱 선택하기"
}
