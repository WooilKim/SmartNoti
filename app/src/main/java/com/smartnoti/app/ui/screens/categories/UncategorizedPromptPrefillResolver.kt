package com.smartnoti.app.ui.screens.categories

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill

/**
 * Pure decision helper for plan
 * `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` Task 5.
 *
 * `CategoriesScreen` receives two optional nav args
 * (`prefillPackage` / `prefillLabel`) carried in by Home's
 * uncategorized-prompt card. On first composition the screen must:
 *
 *  1. Decide whether the args are present + non-blank enough to warrant an
 *     auto-open of the editor.
 *  2. If yes, construct the [CategoryEditorPrefill] and flip a
 *     `rememberSaveable` consumed flag so the auto-open never re-fires
 *     after the user dismisses or saves the dialog.
 *
 * Both decisions are non-Compose pure functions so they can be unit-tested
 * without `composeTestRule`. Compose-side wiring lives in
 * [CategoriesScreen]; that file owns the `rememberSaveable("uncategorized
 * PrefillConsumed")` state and the [androidx.compose.runtime.LaunchedEffect]
 * that triggers a single-shot consume.
 *
 * Truth table (encoded by [shouldAutoOpen]):
 *
 *  | prefillPackage | prefillLabel | consumed | result |
 *  |---|---|---|---|
 *  | non-blank | any | false | true (auto-open with prefill) |
 *  | blank/null | non-blank | false | false (label alone is meaningless) |
 *  | blank/null | blank/null | false | false (nothing to prefill) |
 *  | any | any | true | false (single-shot lock) |
 */
object UncategorizedPromptPrefillResolver {

    /**
     * Whether [CategoriesScreen] should auto-open the editor with prefill on
     * this composition tick. Returns `false` if the lock has already been
     * consumed or if the inputs cannot construct a useful prefill (label
     * alone has no effect — the editor needs the package to pin its app
     * dropdown).
     */
    fun shouldAutoOpen(
        prefillPackage: String?,
        @Suppress("UNUSED_PARAMETER") prefillLabel: String?,
        alreadyConsumed: Boolean,
    ): Boolean {
        if (alreadyConsumed) return false
        // Decision is package-driven: a stray label without a package can't
        // pin the editor's app dropdown, so we treat it as "no prefill".
        // [prefillLabel] is part of the signature so call sites read like
        // the truth-table comment above and so the JVM tests can pin the
        // contract without callers having to drop the arg.
        return !prefillPackage.isNullOrBlank()
    }

    /**
     * Build the [CategoryEditorPrefill] passed into [CategoryEditorScreen]
     * for the auto-open. `defaultAction = PRIORITY` is plan-fixed (see plan's
     * "Product intent / assumptions" — tap-on-prompt signals "I want to keep
     * an eye on this app", and DIGEST default would risk silent classification
     * the user did not opt into). `pendingRule = null` because Home's prompt
     * is an app-level signal rather than a per-notification one.
     *
     * Caller MUST gate this with [shouldAutoOpen] — calling with a blank
     * package returns a prefill with `appPackageName = null` which would
     * silently produce an empty editor.
     */
    fun buildPrefill(
        prefillPackage: String?,
        prefillLabel: String?,
    ): CategoryEditorPrefill {
        val pkg = prefillPackage?.trim()?.takeIf { it.isNotEmpty() }
        val label = prefillLabel?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
        return CategoryEditorPrefill(
            name = label,
            appPackageName = pkg,
            pendingRule = null,
            defaultAction = CategoryAction.PRIORITY,
            seedExistingRuleIds = emptyList(),
        )
    }
}
