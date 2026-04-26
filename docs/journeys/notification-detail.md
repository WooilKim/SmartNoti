---
id: notification-detail
title: 알림 상세 및 피드백 액션
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

한 건의 알림에 대해 SmartNoti 가 어떻게 판단했고 어떻게 전달했는지 투명하게 보여주고, 즉석에서 재분류해 학습시킬 수 있는 단일 상세 화면을 제공한다.

## Preconditions

- 해당 `notificationId` 가 DB 에 존재 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료

## Trigger

- Home / Priority (검토 대기) / 정리함 (Digest / 보관 중 / 처리됨) / Insight 어디서든 카드 탭 → `navController.navigate(Routes.Detail.create(id))`
- replacement 알림 본문 탭 → 딥링크로 parent route + detail 연결 (→ [digest-suppression](digest-suppression.md)). 2026-04-22 plan `categories-runtime-wiring-fix` (#245) 이후 replacement 알림에는 per-action 버튼 (중요/Digest/조용히/무시) 이 **전부 제거**되어 tap-only — 본문 탭이 Detail 진입의 유일한 경로.
- IGNORE 상태 row 는 기본 뷰에서 제외되므로 Detail 도달 경로가 제한됨 — Settings 의 "무시된 알림 아카이브 표시" 토글이 ON 일 때 [ignored-archive](ignored-archive.md) 화면의 row 탭만 유일한 진입점

## Observable steps

1. `NotificationDetailScreen` composable 마운트, `repository.observeNotification(id)` 구독.
2. `DetailTopBar` 렌더 — 좌측 뒤로가기 `IconButton` + 타이틀 "알림 상세".
3. 대상 알림이 null 이면 `EmptyState("알림을 찾을 수 없어요", ...)` 를 보여주고 return.
4. 존재하면 섹션 순서대로 렌더링:
   - 알림 요약 카드 — appName, sender/title, body, `StatusBadge(status)`
   - "왜 이렇게 처리됐나요?" — `ReasonChipRow(reasonTags)` (FlowRow 이므로 줄바꿈). 그 아래에 (조건부) `QuietHoursExplainerBuilder` 가 합성한 "지금 적용된 정책" sub-section 한 줄 카피 — DIGEST + `조용한 시간` reasonTag + `사용자 규칙` 미동시-매치이면 노출 (→ [quiet-hours](quiet-hours.md))
   - (선택) 온보딩 추천 반영 카드 — `NotificationDetailOnboardingRecommendationSummaryBuilder`
   - (선택) "어떻게 전달되나요?" — `NotificationDetailDeliveryProfileSummaryBuilder` 결과. 전달 모드/소리/진동/Heads-up/잠금화면 라벨
   - (선택) "원본 알림 처리 상태" — `NotificationDetailSourceSuppressionSummaryBuilder` 결과 (원본 상태 / 대체 알림 여부)
   - "이 알림 학습시키기" 카드 — 단일 CTA 버튼 `"분류 변경"` (2026-04-22 redesign 이전에는 4개 액션 버튼이었음)
5. `"분류 변경"` 탭 → `CategoryAssignBottomSheet` 모달 바텀시트 오픈. 내부 동작 및 후속 플로우는 [rules-feedback-loop](rules-feedback-loop.md) 에 위임. 요약:
   - 상단 "기존 분류에 포함" — 사용자 Category 리스트 (`Category.order` 오름차순). 한 Category row 탭 시 `AssignNotificationToCategoryUseCase.assignToExisting` 이 자동 rule (PERSON if sender else APP) 을 upsert + 해당 Category 의 `ruleIds` 에 dedup append. Category 의 `action` / `name` / `order` 는 불변.
   - 하단 "새 분류 만들기" — `buildPrefillForNewCategory` 로 `CategoryEditorPrefill` 조립 (`name=sender-or-appName`, `appPackageName=packageName if APP rule`, `pendingRule=PERSON/APP rule`, `defaultAction=현재 Category action 의 dynamic-opposite` — DIGEST/SILENT/IGNORE → PRIORITY, PRIORITY → DIGEST, 소유 Category 없으면 PRIORITY). `AppNavHost` 가 `PrefillStore` 경유로 `CategoryEditorScreen` 으로 navigate — editor 저장 시에만 Rule + Category 가 persist (취소 시 아무것도 쓰이지 않음).
   - 시트 dismiss 직후 화면 하단 (BottomNav 위) 에 짧은 confirmation snackbar 가 한 번 표시된다 — 경로 A `"<카테고리명> 분류로 옮겼어요"`, 경로 B `"새 분류 '<카테고리명>' 만들었어요"`. 경로 B 에서 editor 를 cancel 하면 snackbar 는 표시되지 않는다.
6. 재분류 효과는 **다음 tick** 에 관측됨 — 이 경로는 대상 알림 row 자체의 status / reasonTags 를 즉시 업데이트하지 않는다. 동일 sender/앱으로 들어오는 후속 알림이 새로 묶인 Category 의 action 으로 자동 분류된다 (→ [notification-capture-classify](notification-capture-classify.md)).

## Exit state

- RulesRepository: 경로 A/B 모두 자동-유도 Rule 이 upsert 됨 (deterministic id → 재탭 idempotent).
- CategoriesRepository:
  - 경로 A — 선택한 Category 의 `ruleIds` 에 rule.id 가 dedup 으로 append. 기타 필드 불변.
  - 경로 B — editor 저장 시점에 새 Category 가 persist (Rule+Category 동시).
- NotificationRepository: **대상 알림 row 는 이 경로에서 갱신되지 않는다** (legacy 4-버튼과의 계약 차이). 재분류 효과는 후속 동일 sender/앱 알림에 대해 발현.
- 사용자는 시트 dismiss 후 Detail 에 머무르고, 뒤로가기로 원래 화면 (Home / 검토 대기 / 정리함 / Insight) 으로 복귀.

## Out of scope

- 룰 편집/삭제 (→ [rules-management](rules-management.md))
- 보호 대상 알림의 원본 유지 보장 (→ [protected-source-notifications](protected-source-notifications.md))
- 알림 자체의 분류 결정 (→ [notification-capture-classify](notification-capture-classify.md))

## Code pointers

- `ui/screens/detail/NotificationDetailScreen` — 단일 "분류 변경" Button + `CategoryAssignBottomSheet` 호스팅 + Path A/B 후 confirmation `SnackbarHost` (BottomNav 인셋 위에 띄움)
- `ui/screens/detail/DetailReclassifyConfirmationMessageBuilder` — `AssignedExisting(categoryId)` / `CreatedNew(categoryName)` outcome 을 사용자 노출 한국어 카피로 변환 (race / blank 시 null → 무표시)
- `ui/notification/CategoryAssignBottomSheet` — ModalBottomSheet ("기존 분류에 포함" 리스트 + "새 분류 만들기" terminal row)
- `ui/notification/ChangeCategorySheetState` — 시트의 pure state 모델 (Category.order 오름차순)
- `domain/usecase/AssignNotificationToCategoryUseCase` — `assignToExisting` + `buildPrefillForNewCategory` + 자동 rule 유도
- `data/categories/CategoriesRepository#appendRuleIdToCategory` — 경로 A 의 Category 단일 필드 갱신
- `ui/screens/categories/CategoryEditorScreen` — prefill 수용 (경로 B)
- `navigation/AppNavHost` + `PrefillStore` — Detail → Editor 로 prefill 전달
- `domain/usecase/NotificationDetailDeliveryProfileSummaryBuilder`
- `domain/usecase/NotificationDetailOnboardingRecommendationSummaryBuilder`
- `domain/usecase/NotificationDetailSourceSuppressionSummaryBuilder`
- `data/local/NotificationRepository#observeNotification`
- `notification/SmartNotiNotifier` — replacement 알림에서 per-action `addAction(...)` 호출 전부 삭제 (tap-only)
- `notification/SmartNotiNotificationActionReceiver` — per-action broadcast 핸들러 전부 삭제됨 (receiver 자체 제거)

## Tests

- `AssignNotificationToCategoryUseCaseTest` — 경로 A PERSON / APP 분기 + idempotent dedupe
- `CreateCategoryFromNotificationPrefillTest` — prefill 필드 + dynamic-opposite default action
- `ChangeCategorySheetStateTest` — Category.order 정렬 + "새 분류 만들기" row 합성
- `DetailReclassifyConfirmationMessageBuilderTest` — Path A/B 카피 합성 + race / blank fallback (null = 무표시)
- `NotificationDetailDeliveryProfileSummaryBuilderTest` (있다면)

## Verification recipe

```bash
# 1. 알림 게시 → 분류 → Detail 진입 (fresh sender 로 baseline 오염 방지)
adb shell cmd notification post -S bigtext -t "DetailTest_0421" FbTest1 "분류 변경 테스트"
adb shell am start -n com.smartnoti.app/.MainActivity
# Home / 검토 대기 / 정리함 어디서든 카드 탭 → Detail 마운트

# 2. "이 알림 학습시키기" 카드에 단일 "분류 변경" 버튼만 렌더되는지 확인
# 3. "분류 변경" 탭 → CategoryAssignBottomSheet 렌더:
#    - 기존 Category 리스트 (Category.order 오름차순)
#    - 하단 "새 분류 만들기" terminal row

# 4. 경로 A — 기존 Category 하나 탭 → 시트 닫힘
#    → 후속 동일 sender 알림 포스팅 시 해당 Category 의 action 으로 분류되는지 관측
adb shell cmd notification post -S bigtext -t "DetailTest_0421" FbTest2 "두 번째"

# 5. 경로 B — Detail 재진입 → "분류 변경" → "새 분류 만들기"
#    → CategoryEditor 가 prefill (name/appPackageName/pendingRule/defaultAction) 로 오픈
#    → 저장 시 Rule+Category 동시 persist
```

## Known gaps

- (resolved 2026-04-25, plan 2026-04-24-detail-reclassify-confirm-toast) 재분류 시 토스트/확인 UI 없음 (시트만 닫혀 UX 가 조용함). → plan: `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md`
- (resolved, verified 2026-04-26) Detail 내부 "룰 보기" 바로가기 부재 — `RuleHitChipRow` 가 "적용된 규칙" 서브섹션으로 렌더되고 chip 탭 시 `Routes.Rules.create(highlightRuleId=…)` 로 Rules 화면으로 딥링크.
- **대상 알림 row 즉시 상태 변경 부재** (redesign 2026-04-22): legacy 4-버튼은 `NotificationRepository.updateNotification` 으로 해당 row 의 status/reasonTags 를 즉시 덮어써 Home/리스트에 즉시 반영했으나, redesign 이후 이 경로는 Rule+Category 만 persist 하고 row 자체는 건드리지 않는다. 재분류 효과는 **후속 동일 sender/앱 알림** 에 대해 다음 tick 부터 발현. 이 UX 후퇴를 후속 plan 이 명시적 "이 알림도 지금 재분류" CTA 로 보완할지 결정 필요.
- **per-action broadcast 경로 제거** (2026-04-22): `SmartNotiNotificationActionReceiver` 및 `SmartNotiNotifier.ACTION_*` 상수 4종이 삭제되어 replacement 알림 / 3rd-party notification action 경유 재분류 훅이 완전히 사라졌다. 재분류는 오직 Detail 의 "분류 변경" 시트 경유로만 가능 — tap-only UX 로 의도적 단순화.
- 2026-04-21 (journey-tester): 구 Verification recipe 가 테스트용 sender 로 `"광고"` 를 제안했는데 이 값이 온보딩 KEYWORD 룰 (`광고,프로모션,쿠폰,세일,특가,이벤트,혜택 → DIGEST`) 과 충돌해 baseline 이 오염됐다. Redesigned recipe 는 중립 sender (`DetailTest_0421` 등) 사용.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verification (emulator-5554) — Digest 카드 탭 경로로 Observable steps 1~5 전부 관측, 액션 버튼 3종 존재 확인
- 2026-04-21: journey-tester end-to-end re-verify (emulator-5554, APK `lastUpdateTime=2026-04-21 15:47:57`) — `cmd notification post -S bigtext -t '광고' DetailTest_0421 '오늘의 이벤트 테스트'` → DIGEST 분류 → Digest 탭 → 프리뷰 row 탭 → Detail 진입. Observable steps 1–4 의 모든 섹션 (알림 요약 / `StatusBadge=Digest` / "왜 이렇게 처리됐나요?" + 5개 chip / 온보딩 추천 카드 / 전달 모드 5개 라벨 / 원본 상태+대체 알림 / "이 알림 학습시키기" 3 버튼) 전부 렌더 확인. "중요로 고정" 탭 → DB row `status=PRIORITY` 로 업데이트, `RulesRepository` 에 `person:광고|광고|항상 바로 보기|PERSON|ALWAYS_PRIORITY` 신규 upsert (Observable step 5.i–iv 증명). PERSON→ALWAYS_PRIORITY 자동 적용은 별도 sender (`TestSender_0421_T12`) 로 확인 — 동일 sender 재-post 시 `reasonTags` 에 sender 레이블 + "사용자 규칙" 태그 + `status=PRIORITY` 반영. DRIFT 없음.
- 2026-04-21: "이 알림 학습시키기" 카드에 4번째 버튼 **"무시"** 추가 — 파괴성을 드러내기 위해 least → most destructive 순서로 배치 (중요로 고정 → Digest → 조용히 → 무시). 탭 시 `IgnoreConfirmationDialog` 확인 다이얼로그 + 확정 시 `applyIgnoreWithUndo` 가 `NotificationFeedbackPolicy.applyAction(IGNORE)` + `RulesRepository.upsertRule(RuleActionUi.IGNORE)` + `onBack()` + 3초 "되돌리기" 스낵바 체인을 실행. 스낵바 action 탭 시 prior `NotificationUiModel` / prior rule 복원 (새 룰이면 delete, 기존 룰이면 prior action 으로 upsert). Undo 는 in-memory 로만 유지되며 스낵바 닫힘 = 영구화. Trigger 섹션에 IGNORE row 가 기본 뷰에서 제외되어 [ignored-archive](ignored-archive.md) 경유로만 Detail 도달 가능함을 명시. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6a (#187 `57df6ac`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: journey-tester end-to-end re-verify of IGNORE flow (emulator-5554, APK `lastUpdateTime=2026-04-22 02:35:44` post-#189+#192). `cmd notification post -S bigtext -t '광고' IgnoreBtnTest '무시 버튼 Detail 테스트'` → DIGEST 분류 → 정리함 탭 → 프리뷰 row 탭 → Detail 마운트. Observable step 4 "이 알림 학습시키기" 카드에 4 버튼 (`중요로 고정` / `Digest로 보내기` / `조용히 처리` / `무시`) 존재 및 순서 확인. `무시` 탭 → `IgnoreConfirmationDialog` 제목 "이 알림을 무시할까요?" 본문 "이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기." `취소` / `무시` 버튼 렌더 확인 (Observable step 5.i). 다이얼로그 `무시` 확정 → Detail pop → 정리함 배경 복귀 + 스낵바 "무시됨. 되돌리려면 탭" + action `되돌리기` 렌더 (Observable step 5.ii). DB row `status=IGNORE` + `RulesRepository` 에 신규 `person:광고|광고|무시 (즉시 삭제)|PERSON|IGNORE|true|광고` upsert 확인 (`applyAction(IGNORE)` + `upsertRule(IGNORE)` 증명). `되돌리기` 탭 → DB row `status=DIGEST` 복원, rule 은 prior `person:광고|...|ALWAYS_PRIORITY` 로 복원 (기존 룰 prior action rollback 경로 증명; 새 룰이 아니었으므로 delete 대신 upsert). DRIFT 없음.
- 2026-04-22: **Rule/Category 분리 아키텍처** 반영 — "이 알림 학습시키기" 4개 버튼의 내부 처리 계약에 "Category 는 자동 생성되지 않음" 을 명시. 피드백 액션은 여전히 `NotificationRepository.updateNotification` + `RulesRepository.upsertRule` 두 write 만 수행 — 즉시 UI 반영은 보장되나 classifier hot path 의 자동 재적용은 해당 Rule 을 소유한 Category 가 있을 때만. "무시" 버튼 의미도 "Category.action = IGNORE 지향" 으로 copy 정렬. Trigger 에서 "Priority/Digest/Hidden 탭" 표현을 "검토 대기 / 정리함 (통합)" 로 갱신. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Tasks 1-4 (#236), P3 Tasks 10-11 (#240). `last-verified` 는 후속 ADB sweep 이 Category-free feedback path 를 재검증할 때까지 갱신 없음.
- 2026-04-22: journey-tester end-to-end re-verify (emulator-5554, APK `lastUpdateTime=2026-04-22 17:29:33`) — `cmd notification post -S bigtext -t 'DetailTest_0422' FbTest1 '분류 변경 테스트'` → DIGEST 분류 → 정리함 → Digest 묶음 → Shell 번들 드릴 → Detail 마운트. Observable steps 1–4 모두 관측: `DetailTopBar` "알림 상세" + 뒤로가기, 알림 요약 카드 `StatusBadge=Digest`, "왜 이렇게 처리됐나요?" chip row (`반복 알림` / `발신자 있음`), "어떻게 전달되나요?" 4개 라벨 (전달 모드·Digest / 소리·조용히 / 진동·없음 / Heads-up·꺼짐 / 잠금화면·내용 일부), "SmartNoti 가 본 신호", "이 알림 분류하기" 카드 + 단일 `Button("분류 변경")` 렌더. 버튼 탭 → `CategoryAssignBottomSheet` 오픈 (타이틀 "분류 변경" / "이 알림이 속할 분류를 고르거나 새 분류를 만들어요." / terminal row `+ 새 분류 만들기`; 기존 Category 없어 상단 리스트 비어 있음). 경로 B 검증 — "새 분류 만들기" 탭 → `CategoryEditorScreen` prefill 오픈 (`전달 방식 = 즉시 전달 (PRIORITY)` = DIGEST 소스의 dynamic-opposite, Observable step 5.ii 증명). DRIFT 없음.
- 2026-04-24: journey-tester end-to-end re-verify (emulator-5554, APK `lastUpdateTime=2026-04-24 01:44:11`) — `cmd notification post -S bigtext -t 'DetailTest_0424' FbTest1 '분류 변경 테스트'` → DIGEST 분류 → 정리함 → Digest 묶음 → Shell 번들 preview row 탭 → Detail 마운트. Observable steps 1–5 모두 PASS: `DetailTopBar` "알림 상세" + 뒤로가기, 알림 요약 카드 `StatusBadge=Digest`, "왜 이렇게 처리됐나요?" + 4개 chip (`발신자 있음` / `사용자 규칙` / `프로모션 알림` / `온보딩 추천`), 온보딩 추천 카드, "어떻게 전달되나요?" + 5개 라벨, "원본 알림 처리 상태" 섹션, "이 알림 분류하기" 카드 + 단일 `Button("분류 변경")` 렌더. 버튼 탭 → `CategoryAssignBottomSheet` (타이틀 "분류 변경" / 부제 "이 알림이 속할 분류를 고르거나 새 분류를 만들어요." / "기존 분류에 포함" 헤더 + 3개 Category row (중요 알림 / 프로모션 알림 / 반복 알림) + terminal `+ 새 분류 만들기`). 경로 B 검증 — "새 분류 만들기" 탭 → `CategoryEditorScreen` prefill (name="프로모션", 전달 방식="즉시 전달 (PRIORITY)" = DIGEST 소스의 dynamic-opposite). DRIFT 없음.
- 2026-04-25: **재분류 confirmation snackbar 추가** — Detail "분류 변경" 시트 dismiss 직후 화면 하단 (BottomNav 위) 에 짧은 Material3 `Snackbar` 가 한 번 표시된다. 경로 A `"<카테고리명> 분류로 옮겼어요"`, 경로 B (editor 저장 후) `"새 분류 '<카테고리명>' 만들었어요"`. 경로 B cancel 시 표시되지 않음. 메시지 합성은 신규 `DetailReclassifyConfirmationMessageBuilder` (pure Kotlin, race / blank 시 null → 무표시) 가 담당. `CategoryEditorScreen.onSaved` 시그니처가 `(String) -> Unit` 에서 `(Category) -> Unit` 으로 확장 — Detail 이 persist 직후 카테고리 이름을 categories Flow round-trip 없이 즉시 읽을 수 있게. `CategoriesScreen` 호출 사이트도 동시 갱신. ADB 검증: 경로 A "프로모션 알림" / "반복 알림" 탭 후 snackbar 캡처, 경로 B "ToastVerify" 신규 분류 저장 후 snackbar 캡처, 경로 B cancel 시 미등장 모두 PASS. Plan: `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` (this PR). `last-verified` 변경 없음 — full Verification recipe 재실행은 다음 journey-tester sweep.
- 2026-04-25: journey-tester end-to-end re-verify (emulator-5554, APK `lastUpdateTime=2026-04-25 19:27:39`, post-#323) — Detail 진입 후 Observable steps 1–5 PASS. 경로 A "중요 알림" 탭 → 시트 dismiss + snackbar `"중요 알림 분류로 옮겼어요"` 렌더 (BottomNav 위, 자동 사라짐 확인). 경로 B "새 분류 만들기" → editor prefill name="오늘만 특가 안내" / 전달 방식="Digest" (직전 Path A 로 source category 가 PRIORITY 로 바뀌었으므로 dynamic-opposite=DIGEST 로 정합) → name 을 "ToastBverify_0425" 로 교체 → "추가" 저장 → snackbar `"새 분류 'ToastBverify_0425' 만들었어요"` 렌더. 경로 B cancel 케이스 ("닫기" 탭) → snackbar 미등장 확인. DRIFT 없음.
- 2026-04-26: journey-tester end-to-end re-verify (emulator-5554) + drift correction. `cmd notification post -S bigtext -t 'DetailTest_0426' RuleHitTest '룰 칩 테스트'` baseline → Home 의 PayTest (rule hit 보유 알림) 카드 탭 → Detail 진입. Observable steps 1–4 PASS: `DetailTopBar` "알림 상세", 알림 요약 `StatusBadge=즉시 전달`, "왜 이렇게 처리됐나요?" 카드에 두 서브섹션 ("SmartNoti 가 본 신호" + "적용된 규칙") 정상 렌더, **`RuleHitChipRow` 가 "Shell" rule chip 을 clickable 로 표시 (bounds [92,1292][218,1418])**. chip 탭 → Rules 화면으로 navigate (4개 룰 리스트 렌더) — `NotificationDetailScreen.kt:239-241` `onRuleClick → Routes.Rules.create(highlightRuleId=…)` 경로 (`AppNavHost.kt:339-346`) 동작 확인. Known gaps 의 "Detail 내부에서 '룰 보기' 바로가기 부재" 항목은 이미 shipped 였으므로 resolved 마킹 + drift 정정. DRIFT 없음 (doc 만 stale 했음).
- 2026-04-22: **Detail "분류 변경" 단일 CTA redesign** — "이 알림 학습시키기" 카드의 4-버튼 grid ("중요로 고정" / "Digest로 보내기" / "조용히 처리" / "무시") 와 `IgnoreConfirmationDialog` / 3초 undo 스낵바 / Detail 호출 경로 `NotificationFeedbackPolicy.applyAction` 을 전부 제거. 대체: 단일 `Button("분류 변경")` → `CategoryAssignBottomSheet` (기존 분류 리스트 + "새 분류 만들기" terminal row). 경로 A = `AssignNotificationToCategoryUseCase.assignToExisting` (기존 Category 의 `ruleIds` append, action 불변). 경로 B = `buildPrefillForNewCategory` → `CategoryEditorPrefill` → `PrefillStore` → `CategoryEditorScreen` seed (`defaultAction` = 현재 Category action 의 dynamic-opposite). Replacement 알림 per-action 버튼 4종 제거 (tap-only), `SmartNotiNotificationActionReceiver` + manifest `<receiver>` 삭제, `SmartNotiNotifier.ACTION_*` 상수 4종 삭제. 재분류 효과는 후속 동일 알림에 대해 다음 tick 부터 발현 (대상 row status 즉시 갱신 X). Plan: `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Tasks 2-6 (#245), Task 7 (this PR). `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
