package com.smartnoti.app.ui.screens.categories

/**
 * Save-gate for the Task 9 Category editor.
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 9 requires two validation rules:
 *  1. A Category name is mandatory.
 *  2. A Category must include at least one Rule id.
 *
 * Plan `docs/plans/2026-04-25-category-name-uniqueness.md` adds a third
 * gate via [nameAvailable]: the editor must keep [canSave] backwards
 * compatible (single existing call site + four existing tests) while
 * letting the Editor `&&` a uniqueness check on top. We picked option B
 * (separate predicate) over option A (extending [canSave]) so the
 * existing tests stay untouched and the uniqueness rule lives next to
 * its own pure helper [CategoryNameUniqueness].
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

    /**
     * `true` when [name] is either OK or EMPTY (EMPTY is already caught
     * by [canSave]; we deliberately do not double-fail it here so the
     * editor's name field only surfaces the DUPLICATE error and the
     * empty-name case stays implicit via the disabled save button).
     *
     * Pass [existing] as `(id, name)` pairs so the caller can forward
     * `categories.map { it.id to it.name }` from its current props
     * without wiring the full Category model into this validator.
     */
    fun nameAvailable(
        name: String,
        existing: List<Pair<String, String>>,
        currentCategoryId: String?,
    ): Boolean {
        return CategoryNameUniqueness.evaluate(
            candidate = name,
            currentCategoryId = currentCategoryId,
            existing = existing,
        ) != CategoryNameStatus.DUPLICATE
    }
}
