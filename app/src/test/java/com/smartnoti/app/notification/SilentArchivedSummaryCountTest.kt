package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the summary badge count rule introduced by
 * `silent-archive-vs-process-split` Task 5:
 * the summary should only count SILENT × ARCHIVED rows.
 */
class SilentArchivedSummaryCountTest {

    @Test
    fun counts_only_silent_archived_rows() {
        val archivedA = silent(id = "1", mode = SilentMode.ARCHIVED)
        val archivedB = silent(id = "2", mode = SilentMode.ARCHIVED)
        val processed = silent(id = "3", mode = SilentMode.PROCESSED)
        val legacyNull = silent(id = "4", mode = null)

        val count = listOf(archivedA, archivedB, processed, legacyNull)
            .countSilentArchivedForSummary(hidePersistentNotifications = false)

        assertEquals(2, count)
    }

    @Test
    fun legacy_null_silent_rows_are_treated_as_processed_and_excluded() {
        val legacyNull = silent(id = "1", mode = null)

        val count = listOf(legacyNull)
            .countSilentArchivedForSummary(hidePersistentNotifications = false)

        assertEquals(0, count)
    }

    @Test
    fun processed_rows_are_never_counted() {
        val processedA = silent(id = "1", mode = SilentMode.PROCESSED)
        val processedB = silent(id = "2", mode = SilentMode.PROCESSED)

        val count = listOf(processedA, processedB)
            .countSilentArchivedForSummary(hidePersistentNotifications = false)

        assertEquals(0, count)
    }

    @Test
    fun non_silent_rows_are_excluded_regardless_of_silent_mode_value() {
        val priorityWithStaleMode = silent(id = "p1", mode = SilentMode.ARCHIVED)
            .copy(status = NotificationStatusUi.PRIORITY)
        val digestWithStaleMode = silent(id = "d1", mode = SilentMode.ARCHIVED)
            .copy(status = NotificationStatusUi.DIGEST)
        val archived = silent(id = "s1", mode = SilentMode.ARCHIVED)

        val count = listOf(priorityWithStaleMode, digestWithStaleMode, archived)
            .countSilentArchivedForSummary(hidePersistentNotifications = false)

        assertEquals(1, count)
    }

    @Test
    fun persistent_filter_is_honoured_so_summary_matches_home_and_hidden_header() {
        val archivedPersistent = silent(id = "1", mode = SilentMode.ARCHIVED)
            .copy(isPersistent = true)
        val archivedNormal = silent(id = "2", mode = SilentMode.ARCHIVED)

        val hiding = listOf(archivedPersistent, archivedNormal)
            .countSilentArchivedForSummary(hidePersistentNotifications = true)
        val notHiding = listOf(archivedPersistent, archivedNormal)
            .countSilentArchivedForSummary(hidePersistentNotifications = false)

        assertEquals(1, hiding)
        assertEquals(2, notHiding)
    }

    private fun silent(id: String, mode: SilentMode?): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "앱-$id",
        packageName = "com.example.$id",
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
        postedAtMillis = 1_700_000_000_000L + id.hashCode().toLong(),
        silentMode = mode,
    )
}
