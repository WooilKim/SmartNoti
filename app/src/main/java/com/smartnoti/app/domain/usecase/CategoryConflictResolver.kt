package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category

/**
 * Picks the winning [Category] from a set of matches, honoring the tie-break
 * contract pinned by
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 1
 * (`CategoryTieBreakTest`) and fleshed out by Phase P2 Task 6:
 *
 *  1. `Category.appPackageName != null` ("app-pinned") beats a Category with
 *     no app pin. This is the only specificity signal surfaced at Task 2;
 *     Task 6 will extend it with per-Rule specificity (keyword > sender >
 *     time) once the classifier hot path has been rewired.
 *  2. Among Categories of equal specificity, the one with the lowest `order`
 *     wins — i.e. whichever Category the user dragged nearer the top of the
 *     분류 tab. The user explicitly chose this over any hard-coded action
 *     precedence so IGNORE vs SILENT conflicts defer to the drag order, too.
 *  3. If nothing matched, the resolver returns `null`.
 *
 * The `allCategories` parameter is accepted now so the Phase P2 extension
 * (which will need access to the full Category set to look up per-Rule
 * specificity against non-matching Categories) can slot in without churning
 * the callsite signature again.
 */
class CategoryConflictResolver {

    fun resolve(
        matched: List<Category>,
        @Suppress("UNUSED_PARAMETER") allCategories: List<Category>,
    ): Category? {
        if (matched.isEmpty()) return null

        return matched
            .asSequence()
            .sortedWith(
                compareByDescending<Category> { if (it.appPackageName != null) 1 else 0 }
                    .thenBy { it.order },
            )
            .first()
    }
}
