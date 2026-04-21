---
id: notification-detail
title: 알림 상세 및 피드백 액션
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

한 건의 알림에 대해 SmartNoti 가 어떻게 판단했고 어떻게 전달했는지 투명하게 보여주고, 즉석에서 재분류해 학습시킬 수 있는 단일 상세 화면을 제공한다.

## Preconditions

- 해당 `notificationId` 가 DB 에 존재 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료

## Trigger

- Home / Priority / Digest / Hidden / Insight 어디서든 카드 탭 → `navController.navigate(Routes.Detail.create(id))`
- replacement 알림 본문 탭 → 딥링크로 parent route + detail 연결 (→ [digest-suppression](digest-suppression.md))
- IGNORE 상태 row 는 기본 뷰에서 제외되므로 Detail 도달 경로가 제한됨 — Settings 의 "무시된 알림 아카이브 표시" 토글이 ON 일 때 [ignored-archive](ignored-archive.md) 화면의 row 탭만 유일한 진입점

## Observable steps

1. `NotificationDetailScreen` composable 마운트, `repository.observeNotification(id)` 구독.
2. `DetailTopBar` 렌더 — 좌측 뒤로가기 `IconButton` + 타이틀 "알림 상세".
3. 대상 알림이 null 이면 `EmptyState("알림을 찾을 수 없어요", ...)` 를 보여주고 return.
4. 존재하면 섹션 순서대로 렌더링:
   - 알림 요약 카드 — appName, sender/title, body, `StatusBadge(status)`
   - "왜 이렇게 처리됐나요?" — `ReasonChipRow(reasonTags)` (FlowRow 이므로 줄바꿈)
   - (선택) 온보딩 추천 반영 카드 — `NotificationDetailOnboardingRecommendationSummaryBuilder`
   - (선택) "어떻게 전달되나요?" — `NotificationDetailDeliveryProfileSummaryBuilder` 결과. 전달 모드/소리/진동/Heads-up/잠금화면 라벨
   - (선택) "원본 알림 처리 상태" — `NotificationDetailSourceSuppressionSummaryBuilder` 결과 (원본 상태 / 대체 알림 여부)
   - "이 알림 학습시키기" 카드 — 4개 액션 버튼 (least-destructive → most-destructive)
5. 액션 탭:
   - `중요로 고정` → `RuleActionUi.ALWAYS_PRIORITY`
   - `Digest로 보내기` → `RuleActionUi.DIGEST`
   - `조용히 처리` → `RuleActionUi.SILENT`
   - `무시` → `RuleActionUi.IGNORE` (파괴적 — 확인 다이얼로그 경유)

   내부 처리 (`중요로 고정` / `Digest로 보내기` / `조용히 처리`):
   1. `NotificationFeedbackPolicy.applyAction(notification, action)` → status/reasonTags 업데이트된 UI 모델 반환
   2. `NotificationRepository.updateNotification(updated)` → DB upsert
   3. `NotificationFeedbackPolicy.toRule(notification, action)` → 새 `RuleUiModel` (sender 있으면 PERSON, 없으면 APP)
   4. `RulesRepository.upsertRule(rule)` — 다음 동일 알림부터 자동 적용

   내부 처리 (`무시`):
   1. `IgnoreConfirmationDialog` 렌더 — "이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기." 확인 문구
   2. 확인 → `applyIgnoreWithUndo`: 이전 `NotificationUiModel` + 이전 매칭 룰 (있으면) 스냅샷 저장 → `applyAction(..., IGNORE)` → `updateNotification` → `upsertRule(IGNORE)` → `onBack()` 으로 Detail pop → 3초 "되돌리기" 스낵바. 스낵바 action 탭 시 status/룰 rollback (새 룰이면 `deleteRule`, 기존 룰이면 prior action 으로 `upsertRule`). Undo 는 in-memory — 스낵바 닫히면 영구화.

## Exit state

- DB 의 알림 row 는 새로운 status/태그로 업데이트됨 (해당 리스트 화면 자동 갱신).
- RulesRepository 에 매칭 룰이 있다면 upsert, 없으면 insert.
- 사용자는 뒤로가기로 원래 화면(Priority/Digest/Hidden/Home)으로 복귀.

## Out of scope

- 룰 편집/삭제 (→ [rules-management](rules-management.md))
- 보호 대상 알림의 원본 유지 보장 (→ [protected-source-notifications](protected-source-notifications.md))
- 알림 자체의 분류 결정 (→ [notification-capture-classify](notification-capture-classify.md))

