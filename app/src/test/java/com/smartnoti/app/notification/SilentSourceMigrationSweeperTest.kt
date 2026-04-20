package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentSourceMigrationSweeperTest {

    @Test
    fun active_notification_matching_silent_db_entry_is_returned_for_cancel() {
        val active = ActiveSourceNotification(
            key = "0|com.promoapp|null|1|0",
            packageName = "com.promoapp",
            contentSignature = "광고 오늘만 할인",
            isProtected = false,
        )

        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = listOf(active),
            silentEntries = setOf(SilentEntry("com.promoapp", "광고 오늘만 할인")),
        )

        assertEquals(listOf(active.key), keys)
    }

    @Test
    fun active_notification_not_in_silent_set_is_left_alone() {
        val active = ActiveSourceNotification(
            key = "0|com.other|null|1|0",
            packageName = "com.other",
            contentSignature = "다른 내용",
            isProtected = false,
        )

        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = listOf(active),
            silentEntries = setOf(SilentEntry("com.promoapp", "광고 오늘만 할인")),
        )

        assertTrue(keys.isEmpty())
    }

    @Test
    fun protected_active_notification_is_never_cancelled_even_if_silent_row_matches() {
        val active = ActiveSourceNotification(
            key = "0|com.music|null|1|0",
            packageName = "com.music",
            contentSignature = "재생 중",
            isProtected = true,
        )

        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = listOf(active),
            silentEntries = setOf(SilentEntry("com.music", "재생 중")),
        )

        assertTrue(keys.isEmpty())
    }

    @Test
    fun empty_active_list_returns_empty_keys() {
        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = emptyList(),
            silentEntries = setOf(SilentEntry("com.promoapp", "광고")),
        )

        assertTrue(keys.isEmpty())
    }

    @Test
    fun multiple_matches_return_every_key() {
        val first = ActiveSourceNotification(
            key = "0|com.promoapp|null|1|0",
            packageName = "com.promoapp",
            contentSignature = "광고 오늘만",
            isProtected = false,
        )
        val second = ActiveSourceNotification(
            key = "0|com.promoapp|null|2|0",
            packageName = "com.promoapp",
            contentSignature = "광고 오늘만",
            isProtected = false,
        )

        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = listOf(first, second),
            silentEntries = setOf(SilentEntry("com.promoapp", "광고 오늘만")),
        )

        assertEquals(listOf(first.key, second.key), keys)
    }

    @Test
    fun mixed_active_list_only_returns_matching_non_protected_entries() {
        val promoActive = ActiveSourceNotification(
            key = "active:promo",
            packageName = "com.promoapp",
            contentSignature = "광고",
            isProtected = false,
        )
        val musicActive = ActiveSourceNotification(
            key = "active:music",
            packageName = "com.music",
            contentSignature = "재생 중",
            isProtected = true,
        )
        val unrelatedActive = ActiveSourceNotification(
            key = "active:unrelated",
            packageName = "com.chat",
            contentSignature = "안녕",
            isProtected = false,
        )

        val keys = SilentSourceMigrationSweeper.keysToCancel(
            activeNotifications = listOf(promoActive, musicActive, unrelatedActive),
            silentEntries = setOf(
                SilentEntry("com.promoapp", "광고"),
                SilentEntry("com.music", "재생 중"),
            ),
        )

        assertEquals(listOf("active:promo"), keys)
    }
}
