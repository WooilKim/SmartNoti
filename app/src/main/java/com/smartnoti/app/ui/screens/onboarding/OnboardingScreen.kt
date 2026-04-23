package com.smartnoti.app.ui.screens.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.onboarding.OnboardingRequirement
import com.smartnoti.app.onboarding.OnboardingStatus
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.Navy800
import com.smartnoti.app.ui.theme.Navy900
import kotlinx.coroutines.launch

private enum class OnboardingStep {
    PERMISSIONS,
    QUICK_START,
}

private data class QuickStartPreviewSummary(
    val title: String,
    val body: String,
    val supporting: String,
)

private fun buildQuickStartPreviewSummary(
    selectedPresetIds: Set<OnboardingQuickStartPresetId>,
    summaryText: String,
): QuickStartPreviewSummary {
    val allSelected = selectedPresetIds.containsAll(
        setOf(
            OnboardingQuickStartPresetId.PROMO_QUIETING,
            OnboardingQuickStartPresetId.REPEAT_BUNDLING,
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
        ),
    )
    return when {
        selectedPresetIds.isEmpty() -> QuickStartPreviewSummary(
            title = "직접 고르기",
            body = "기본 추천 없이 시작하고, 나중에 Rules에서 필요한 규칙만 직접 정할 수 있어요.",
            supporting = "자동으로 추가되는 빠른 시작 규칙 없음",
        )
        allSelected -> QuickStartPreviewSummary(
            title = "방해는 줄이고 중요한 건 유지",
            body = summaryText,
            supporting = "프로모션·반복 알림은 정리하고 결제·배송·인증은 바로 보여줘요",
        )
        selectedPresetIds.contains(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY) -> QuickStartPreviewSummary(
            title = "놓치면 안 되는 알림 보호",
            body = summaryText,
            supporting = "결제·배송·인증처럼 중요한 알림이 묻히지 않게 우선 전달해요",
        )
        else -> QuickStartPreviewSummary(
            title = "알림 소음 먼저 줄이기",
            body = summaryText,
            supporting = "여러 앱의 반복·프로모션 알림을 한 번에 덜 방해되게 정리해요",
        )
    }
}

private fun OnboardingQuickStartPresetUiModel.stateChipLabel(): String = when (id) {
    OnboardingQuickStartPresetId.PROMO_QUIETING -> "여러 앱 적용"
    OnboardingQuickStartPresetId.REPEAT_BUNDLING -> "반복 소음 감소"
    OnboardingQuickStartPresetId.IMPORTANT_PRIORITY -> "중요 알림 보호"
}