## Code pointers

- `ui/screens/detail/NotificationDetailScreen` — 4버튼 feedback row + `applyIgnoreWithUndo` 내부 헬퍼 + 로컬 `SnackbarHost`
- `ui/components/IgnoreConfirmationDialog` — 파괴적 액션 확인 다이얼로그
- `domain/usecase/NotificationFeedbackPolicy` — applyAction + toRule (IGNORE 케이스 포함)
- `domain/usecase/NotificationDetailDeliveryProfileSummaryBuilder`
- `domain/usecase/NotificationDetailOnboardingRecommendationSummaryBuilder`
- `domain/usecase/NotificationDetailSourceSuppressionSummaryBuilder`
- `data/local/NotificationRepository#observeNotification`, `#updateNotification`
- `data/rules/RulesRepository#upsertRule`, `#deleteRule` (undo 경로)
- `notification/SmartNotiNotifier#ACTION_IGNORE` — broadcast 상수 (replacement alert 에는 IGNORE 버튼을 노출하지 않음 — replacement 자체가 non-IGNORE 결정)

## Tests

- `NotificationFeedbackPolicyTest` — sender → PERSON, else → APP 규칙 생성
- `NotificationDetailDeliveryProfileSummaryBuilderTest` (있다면)

## Verification recipe

```bash
# 1. 알림 게시 → 분류 → Detail 진입
adb shell cmd notification post -S bigtext -t "광고" Promo1 "오늘의 이벤트"
adb shell am start -n com.smartnoti.app/.MainActivity
# Priority/Digest/Hidden 어느 화면에서든 카드 탭

# 2. "중요로 고정" 탭 → 해당 알림이 Priority 로 이동했는지 확인
# 3. 다음 동일 signature 알림을 보내 자동 Priority 로 분류되는지 확인
```

## Known gaps

