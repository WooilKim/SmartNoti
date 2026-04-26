package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin the [Routes.Categories.create] nav arg encoding contract — Home's
 * uncategorized-prompt card hands the first uncovered app's package + label
 * through these two query args so the Categories tab can auto-open the
 * editor with prefill (plan
 * `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` Task 1).
 *
 * Encoding mirrors [Routes.Hidden.create] — UTF-8 percent encoding with
 * `+` rewritten to `%20` so a space inside a Korean app label survives the
 * round-trip without turning into a literal `+`.
 */
class RoutesCategoriesPrefillTest {

    @Test
    fun create_without_args_yields_bare_route() {
        // Existing call sites (BottomNavItem, AppNavHost.popUpTo) rely on the
        // bare `"categories"` form — query-arg variant must remain optional so
        // the pattern still matches when no prefill is supplied.
        assertEquals("categories", Routes.Categories.create())
    }

    @Test
    fun create_with_package_and_label_encodes_both_query_params() {
        assertEquals(
            "categories?prefillPackage=com.example.app&prefillLabel=%EC%98%88%EC%8B%9C%20%EC%95%B1",
            Routes.Categories.create(
                prefillPackage = "com.example.app",
                prefillLabel = "예시 앱",
            ),
        )
    }

    @Test
    fun create_with_blank_package_drops_prefill_entirely() {
        // Label alone is meaningless for prefill — editor needs the package to
        // pin the app dropdown. Treat blank package as "no prefill at all" so
        // a stray label can't smuggle through.
        assertEquals(
            "categories",
            Routes.Categories.create(prefillPackage = "  ", prefillLabel = "라벨"),
        )
    }

    @Test
    fun create_with_package_only_omits_label_query() {
        // Label is allowed to be absent — editor will leave the name field
        // empty but the app dropdown still gets pre-selected.
        assertEquals(
            "categories?prefillPackage=com.example.app",
            Routes.Categories.create(prefillPackage = "com.example.app", prefillLabel = ""),
        )
    }
}
