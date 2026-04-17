package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
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
        )

        val entity = model.toEntity(postedAtMillis = 1_700_000_000_000)
        val roundTrip = entity.toUiModel()

        assertEquals(model.id, entity.id)
        assertEquals("중요한 사람|발신자 있음", entity.reasonTags)
        assertEquals(model.id, roundTrip.id)
        assertEquals(model.appName, roundTrip.appName)
        assertEquals(model.sender, roundTrip.sender)
        assertEquals(model.status, roundTrip.status)
        assertEquals(model.reasonTags, roundTrip.reasonTags)
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
        )

        val model = entity.toUiModel()

        assertEquals(emptyList<String>(), model.reasonTags)
        assertEquals(NotificationStatusUi.SILENT, model.status)
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
        )

        val entity = model.toEntity(
            postedAtMillis = 1_700_000_100_000,
            contentSignature = "속보 중요 뉴스가 도착했어요",
        )

        assertEquals("속보 중요 뉴스가 도착했어요", entity.contentSignature)
    }
}