- 재분류 시 토스트/확인 UI 없음 (상태만 바뀌어 UX 가 조용함).
- Detail 내부에서 "룰 보기" 바로가기 부재 — 룰이 저장됐는지 즉시 확인하려면 Rules 탭으로 수동 이동 필요.
- 2026-04-21 (journey-tester): Verification recipe step 3 ("다음 동일 signature 알림을 보내 자동 Priority 로 분류되는지 확인") 이 테스트용 sender 로 `"광고"` 를 제안 — 이 값은 온보딩이 기본 주입하는 KEYWORD 룰 (`광고,프로모션,쿠폰,세일,특가,이벤트,혜택 → DIGEST`) 의 매치 대상과 겹쳐 `RuleConflictResolver` 가 PERSON-ALWAYS_PRIORITY 대신 KEYWORD-DIGEST 로 라우팅하므로 step 3 만 단독으로 보면 기대와 달라 보인다. PERSON 분기 자체는 중립 sender (e.g. `TestSender_0421_T12`) 로 검증 시 정상 동작하므로 contract 문제 아님 — recipe 문구를 중립 sender 기반으로 바꾸는 편이 후속 재현성 향상에 도움. (Phase B `ruleHitIds` 를 활용한 "적용된 규칙" 섹션 전용 관측으로 확장되면 자연스레 해소될 가능성 있음.)
- 2026-04-21 (ui-ux-inspector, emulator-5554): "이 알림 학습시키기" 카드의 3-버튼 secondary row (`Digest로 보내기` / `조용히 처리` / `무시`) 에서 `Digest로 보내기` 라벨만 2줄로 줄바꿈되어 버튼 높이가 나머지 두 버튼과 불일치. `ui-improvement.md` 의 "tighter spacing rhythm" + "Lists and rows: tap targets large enough while reducing visual clutter" 항목에 대한 시각 위반 (moderate). 3개 버튼이 동일한 row 를 공유하므로 폭 제약으로 첫 버튼만 wrap — 라벨 단축 (e.g. `Digest` / `조용히` / `무시`) 또는 `FlowRow` 기반 레이아웃 재고 필요. 같은 스크린 screenshot: `/tmp/ui-ignore-detail2.png`.
- 2026-04-21 (ui-ux-inspector, emulator-5554): `IgnoreConfirmationDialog` 의 확정 버튼 "무시" 가 primary accent 파랑을 사용 — 파괴성 신호는 전적으로 본문 카피 ("앱에서도 삭제됩니다") 에 의존. `ui-improvement.md` 의 "accent color sparingly for ... primary actions" 규칙에는 부합하므로 Linear/Superhuman 톤 유지. Minor — 현재 문구 중심 접근이 의도적이라면 그대로 두고, 후속에서 파괴 액션 전용 tonal variant 도입 여부를 제품 결정으로 재확인 필요. Screenshot: `/tmp/ui-ignore-confirm-dialog.png`.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verification (emulator-5554) — Digest 카드 탭 경로로 Observable steps 1~5 전부 관측, 액션 버튼 3종 존재 확인
- 2026-04-21: journey-tester end-to-end re-verify (emulator-5554, APK `lastUpdateTime=2026-04-21 15:47:57`) — `cmd notification post -S bigtext -t '광고' DetailTest_0421 '오늘의 이벤트 테스트'` → DIGEST 분류 → Digest 탭 → 프리뷰 row 탭 → Detail 진입. Observable steps 1–4 의 모든 섹션 (알림 요약 / `StatusBadge=Digest` / "왜 이렇게 처리됐나요?" + 5개 chip / 온보딩 추천 카드 / 전달 모드 5개 라벨 / 원본 상태+대체 알림 / "이 알림 학습시키기" 3 버튼) 전부 렌더 확인. "중요로 고정" 탭 → DB row `status=PRIORITY` 로 업데이트, `RulesRepository` 에 `person:광고|광고|항상 바로 보기|PERSON|ALWAYS_PRIORITY` 신규 upsert (Observable step 5.i–iv 증명). PERSON→ALWAYS_PRIORITY 자동 적용은 별도 sender (`TestSender_0421_T12`) 로 확인 — 동일 sender 재-post 시 `reasonTags` 에 sender 레이블 + "사용자 규칙" 태그 + `status=PRIORITY` 반영. DRIFT 없음.
- 2026-04-21: "이 알림 학습시키기" 카드에 4번째 버튼 **"무시"** 추가 — 파괴성을 드러내기 위해 least → most destructive 순서로 배치 (중요로 고정 → Digest → 조용히 → 무시). 탭 시 `IgnoreConfirmationDialog` 확인 다이얼로그 + 확정 시 `applyIgnoreWithUndo` 가 `NotificationFeedbackPolicy.applyAction(IGNORE)` + `RulesRepository.upsertRule(RuleActionUi.IGNORE)` + `onBack()` + 3초 "되돌리기" 스낵바 체인을 실행. 스낵바 action 탭 시 prior `NotificationUiModel` / prior rule 복원 (새 룰이면 delete, 기존 룰이면 prior action 으로 upsert). Undo 는 in-memory 로만 유지되며 스낵바 닫힘 = 영구화. Trigger 섹션에 IGNORE row 가 기본 뷰에서 제외되어 [ignored-archive](ignored-archive.md) 경유로만 Detail 도달 가능함을 명시. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6a (#187 `57df6ac`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: journey-tester end-to-end re-verify of IGNORE flow (emulator-5554, APK `lastUpdateTime=2026-04-22 02:35:44` post-#189+#192). `cmd notification post -S bigtext -t '광고' IgnoreBtnTest '무시 버튼 Detail 테스트'` → DIGEST 분류 → 정리함 탭 → 프리뷰 row 탭 → Detail 마운트. Observable step 4 "이 알림 학습시키기" 카드에 4 버튼 (`중요로 고정` / `Digest로 보내기` / `조용히 처리` / `무시`) 존재 및 순서 확인. `무시` 탭 → `IgnoreConfirmationDialog` 제목 "이 알림을 무시할까요?" 본문 "이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기." `취소` / `무시` 버튼 렌더 확인 (Observable step 5.i). 다이얼로그 `무시` 확정 → Detail pop → 정리함 배경 복귀 + 스낵바 "무시됨. 되돌리려면 탭" + action `되돌리기` 렌더 (Observable step 5.ii). DB row `status=IGNORE` + `RulesRepository` 에 신규 `person:광고|광고|무시 (즉시 삭제)|PERSON|IGNORE|true|광고` upsert 확인 (`applyAction(IGNORE)` + `upsertRule(IGNORE)` 증명). `되돌리기` 탭 → DB row `status=DIGEST` 복원, rule 은 prior `person:광고|...|ALWAYS_PRIORITY` 로 복원 (기존 룰 prior action rollback 경로 증명; 새 룰이 아니었으므로 delete 대신 upsert). DRIFT 없음.
