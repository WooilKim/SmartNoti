package com.smartnoti.app.ui.screens.digest

import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` Task 4.
 *
 * Pure-logic guard for the Digest sub-tab's per-group `bulkActions` slot
 * wiring. The `DigestScreen` Composable wires two `OutlinedButton`s — `모두
 * 중요로 변경` (calls `restoreDigestToPriorityByPackage`) + `모두 지우기`
 * (calls `deleteDigestByPackage`) — into the `DigestGroupCard.bulkActions`
 * slot for every group. ComposeRule UI tests are not wired into this
 * codebase's unit-test classpath (the project pattern is to extract
 * Composable view-model helpers and test them directly, mirroring
 * `DigestGroupCardPreviewState` and `RuleRowDescriptionBuilder`), so this
 * test pins the contract by exercising the [digestGroupBulkActionsSpec]
 * helper that the Composable delegates to.
 *
 * Contract:
 * - Non-empty group → spec returned with `packageName == group.items.first().packageName`,
 *   `restoreLabel == "모두 중요로 변경"`, `deleteLabel == "모두 지우기"`.
 * - Empty group → spec is `null` (defensive guard; real flows never emit
 *   empty groups today).
 */
class DigestScreenBulkActionsWiringTest {

    @Test
    fun nonempty_group_yields_spec_keyed_on_first_item_package() {
        val group = digestGroup(
            id = "digest:com.coupang.mobile",
            items = listOf(
                notification(id = "n-1", packageName = "com.coupang.mobile"),
                notification(id = "n-2", packageName = "com.coupang.mobile"),
            ),
        )

        val spec = digestGroupBulkActionsSpec(group)

        assertNotNull(spec)
        assertEquals("com.coupang.mobile", spec!!.packageName)
    }

    @Test
    fun spec_carries_documented_button_copy() {
        val group = digestGroup(
            id = "digest:com.android.shell",
            items = listOf(notification(id = "n-1", packageName = "com.android.shell")),
        )

        val spec = digestGroupBulkActionsSpec(group)

        assertNotNull(spec)
        assertEquals("모두 중요로 변경", spec!!.restoreLabel)
        assertEquals("모두 지우기", spec.deleteLabel)
    }

    @Test
    fun empty_group_returns_null_spec_to_avoid_first_item_throw() {
        val group = digestGroup(
            id = "digest:com.empty",
            items = emptyList(),
        )

        val spec = digestGroupBulkActionsSpec(group)

        assertNull(spec)
    }

    private fun digestGroup(
        id: String,
        items: List<NotificationUiModel>,
    ): DigestGroupUiModel = DigestGroupUiModel(
        id = id,
        appName = "테스트 앱",
        count = items.size,
        summary = "테스트 앱 관련 알림 ${items.size}건",
        items = items,
    )

    private fun notification(
        id: String,
        packageName: String,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "테스트 앱",
        packageName = packageName,
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.DIGEST,
        reasonTags = emptyList(),
    )
}
