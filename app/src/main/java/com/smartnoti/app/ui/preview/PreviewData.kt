package com.smartnoti.app.ui.preview

import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

object PreviewData {
    val priorityNotification = NotificationUiModel(
        id = "1",
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "엄마",
        title = "엄마",
        body = "오늘 저녁 몇 시에 와?",
        receivedAtLabel = "3분 전",
        status = NotificationStatusUi.PRIORITY,
        reasonTags = listOf("가족 연락처"),
        score = 95,
    )

    val digestNotification = NotificationUiModel(
        id = "2",
        appName = "쿠팡",
        packageName = "com.coupang.mobile",
        sender = null,
        title = "오늘의 특가",
        body = "장바구니 상품이 할인 중이에요",
        receivedAtLabel = "12분 전",
        status = NotificationStatusUi.DIGEST,
        reasonTags = listOf("쇼핑 앱", "업무 집중"),
        score = 42,
    )

    val digestGroup = DigestGroupUiModel(
        id = "dg1",
        appName = "쿠팡",
        count = 3,
        summary = "쇼핑 관련 알림 3건",
        items = listOf(
            digestNotification,
            digestNotification.copy(id = "3", title = "가격 하락", body = "관심 상품 가격이 내려갔어요"),
            digestNotification.copy(id = "4", title = "배송 추천", body = "지금 주문하면 내일 도착"),
        ),
    )

    val rules = listOf(
        RuleUiModel(
            id = "r1",
            title = "엄마",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        ),
        RuleUiModel(
            id = "r2",
            title = "쿠팡",
            subtitle = "Digest로 묶기",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.coupang.mobile",
        ),
        RuleUiModel(
            id = "r3",
            title = "인증번호",
            subtitle = "즉시 전달",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "인증번호",
        ),
    )
}
