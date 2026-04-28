package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 7 — pin the SENDER one-tap CTA path that converts the currently-viewed
 * notification's title into a SENDER rule attached to a PRIORITY Category.
 *
 * The use case is a thin orchestration over two repository writes
 * (`upsertRule` + `appendRuleIdToCategory`) so it stays unit-testable in
 * isolation with in-memory fakes; the production wiring builds the [Ports]
 * lambda stack against `RulesRepository` + `CategoriesRepository` inside
 * `NotificationDetailScreen`.
 *
 * Five fixtures cover the destination-Category resolution ladder:
 *  1. Pre-existing `important_priority` Category present → that one is used.
 *  2. No `important_priority`, but other PRIORITY Categories → first PRIORITY
 *     Category by `order` is used.
 *  3. No PRIORITY Category at all → outcome reports `noPriorityCategory` so
 *     the caller can present a picker (per Plan §Task 7).
 *  4. Title is trimmed before the rule's `matchValue` is set.
 *  5. The deterministic rule id matches `RuleDraftFactory.create` — so a
 *     repeat tap on the same title is idempotent (same id → upsert no-op,
 *     append dedupe).
 */
class AcceptSenderSuggestionUseCaseTest {

    @Test
    fun usesImportantPriorityCategoryWhenAvailable() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        val outcome = useCase.accept(
            title = "김동대(Special Recon)",
            categories = listOf(
                category("important_priority", CategoryAction.PRIORITY, order = 5),
                category("other_priority", CategoryAction.PRIORITY, order = 0),
            ),
        )

        assertTrue(outcome is AcceptSenderSuggestionUseCase.Outcome.Attached)
        assertEquals(
            "important_priority",
            (outcome as AcceptSenderSuggestionUseCase.Outcome.Attached).categoryId,
        )
        assertEquals(1, ports.upsertedRules.size)
        assertEquals(RuleTypeUi.SENDER, ports.upsertedRules.single().type)
        assertEquals("김동대(Special Recon)", ports.upsertedRules.single().matchValue)
        assertEquals(
            "important_priority" to ports.upsertedRules.single().id,
            ports.appendedRuleIds.single(),
        )
    }

    @Test
    fun fallsBackToFirstPriorityCategoryByOrder() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        val outcome = useCase.accept(
            title = "김동대(Special Recon)",
            categories = listOf(
                category("digest_only", CategoryAction.DIGEST, order = 0),
                category("priority_late", CategoryAction.PRIORITY, order = 5),
                category("priority_early", CategoryAction.PRIORITY, order = 2),
            ),
        )

        assertTrue(outcome is AcceptSenderSuggestionUseCase.Outcome.Attached)
        assertEquals(
            "priority_early",
            (outcome as AcceptSenderSuggestionUseCase.Outcome.Attached).categoryId,
        )
    }

    @Test
    fun reportsNoPriorityCategoryWhenNoneExists() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        val outcome = useCase.accept(
            title = "김동대(Special Recon)",
            categories = listOf(
                category("digest", CategoryAction.DIGEST, order = 0),
                category("silent", CategoryAction.SILENT, order = 1),
            ),
        )

        assertEquals(AcceptSenderSuggestionUseCase.Outcome.NoPriorityCategory, outcome)
        // Per Plan §Task 7, the rule must not be persisted when there is no
        // destination — otherwise we leak a categoryless SENDER rule.
        assertTrue(ports.upsertedRules.isEmpty())
        assertTrue(ports.appendedRuleIds.isEmpty())
    }

    @Test
    fun trimsTitleBeforeBuildingMatchValue() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        useCase.accept(
            title = "  김동대(Special Recon)  ",
            categories = listOf(
                category("important_priority", CategoryAction.PRIORITY, order = 0),
            ),
        )

        assertEquals("김동대(Special Recon)", ports.upsertedRules.single().matchValue)
    }

    @Test
    fun rejectsBlankTitleWithoutWriting() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        val outcome = useCase.accept(
            title = "   ",
            categories = listOf(
                category("important_priority", CategoryAction.PRIORITY, order = 0),
            ),
        )

        assertEquals(AcceptSenderSuggestionUseCase.Outcome.BlankTitle, outcome)
        assertTrue(ports.upsertedRules.isEmpty())
        assertTrue(ports.appendedRuleIds.isEmpty())
    }

    @Test
    fun deterministicRuleIdEnablesRepeatTapIdempotency() = runTest {
        val ports = RecordingPorts()
        val useCase = AcceptSenderSuggestionUseCase(ports)

        useCase.accept(
            title = "김동대(Special Recon)",
            categories = listOf(
                category("important_priority", CategoryAction.PRIORITY, order = 0),
            ),
        )
        useCase.accept(
            title = "김동대(Special Recon)",
            categories = listOf(
                category("important_priority", CategoryAction.PRIORITY, order = 0),
            ),
        )

        assertEquals(2, ports.upsertedRules.size)
        // Both upserts produce the same id — the production
        // RulesRepository.upsertRule then dedupes by id, and
        // CategoriesRepository.appendRuleIdToCategory dedupes ruleIds.
        assertEquals(ports.upsertedRules[0].id, ports.upsertedRules[1].id)
    }

    private fun category(
        id: String,
        action: CategoryAction,
        order: Int,
    ): Category = Category(
        id = id,
        name = id,
        appPackageName = null,
        ruleIds = emptyList(),
        action = action,
        order = order,
    )

    /** Captures every write so assertions can pin both side effects. */
    private class RecordingPorts : AcceptSenderSuggestionUseCase.Ports {
        val upsertedRules: MutableList<RuleUiModel> = mutableListOf()
        val appendedRuleIds: MutableList<Pair<String, String>> = mutableListOf()

        override suspend fun upsertRule(rule: RuleUiModel) {
            upsertedRules += rule
        }

        override suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String) {
            appendedRuleIds += categoryId to ruleId
        }
    }
}
