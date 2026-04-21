package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.SilentGroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests-first for `silent-tray-sender-grouping` Task 3 — the pure mapping layer behind the
 * listener's `silentSummaryJob`.
 *
 * The planner takes the set of previously-posted group keys + the current SILENT rows and
 * decides what to post / cancel on the tray. The listener is left with only side-effect
 * dispatch (calling the notifier), so the decision logic can be pinned by plain JUnit.
 *
 * Plan decisions being pinned:
 *  - count ≥ 2 → post group summary + post every member as a group child
 *  - count == 1 → cancel group summary + cancel the single child (plan Q3-A keeps the
 *    "SILENT never occupies tray alone" contract)
 *  - groups that were previously posted but are absent from the current state → cancel
 *    summary + cancel every previously-posted child for that key
 *  - rows that belonged to a posted child but are no longer present in the current state
 *    (e.g. user marked processed, or the row moved out of SILENT) → cancel the child even
 *    if the group survives
 */
class SilentGroupTrayPlannerTest {

    private val planner = SilentGroupTrayPlanner()

    @Test
    fun group_with_count_two_posts_summary_and_all_children() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.android.mms", sender = "엄마")

        val plan = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )

        val momKey = SilentGroupKey.Sender("엄마")
        assertEquals(1, plan.summaryPosts.size)
        plan.summaryPosts.single().let { post ->
            assertEquals(momKey, post.key)
            assertEquals(2, post.count)
            assertEquals(listOf("a", "b"), post.preview.map { it.id })
        }
        assertEquals(
            setOf("a" to momKey, "b" to momKey),
            plan.childPosts.map { it.entity.id to it.key }.toSet(),
        )
        assertTrue(plan.summaryCancels.isEmpty())
        assertTrue(plan.childCancels.isEmpty())

        // State advances so the next tick knows which children were posted.
        assertEquals(setOf(momKey), plan.nextState.keys())
        assertEquals(setOf("a", "b"), plan.nextState.childrenOf(momKey))
    }

    @Test
    fun singleton_group_is_cancelled_rather_than_posted() {
        val lone = silent(id = "solo", packageName = "com.example.shop", sender = null)

        val plan = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(lone),
        )

        val appKey = SilentGroupKey.App("com.example.shop")
        // count == 1 → no summary post, no child post (plan Q3-A).
        assertTrue(plan.summaryPosts.isEmpty())
        assertTrue(plan.childPosts.isEmpty())
        // Nothing was posted previously, so nothing to cancel either.
        assertTrue(plan.summaryCancels.isEmpty())
        assertTrue(plan.childCancels.isEmpty())
        // State does not remember the singleton — it was never surfaced in the tray.
        assertTrue(appKey !in plan.nextState.keys())
    }

    @Test
    fun group_shrinking_from_two_to_one_cancels_summary_and_previously_posted_child() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.kakao.talk", sender = "엄마")
        val momKey = SilentGroupKey.Sender("엄마")

        val tick1 = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )
        // After tick 1 the state knows about "엄마" → {a, b}.
        val tick2 = planner.plan(
            previousState = tick1.nextState,
            currentSilent = listOf(a), // b was marked processed / cleared
        )

        // Summary must be cancelled because the surviving group dropped to 1.
        assertEquals(listOf(momKey), tick2.summaryCancels)
        // Both the lingering child ("a", whose summary is now gone) and the disappeared child
        // ("b") must be cancelled to keep the 'SILENT never occupies tray alone' contract.
        assertEquals(setOf("a", "b"), tick2.childCancels.map { it.rowId }.toSet())
        assertTrue(tick2.summaryPosts.isEmpty())
        assertTrue(tick2.childPosts.isEmpty())
        assertTrue(momKey !in tick2.nextState.keys())
    }

    @Test
    fun group_disappearing_entirely_cancels_summary_and_every_previously_posted_child() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.kakao.talk", sender = "엄마")
        val momKey = SilentGroupKey.Sender("엄마")

        val tick1 = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )
        val tick2 = planner.plan(
            previousState = tick1.nextState,
            currentSilent = emptyList(),
        )

        assertEquals(listOf(momKey), tick2.summaryCancels)
        assertEquals(setOf("a", "b"), tick2.childCancels.map { it.rowId }.toSet())
        assertTrue(tick2.summaryPosts.isEmpty())
        assertTrue(tick2.childPosts.isEmpty())
        assertTrue(tick2.nextState.keys().isEmpty())
    }

    @Test
    fun growing_group_reposts_summary_and_only_the_new_children() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.kakao.talk", sender = "엄마")
        val c = silent(id = "c", packageName = "com.android.mms", sender = "엄마")
        val momKey = SilentGroupKey.Sender("엄마")

        val tick1 = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )
        val tick2 = planner.plan(
            previousState = tick1.nextState,
            currentSilent = listOf(a, b, c),
        )

        // Summary repost reflects the new count.
        assertEquals(1, tick2.summaryPosts.size)
        assertEquals(3, tick2.summaryPosts.single().count)
        // Only the new row needs to be posted as a child — the planner tells the listener
        // which members are already on the tray so we don't respam Android with reposts.
        assertEquals(listOf("c"), tick2.childPosts.map { it.entity.id })
        assertTrue(tick2.summaryCancels.isEmpty())
        assertTrue(tick2.childCancels.isEmpty())
        assertEquals(setOf("a", "b", "c"), tick2.nextState.childrenOf(momKey))
    }

    @Test
    fun shrinking_group_that_stays_above_two_keeps_summary_and_only_cancels_gone_children() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.kakao.talk", sender = "엄마")
        val c = silent(id = "c", packageName = "com.kakao.talk", sender = "엄마")
        val momKey = SilentGroupKey.Sender("엄마")

        val tick1 = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b, c),
        )
        val tick2 = planner.plan(
            previousState = tick1.nextState,
            currentSilent = listOf(a, b), // c disappeared but group still at 2
        )

        assertEquals(1, tick2.summaryPosts.size)
        assertEquals(2, tick2.summaryPosts.single().count)
        assertEquals(setOf("c"), tick2.childCancels.map { it.rowId }.toSet())
        assertTrue(tick2.summaryCancels.isEmpty())
        // No re-post of surviving children.
        assertTrue(tick2.childPosts.isEmpty())
        assertEquals(setOf("a", "b"), tick2.nextState.childrenOf(momKey))
    }

    @Test
    fun multiple_independent_groups_are_planned_without_cross_effects() {
        val mom1 = silent(id = "m1", packageName = "com.kakao.talk", sender = "엄마")
        val mom2 = silent(id = "m2", packageName = "com.android.mms", sender = "엄마")
        val promo1 = silent(id = "p1", packageName = "com.shop", sender = "광고")
        val promo2 = silent(id = "p2", packageName = "com.shop", sender = "광고")
        val solo = silent(id = "s1", packageName = "com.example.news", sender = null)

        val plan = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(mom1, mom2, promo1, promo2, solo),
        )

        val momKey = SilentGroupKey.Sender("엄마")
        val promoKey = SilentGroupKey.Sender("광고")
        val keysPosted = plan.summaryPosts.map { it.key }.toSet()
        assertEquals(setOf(momKey, promoKey), keysPosted)
        // Singleton has no summary and no child posted.
        assertTrue(plan.summaryPosts.none { it.key is SilentGroupKey.App })
        assertTrue(plan.childPosts.none { it.entity.id == "s1" })
        assertTrue(plan.summaryCancels.isEmpty())
        assertTrue(plan.childCancels.isEmpty())
        assertEquals(setOf(momKey, promoKey), plan.nextState.keys())
    }

    @Test
    fun noop_tick_with_identical_state_produces_no_actions() {
        val a = silent(id = "a", packageName = "com.kakao.talk", sender = "엄마")
        val b = silent(id = "b", packageName = "com.kakao.talk", sender = "엄마")

        val tick1 = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )
        val tick2 = planner.plan(
            previousState = tick1.nextState,
            currentSilent = listOf(a, b),
        )

        assertTrue("no-op tick should not re-post a summary", tick2.summaryPosts.isEmpty())
        assertTrue("no-op tick should not re-post children", tick2.childPosts.isEmpty())
        assertTrue(tick2.summaryCancels.isEmpty())
        assertTrue(tick2.childCancels.isEmpty())
    }

    @Test
    fun fallback_to_app_key_when_sender_missing_still_groups_and_posts() {
        val a = silent(id = "a", packageName = "com.example.news", sender = null)
        val b = silent(id = "b", packageName = "com.example.news", sender = "   ")
        val appKey = SilentGroupKey.App("com.example.news")

        val plan = planner.plan(
            previousState = SilentGroupTrayState.EMPTY,
            currentSilent = listOf(a, b),
        )

        assertEquals(listOf(appKey), plan.summaryPosts.map { it.key })
        assertEquals(setOf("a", "b"), plan.childPosts.map { it.entity.id }.toSet())
    }

    private fun silent(
        id: String,
        packageName: String,
        sender: String?,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = packageName,
        packageName = packageName,
        sender = sender,
        title = sender ?: "알림 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
        postedAtMillis = 1_700_000_000_000L + id.hashCode().toLong(),
        silentMode = SilentMode.ARCHIVED,
    )
}
