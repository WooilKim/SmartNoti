---
id: rules-feedback-loop
title: Detail "분류 변경" 으로 분류 학습
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

한 건의 알림을 본 사용자가 "이 알림은 이 분류에 속한다" 는 의도를 한 번의 액션으로 표현하면, 그 의도가 (a) 해당 알림의 소유 Category 에 즉시 반영되고 (b) 동일 sender/앱의 다음 알림이 그 Category 의 action 으로 자동 분류되도록 한다. 사용자는 개별 action (중요/Digest/조용히/무시) 을 고르지 않는다 — **Category 를 고르거나, Category 를 만든다**.

> 2026-04-22 plan `categories-runtime-wiring-fix` (#245) 이후 Detail 의 4-버튼 grid ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시") 는 **제거**되고 단일 "분류 변경" CTA + `CategoryAssignBottomSheet` 로 대체됐다. `NotificationFeedbackPolicy.applyAction` / `toRule` 및 부수되는 `IgnoreConfirmationDialog` / 3초 undo 스낵바는 Detail 호출 경로에서 빠졌다 (inline Passthrough reclassify 경로의 `applyAction` 은 별도 collateral 로 보존 — `rules-ux-v2-inbox-restructure` Phase A Task 3). Replacement 알림에서도 per-action 버튼이 전부 사라졌다.

## Preconditions

- 대상 알림이 DB 에 존재
- 최소 하나 이상의 Category 가 존재 (온보딩 quick-start 로 기본 Category 들이 주입됨 → [onboarding-bootstrap](onboarding-bootstrap.md))

## Trigger

- [notification-detail](notification-detail.md) 화면 하단 CTA "분류 변경" 탭 → `CategoryAssignBottomSheet` 모달 바텀시트 오픈
- replacement 알림 (silent-suppression 의 대체 알림) 본문 탭 → Detail 진입 → 동일 "분류 변경" 버튼 (replacement 자체에는 더 이상 per-action 버튼이 없음)

## Observable steps

1. Detail 화면에서 "분류 변경" 버튼 탭 → `CategoryAssignBottomSheet` 렌더. 시트 구성:
   - 상단 섹션 "기존 분류에 포함" — `Category.order` 오름차순으로 사용자 Category 리스트 (이름 + action chip + `ruleIds.size` 소형 라벨).
   - 하단 terminal row "새 분류 만들기" CTA.
2. 경로 A — 기존 분류 선택:
   1. 사용자가 Category row 탭 → `NotificationDetailViewModel.onAssignToExisting(categoryId)`.
   2. `AssignNotificationToCategoryUseCase.assignToExisting(notification, categoryId)` 호출:
      - 자동 rule 유도: sender 가 non-blank → `RuleTypeUi.PERSON, matchValue=sender`; blank → `RuleTypeUi.APP, matchValue=packageName`. Id = `"${type.lowercase()}:${matchValue}"` (deterministic — 동일 키 재탭은 idempotent).
      - `RulesRepository.upsertRule(rule)` 으로 Rule 저장.
      - `CategoriesRepository.appendRuleIdToCategory(categoryId, rule.id)` 가 해당 Category 를 읽어 `ruleIds` 에 rule.id 를 deduped 로 append + persist. `action` / `name` / `order` / `appPackageName` 는 건드리지 않는다.
   3. 시트 dismiss, Detail 에 머무른다.
3. 경로 B — 새 분류 만들기:
   1. 사용자가 "새 분류 만들기" row 탭 → `AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(notification, currentCategoryAction)` 로 `CategoryEditorPrefill` 생성:
      - `name` = rule.title (sender 있으면 sender, 없으면 appName)
      - `appPackageName` = `if (rule.type == APP) notification.packageName else null`
      - `pendingRule` = 위에서 유도한 자동 rule
      - `defaultAction` = **현재 알림의 소유 Category action 의 dynamic-opposite** (DIGEST → PRIORITY, SILENT → PRIORITY, IGNORE → PRIORITY, PRIORITY → DIGEST). 소유 Category 가 없으면 PRIORITY.
   2. `AppNavHost` 가 prefill payload 를 `PrefillStore` 에 한 번만 담아 `Routes.CategoryEditor` 로 navigate. Editor 첫 컴포지션에서 `name` / `appPackageName` / `pendingRule` / `action` 을 해당 값으로 seed.
   3. 사용자가 editor 에서 원하면 이름/action/rule 을 수정하고 **저장 탭 시점에만** Rule + 신규 Category 가 persist (취소 시 어느 것도 쓰이지 않음).
   4. 저장 → `rulesRepository.upsertRule(rule)` + `categoriesRepository.upsertCategory(category)` (rule.id 포함) → 뒤로가기 시 Detail 로 복귀.
4. 이후 같은 sender/앱으로 새 알림이 들어오면 (→ [notification-capture-classify](notification-capture-classify.md)): listener 가 주입한 `currentCategories()` 스냅샷을 classifier 가 조회, 매치된 Rule 을 소유한 Category 가 cascade 에서 승자로 뽑혀 그 `action` 이 `NotificationDecision` 으로 변환된다. 더 이상 "orphan → SILENT fallback" 으로 새지 않는다.

## Exit state

- RulesRepository: 해당 sender/앱에 대응하는 Rule 이 upsert 됨 (동일 id 는 no-op idempotent).
- CategoriesRepository:
  - 경로 A → 선택한 Category 의 `ruleIds` 에 rule.id 가 deduped 로 추가됨. 그 밖 필드 변화 없음.
  - 경로 B → 신규 Category 가 저장되며 `ruleIds` 에 rule.id 포함. Editor 에서 고른 `action` 이 persist.
- NotificationRepository: **이 경로는 대상 알림 row 자체를 갱신하지 않는다** (legacy 4-버튼과의 핵심 차이). 사용자가 "이 알림 자체" 의 상태를 즉시 바꾸고 싶으면 분류가 달라지는 즉시 다음 tick 에 반영된다 — 과거처럼 status / reasonTags 에 "사용자 규칙" 을 즉시 덮어쓰지 않음.
- 알림센터: replacement 알림은 별도 cancel 되지 않는다 (이 경로가 `notificationManager.cancel` 을 호출하지 않음 — replacement 수명 주기는 기존 silent-suppression 계약을 따름).
- 후속 동일 유형 알림: 이번에 묶인 Category 의 action 으로 자동 분류됨.

## Out of scope

- 수동 룰 편집/삭제 (→ [rules-management](rules-management.md))
- Category 자체의 편집 / 순서 변경 (→ [categories-management](categories-management.md))
- 대상 알림 row 의 즉시 status 변경 (이 redesign 이후 Detail 에는 "즉시 상태를 바꾸는" 버튼이 없음 — 자연스러운 재분류는 "다음 tick" 의 자동 classifier 재적용으로 관측)

## Code pointers

- `ui/screens/detail/NotificationDetailScreen` — 단일 "분류 변경" Button + `CategoryAssignBottomSheet` 호스팅
- `ui/notification/CategoryAssignBottomSheet` — ModalBottomSheet composable, 기존 분류 리스트 + "새 분류 만들기" terminal row
- `ui/notification/ChangeCategorySheetState` — `Category.order` 오름차순 + "새 분류 만들기" flag 를 UI 에 공급하는 pure state 모델
- `domain/usecase/AssignNotificationToCategoryUseCase` — `assignToExisting` + `buildPrefillForNewCategory` + 자동 rule 유도 (PERSON / APP)
- `data/categories/CategoriesRepository#appendRuleIdToCategory` — 경로 A 의 Category 단일 필드 갱신
- `data/categories/CategoriesRepository#onRuleDeleted` — Rule 삭제 시 cascade (→ [rules-management](rules-management.md))
- `ui/screens/categories/CategoryEditorScreen` — prefill (`name` / `appPackageName` / `pendingRule` / `defaultAction`) 수용 + 저장 시 Rule+Category 동시 upsert
- `navigation/AppNavHost` — Detail → Editor prefill payload 를 `PrefillStore` 경유로 한 번만 전달
- `notification/SmartNotiNotifier` — replacement 알림에서 `addAction(...)` 4종 호출이 전부 제거됨 (tap-only)

## Tests

- `AssignNotificationToCategoryUseCaseTest` — 경로 A (sender 있음 → PERSON) / 경로 A (sender 없음 → APP) / idempotent dedupe
- `CreateCategoryFromNotificationPrefillTest` — prefill 필드 + dynamic-opposite default action 매핑
- `ChangeCategorySheetStateTest` — Category 정렬 + "새 분류 만들기" row 합성
- `RulesRepositoryDeleteCascadeTest` — rule 삭제 시 Category.ruleIds 가 정리됨 (→ [rules-management](rules-management.md))
- `SmartNotiNotificationListenerServiceCategoryInjectionTest` — listener 가 Categories 를 classifier 로 전달함 (→ [notification-capture-classify](notification-capture-classify.md))

## Verification recipe

```bash
# 1. 알림 게시 (신규 sender 권장 — baseline Rule 오염 방지)
adb shell cmd notification post -S bigtext -t "AssignTest_0421" FbTest "분류 변경 테스트"

# 2. Home 카드 탭 → Detail → "분류 변경" 버튼
#    → 바텀시트 렌더 확인 (기존 분류 리스트 + "새 분류 만들기")

# 3. 경로 A 검증: 기존 Category 하나 탭 → 시트 닫힘
#    → Rules 화면 진입 (Settings > "고급 규칙 편집") 해서 person:AssignTest_0421 rule 저장 확인
#    → 해당 Category 의 ruleIds 에 rule.id 가 추가됐는지 (Editor 진입해 rule 링크 확인)

# 4. 경로 B 검증: Detail 재진입 → "분류 변경" → "새 분류 만들기" 탭
#    → CategoryEditor 가 pre-filled (name=AssignTest_0421, pending rule=PERSON:..., action=dynamic-opposite) 로 오픈
#    → 저장 → Rule + 신규 Category 동시 저장 확인

# 5. 자동 재분류 검증: 동일 sender 로 후속 알림 포스팅
adb shell cmd notification post -S bigtext -t "AssignTest_0421" FbTest2 "두 번째 알림"
# → 묶인 Category 의 action 으로 자동 분류되는지 관측 (Home StatPill / 해당 탭 증가)
```

## Known gaps

- Detail 의 "분류 변경" 시트 상단 텍스트는 실제로 "이 알림이 속할 분류를 고르거나 새 분류를 만들어요." 로 렌더되며 문서의 "기존 분류에 포함" 섹션 헤더는 관측되지 않음 (기존 분류가 0 건인 상태라 헤더 미노출일 수 있으나 확인 불가 — 후속 verification 에서 기존 분류 존재 조건에서 재검증 필요).
- 시트 확정 후 사용자에게 토스트나 확인 UI 없음 (조용히 성공).
- 자동 rule 유도는 PERSON / APP 만 지원 — KEYWORD / SCHEDULE / REPEAT_BUNDLE 기반 묶기는 제공하지 않음 (사용자가 원하면 editor 에서 추가해야 함).
- Deterministic rule id 의 교차 Category 중복: 동일 sender 를 서로 다른 Category 두 곳에 연이어 할당하면 같은 rule.id 를 두 Category 가 `ruleIds` 에 갖게 된다. Classifier 는 `order` 드래그 tie-break 로 승자를 결정 — 사용자가 분류 탭에서 정한 순서가 그대로 지배한다. Bug 아님 (계약 명시).
- PrefillStore 프로세스 생존: 경로 B 의 Editor 전이 도중 프로세스가 죽으면 prefill 이 소실되고 Editor 는 빈 상태로 열린다. 재시도 시 문제 없음 (저장 이전까지 아무것도 persist 되지 않음).
- Detail "즉시 상태 변경" 부재: legacy 4-버튼은 알림 row 자체의 status 를 즉시 바꿔줬는데 redesign 에서는 의도적으로 제거. 사용자가 "이 알림 자체를 지금 이 버킷으로 보내기" 를 원한다면 classifier 가 다음 tick 에 재적용하는 동안 row 상태는 그대로 보임. 이 UX 후퇴를 감지하면 별도 plan 으로 restore 결정 필요.
- Replacement 알림 per-action 버튼 제거 (intended trade): silent-suppression 의 대체 알림이 tap-only 로 바뀌어 `notification-replacement-alert` recipe 는 탭 수가 한 번 더 필요하다. journey-tester 가 해당 recipe 재검증 필요.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verify — Detail 경유 PASS (fresh sender `TestSender_0421_T12`, `person:…` rule ALWAYS_PRIORITY 생성 확인)
- 2026-04-21: v1 loop re-verify #3 — KEEP_SILENT 분기 PASS (fresh sender `SilentTest_0421_T1`, Detail "조용히 처리" 탭 → Rules 탭 "조용히 1" 카테고리에 person 타입 룰 `person:SilentTest_0421_T1` 생성 + reasonTags 에 "사용자 규칙" 추가 관측). 3개 액션 분기 (ALWAYS_PRIORITY / ALWAYS_DIGEST / KEEP_SILENT) 모두 관측 완료.
- 2026-04-21: 4번째 feedback 액션 **"무시"** 추가 — Detail 화면에서만 trigger. `IgnoreConfirmationDialog` 확인 → `applyIgnoreWithUndo` 가 `NotificationFeedbackPolicy.applyAction(IGNORE)` + `RulesRepository.upsertRule(RuleActionUi.IGNORE)` + `onBack()` + 3초 "되돌리기" 스낵바 체인을 실행. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6a (#187 `57df6ac`).
- 2026-04-22: **Rule/Category 분리 아키텍처** 반영 — Goal/Observable/Exit state 에 "Category 는 갱신되지 않음" 을 명시. 피드백 경로는 여전히 Rule 만 upsert 하고 Category upsert 는 생략 — 이는 Phase P1 Tasks 1-4 (#236) 이후 classifier cascade 가 Category 기준으로 바뀌면서 생긴 신규 drift. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 (#236), P2 (#239), P3 (#240). `last-verified` 변경 없음.
- 2026-04-22: journey-tester ADB verify on fresh Categories APK — Detail "분류 변경" 시트 렌더 + Path B editor prefill (name / PRIORITY action / pre-checked rule) PASS, 그러나 editor "추가" 탭이 신규 Category 를 persist 하지 않는 DRIFT 발견 (Known gaps 참조). `last-verified` 갱신 보류.
- 2026-04-22: **Detail "분류 변경" 단일 CTA redesign** — legacy 4-버튼 grid ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시") 와 `IgnoreConfirmationDialog` / 3초 undo 스낵바 / `NotificationFeedbackPolicy.applyAction` Detail 호출 경로를 **제거**. 대체: 단일 `Button("분류 변경")` → `CategoryAssignBottomSheet` ("기존 분류에 포함" 리스트 + "새 분류 만들기" terminal row). 경로 A (기존 Category 선택) = `AssignNotificationToCategoryUseCase.assignToExisting` → `rulesRepository.upsertRule` + `categoriesRepository.appendRuleIdToCategory` (idempotent dedupe, Category 의 action/name/order 불변). 경로 B (새 분류 만들기) = `buildPrefillForNewCategory` 로 `CategoryEditorPrefill` 조립 → PrefillStore 경유로 `CategoryEditorScreen` 에 seed (`defaultAction` = 현재 Category action 의 dynamic-opposite) → 저장 시 Rule+Category 동시 persist. Replacement 알림은 per-action 버튼 4종 제거로 tap-only. 이전 "Category 자동 생성 미구현" Known gap 은 redesign 으로 해소. Plan: `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Tasks 2-6 (#245), Task 7 (this PR). `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: **CategoryEditor save wiring fix** — Path B 에서 "추가" 버튼이 editor dismiss 전에 DataStore write 를 flush 하지 못해 `smartnoti_categories.preferences_pb` 가 생성되지 않던 race 해소. Fix: `CategoryEditorScreen` 의 save onClick 이 `scope.launch { rulesRepository.upsertRule; categoriesRepository.upsertCategory }` 뒤에 **비동기 대기 없이** `onSaved(id)` 를 호출해 composition teardown 이 `rememberCoroutineScope` 를 `DataStore.edit{}` 블록 진입 전에 취소하던 문제를 `saveCategoryDraft(...)` 순수 suspend 헬퍼로 재배선 — persist 완료 후에만 `onSaved` 가 실행되도록 보장. 중복 탭 방지 `saving` flag 추가. 해당 2026-04-22 DRIFT bullet 을 Known gaps 에서 제거. Plan: `docs/plans/2026-04-22-category-editor-save-wiring-fix.md` (#248 shipped). `last-verified` 는 미bump — full ADB recipe 는 별도 pre-existing `LegacyRuleActionReader` / `RulesRepository` DataStore 중복 오픈 crash 로 재현 불가 (orthogonal follow-up 필요).