@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val quickStartPresetBuilder = remember { OnboardingQuickStartPresetBuilder() }
    val quickStartRuleApplier = remember { OnboardingQuickStartRuleApplier(RuleDraftFactory()) }
    val quickStartCategoryApplier = remember { OnboardingQuickStartCategoryApplier() }
    val quickStartSettingsApplier = remember {
        OnboardingQuickStartSettingsApplier(
            ruleApplier = quickStartRuleApplier,
            categoryApplier = quickStartCategoryApplier,
        )
    }
    val selectionSummaryBuilder = remember { OnboardingQuickStartSelectionSummaryBuilder() }
    val quickStartPresets = remember { quickStartPresetBuilder.buildDefaultPresets() }
    val defaultSelectedPresetIds = remember(quickStartPresets) {
        quickStartPresets
            .filter { it.defaultEnabled }
            .map { it.id.name }
    }

    var status by remember { mutableStateOf(OnboardingPermissions.currentStatus(context)) }
    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.PERMISSIONS.name) }
    var selectedPresetIds by rememberSaveable { mutableStateOf(defaultSelectedPresetIds) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    val currentStepUi = OnboardingStep.valueOf(currentStep)
    val selectedPresetIdSet = selectedPresetIds.map { presetId -> OnboardingQuickStartPresetId.valueOf(presetId) }.toSet()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OnboardingHero(step = currentStepUi)
                if (currentStepUi == OnboardingStep.PERMISSIONS) {
                    PermissionStepContent(
                        status = status,
                        onOpenNotificationListenerSettings = {
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
                        onRequestPostNotifications = {
                            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                } else {
                    QuickStartStepContent(
                        presets = quickStartPresets,
                        selectedPresetIds = selectedPresetIdSet,
                        onTogglePreset = { presetId ->
                            val presetValue = presetId.name
                            selectedPresetIds = if (selectedPresetIds.contains(presetValue)) {
                                selectedPresetIds - presetValue
                            } else {
                                selectedPresetIds + presetValue
                            }
                        },
                        summaryText = selectionSummaryBuilder.build(selectedPresetIdSet),
                    )
                }
            }

            if (currentStepUi == OnboardingStep.PERMISSIONS) {
                Button(
                    onClick = { currentStep = OnboardingStep.QUICK_START.name },
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
                            "빠른 시작 추천 보기"
                        } else {
                            pendingSummary(status)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    quickStartSettingsApplier.applySelection(
                                        rulesRepository = rulesRepository,
                                        settingsRepository = settingsRepository,
                                        categoriesRepository = categoriesRepository,
                                        notificationRepository = notificationRepository,
                                        selectedPresetIds = selectedPresetIdSet,
                                    )
                                    onCompleted()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            text = if (isSaving) "적용하는 중..." else "이대로 시작할게요",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    rulesRepository.replaceAllRules(emptyList())
                                    onCompleted()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("직접 설정할게요")
                    }
                    Text(
                        text = "선택한 추천은 나중에 Rules에서 언제든 바꿀 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingHero(step: OnboardingStep) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape,
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
                text = "SmartNoti",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (step == OnboardingStep.PERMISSIONS) {
                    "시작하려면 권한을\n허용해 주세요"
                } else {
                    "먼저 이런 알림부터\n정리해볼까요?"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (step == OnboardingStep.PERMISSIONS) {
                    "SmartNoti가 알림을 읽고 정리하려면 아래 권한이 필요해요."
                } else {
                    "앱마다 따로 설정하지 않아도 여러 앱에 한 번에 적용돼요. 무엇이 조용해지고, 무엇은 그대로 보이는지 바로 확인할 수 있어요."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStepContent(
    status: OnboardingStatus,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RequirementCard(
            title = "알림 접근 허용",
            description = "설정 → 알림 접근에서 SmartNoti를 켜 주세요.",
            granted = status.notificationListenerGranted,
            actionLabel = if (status.notificationListenerGranted) "허용됨" else "설정 열기",
            onAction = onOpenNotificationListenerSettings,
        )
        if (status.postNotificationsRequired) {
            RequirementCard(
                title = "알림 표시 권한",
                description = "Android 13 이상에서는 알림 표시 권한이 필요해요.",
                granted = status.postNotificationsGranted,
                actionLabel = if (status.postNotificationsGranted) "허용됨" else "권한 요청",
                onAction = onRequestPostNotifications,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickStartStepContent(
    presets: List<OnboardingQuickStartPresetUiModel>,
    selectedPresetIds: Set<OnboardingQuickStartPresetId>,
    onTogglePreset: (OnboardingQuickStartPresetId) -> Unit,
    summaryText: String,
) {
    val previewSummary = remember(selectedPresetIds, summaryText) {
        buildQuickStartPreviewSummary(selectedPresetIds, summaryText)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SmartSurfaceCard(
            containerColor = Navy800,
            borderColor = BorderSubtle,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "예상되는 변화",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = previewSummary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = previewSummary.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContextBadge(
                    label = "여러 앱 한 번에",
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                ContextBadge(
                    label = "중요 알림은 유지",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
                ContextBadge(
                    label = "나중에 변경 가능",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = previewSummary.supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        presets.forEach { preset ->
            QuickStartPresetCard(
                preset = preset,
                selected = selectedPresetIds.contains(preset.id),
                onClick = { onTogglePreset(preset.id) },
            )
        }
    }
}

@Composable
private fun QuickStartPresetCard(
    preset: OnboardingQuickStartPresetUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        BorderSubtle
    }

    SmartSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, MaterialTheme.shapes.large),
        containerColor = if (selected) Navy800.copy(alpha = 0.96f) else Navy800,
        borderColor = androidx.compose.ui.graphics.Color.Transparent,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    ContextBadge(
                        label = preset.stateChipLabel(),
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.material3.HorizontalDivider(color = BorderSubtle.copy(alpha = 0.72f))
        ImpactSection(section = preset.reducedSection)
        ImpactSection(section = preset.preservedSection)
        androidx.compose.material3.HorizontalDivider(color = BorderSubtle.copy(alpha = 0.72f))
        Text(
            text = preset.footerText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImpactSection(section: OnboardingQuickStartImpactSection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        section.examples.forEach { example ->
            Text(
                text = "• $example",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val first = status.pendingRequirements.firstOrNull() ?: return "빠른 시작 추천 보기"
    return when (first) {
        OnboardingRequirement.NOTIFICATION_LISTENER -> "알림 접근을 허용해 주세요"
        OnboardingRequirement.POST_NOTIFICATIONS -> "알림 표시 권한을 허용해 주세요"
    }
}
