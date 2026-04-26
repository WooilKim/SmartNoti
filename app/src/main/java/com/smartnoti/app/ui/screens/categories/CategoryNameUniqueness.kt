package com.smartnoti.app.ui.screens.categories

/**
 * Plan `docs/plans/2026-04-25-category-name-uniqueness.md` Task 2.
 *
 * Pure rule for the Category editor's name field: the user-facing name
 * should feel meaningfully unique, even though the persistence layer keys
 * Categories by `id`. Comparison normalises whitespace (`trim`) and is
 * case-insensitive so visual variants like " 프로모션 " / "Work" / "work"
 * collapse onto the same name.
 *
 * The row identified by [currentCategoryId] is excluded from the
 * collision pool so the Edit flow does not flag a Category against
 * itself when the user keeps the existing name and edits other fields.
 * Path B (Detail "새 분류 만들기") passes `currentCategoryId = null` —
 * that flow compares against every persisted Category, by design.
 *
 * Surface: [CategoryEditorScreen] feeds the result to its name field's
 * `isError` + `supportingText` slots and gates the save button so the
 * user can't get past the editor with a duplicate name.
 */
enum class CategoryNameStatus { OK, EMPTY, DUPLICATE }

object CategoryNameUniqueness {
    fun evaluate(
        candidate: String,
        currentCategoryId: String?,
        existing: List<Pair<String, String>>,
    ): CategoryNameStatus {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return CategoryNameStatus.EMPTY
        val collision = existing.any { (id, name) ->
            id != currentCategoryId && name.trim().equals(trimmed, ignoreCase = true)
        }
        return if (collision) CategoryNameStatus.DUPLICATE else CategoryNameStatus.OK
    }
}
