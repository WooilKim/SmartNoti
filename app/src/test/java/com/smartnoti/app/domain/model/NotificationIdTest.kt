package com.smartnoti.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationIdTest {

    @Test
    fun id_is_stable_across_updates_when_sourceEntryKey_is_present() {
        val pkg = "com.spotify.music"
        val sourceKey = "0|com.spotify.music|MusicPlayback|42|0"

        val first = buildNotificationId(
            packageName = pkg,
            postedAtMillis = 1_000L,
            sourceEntryKey = sourceKey,
        )
        val second = buildNotificationId(
            packageName = pkg,
            postedAtMillis = 9_999L, // music 알림이 재생 중 여러 번 업데이트되는 상황
            sourceEntryKey = sourceKey,
        )

        assertEquals(
            "같은 sourceEntryKey 에서 온 업데이트는 같은 id 여야 upsert 가 replace 로 동작한다",
            first,
            second,
        )
    }

    @Test
    fun id_falls_back_to_timestamped_form_when_sourceEntryKey_is_null() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 42L,
            sourceEntryKey = null,
        )

        assertEquals("com.example.app:42", id)
    }

    @Test
    fun id_falls_back_to_timestamped_form_when_sourceEntryKey_is_blank() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 42L,
            sourceEntryKey = "   ",
        )

        assertEquals("com.example.app:42", id)
    }

    @Test
    fun id_preserves_tag_and_id_segments_from_sourceEntryKey() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 100L,
            sourceEntryKey = "0|com.example.app|ChatRoom|7|0",
        )

        assertEquals("com.example.app:ChatRoom:7", id)
    }

    @Test
    fun id_for_short_sourceEntryKey_uses_raw_value() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 100L,
            sourceEntryKey = "raw-key",
        )

        assertEquals("com.example.app:raw-key", id)
    }

    @Test
    fun id_sanitizes_colons_in_source_segments() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 100L,
            sourceEntryKey = "0|com.example.app|tag:weird|id:1|0",
        )

        assertEquals("com.example.app:tag_weird:id_1", id)
    }

    @Test
    fun legacy_ids_still_parse_postedAtMillis_via_fallback() {
        val legacy = "com.legacy.app:1234567890:MusicPlayback:42"

        val parsed = legacy.notificationPostedAtMillisOrNull()

        assertNotNull("legacy id 형식은 이전 timestamp 추출 경로를 유지해야 한다", parsed)
        assertEquals(1_234_567_890L, parsed)
    }

    @Test
    fun new_stable_ids_do_not_embed_postedAtMillis() {
        val id = buildNotificationId(
            packageName = "com.example.app",
            postedAtMillis = 42L,
            sourceEntryKey = "0|com.example.app|tag|7|0",
        )

        val parsed = id.notificationPostedAtMillisOrNull()

        assertNull(
            "새 id 에서는 두 번째 세그먼트가 tag 이므로 timestamp 로 파싱되면 안 된다",
            parsed,
        )
    }
}
