package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.usecase.UncategorizedAppsDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` Task 6.
 *
 * The Home `HomeUncategorizedAppsPromptCard.onCreateCategory` callback fires
 * with the prompt itself; the HomeScreen wiring then extracts the **first**
 * uncovered sample (`samplePackageNames[0]` + `sampleAppLabels[0]`) and hands
 * those two strings to the navigation layer (which assembles
 * `Routes.Categories.create(...)`).
 *
 * [HomeUncategorizedPromptPrefillExtractor] is the pure function that does
 * that extraction. It must:
 *
 * - Pull index 0 from each list (newest-first per the detector contract).
 * - Defensively skip blank package or label strings — a blank package would
 *   make `Routes.Categories.create` collapse to bare `"categories"` anyway,
 *   so we surface that as an absent prefill so the caller can short-circuit
 *   if it wants to keep the auto-open contract honest.
 * - Tolerate empty / shorter sample lists without crashing.
 */
class HomeUncategorizedPromptPrefillExtractorTest {

    @Test
    fun `extracts first sample package and label from a typical prompt`() {
        val prompt = UncategorizedAppsDetection.Prompt(
            uncoveredCount = 5,
            sampleAppLabels = listOf("테스트앱A", "테스트앱B", "테스트앱C"),
            samplePackageNames = listOf("com.example.a", "com.example.b", "com.example.c"),
        )

        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(prompt)

        assertEquals("com.example.a", prefill?.packageName)
        assertEquals("테스트앱A", prefill?.label)
    }

    @Test
    fun `returns null when both sample lists are empty`() {
        val prompt = UncategorizedAppsDetection.Prompt(
            uncoveredCount = 0,
            sampleAppLabels = emptyList(),
            samplePackageNames = emptyList(),
        )

        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(prompt)

        assertNull(prefill)
    }

    @Test
    fun `returns null when the first package is blank`() {
        val prompt = UncategorizedAppsDetection.Prompt(
            uncoveredCount = 3,
            sampleAppLabels = listOf("라벨", "x", "y"),
            samplePackageNames = listOf("   ", "com.example.b", "com.example.c"),
        )

        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(prompt)

        assertNull(prefill)
    }

    @Test
    fun `falls back to null label when the first label is blank but package present`() {
        val prompt = UncategorizedAppsDetection.Prompt(
            uncoveredCount = 3,
            sampleAppLabels = listOf("   ", "라벨B", "라벨C"),
            samplePackageNames = listOf("com.example.a", "com.example.b", "com.example.c"),
        )

        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(prompt)

        assertEquals("com.example.a", prefill?.packageName)
        assertNull(prefill?.label)
    }

    @Test
    fun `tolerates a shorter label list than package list`() {
        val prompt = UncategorizedAppsDetection.Prompt(
            uncoveredCount = 3,
            sampleAppLabels = emptyList(),
            samplePackageNames = listOf("com.example.a"),
        )

        val prefill = HomeUncategorizedPromptPrefillExtractor.extract(prompt)

        assertEquals("com.example.a", prefill?.packageName)
        assertNull(prefill?.label)
    }
}
