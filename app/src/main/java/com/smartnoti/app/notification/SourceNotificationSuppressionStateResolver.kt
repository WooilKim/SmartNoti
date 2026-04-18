package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState

internal object SourceNotificationSuppressionStateResolver {
    fun resolve(
        decision: NotificationDecision,
        suppressDigestAndSilent: Boolean,
        suppressedApps: Set<String>,
        packageName: String,
        hidePersistentSourceNotifications: Boolean,
        isPersistent: Boolean,
        bypassPersistentHiding: Boolean,
        sourceRouting: SourceNotificationRouting,
    ): SourceNotificationSuppressionState {
        if (sourceRouting.cancelSourceNotification) {
            return SourceNotificationSuppressionState.CANCEL_ATTEMPTED
        }
        if (isPersistent && bypassPersistentHiding) {
            return SourceNotificationSuppressionState.PERSISTENT_PROTECTED
        }
        if (decision == NotificationDecision.PRIORITY) {
            return SourceNotificationSuppressionState.PRIORITY_KEPT
        }
        if (suppressDigestAndSilent && packageName !in suppressedApps) {
            return SourceNotificationSuppressionState.APP_NOT_SELECTED
        }
        if (!suppressDigestAndSilent && !(isPersistent && hidePersistentSourceNotifications)) {
            return SourceNotificationSuppressionState.NOT_CONFIGURED
        }
        return SourceNotificationSuppressionState.NOT_CONFIGURED
    }

    fun replacementNotificationRecorded(
        sourceRouting: SourceNotificationRouting,
        replacementNotificationPosted: Boolean,
    ): Boolean {
        return sourceRouting.notifyReplacementNotification && replacementNotificationPosted
    }
}
