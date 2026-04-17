package com.smartnoti.app.ui.screens.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.onboarding.OnboardingRequirement
import com.smartnoti.app.onboarding.OnboardingStatus
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.Navy800
import com.smartnoti.app.ui.theme.Navy900

@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var status by remember { mutableStateOf(OnboardingPermissions.currentStatus(context)) }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        status = OnboardingPermissions.currentStatus(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = OnboardingPermissions.currentStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(status.allRequirementsMet) {
        if (status.allRequirementsMet) {
            onCompleted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "SmartNoti",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "시작하려면 권한을\n허용해 주세요",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "SmartNoti가 알림을 읽고 정리하려면 아래 권한이 필요해요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RequirementCard(
                        title = "알림 접근 허용",
                        description = "설정 → 알림 접근에서 SmartNoti를 켜 주세요.",
                        granted = status.notificationListenerGranted,
                        actionLabel = if (status.notificationListenerGranted) "허용됨" else "설정 열기",
                        onAction = {
                            try {
                                context.startActivity(
                                    OnboardingPermissions.notificationListenerSettingsIntent()
                                )
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    "알림 접근 설정을 찾을 수 없어요.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                    if (status.postNotificationsRequired) {
                        RequirementCard(
                            title = "알림 표시 권한",
                            description = "Android 13 이상에서는 알림 표시 권한이 필요해요.",
                            granted = status.postNotificationsGranted,
                            actionLabel = if (status.postNotificationsGranted) "허용됨" else "권한 요청",
                            onAction = {
                                postNotificationsLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            },
                        )
                    }
                }
            }

            Button(
                onClick = onCompleted,
                enabled = status.allRequirementsMet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (status.allRequirementsMet) {
                        "시작하기"
                    } else {
                        pendingSummary(status)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RequirementCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val borderColor = if (granted) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    } else {
        BorderSubtle
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy800, MaterialTheme.shapes.medium)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (granted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (granted) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = onAction,
            enabled = !granted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (granted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.NotificationsActive
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(actionLabel)
        }
    }
}

private fun pendingSummary(status: OnboardingStatus): String {
    val first = status.pendingRequirements.firstOrNull() ?: return "시작하기"
    return when (first) {
        OnboardingRequirement.NOTIFICATION_LISTENER -> "알림 접근을 허용해 주세요"
        OnboardingRequirement.POST_NOTIFICATIONS -> "알림 표시 권한을 허용해 주세요"
    }
}
