package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.usecase.SilentGroupKey
import com.smartnoti.app.domain.usecase.SilentNotificationGroupingPolicy

/**
 * Pure diff planner driving the listener's per-sender group tray (plan
 * `silent-tray-sender-grouping`, Task 3).
 *
 * Given the set of previously-posted group summaries / children and the current SILENT rows
 * the listener is observing, the planner decides what summaries and children need to be
 * posted or cancelled on this tick. Keeping this logic off the coroutine lets us pin the
 * grouping contract (plan Q3-A "SILENT never occupies tray alone") with plain JUnit instead
 * of Robolectric.
 *
 * The listener is expected to thread [SilentGroupTrayState] through ticks — passing
 * [SilentGroupTrayState.EMPTY] on boot and the [Plan.nextState] returned by each call into
 * the next one. Only groups the planner actually surfaced in the tray (count ≥ 2) are
 * tracked in the state; singleton groups are invisible both in terms of side effects and
 * carried-over state.
 */
class SilentGroupTrayPlanner(
    private val groupingPolicy: SilentNotificationGroupingPolicy = SilentNotificationGroupingPolicy(),
) {

    fun plan(
        previousState: SilentGroupTrayState,
        currentSilent: List<NotificationUiModel>,
    ): Plan {
        val currentGroups: Map<SilentGroupKey, List<NotificationUiModel>> = currentSilent
            .groupBy { groupingPolicy.groupKeyFor(it) }

        val summaryPosts = mutableListOf<GroupSummaryPost>()
        val summaryCancels = mutableListOf<SilentGroupKey>()
        val childPosts = mutableListOf<GroupChildPost>()
        val childCancels = mutableListOf<GroupChildCancel>()
        val nextGroups = mutableMapOf<SilentGroupKey, Set<String>>()

        // 1) Process every group present in the current state — promote or demote based on count.
        currentGroups.forEach { (key, members) ->
            val previousChildren = previousState.childrenOf(key)
            val memberIds = members.map { it.id }
            if (members.size >= 2) {
                // Only (re)post the summary when its contents actually changed — a swipe-
                // dismissed summary must stay dismissed until the group's membership moves,
                // matching the behaviour of the legacy archived-summary path.
                val memberIdSet = memberIds.toSet()
                if (memberIdSet != previousChildren) {
                    summaryPosts += GroupSummaryPost(
                        key = key,
                        count = members.size,
                        preview = members,
                    )
                }
                members
                    .filter { it.id !in previousChildren }
                    .forEach { entity ->
                        childPosts += GroupChildPost(
                            entity = entity,
                            key = key,
                            notificationId = rowNotificationIdFor(entity.id),
                        )
                    }
                // Cancel any previously-posted child that no longer belongs to this group
                // (e.g. user marked that single row processed while others arrived).
                previousChildren
                    .filter { it !in memberIds }
                    .forEach { gone ->
                        childCancels += GroupChildCancel(
                            rowId = gone,
                            notificationId = rowNotificationIdFor(gone),
                        )
                    }
                nextGroups[key] = memberIds.toSet()
            } else {
                // Plan Q3-A: "SILENT never occupies tray alone." If the group has collapsed to
                // a single row, take down the summary and every child we posted under it.
                if (key in previousState.keys()) {
                    summaryCancels += key
                }
                previousChildren.forEach { rowId ->
                    childCancels += GroupChildCancel(
                        rowId = rowId,
                        notificationId = rowNotificationIdFor(rowId),
                    )
                }
                // A singleton that was already invisible stays invisible — nothing to surface
                // and nothing to remember in nextGroups.
            }
        }

        // 2) Any previously-posted group that is entirely absent now must be cancelled.
        previousState.keys()
            .filter { it !in currentGroups.keys }
            .forEach { goneKey ->
                summaryCancels += goneKey
                previousState.childrenOf(goneKey).forEach { rowId ->
                    childCancels += GroupChildCancel(
                        rowId = rowId,
                        notificationId = rowNotificationIdFor(rowId),
                    )
                }
            }

        return Plan(
            summaryPosts = summaryPosts,
            summaryCancels = summaryCancels,
            childPosts = childPosts,
            childCancels = childCancels,
            nextState = SilentGroupTrayState(nextGroups),
        )
    }

    companion object {
        /**
         * Stable Long id derived from the row's String id for
         * [SilentHiddenSummaryNotifier.groupChildNotificationId]. The notifier ultimately
         * folds this value into an Int via XOR, so any deterministic widening of the hash
         * is sufficient — we keep the conversion centralised so listener and planner agree.
         */
        fun rowNotificationIdFor(rowId: String): Long = rowId.hashCode().toLong()
    }

    data class Plan(
        val summaryPosts: List<GroupSummaryPost>,
        val summaryCancels: List<SilentGroupKey>,
        val childPosts: List<GroupChildPost>,
        val childCancels: List<GroupChildCancel>,
        val nextState: SilentGroupTrayState,
    )

    data class GroupSummaryPost(
        val key: SilentGroupKey,
        val count: Int,
        val preview: List<NotificationUiModel>,
    )

    data class GroupChildPost(
        val entity: NotificationUiModel,
        val key: SilentGroupKey,
        val notificationId: Long,
    )

    data class GroupChildCancel(
        val rowId: String,
        val notificationId: Long,
    )
}

/**
 * Snapshot of which group summaries (and their child rows) the listener currently has posted
 * on the system tray. Immutable so the planner can compute a clean diff — the listener holds
 * the latest instance returned by [SilentGroupTrayPlanner.plan].
 */
data class SilentGroupTrayState internal constructor(
    private val groups: Map<SilentGroupKey, Set<String>>,
) {
    fun keys(): Set<SilentGroupKey> = groups.keys

    fun childrenOf(key: SilentGroupKey): Set<String> = groups[key] ?: emptySet()

    companion object {
        val EMPTY = SilentGroupTrayState(emptyMap())
    }
}
