package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

fun NotificationUiModel.toEntity(
    postedAtMillis: Long,
    contentSignature: String = listOf(title, body).joinToString(" ").trim(),
): NotificationEntity = NotificationEntity(
    id = id,
    appName = appName,
    packageName = packageName,
    sender = sender,
    title = title,
    body = body,
    postedAtMillis = postedAtMillis,
    status = status.name,
    reasonTags = reasonTags.joinToString("|"),
    score = score,
    isBundled = isBundled,
    contentSignature = contentSignature,
)

fun NotificationEntity.toUiModel(): NotificationUiModel = NotificationUiModel(
    id = id,
    appName = appName,
    packageName = packageName,
    sender = sender,
    title = title,
    body = body,
    receivedAtLabel = "방금",
    status = NotificationStatusUi.valueOf(status),
    reasonTags = reasonTags.takeIf { it.isNotBlank() }?.split("|") ?: emptyList(),
    score = score,
    isBundled = isBundled,
)
