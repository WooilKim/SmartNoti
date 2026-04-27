package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.usecase.NotificationDetailOnboardingRecommendationSummary
import com.smartnoti.app.domain.usecase.NotificationDetailReasonSections
import com.smartnoti.app.domain.usecase.QuietHoursExplainer
import com.smartnoti.app.ui.components.ReasonChipRow
import com.smartnoti.app.ui.components.RuleHitChipRow

/**
 * "왜 이렇게 처리됐나요?" 카드. classifier signals / quiet-hours explainer /
 * rule hits 세 sub-section 중 적어도 하나가 채워졌을 때만 emit. 호출자는
 * visibility 조건을 알 필요 없이 항상 호출하면 됨 — gate 가 함수 안에서
 * 처리됨. Refactor `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md`
 * Task 2 에서 root composable 에서 cut-and-paste 됨, 동작 변경 없음.
 */
@Composable
internal fun NotificationDetailReasonCard(
    sections: NotificationDetailReasonSections?,
    quietHoursExplainer: QuietHoursExplainer?,
    onRuleClick: (String) -> Unit,
) {
    val hasReasonContent = (sections != null &&
        (sections.classifierSignals.isNotEmpty() || sections.ruleHits.isNotEmpty())) ||
        quietHoursExplainer != null
    if (!hasReasonContent) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "왜 이렇게 처리됐나요?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (sections?.classifierSignals?.isNotEmpty() == true) {
                ReasonSubSection(
                    title = "SmartNoti 가 본 신호",
                    description = "분류에 참고한 내부 신호예요. 직접 수정할 수는 없어요.",
                ) {
                    ReasonChipRow(sections.classifierSignals)
                }
            }
            // Plan
            // `docs/plans/2026-04-26-quiet-hours-explainer-copy.md`
            // Task 3: when the quiet-hours branch decided this
            // notification, surface a one-line explainer so users
            // do not have to infer what the `조용한 시간` chip
            // means on its own. Higher-precedence signals (e.g.
            // 사용자 규칙) suppress the explainer upstream.
            if (quietHoursExplainer != null) {
                ReasonSubSection(
                    title = "지금 적용된 정책",
                    description = "사용자가 설정한 시간 정책에 따라 자동으로 분류됐어요.",
                ) {
                    Text(
                        quietHoursExplainer.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (sections?.ruleHits?.isNotEmpty() == true) {
                ReasonSubSection(
                    title = "적용된 규칙",
                    description = "내 규칙 탭에서 수정하거나 끌 수 있는 규칙이에요. 탭하면 해당 규칙으로 이동해요.",
                ) {
                    RuleHitChipRow(
                        hits = sections.ruleHits,
                        onHitClick = { onRuleClick(it.ruleId) },
                    )
                }
            }
        }
    }
}

/**
 * 온보딩 추천 카드. summary 가 null 이면 nothing emit. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 2 에서
 * root composable 에서 cut-and-paste 됨, 동작 변경 없음.
 */
@Composable
internal fun NotificationDetailOnboardingRecommendationCard(
    summary: NotificationDetailOnboardingRecommendationSummary?,
) {
    if (summary == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                summary.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                summary.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Reason 카드 안에서 sub-section 하나를 그리는 작은 helper. title / description /
 * content 슬롯. Refactor 시 reason card 와 함께 같은 파일로 옮김 — 같은 파일
 * 안에서만 호출되므로 `private` 유지.
 */
@Composable
private fun ReasonSubSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
