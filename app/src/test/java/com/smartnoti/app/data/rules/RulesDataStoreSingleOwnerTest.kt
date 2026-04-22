package com.smartnoti.app.data.rules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.LegacyRuleActionReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for plan
 * `docs/plans/2026-04-22-rules-datastore-dedup-fix.md` Task 1.
 *
 * Contract: `RulesRepository` and `LegacyRuleActionReader` both operate on
 * the same `smartnoti_rules.preferences_pb` file. AndroidX
 * `preferencesDataStore` refuses to construct two delegates targeting the
 * same file and throws `IllegalStateException` on first `.data` access.
 * This is the production app-launch crash discovered after Categories
 * Phase P1 shipped.
 *
 * The fix (Task 2) makes `RulesRepository` the single owner by exposing
 * `RulesRepository.dataStore` and pivoting `LegacyRuleActionReader` to
 * accept that handle via constructor injection. With one delegate per
 * file, both readers can coexist against the same Context.
 *
 * Payload semantics live in other tests (`RuleStorageCodecTest`,
 * `RulesRepositoryDeleteCascadeTest`, etc.) — this test only asserts that
 * the two code paths can be stood up together without exploding.
 */
@RunWith(RobolectricTestRunner::class)
class RulesDataStoreSingleOwnerTest {

    @After
    fun tearDown() {
        // RulesRepository.getInstance is process-wide; reset the singleton so
        // subsequent tests see a fresh instance and avoid cross-test Context
        // leaks. The existing `clearInstanceForTest` surface (added earlier
        // by the Category editor save-wiring PR) is sufficient — no new
        // test-only API needed for this fix.
        RulesRepository.clearInstanceForTest()
    }

    @Test
    fun both_readers_can_share_the_same_smartnoti_rules_datastore() {
        runBlocking {
            val ctx = ApplicationProvider.getApplicationContext<Context>()

            val repo = RulesRepository.getInstance(ctx)
            val reader = LegacyRuleActionReader(repo.dataStore)

            // Touch both code paths so the DataStore delegate is actually
            // instantiated. Prior to the fix, exercising both against the
            // same Context raised `IllegalStateException: There are
            // multiple DataStores active for the same file`.
            repo.observeRules().first()
            reader.readRuleActions()
        }
    }
}
