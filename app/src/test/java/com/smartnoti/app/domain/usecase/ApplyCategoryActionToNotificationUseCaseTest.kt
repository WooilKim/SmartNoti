package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-26-detail-reclassify-this-row-now.md` Task 2.
 *
 * The "이 알림도 지금 재분류" CTA needs a use case that, given the
 * currently-shown [NotificationUiModel] and the destination Category's
 * [CategoryAction], (a) maps action → status via
 * [CategoryActionToNotificationStatusMapper], (b) appends the manual
 * reason tag `사용자 분류` (dedup), and (c) preserves every other field
 * before delegating to a single [Ports.updateNotification] write.
 *
 * The IGNORE branch is exercised here too — the use case is "just write"
 * and does not own the UX of "row vanishes from the default view"; that
 * is the caller's responsibility (see plan Risks open question).
 *
 * The use case symbol does not exist yet — Task 3 turns this RED test
 * green by introducing
 * [com.smartnoti.app.domain.usecase.ApplyCategoryActionToNotificationUseCase].
 */
class ApplyCategoryActionToNotificationUseCaseTest {

    @Test
    fun apply_priority_to_digest_row_updates_status_and_appends_reason_tag() = runBlocking {
        val notification = notification(
            status = NotificationStatusUi.DIGEST,
            reasonTags = listOf("디지스트로 분류"),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.PRIORITY)

        val written = fixture.lastWritten
        assertNotNull("expected a single updateNotification call", written)
        assertEquals(NotificationStatusUi.PRIORITY, written!!.status)
        assertEquals(listOf("디지스트로 분류", "사용자 분류"), written.reasonTags)
        // Other fields preserved verbatim
        assertEquals(notification.id, written.id)
        assertEquals(notification.appName, written.appName)
        assertEquals(notification.packageName, written.packageName)
        assertEquals(notification.sender, written.sender)
        assertEquals(notification.title, written.title)
        assertEquals(notification.body, written.body)
        assertEquals(notification.receivedAtLabel, written.receivedAtLabel)
    }

    @Test
    fun apply_dedupes_existing_사용자분류_reason_tag() = runBlocking {
        val notification = notification(
            status = NotificationStatusUi.SILENT,
            reasonTags = listOf("키워드 일치", "사용자 분류"),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.PRIORITY)

        val written = fixture.lastWritten!!
        assertEquals(NotificationStatusUi.PRIORITY, written.status)
        // No duplicate appended — `사용자 분류` stays single-occurrence.
        assertEquals(listOf("키워드 일치", "사용자 분류"), written.reasonTags)
    }

    @Test
    fun apply_to_empty_reason_tags_sets_사용자분류_alone() = runBlocking {
        val notification = notification(
            status = NotificationStatusUi.PRIORITY,
            reasonTags = emptyList(),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.DIGEST)

        val written = fixture.lastWritten!!
        assertEquals(NotificationStatusUi.DIGEST, written.status)
        assertEquals(listOf("사용자 분류"), written.reasonTags)
    }

    @Test
    fun apply_ignore_action_writes_ignore_status_without_caller_side_effect() = runBlocking {
        // Use case is a thin write — caller (Detail) handles "row disappears
        // from default view" UX by leaving Detail open over the now-IGNORE row.
        val notification = notification(
            status = NotificationStatusUi.PRIORITY,
            reasonTags = emptyList(),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.IGNORE)

        val written = fixture.lastWritten!!
        assertEquals(NotificationStatusUi.IGNORE, written.status)
        assertTrue("사용자 분류" in written.reasonTags)
    }

    @Test
    fun apply_silent_action_writes_silent_status() = runBlocking {
        val notification = notification(
            status = NotificationStatusUi.PRIORITY,
            reasonTags = emptyList(),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.SILENT)

        val written = fixture.lastWritten!!
        assertEquals(NotificationStatusUi.SILENT, written.status)
    }

    @Test
    fun apply_invokes_update_notification_exactly_once() = runBlocking {
        val notification = notification(
            status = NotificationStatusUi.DIGEST,
            reasonTags = emptyList(),
        )
        val fixture = fixture()

        fixture.useCase.apply(notification, CategoryAction.PRIORITY)

        assertEquals(1, fixture.writeCount)
    }

    private fun notification(
        status: NotificationStatusUi,
        reasonTags: List<String>,
    ) = NotificationUiModel(
        id = "n-1",
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "Alice",
        title = "Alice",
        body = "회의 곧 시작합니다",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = reasonTags,
    )

    private class Fixture(
        val useCase: ApplyCategoryActionToNotificationUseCase,
        private val writes: MutableList<NotificationUiModel>,
    ) {
        val lastWritten: NotificationUiModel? get() = writes.lastOrNull()
        val writeCount: Int get() = writes.size
    }

    private fun fixture(): Fixture {
        val writes = mutableListOf<NotificationUiModel>()
        val ports = object : ApplyCategoryActionToNotificationUseCase.Ports {
            override suspend fun updateNotification(notification: NotificationUiModel) {
                writes += notification
            }
        }
        return Fixture(
            useCase = ApplyCategoryActionToNotificationUseCase(
                ports = ports,
                mapper = CategoryActionToNotificationStatusMapper(),
            ),
            writes = writes,
        )
    }
}
