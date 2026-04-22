package com.smartnoti.app.ui.screens.categories

/**
 * Save-gate for the Task 9 Category editor.
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 9 requires two validation rules:
 *  1. A Category name is mandatory.
 *  2. A Category must include at least one Rule id.
 *
 * The [appPackageName] and [action] fields are **not** validated here — the
 * UI already constrains them (picker is optional, action is a required
 * dropdown with a default). Kept as a pure class so the rule is unit-testable
 * without touching Compose.
 */
class CategoryEditorDraftValidator {
    fun canSave(name: String, selectedRuleIds: List<String>): Boolean {
        if (name.trim().isBlank()) return false
        if (selectedRuleIds.isEmpty()) return false
        return true
    }
}
