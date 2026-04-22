package com.smartnoti.app.ui.screens.categories

/**
 * Single source of truth for the user-facing label on the 분류 탭's primary
 * "create a new Category" action. Used by both the normal-state FAB in
 * [CategoriesScreen] and the empty-state inline CTA wired through
 * [com.smartnoti.app.ui.components.EmptyState]'s `action` slot.
 *
 * Keeping both entry points on one constant prevents the two call sites
 * from silently drifting when copy is later adjusted.
 *
 * Plan: `docs/plans/2026-04-22-categories-empty-state-inline-cta.md`.
 */
object CategoriesEmptyStateAction {
    const val LABEL = "새 분류 만들기"
}
