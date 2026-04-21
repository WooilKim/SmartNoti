package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.buildNotificationId
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.toUiStatus

class NotificationCaptureProcessor(
    private val classifier: NotificationClassifier,
    private val deliveryProfilePolicy: DeliveryProfilePolicy,
) {
    fun process(
        input: CapturedNotificationInput,
        rules: List<RuleUiModel> = emptyList(),
        settings: SmartNotiSettings,
    ): NotificationUiModel {
        val classification = classifier.classify(
            com.smartnoti.app.domain.model.ClassificationInput(
                sender = input.sender,
                packageName = input.packageName,
                title = input.title,
                body = input.body,
                quietHours = input.quietHours,
                duplicateCountInWindow = input.duplicateCountInWindow,
                hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            ),
            rules = rules,
        )
        val decision = classification.decision
        val deliveryProfile = deliveryProfilePolicy.resolve(
            decision = decision,
            settings = settings,
            context = NotificationContext(
                quietHoursEnabled = input.quietHours,
                quietHoursPolicy = com.smartnoti.app.domain.usecase.QuietHoursPolicy(startHour = 0, endHour = 0),
                currentHourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                duplicateCountInWindow = input.duplicateCountInWindow,
            ),
            isPersistent = input.isPersistent,
        )

        return NotificationUiModel(
            id = buildNotificationId(
                packageName = input.packageName,
                postedAtMillis = input.postedAtMillis,
                sourceEntryKey = input.sourceEntryKey,
            ),
            appName = input.appName,
            packageName = input.packageName,
            sender = input.sender,
            title = input.title,
            body = input.body,
            receivedAtLabel = "방금",
            status = decision.toUiStatus(),
            reasonTags = buildReasonTags(input, decision, rules),
            score = null,
            isPersistent = input.isPersistent,
            deliveryChannelKey = deliveryProfile.channelKey,
            alertLevel = deliveryProfile.alertLevel,
            vibrationMode = deliveryProfile.vibrationMode,
            headsUpEnabled = deliveryProfile.headsUpEnabled,
            lockScreenVisibility = deliveryProfile.lockScreenVisibilityMode,
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
            postedAtMillis = input.postedAtMillis,
            replacementNotificationIssued = false,
            sourceEntryKey = input.sourceEntryKey,
            matchedRuleIds = classification.matchedRuleIds,
        )
    }

    private fun buildReasonTags(
        input: CapturedNotificationInput,
        decision: NotificationDecision,
        rules: List<RuleUiModel>,
    ): List<String> {
        val tags = linkedSetOf<String>()

        if (!input.sender.isNullOrBlank()) {
            tags += "발신자 있음"
        }
        if (input.sender in setOf("엄마", "팀장")) {
            tags += "중요한 사람"
        }
        matchingRule(input, rules)?.let { rule ->
            tags += "사용자 규칙"
            tags += rule.title
            if (rule.title in setOf("프로모션 알림", "반복 알림", "중요 알림")) {
                tags += "온보딩 추천"
            }
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
        if (input.isPersistent) {
            tags += "지속 알림"
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

    private fun matchingRule(
        input: CapturedNotificationInput,
        rules: List<RuleUiModel>,
    ): RuleUiModel? {
        val content = listOf(input.title, input.body).joinToString(" ")
        return rules.firstOrNull { rule ->
            rule.enabled && when (rule.type) {
                RuleTypeUi.PERSON -> !input.sender.isNullOrBlank() && input.sender.equals(rule.matchValue, ignoreCase = true)
                RuleTypeUi.APP -> input.packageName.equals(rule.matchValue, ignoreCase = true)
                RuleTypeUi.KEYWORD -> rule.matchValue
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .any { keyword -> content.contains(keyword, ignoreCase = true) }
                RuleTypeUi.SCHEDULE,
                RuleTypeUi.REPEAT_BUNDLE -> false
            }
        }
    }
}
