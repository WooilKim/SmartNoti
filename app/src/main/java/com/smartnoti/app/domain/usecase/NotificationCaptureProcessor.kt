package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

class NotificationCaptureProcessor(
    private val classifier: NotificationClassifier,
) {
    fun process(input: CapturedNotificationInput): NotificationUiModel {
        val decision = classifier.classify(
            com.smartnoti.app.domain.model.ClassificationInput(
                sender = input.sender,
                packageName = input.packageName,
                title = input.title,
                body = input.body,
                quietHours = input.quietHours,
                duplicateCountInWindow = input.duplicateCountInWindow,
            )
        )

        return NotificationUiModel(
            id = "${input.packageName}:${input.postedAtMillis}",
            appName = input.appName,
            packageName = input.packageName,
            sender = input.sender,
            title = input.title.ifBlank { input.appName },
            body = input.body,
            receivedAtLabel = "방금",
            status = decision.toUiStatus(),
            reasonTags = buildReasonTags(input, decision),
            score = null,
        )
    }

    private fun buildReasonTags(
        input: CapturedNotificationInput,
        decision: NotificationDecision,
    ): List<String> {
        val tags = linkedSetOf<String>()

        if (!input.sender.isNullOrBlank()) {
            tags += "발신자 있음"
        }
        if (input.sender in setOf("엄마", "팀장")) {
            tags += "중요한 사람"
        }
        if (input.quietHours) {
            tags += "조용한 시간"
        }
        if (input.packageName == "com.coupang.mobile") {
            tags += "쇼핑 앱"
        }
        if (input.duplicateCountInWindow >= 3) {
            tags += "반복 알림"
        }
        if (listOf(input.title, input.body).joinToString(" ").contains("인증번호")) {
            tags += "중요 키워드"
        }

        if (tags.isEmpty()) {
            tags += when (decision) {
                NotificationDecision.PRIORITY -> "즉시 확인"
                NotificationDecision.DIGEST -> "나중에 정리"
                NotificationDecision.SILENT -> "조용히 처리"
            }
        }

        return tags.toList()
    }

    private fun NotificationDecision.toUiStatus(): NotificationStatusUi = when (this) {
        NotificationDecision.PRIORITY -> NotificationStatusUi.PRIORITY
        NotificationDecision.DIGEST -> NotificationStatusUi.DIGEST
        NotificationDecision.SILENT -> NotificationStatusUi.SILENT
    }
}
