package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.usecase.UncategorizedAppsDetection

/**
 * Plan `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` Task 6.
 *
 * Pure helper that extracts the **first** uncovered app sample from an
 * [UncategorizedAppsDetection.Prompt] for the Home prompt-card → Categories
 * editor auto-open flow.
 *
 * Why a separate object instead of inlining the two `firstOrNull()` calls in
 * the HomeScreen lambda:
 *
 * - Keeps the wiring testable on JVM without spinning up Compose.
 * - Centralizes the "blank package == no prefill" rule so HomeScreen and the
 *   future deep-link / launcher-shortcut entry points stay in sync.
 * - Documents the contract that `samplePackageNames[0]` and `sampleAppLabels[0]`
 *   pair up positionally (the detector populates both lists from the same
 *   `top` slice in newest-first order).
 *
 * The helper does NOT call into navigation — the caller assembles
 * `Routes.Categories.create(prefill.packageName, prefill.label)` itself.
 */
internal object HomeUncategorizedPromptPrefillExtractor {

    fun extract(prompt: UncategorizedAppsDetection.Prompt): Prefill? {
        val pkg = prompt.samplePackageNames.firstOrNull()?.trim().orEmpty()
        if (pkg.isEmpty()) return null
        val label = prompt.sampleAppLabels.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return Prefill(packageName = pkg, label = label)
    }

    /**
     * Resolved prefill payload. `label` may be `null` when the detector did
     * not surface a usable app label — `Routes.Categories.create` accepts a
     * null label and the editor will leave the name field empty for the user
     * to fill in.
     */
    data class Prefill(
        val packageName: String,
        val label: String?,
    )
}
