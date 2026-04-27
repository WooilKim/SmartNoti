package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.domain.model.InboxSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-inbox-sort-by-priority-or-app.md` Task 4.
 *
 * Pins the `InboxSortDropdown` mode-to-label contract so a future PR cannot
 * silently rename an option (or skip one when adding a new enum case) — the
 * dropdown's three visible labels are user-facing copy that the journey
 * `inbox-unified.md` Observable steps cite verbatim.
 *
 * Compose UI tests are intentionally avoided: the project has historically
 * preferred pure JVM contract tests (`DigestGroupCardSeeAllTest`,
 * `RuleRowDescriptionBuilder`-style) over instrumented compose tests. The
 * label helper lives outside the Composable specifically to support this.
 */
class InboxSortDropdownLabelTest {

    @Test
    fun labelFor_recent_is_최신순() {
        assertEquals("최신순", labelFor(InboxSortMode.RECENT))
    }

    @Test
    fun labelFor_importance_is_중요도순() {
        assertEquals("중요도순", labelFor(InboxSortMode.IMPORTANCE))
    }

    @Test
    fun labelFor_byApp_is_앱별_묶기() {
        assertEquals("앱별 묶기", labelFor(InboxSortMode.BY_APP))
    }

    @Test
    fun labelFor_returns_a_distinct_label_per_mode() {
        // Defense-in-depth: even if the per-mode tests were collectively
        // mass-renamed in a single bad PR, this guards against the entire
        // dropdown collapsing onto a single label by accident.
        val labels = InboxSortMode.values().map { labelFor(it) }
        assertEquals(InboxSortMode.values().size, labels.toSet().size)
        // And none should be empty.
        labels.forEach { label -> assertNotEquals("", label) }
    }
}
