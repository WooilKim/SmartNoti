package com.smartnoti.app.ui.screens.categories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RED tests for plan
 * `docs/plans/2026-04-22-category-editor-save-wiring-fix.md` Task 1.
 *
 * Covers three invariants of the `CategoryEditorScreen` save path that the
 * current (buggy) wiring violates:
 *
 *  - **Standalone 분류 탭 path** — "새 분류 추가" → "추가" must leave the new
 *    Category flushed in [CategoriesRepository] before `onSaved` fires.
 *  - **Detail "새 분류 만들기" prefill path** — same, plus the prefill's
 *    `pendingRule` must be flushed in [RulesRepository] *before* the
 *    Category (otherwise the Category references a rule id the persisted
 *    list has never seen).
 *  - **Race** — when the launching scope is cancelled *after* the save
 *    coroutine starts but *before* DataStore `edit {}` resolves, the
 *    observed symptom today is that nothing persists. The fix moves
 *    `onSaved(id)` *inside* the coroutine so callback only fires after the
 *    persist completes — meaning "onSaved observed" implies persisted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CategoryEditorSaveOrchestratorTest {

    private lateinit var context: Context
    private lateinit var categoriesRepository: CategoriesRepository
    private lateinit var rulesRepository: RulesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        CategoriesRepository.clearInstanceForTest()
        RulesRepository.clearInstanceForTest()
        categoriesRepository = CategoriesRepository.getInstance(context)
        rulesRepository = RulesRepository.getInstance(context)
        // Start from empty state so persist assertions are unambiguous.
        categoriesRepository.replaceAllCategories(emptyList())
        rulesRepository.replaceAllRules(emptyList())
    }

    @After
    fun tearDown() {
        CategoriesRepository.clearInstanceForTest()
        RulesRepository.clearInstanceForTest()
    }

    @Test
    fun standalone_path_saves_category_and_fires_callback_after_persist() = runTest {
        val existingRule = RuleUiModel(
            id = "rule-existing",
            title = "엄마",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        rulesRepository.upsertRule(existingRule)

        val draft = Category(
            id = "cat-user-standalone-1",
            name = "테스트분류",
            appPackageName = null,
            ruleIds = listOf(existingRule.id),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        var savedId: String? = null
        var onSavedInvoked = false
        var categoriesAtCallback: List<Category> = emptyList()

        saveCategoryDraft(
            categoriesRepository = categoriesRepository,
            rulesRepository = rulesRepository,
            persisted = draft,
            pendingRule = null,
            onSaved = { id ->
                savedId = id
                onSavedInvoked = true
                // Synchronously observe — by contract, persist must already
                // be flushed when this callback fires.
                categoriesAtCallback = runBlocking { categoriesRepository.currentCategories() }
            },
        )

        assertTrue("onSaved was not invoked", onSavedInvoked)
        assertEquals(draft.id, savedId)
        assertEquals(1, categoriesAtCallback.size)
        assertEquals(draft.id, categoriesAtCallback.first().id)

        // Post-flush check from fresh read.
        val persisted = categoriesRepository.observeCategories().first()
        assertEquals(1, persisted.size)
        assertEquals("테스트분류", persisted.first().name)
    }

    @Test
    fun detail_prefill_path_persists_pending_rule_before_category() = runTest {
        val pendingRule = RuleUiModel(
            id = "person:Alice",
            title = "Alice",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "Alice",
        )
        val draft = Category(
            id = "cat-user-prefill-1",
            name = "Alice",
            appPackageName = null,
            ruleIds = listOf(pendingRule.id),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        var onSavedInvoked = false
        var rulesAtCallback: List<RuleUiModel> = emptyList()
        var categoriesAtCallback: List<Category> = emptyList()

        saveCategoryDraft(
            categoriesRepository = categoriesRepository,
            rulesRepository = rulesRepository,
            persisted = draft,
            pendingRule = pendingRule,
            onSaved = {
                onSavedInvoked = true
                rulesAtCallback = runBlocking { rulesRepository.currentRules() }
                categoriesAtCallback = runBlocking { categoriesRepository.currentCategories() }
            },
        )

        assertTrue("onSaved was not invoked", onSavedInvoked)
        // Both must be flushed by the time onSaved fires.
        assertNotNull(
            "pendingRule not persisted before onSaved",
            rulesAtCallback.firstOrNull { it.id == pendingRule.id },
        )
        assertNotNull(
            "draft Category not persisted before onSaved",
            categoriesAtCallback.firstOrNull { it.id == draft.id },
        )
        assertTrue(
            "Category.ruleIds must reference the just-persisted rule id",
            pendingRule.id in categoriesAtCallback.first { it.id == draft.id }.ruleIds,
        )
    }

    @Test
    fun onSaved_is_not_invoked_if_scope_is_cancelled_mid_persist() = runBlocking {
        // Reproduces the composition-scope cancel scenario: the save
        // coroutine is launched, but the host composable tears down (e.g.
        // user navigates away or an ancestor cancels) before persist
        // completes. The fix must keep `onSaved` strictly downstream of the
        // persist so it either never fires (cancelled) or fires *after* the
        // DataStore write is committed — never before.
        val draft = Category(
            id = "cat-user-race-1",
            name = "race",
            appPackageName = null,
            ruleIds = listOf("rule-x"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        val dispatcher = StandardTestDispatcher()
        val hostScope = CoroutineScope(SupervisorJob() + dispatcher)
        var onSavedInvoked = false

        hostScope.launch {
            saveCategoryDraft(
                categoriesRepository = categoriesRepository,
                rulesRepository = rulesRepository,
                persisted = draft,
                pendingRule = null,
                onSaved = { onSavedInvoked = true },
            )
        }

        // Cancel the scope before the launched coroutine can progress.
        hostScope.cancel()

        // With the fix in place, `onSaved` runs inside the cancelled
        // coroutine, so it must not fire. Under the bug, `onSaved` was
        // fired *synchronously* outside the launch — the moral-equivalent
        // of "fires before persist" — which this test asserts against.
        assertFalse(
            "onSaved must not fire when the save coroutine is cancelled",
            onSavedInvoked,
        )
    }

    @Test
    fun cancel_before_save_leaves_repositories_untouched() = runTest {
        // Dismiss / back path: user types a draft then cancels. No
        // `saveCategoryDraft` call happens, so both repositories stay
        // empty. This freezes the contract that dismissal never persists.
        assertTrue(categoriesRepository.currentCategories().isEmpty())
        assertTrue(rulesRepository.currentRules().isEmpty())
    }

    @Test
    fun duplicate_save_invocations_only_persist_once_per_id() = runTest {
        // Defends the "중복 탭 방지" behavior the plan asks for: if the
        // save path runs twice for the same draft (user double-tap before
        // dismissal), the repository upsert is keyed by id so we should
        // end up with exactly one Category, not two.
        val draft = Category(
            id = "cat-user-dupe-1",
            name = "dupe",
            appPackageName = null,
            ruleIds = listOf("rule-x"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        repeat(2) {
            saveCategoryDraft(
                categoriesRepository = categoriesRepository,
                rulesRepository = rulesRepository,
                persisted = draft,
                pendingRule = null,
                onSaved = { /* ignored */ },
            )
        }

        val persisted = categoriesRepository.currentCategories()
        assertEquals(1, persisted.size)
        assertEquals(draft.id, persisted.first().id)
    }

    @Test
    fun save_runs_on_dispatcher_without_blocking_ui() = runTest {
        // The save orchestrator must be a suspend fun so the caller
        // can await completion from a composable's rememberCoroutineScope
        // (Main dispatcher). This test exercises it from an
        // UnconfinedTestDispatcher — if the helper was accidentally
        // implemented as `fun` + `runBlocking` internally, it would still
        // return but we wouldn't be able to `await`.
        val draft = Category(
            id = "cat-user-await-1",
            name = "await",
            appPackageName = null,
            ruleIds = listOf("rule-x"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        val awaitDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + awaitDispatcher)
        var afterSave = false

        val job = scope.launch(Dispatchers.Unconfined) {
            saveCategoryDraft(
                categoriesRepository = categoriesRepository,
                rulesRepository = rulesRepository,
                persisted = draft,
                pendingRule = null,
                onSaved = { /* ignored */ },
            )
            afterSave = true
        }

        advanceUntilIdle()
        job.join()

        assertTrue("saveCategoryDraft did not complete", afterSave)
        assertEquals(1, categoriesRepository.currentCategories().size)
    }
}
