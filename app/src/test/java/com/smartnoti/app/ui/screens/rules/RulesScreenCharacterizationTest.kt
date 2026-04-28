package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.UnassignedRulesPartitioner
import com.smartnoti.app.ui.components.RuleRowPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-refactor-rules-screen-split.md` Task 1 —
 * pin the user-visible affordances on RulesScreen before the per-section
 * file split runs in Tasks 2-3.
 *
 * The RulesScreen root composable is tightly coupled to four singleton
 * repositories (`RulesRepository.getInstance(...)` and friends) and runs
 * inside an `AlertDialog` + `ModalBottomSheet`, so unit-rendering it under
 * Robolectric would require setting up a real DataStore + a Compose UI
 * test host. That infra is out of scope for a refactor PR.
 *
 * Instead we pin the **pure helper functions and pure-state contracts**
 * that drive the affordances the plan calls out. The four helpers below
 * (`typeLabel`, `matchLabelFor`, `categoryActionLabel`, `supersetWarningMessage`,
 * `ruleRowPresentationFor`) all move to the new sub-files in Task 2/3 —
 * if their string outputs or branching logic changes during the carve-out
 * the test fails. Affordance copy that lives in already-extracted
 * composables ([RulesMultiSelectActionBar], [BulkRulesAssignBottomSheet])
 * is out of scope here because it is verified by the existing
 * `rules-management` ADB recipe (re-run last on 2026-04-27, recorded in the
 * journey doc Verification log).
 *
 * Sub-bucket conditional rendering (the "작업 필요" / "보류" headers gating
 * on `visibleActionNeededRules.isNotEmpty()`) is pinned by exercising the
 * partitioner contract directly — that is the only conditional logic
 * RulesScreen wraps around the `SectionLabel` calls.
 */
class RulesScreenCharacterizationTest {

    private val sampleActiveRule = RuleUiModel(
        id = "rule-active",
        title = "광고",
        subtitle = "키워드 · 광고",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "광고",
        overrideOf = null,
        draft = false,
    )

    private val sampleDraftRule = RuleUiModel(
        id = "rule-draft",
        title = "VTest",
        subtitle = "키워드 · vtestkw",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "vtestkw",
        overrideOf = null,
        draft = true,
    )

    private val sampleParkedRule = RuleUiModel(
        id = "rule-parked",
        title = "ParkedTest",
        subtitle = "키워드 · parkedkw",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "parkedkw",
        overrideOf = null,
        draft = false,
    )

    private val sampleCategories = listOf(
        Category(
            id = "cat-priority",
            name = "중요 알림",
            appPackageName = null,
            ruleIds = listOf("rule-active"),
            action = CategoryAction.PRIORITY,
            order = 0,
        ),
        Category(
            id = "cat-digest",
            name = "프로모션 알림",
            appPackageName = null,
            ruleIds = emptyList(),
            action = CategoryAction.DIGEST,
            order = 1,
        ),
    )

    // ---------- Sub-bucket conditional rendering --------------------------
    // Pins the inputs that drive RulesScreen's
    // `if (visibleActionNeededRules.isNotEmpty())` /
    // `if (visibleParkedRules.isNotEmpty())` gates around the
    // "작업 필요" / "보류" SectionLabel emissions.

    @Test
    fun unassignedPartitioner_emits_actionNeeded_and_parked_buckets_for_mixed_input() {
        val partitioner = UnassignedRulesPartitioner()

        val result = partitioner.partition(
            rules = listOf(sampleActiveRule, sampleDraftRule, sampleParkedRule),
            categories = sampleCategories,
        )

        // sampleActiveRule is claimed by cat-priority -> excluded.
        // sampleDraftRule (draft=true, unclaimed) -> "작업 필요"
        // sampleParkedRule (draft=false, unclaimed) -> "보류"
        assertEquals(listOf(sampleDraftRule), result.actionNeeded)
        assertEquals(listOf(sampleParkedRule), result.parked)
    }

    @Test
    fun unassignedPartitioner_returns_empty_buckets_when_all_rules_claimed() {
        val partitioner = UnassignedRulesPartitioner()
        val claimsAll = sampleCategories.first().copy(
            ruleIds = listOf(sampleDraftRule.id, sampleParkedRule.id, sampleActiveRule.id),
        )

        val result = partitioner.partition(
            rules = listOf(sampleActiveRule, sampleDraftRule, sampleParkedRule),
            categories = listOf(claimsAll, sampleCategories[1]),
        )

        // Both buckets empty -> RulesScreen suppresses both SectionLabel
        // headers (regression guard against the historical unconditional
        // "미분류" SectionLabel).
        assertTrue(result.actionNeeded.isEmpty())
        assertTrue(result.parked.isEmpty())
    }

    // ---------- typeLabel -------------------------------------------------
    // Plan Task 3 moves `typeLabel` to RuleEditorTypeSection.kt — pin every
    // case so the move can't drop or rename a label.

    @Test
    fun typeLabel_returns_korean_label_for_every_RuleTypeUi_value() {
        assertEquals("사람", typeLabel(RuleTypeUi.PERSON))
        assertEquals("앱", typeLabel(RuleTypeUi.APP))
        assertEquals("키워드", typeLabel(RuleTypeUi.KEYWORD))
        assertEquals("시간", typeLabel(RuleTypeUi.SCHEDULE))
        assertEquals("반복", typeLabel(RuleTypeUi.REPEAT_BUNDLE))
        // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
        // Task 6 — pin SENDER label so the dropdown render stays stable.
        assertEquals("발신자", typeLabel(RuleTypeUi.SENDER))
    }

    // ---------- matchLabelFor --------------------------------------------
    // Plan Task 3 moves `matchLabelFor` to RuleEditorTypeSection.kt — same
    // exhaustive pin.

    @Test
    fun matchLabelFor_returns_field_label_for_every_RuleTypeUi_value() {
        assertEquals("이름 또는 발신자", matchLabelFor(RuleTypeUi.PERSON))
        assertEquals("패키지명", matchLabelFor(RuleTypeUi.APP))
        assertEquals("키워드", matchLabelFor(RuleTypeUi.KEYWORD))
        assertEquals("시간 조건", matchLabelFor(RuleTypeUi.SCHEDULE))
        assertEquals("반복 기준", matchLabelFor(RuleTypeUi.REPEAT_BUNDLE))
        // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
        // Task 6 — pin SENDER match-value label so the dropdown / editor
        // sync stays consistent (label drives the OutlinedTextField label).
        assertEquals("발신자 이름", matchLabelFor(RuleTypeUi.SENDER))
    }

    // ---------- categoryActionLabel --------------------------------------
    // Plan Task 2 moves `categoryActionLabel` to RulesUnassignedSection.kt
    // (used by `CategoryAssignRow` for the meta line "규칙 N개 · {label}").
    // Pin every CategoryAction case.

    @Test
    fun categoryActionLabel_returns_korean_label_for_every_CategoryAction_value() {
        assertEquals("즉시 전달", categoryActionLabel(CategoryAction.PRIORITY))
        assertEquals("Digest", categoryActionLabel(CategoryAction.DIGEST))
        assertEquals("조용히", categoryActionLabel(CategoryAction.SILENT))
        assertEquals("무시", categoryActionLabel(CategoryAction.IGNORE))
    }

    // ---------- supersetWarningMessage -----------------------------------
    // Plan Task 3 moves `supersetWarningMessage` to RuleEditorOverrideSection.kt.
    // Pin every Reason variant so the override-editor warning copy stays
    // identical post-extraction.

    @Test
    fun supersetWarningMessage_returns_copy_for_every_validator_Reason() {
        assertEquals(
            "기준 규칙을 찾을 수 없어요. 삭제됐거나 아직 저장되지 않은 규칙일 수 있어요.",
            supersetWarningMessage(RuleOverrideSupersetValidator.Reason.BASE_MISSING),
        )
        assertEquals(
            "기준 규칙과 타입이 달라요. 같은 타입으로 맞추면 더 정확하게 동작해요.",
            supersetWarningMessage(RuleOverrideSupersetValidator.Reason.TYPE_MISMATCH),
        )
        assertEquals(
            "기준 규칙의 키워드를 모두 포함해야 예외가 정상적으로 동작해요.",
            supersetWarningMessage(RuleOverrideSupersetValidator.Reason.KEYWORD_NOT_SUPERSET),
        )
        assertEquals(
            "기준 규칙과 조건 값이 달라서 예외가 적용되지 않을 수 있어요.",
            supersetWarningMessage(RuleOverrideSupersetValidator.Reason.VALUE_MISMATCH),
        )
    }

    // ---------- ruleRowPresentationFor -----------------------------------
    // Plan Task 3 moves `ruleRowPresentationFor` to RuleEditorOverrideSection.kt.
    // It branches on broken vs override vs base — pin every branch.

    @Test
    fun ruleRowPresentationFor_returns_Base_for_plain_base_node() {
        val node = RuleListNode(
            rule = sampleActiveRule,
            overrideState = RuleOverrideState.Base,
        )

        val presentation = ruleRowPresentationFor(node = node, baseTitle = null)

        assertEquals(RuleRowPresentation.Base, presentation)
    }

    @Test
    fun ruleRowPresentationFor_returns_Override_with_baseTitle_passthrough() {
        val node = RuleListNode(
            rule = sampleDraftRule.copy(overrideOf = sampleActiveRule.id),
            overrideState = RuleOverrideState.Override(baseRuleId = sampleActiveRule.id),
        )

        val presentation = ruleRowPresentationFor(node = node, baseTitle = "광고")

        assertEquals(RuleRowPresentation.Override(baseTitle = "광고"), presentation)
    }

    @Test
    fun ruleRowPresentationFor_returns_BrokenOverride_for_self_reference() {
        val node = RuleListNode(
            rule = sampleDraftRule,
            overrideState = RuleOverrideState.Override(baseRuleId = sampleDraftRule.id),
            brokenReason = RuleOverrideBrokenReason.SelfReference,
        )

        val presentation = ruleRowPresentationFor(node = node, baseTitle = null)

        assertEquals(
            RuleRowPresentation.BrokenOverride(
                reasonMessage = "이 규칙은 자기 자신을 예외로 지정해 동작하지 않아요.",
            ),
            presentation,
        )
    }

    @Test
    fun ruleRowPresentationFor_returns_BrokenOverride_for_missing_base() {
        val node = RuleListNode(
            rule = sampleDraftRule,
            overrideState = RuleOverrideState.Override(baseRuleId = "missing"),
            brokenReason = RuleOverrideBrokenReason.BaseMissing(baseRuleId = "missing"),
        )

        val presentation = ruleRowPresentationFor(node = node, baseTitle = null)

        assertEquals(
            RuleRowPresentation.BrokenOverride(
                reasonMessage = "기준이 되는 규칙이 삭제돼 예외가 동작하지 않아요.",
            ),
            presentation,
        )
    }

    @Test
    fun ruleRowPresentationFor_returns_BrokenOverride_when_base_is_itself_an_override() {
        val node = RuleListNode(
            rule = sampleDraftRule,
            overrideState = RuleOverrideState.Override(baseRuleId = sampleParkedRule.id),
            brokenReason = RuleOverrideBrokenReason.BaseIsOverride(baseRuleId = sampleParkedRule.id),
        )

        val presentation = ruleRowPresentationFor(node = node, baseTitle = null)

        assertEquals(
            RuleRowPresentation.BrokenOverride(
                reasonMessage = "다른 예외의 예외로 지정돼 있어 동작하지 않아요.",
            ),
            presentation,
        )
    }

    // ---------- RuleRowPresentation.Unassigned shape ---------------------
    // Plan Task 2 moves the `UnassignedRuleRowSlot` composable; it always
    // builds `RuleRowPresentation.Unassigned(isParked = isParked)`. This
    // pins the Unassigned data class shape so the presentation contract
    // stays identical for parked vs action-needed rows.

    @Test
    fun ruleRowPresentation_Unassigned_distinguishes_parked_and_action_needed_variants() {
        val actionNeeded = RuleRowPresentation.Unassigned(isParked = false)
        val parked = RuleRowPresentation.Unassigned(isParked = true)

        assertEquals(false, actionNeeded.isParked)
        assertEquals(true, parked.isParked)
        assertTrue(actionNeeded != parked)
    }
}

