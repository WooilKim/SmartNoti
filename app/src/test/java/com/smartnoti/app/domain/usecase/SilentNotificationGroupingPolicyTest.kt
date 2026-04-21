package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for [SilentNotificationGroupingPolicy].
 *
 * The policy treats a non-null, non-blank [NotificationEntity.sender] as a positive MessagingStyle
 * hint (the listener only populates `sender` for messaging-like surfaces — see plan Q2 and Task 3
 * follow-up). When sender is absent or blank the entity falls back to app-level grouping.
 */
class SilentNotificationGroupingPolicyTest {

    private val policy = SilentNotificationGroupingPolicy()

    @Test
    fun sender_present_yields_sender_group_key() {
        val entity = silentEntity(
            id = "n-1",
            packageName = "com.kakao.talk",
            sender = "엄마",
        )

        val key = policy.groupKeyFor(entity)

        assertEquals(SilentGroupKey.Sender("엄마"), key)
    }

    @Test
    fun sender_null_yields_app_group_key() {
        val entity = silentEntity(
            id = "n-2",
            packageName = "com.example.news",
            sender = null,
        )

        val key = policy.groupKeyFor(entity)

        assertEquals(SilentGroupKey.App("com.example.news"), key)
    }

    @Test
    fun sender_blank_yields_app_group_key() {
        val entity = silentEntity(
            id = "n-blank",
            packageName = "com.example.shop",
            sender = "   ",
        )

        val key = policy.groupKeyFor(entity)

        assertEquals(SilentGroupKey.App("com.example.shop"), key)
    }

    @Test
    fun same_sender_across_different_packages_collapses_to_one_sender_key() {
        val fromKakao = silentEntity(
            id = "a",
            packageName = "com.kakao.talk",
            sender = "엄마",
        )
        val fromSms = silentEntity(
            id = "b",
            packageName = "com.android.mms",
            sender = "엄마",
        )
        val fromPhone = silentEntity(
            id = "c",
            packageName = "com.android.dialer",
            sender = "엄마",
        )

        val keys = listOf(fromKakao, fromSms, fromPhone).map { policy.groupKeyFor(it) }.toSet()

        assertEquals(setOf(SilentGroupKey.Sender("엄마")), keys)
    }

    @Test
    fun same_package_with_different_senders_splits_into_distinct_sender_keys() {
        val mom = silentEntity(
            id = "m",
            packageName = "com.kakao.talk",
            sender = "엄마",
        )
        val promo = silentEntity(
            id = "p",
            packageName = "com.kakao.talk",
            sender = "광고팀",
        )

        val momKey = policy.groupKeyFor(mom)
        val promoKey = policy.groupKeyFor(promo)

        assertEquals(SilentGroupKey.Sender("엄마"), momKey)
        assertEquals(SilentGroupKey.Sender("광고팀"), promoKey)
        assertNotEquals(momKey, promoKey)
    }

    @Test
    fun sender_with_surrounding_whitespace_is_trimmed_into_canonical_key() {
        val padded = silentEntity(
            id = "pad",
            packageName = "com.kakao.talk",
            sender = "  엄마  ",
        )
        val clean = silentEntity(
            id = "clean",
            packageName = "com.kakao.talk",
            sender = "엄마",
        )

        assertEquals(policy.groupKeyFor(clean), policy.groupKeyFor(padded))
    }

    @Test
    fun sender_case_is_preserved_so_unicode_stays_intact() {
        val entity = silentEntity(
            id = "u",
            packageName = "com.whatsapp",
            sender = "Müller",
        )

        val key = policy.groupKeyFor(entity)

        assertEquals(SilentGroupKey.Sender("Müller"), key)
    }

    private fun silentEntity(
        id: String,
        packageName: String,
        sender: String?,
    ): NotificationEntity = NotificationEntity(
        id = id,
        appName = packageName,
        packageName = packageName,
        sender = sender,
        title = "제목",
        body = "본문",
        postedAtMillis = 1_700_000_000_000L,
        status = "SILENT",
        reasonTags = "",
        score = null,
        isBundled = false,
        isPersistent = false,
        contentSignature = "$packageName|$id",
    )
}
