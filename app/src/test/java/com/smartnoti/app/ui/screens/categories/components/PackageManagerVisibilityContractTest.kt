package com.smartnoti.app.ui.screens.categories.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Manifest-contract guard for plan
 * `docs/plans/2026-04-25-android-queries-package-visibility.md` Task 1
 * (Option B — pure JVM fallback).
 *
 * This test does NOT exercise the real Android `PackageManager` — that is
 * covered end-to-end by the ADB recipe in the plan's Task 3 (run on emulator
 * `emulator-5554`). What this test pins is the `<queries>` manifest declaration
 * itself: without it, [PackageManagerAppLabelLookup] silently fails on Android
 * 11+ for every non-system caller package and chips fall back to the raw
 * packageName (e.g. `앱=com.android.youtube`). The declaration is a one-line
 * manifest edit but easy to drop accidentally, so we lock the contract here.
 *
 * If this test fails, do NOT delete it — restore the `<queries>` block per the
 * plan's Task 2 instead, then re-run the ADB recipe and confirm chip text.
 */
class PackageManagerVisibilityContractTest {

    @Test
    fun manifestDeclaresQueriesBlockWithMainActionForLauncherVisibility() {
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)

        val hasQueriesOpen = QUERIES_OPEN_REGEX.containsMatchIn(manifest)
        val hasQueriesClose = manifest.contains("</queries>")
        val hasMainAction = MAIN_ACTION_REGEX.containsMatchIn(manifest)

        assertTrue(
            "AndroidManifest.xml must declare a <queries> block (Android 11+ " +
                "package visibility) so PackageManagerAppLabelLookup can resolve " +
                "user-app labels. See docs/plans/2026-04-25-android-queries-" +
                "package-visibility.md.",
            hasQueriesOpen && hasQueriesClose,
        )
        assertTrue(
            "The <queries> block must declare an <intent> with " +
                "android.intent.action.MAIN so launcher-visible apps (e.g. " +
                "YouTube) become visible to PackageManager.getApplicationInfo. " +
                "See plan Task 2.",
            hasMainAction,
        )
    }

    private companion object {
        val QUERIES_OPEN_REGEX = Regex("""<queries(\s|>)""")
        val MAIN_ACTION_REGEX = Regex(
            """<action\s+android:name="android\.intent\.action\.MAIN"\s*/>""",
        )
    }
}
