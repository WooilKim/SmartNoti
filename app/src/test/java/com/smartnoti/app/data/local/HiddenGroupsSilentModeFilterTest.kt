package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failing tests added for the silent-archive-vs-process-split Task 4
 * ("Hidden 탭 분리"). They pin the filter behaviour the Hidden 화면
 * needs so it can split rows into "보관 중" / "처리됨" buckets.
 *
 * Migration decision (plan Open question 4): legacy SILENT rows whose
 * `silentMode == null` are surfaced under the **처리됨** bucket so they
 * don't re-clutter the "보관 중" list after upgrade.
 */
class HiddenGroupsSilentModeFilterTest {

    @Test
    fun archived_filter_only_returns_groups_whose_rows_are_silent_mode_archived() {
        val archived = silent(id = "1", packageName = "com.promo.app", mode = SilentMode.ARCHIVED)
        val processed = silent(id = "2", packageName = "com.ad.app", mode = SilentMode.PROCESSED)
        val legacyNull = silent(id = "3", packageName = "com.legacy.app", mode = null)

        val groups = listOf(archived, processed, legacyNull)
            .toHiddenGroups(silentModeFilter = SilentMode.ARCHIVED)

        assertEquals(listOf("com.promo.app"), groups.map { it.items.first().packageName })
    }

    @Test
    fun processed_filter_includes_legacy_null_silent_rows() {
        val archived = silent(id = "1", packageName = "com.promo.app", mode = SilentMode.ARCHIVED)
        val processed = silent(id = "2", packageName = "com.ad.app", mode = SilentMode.PROCESSED)
        val legacyNull = silent(id = "3", packageName = "com.legacy.app", mode = null)

        val groups = listOf(archived, processed, legacyNull)
            .toHiddenGroups(silentModeFilter = SilentMode.PROCESSED)

        val packages = groups.flatMap { it.items }.map { it.packageName }.toSet()
        assertEquals(setOf("com.ad.app", "com.legacy.app"), packages)
    }

    @Test
    fun no_filter_preserves_existing_behavior_and_returns_all_silent_rows() {
        val archived = silent(id = "1", packageName = "com.promo.app", mode = SilentMode.ARCHIVED)
        val processed = silent(id = "2", packageName = "com.ad.app", mode = SilentMode.PROCESSED)
        val legacyNull = silent(id = "3", packageName = "com.legacy.app", mode = null)

        val groups = listOf(archived, processed, legacyNull).toHiddenGroups()

        val totalCount = groups.sumOf { it.count }
        assertEquals(3, totalCount)
    }

    @Test
    fun archived_filter_on_empty_archive_returns_empty_groups_even_if_processed_rows_exist() {
        val processed = silent(id = "1", packageName = "com.ad.app", mode = SilentMode.PROCESSED)
        val legacyNull = silent(id = "2", packageName = "com.legacy.app", mode = null)

        val groups = listOf(processed, legacyNull)
            .toHiddenGroups(silentModeFilter = SilentMode.ARCHIVED)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun processed_filter_on_empty_processed_returns_empty_groups_even_if_archived_rows_exist() {
        val archived = silent(id = "1", packageName = "com.promo.app", mode = SilentMode.ARCHIVED)

        val groups = listOf(archived).toHiddenGroups(silentModeFilter = SilentMode.PROCESSED)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun non_silent_rows_are_excluded_regardless_of_silent_mode_filter() {
        val priority = silent(
            id = "p1",
            packageName = "com.chat.app",
            mode = null,
        ).copy(status = NotificationStatusUi.PRIORITY)
        val digest = silent(
            id = "d1",
            packageName = "com.news.app",
            mode = null,
        ).copy(status = NotificationStatusUi.DIGEST)

        val archivedGroups = listOf(priority, digest)
            .toHiddenGroups(silentModeFilter = SilentMode.ARCHIVED)
        val processedGroups = listOf(priority, digest)
            .toHiddenGroups(silentModeFilter = SilentMode.PROCESSED)

        assertTrue(archivedGroups.isEmpty())
        assertTrue(processedGroups.isEmpty())
    }

    private fun silent(
        id: String,
        packageName: String,
        mode: SilentMode?,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "앱-$id",
        packageName = packageName,
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
