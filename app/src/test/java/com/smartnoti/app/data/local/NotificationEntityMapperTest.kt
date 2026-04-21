package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationEntityMapperTest {

    @Test
    fun notification_ui_model_maps_to_entity_and_back() {
        val model = NotificationUiModel(
            id = "com.kakao.talk:1700000000",
            appName = "카카오톡",
            packageName = "com.kakao.talk",
            sender = "엄마",
            title = "엄마",
            body = "오늘 저녁 몇 시에 와?",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.PRIORITY,
            reasonTags = listOf("중요한 사람", "발신자 있음"),
            score = 95,
            isBundled = false,
            isPersistent = false,
            deliveryChannelKey = "smartnoti_priority",
            alertLevel = AlertLevel.LOUD,
            vibrationMode = VibrationMode.STRONG,
            headsUpEnabled = true,
            lockScreenVisibility = LockScreenVisibilityMode.PRIVATE,
            sourceSuppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
            replacementNotificationIssued = true,
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_000_000)
        val roundTrip = entity.toUiModel()

        assertEquals(model.id, entity.id)
        assertEquals("중요한 사람|발신자 있음", entity.reasonTags)
        assertEquals("smartnoti_priority", entity.deliveryChannelKey)
        assertEquals("LOUD", entity.alertLevel)
        assertEquals(model.id, roundTrip.id)
        assertEquals(model.appName, roundTrip.appName)
        assertEquals(model.sender, roundTrip.sender)
        assertEquals(model.status, roundTrip.status)
        assertEquals(model.reasonTags, roundTrip.reasonTags)
        assertEquals(model.deliveryChannelKey, roundTrip.deliveryChannelKey)
        assertEquals(model.alertLevel, roundTrip.alertLevel)
        assertEquals(model.vibrationMode, roundTrip.vibrationMode)
        assertEquals(model.headsUpEnabled, roundTrip.headsUpEnabled)
        assertEquals(model.lockScreenVisibility, roundTrip.lockScreenVisibility)
        assertEquals(model.sourceSuppressionState, roundTrip.sourceSuppressionState)
        assertEquals(model.replacementNotificationIssued, roundTrip.replacementNotificationIssued)
    }

    @Test
    fun empty_reason_tags_round_trip_to_empty_list() {
        val entity = NotificationEntity(
            id = "id-1",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "제목",
            body = "본문",
            postedAtMillis = 1_700_000_000_000,
            status = NotificationStatusUi.SILENT.name,
            reasonTags = "",
            score = null,
            isBundled = false,
            isPersistent = false,
            contentSignature = "제목 본문",
            deliveryChannelKey = "smartnoti_silent",
            alertLevel = "NONE",
            vibrationMode = "OFF",
            headsUpEnabled = false,
            lockScreenVisibility = "SECRET",
        )

        val model = entity.toUiModel()

        assertEquals(emptyList<String>(), model.reasonTags)
        assertEquals(NotificationStatusUi.SILENT, model.status)
        assertEquals("smartnoti_silent", model.deliveryChannelKey)
        assertEquals(AlertLevel.NONE, model.alertLevel)
    }

    @Test
    fun legacy_delivery_metadata_values_are_mapped_to_new_enums() {
        val entity = NotificationEntity(
            id = "id-legacy",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "제목",
            body = "본문",
            postedAtMillis = 1_700_000_000_000,
            status = NotificationStatusUi.DIGEST.name,
            reasonTags = "",
            score = null,
            isBundled = false,
            isPersistent = false,
            contentSignature = "제목 본문",
            deliveryChannelKey = "smartnoti_digest",
            alertLevel = "HIGH",
            vibrationMode = "DEFAULT",
            headsUpEnabled = true,
            lockScreenVisibility = "PRIVATE",
            sourceSuppressionState = "APP_NOT_SELECTED",
            replacementNotificationIssued = false,
        )

        val model = entity.toUiModel()

        assertEquals(AlertLevel.LOUD, model.alertLevel)
        assertEquals(VibrationMode.STRONG, model.vibrationMode)
        assertEquals(LockScreenVisibilityMode.PRIVATE, model.lockScreenVisibility)
        assertEquals(SourceNotificationSuppressionState.APP_NOT_SELECTED, model.sourceSuppressionState)
        assertFalse(model.replacementNotificationIssued)
    }

    @Test
    fun silent_mode_archived_round_trips_through_entity() {
        val model = NotificationUiModel(
            id = "com.news.app:1700000200",
            appName = "뉴스",
            packageName = "com.news.app",
            sender = null,
            title = "프로모션",
            body = "오늘만 30% 할인",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.SILENT,
            reasonTags = listOf("광고"),
            silentMode = SilentMode.ARCHIVED,
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_200_000)

        assertEquals("ARCHIVED", entity.silentMode)

        val roundTrip = entity.toUiModel()
        assertEquals(SilentMode.ARCHIVED, roundTrip.silentMode)
        assertEquals(NotificationStatusUi.SILENT, roundTrip.status)
    }

    @Test
    fun silent_mode_processed_round_trips_through_entity() {
        val model = NotificationUiModel(
            id = "com.news.app:1700000300",
            appName = "뉴스",
            packageName = "com.news.app",
            sender = null,
            title = "프로모션",
            body = "오늘만 30% 할인",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.SILENT,
            reasonTags = listOf("광고"),
            silentMode = SilentMode.PROCESSED,
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_300_000)

        assertEquals("PROCESSED", entity.silentMode)

        val roundTrip = entity.toUiModel()
        assertEquals(SilentMode.PROCESSED, roundTrip.silentMode)
    }

    @Test
    fun silent_mode_null_for_non_silent_round_trips_as_null() {
        val model = NotificationUiModel(
            id = "com.kakao.talk:1700000400",
            appName = "카카오톡",
            packageName = "com.kakao.talk",
            sender = "엄마",
            title = "엄마",
            body = "오늘 저녁 몇 시에 와?",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.PRIORITY,
            reasonTags = listOf("중요한 사람"),
            silentMode = null,
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_400_000)

        assertNull(entity.silentMode)

        val roundTrip = entity.toUiModel()
        assertNull(roundTrip.silentMode)
    }

    @Test
    fun legacy_entity_without_silent_mode_defaults_to_null() {
        val entity = NotificationEntity(
            id = "legacy-silent",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "알림",
            body = "내용",
            postedAtMillis = 1_700_000_500_000,
            status = NotificationStatusUi.SILENT.name,
            reasonTags = "",
            score = null,
            isBundled = false,
            isPersistent = false,
            contentSignature = "알림 내용",
        )

        val model = entity.toUiModel()

        assertNull(model.silentMode)
    }

    @Test
    fun unknown_silent_mode_string_falls_back_to_null() {
        val entity = NotificationEntity(
            id = "bad-mode",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "알림",
            body = "내용",
            postedAtMillis = 1_700_000_600_000,
            status = NotificationStatusUi.SILENT.name,
            reasonTags = "",
            score = null,
            isBundled = false,
            isPersistent = false,
            contentSignature = "알림 내용",
            silentMode = "NOT_A_VALID_MODE",
        )

        val model = entity.toUiModel()

        assertNull(model.silentMode)
    }

    @Test
    fun matched_rule_ids_round_trip_as_comma_separated_string() {
        val model = NotificationUiModel(
            id = "com.kakao.talk:1700000700",
            appName = "카카오톡",
            packageName = "com.kakao.talk",
            sender = "고객",
            title = "고객",
            body = "오늘 회의 시간 확인 부탁드려요",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.PRIORITY,
            reasonTags = listOf("사용자 규칙", "고객"),
            matchedRuleIds = listOf("r-vip-customer", "r-keyword-urgent"),
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_700_000)

        assertEquals("r-vip-customer,r-keyword-urgent", entity.ruleHitIds)

        val roundTrip = entity.toUiModel()
        assertEquals(listOf("r-vip-customer", "r-keyword-urgent"), roundTrip.matchedRuleIds)
    }

    @Test
    fun empty_matched_rule_ids_persist_as_null_and_round_trip_to_empty_list() {
        val model = NotificationUiModel(
            id = "com.news.app:1700000800",
            appName = "뉴스",
            packageName = "com.news.app",
            sender = null,
            title = "속보",
            body = "중요한 뉴스",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.SILENT,
            reasonTags = emptyList(),
            matchedRuleIds = emptyList(),
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_800_000)

        assertNull(entity.ruleHitIds)

        val roundTrip = entity.toUiModel()
        assertEquals(emptyList<String>(), roundTrip.matchedRuleIds)
    }

    @Test
    fun legacy_entity_with_null_rule_hit_ids_maps_to_empty_matched_rule_ids() {
        val entity = NotificationEntity(
            id = "legacy-no-rule-hits",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "알림",
            body = "내용",
            postedAtMillis = 1_700_000_900_000,
            status = NotificationStatusUi.SILENT.name,
            reasonTags = "",
            score = null,
            isBundled = false,
            isPersistent = false,
            contentSignature = "알림 내용",
            // ruleHitIds defaults to null (schema v7 row).
        )

        val model = entity.toUiModel()

        assertEquals(emptyList<String>(), model.matchedRuleIds)
    }

    @Test
    fun content_signature_round_trip_is_preserved() {
        val model = NotificationUiModel(
            id = "com.news.app:1700000100",
            appName = "뉴스",
            packageName = "com.news.app",
            sender = null,
            title = "속보",
            body = "중요 뉴스가 도착했어요",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.DIGEST,
            reasonTags = listOf("반복 알림"),
            score = 10,
            isBundled = true,
            isPersistent = false,
            deliveryChannelKey = "smartnoti_digest",
            alertLevel = AlertLevel.SOFT,
            vibrationMode = VibrationMode.LIGHT,
            headsUpEnabled = false,
            lockScreenVisibility = LockScreenVisibilityMode.PRIVATE,
        )

        val entity = model.toEntity(
            postedAtMillis = 1_700_000_100_000,
            contentSignature = "속보 중요 뉴스가 도착했어요",
        )

        assertEquals("속보 중요 뉴스가 도착했어요", entity.contentSignature)
    }
}
