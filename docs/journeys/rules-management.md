---
id: rules-management
title: 규칙 CRUD
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

사용자가 발신자 / 앱 / 키워드 / 스케줄 / 반복묶음 규칙을 직접 만들고 편집/삭제/순서 조정/필터링 해서 분류 파이프라인의 입력으로 활용한다.

## Preconditions

- 온보딩 완료

## Trigger

사용자가 하단 네비의 "규칙" 탭 탭 → `Routes.Rules` 네비게이션.

## Observable steps

1. `RulesScreen` 마운트, 다음 상태 구독:
   - `RulesRepository.observeRules()` — 현재 룰 목록
   - `NotificationRepository.observeCapturedAppsFiltered(...)` — 앱 선택 제안용
   - `SettingsRepository.observeSettings()` — persistent 알림 필터 설정
2. 상단 `ScreenHeader` + "직접 규칙 추가" 카드의 "새 규칙 추가" 버튼.
3. "활성 규칙 N개" 카드 + `FilterChip` (ALWAYS_PRIORITY / DIGEST / SILENT / IGNORE) — `RuleListFilterApplicator` 가 선택된 액션으로 필터링. IGNORE chip 은 사용자가 IGNORE 룰을 최소 하나 이상 만들었을 때만 노출.
4. 액션별로 그룹화된 리스트 (`RuleListGroupingBuilder`, 순서: 즉시 전달 → Digest → 조용히 → 상황별 → 무시). 그룹 내부에서 `RuleListHierarchyBuilder` 가 flat list 를 tree 로 재조립 — base rule 아래 해당 rule 을 `overrideOf` 로 가리키는 override rule 이 자식으로 nest. 삭제/필터링된 base 를 참조하는 override 는 top-level 에 "기본 규칙을 찾을 수 없음" broken indicator 와 함께 렌더.
5. `RuleRow` 는 depth 에 따라 indent (16dp per level), override 는 primary tint pill "이 규칙의 예외 · {base 이름}" 라벨을, broken override 는 error tint 경고 문구를 제목 옆에 표시. 제목 / 매치값 / 액션 / 순서 이동 (drag handle + arrow fallback) / 편집 / 삭제 어포던스는 기존과 동일.
6. 추가 / 편집 탭 → `AlertDialog` (rule editor) 열림:
   - 룰 타입 (`RuleTypeUi`: PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 선택
   - 매치 값 입력 (타입별: 이름, 패키지, 키워드, 시간 범위, 반복 임계)
   - 액션 선택 (`RuleActionUi`: 즉시 전달 / Digest / 조용히 / 무시 (즉시 삭제)). IGNORE 는 파괴적이므로 라벨에 "(즉시 삭제)" 를 병기해 혼동 방지.
   - "예외 규칙" 섹션 — "기존 규칙의 예외로 만들기" `Switch` + (ON 일 때) `RuleEditorOverrideOptionsBuilder` 가 만든 base 후보 dropdown. 후보가 없으면 switch 자체 비활성화.
   - `RuleEditorDraftValidator` 가 입력 유효성 검사 (빈 값/잘못된 시간 등), `RuleOverrideSupersetValidator` 가 draft 매치 조건이 선택된 base 의 superset 인지 검증 (KEYWORD 는 token 포함, 그 외는 strict equality) 하여 helper text 로 경고.
   - 저장 → `RuleDraftFactory.create(..., overrideOf = ...)` 로 `RuleUiModel` 생성 → `rulesRepository.upsertRule(rule)`. `RuleOverrideValidator` 가 self-reference / circular chain 을 감지하면 persist 없이 `Log.e` 후 reject.
7. 삭제 버튼 → `rulesRepository.deleteRule(id)`.
8. 순서 이동 → drag handle long-press + vertical drag (48dp step) 또는 arrow 버튼 → `rulesRepository.moveRule(id, direction)`. `RuleOrdering` 이 tier key (`overrideOf`) 가 동일한 가장 가까운 이웃까지만 swap 하도록 non-tier rows 를 skip — base 는 base 끼리, override 는 같은 base 를 공유하는 sibling 끼리만 우선순위가 바뀌고, 다른 tier 와는 절대 섞이지 않음.
9. 활성 토글 → `rulesRepository.setRuleEnabled(id, enabled)`.

## Exit state

- DataStore 의 `smartnoti_rules` 에 룰 상태가 영속화됨.
- 이후 새로 게시되는 알림은 `RulesRepository.currentRules()` 를 통해 업데이트된 룰을 참조 (→ [notification-capture-classify](notification-capture-classify.md)).

## Out of scope

- 알림 카드 액션으로 인한 자동 룰 생성 (→ [rules-feedback-loop](rules-feedback-loop.md))
- 온보딩 quick-start 로 생성되는 초기 프리셋 룰 (→ [onboarding-bootstrap](onboarding-bootstrap.md))
- 룰이 실제로 분류에 어떻게 적용되는가 (→ [notification-capture-classify](notification-capture-classify.md))

## Code pointers

- `ui/screens/rules/RulesScreen` — 메인 화면 + 편집 다이얼로그
- `domain/usecase/RuleDraftFactory`
- `ui/screens/rules/RuleEditorDraftValidator`
- `ui/screens/rules/RuleEditorAppSuggestionBuilder`
- `ui/screens/rules/RuleEditorRepeatBundleThresholdController`
- `ui/screens/rules/RuleEditorOverrideOptionsBuilder` — override base 후보 dropdown 빌더
- `ui/screens/rules/RuleOverrideSupersetValidator` — override draft superset 검증
- `ui/screens/rules/RuleListPresentationBuilder`
- `ui/screens/rules/RuleListFilterApplicator`
- `ui/screens/rules/RuleListGroupingBuilder`
- `ui/screens/rules/RuleListHierarchyBuilder` — flat list → base/override tree (1-level)
- `domain/usecase/RuleOrdering` — tier-aware `moveRule` 이웃 탐색
- `domain/usecase/RuleOverrideValidator` — self-reference / cycle 감지
- `domain/usecase/RuleConflictResolver` — 동일 tier 타이-브레이크 + base/override 선택 (분류기 측)
- `ui/components/RuleRow` — `RuleRowPresentation.Base` / `Override` / `BrokenOverride`
- `data/rules/RulesRepository` — DataStore `smartnoti_rules`, override-aware upsert
- `data/rules/RuleStorageCodec` — 8-column 포맷 (legacy 7-column tolerant)
- `domain/model/RuleUiModel` — `RuleTypeUi`, `RuleActionUi`, nullable `overrideOf`

## Tests

- `RuleEditorDraftValidatorTest`
- `RuleEditorOverrideOptionsBuilderTest`
- `RuleOverrideSupersetValidatorTest`
- `RuleListFilterApplicatorTest`
- `RuleListGroupingBuilderTest`
- `RuleListHierarchyBuilderTest`
- `RuleOrderingTest` — tier-aware 이웃 swap
- `RuleOverrideValidatorTest`
- `RuleConflictResolverTest`
- `RuleDraftFactoryTest` (있다면)

## Verification recipe

```bash
# 1. 규칙 탭에서 "새 규칙 추가" → 키워드 "인증번호" + ALWAYS_PRIORITY 저장
# 2. 이후 동일 키워드 알림이 Priority 로 분류되는지 확인
adb shell cmd notification post -S bigtext -t "은행" OtpTest "인증번호 123456"
# 3. Home 의 StatPill 즉시 카운트 또는 Priority 탭에 반영 확인
```

## Known gaps

- 룰 편집이 AlertDialog 기반이라 입력 화면이 좁음 — 복잡한 SCHEDULE/REPEAT_BUNDLE 편집이 답답할 수 있음.
- 룰 내보내기/가져오기 미구현.
- 동일 매치값 + 다른 액션 룰이 충돌할 때의 우선순위는 `RuleConflictResolver` 의 "동일 tier 는 `allRules` 인덱스 순 (earlier wins)" 규칙에 따름. UI 에서는 tier 내부 드래그 재배치로만 조정 가능.
- Override 체인은 1-level 만 지원 (base → override). `C → B → A` 같은 다단 체인은 `RuleListHierarchyBuilder` 가 top-level broken 으로 표시하고 분류기 측에서도 resolver 가 한 단계만 탐색. (plan `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Open question #3).
- Recipe fragility — baseline 누적 (2026-04-21 cycle): rules-feedback-loop 경로로 upsert 된 `person:*` 룰이 `smartnoti_rules.preferences_pb` 에 영속화되어 이 recipe 의 "활성 규칙 N개" 헤더 baseline 이 cycle 에 따라 증가한다. 검증 시 추가/삭제 delta (N→N+1, N+1→N) 만 관측하고, 절대값(N) 은 테스트 환경 상태에 따라 달라질 수 있다.
- ADB-only verification 으로는 override pill / nested tree indent / tier-aware drag-reorder 관찰 불가 (2026-04-22 sweep): (a) 현 recipe 가 override 룰을 만들지 않아 DB 에 nested 대상이 없고, (b) `adb shell input text` 가 한글 미지원이라 한글 base (`광고`, `엄마`, `중요 알림` 등) 를 strict-equality 로 override 하는 ASCII 값이 없으며, (c) same-value ASCII 시도 (e.g. `TestSender_0421_T12` → `TestSender_0421_T12`) 는 `RuleOverrideValidator` 의 SELF_REFERENCE 로 정상 reject. Phase C UI 재검증은 instrumentation test (`RulesScreenTest` 혹은 Compose UI test) 또는 사전-seed 된 override 룰이 포함된 fixture 로 이동하는 것이 안정적.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Rule editor 에 IGNORE (무시) 액션 추가 — dropdown 라벨 "무시 (즉시 삭제)", `RuleListPresentationBuilder` 가 IGNORE 룰이 존재할 때만 overview/filter 세그먼트 노출, `RuleListGroupingBuilder` 가 "무시" 섹션을 tier 스택 최하단에 emit, `RuleRow` 의 action chip 이 IGNORE 에 한해 border-only 중립 회색 톤으로 렌더. 코덱/설명/그룹/프레젠테이션 단위 테스트 보강. IGNORE 룰이 매치된 알림은 `NotificationClassifier` 가 `NotificationDecision.IGNORE` 를 반환 (#179 `5f516d6`), `SmartNotiNotificationListenerService` 가 tray cancel 만 수행하고 replacement alert 는 건너뜀 (#181 `fb532c1`), 기본 뷰는 IGNORE 를 제외하고 Settings opt-in 토글 시에만 [ignored-archive](ignored-archive.md) 에 노출 (#185 `9a5b4b9`). Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Tasks 1-6 (#178 `6aad9d5`, #179 `5f516d6`, #181 `fb532c1`, #183 `9a65992`, #185 `9a5b4b9`, #187 `57df6ac`). `last-verified` 는 ADB 검증 실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: Phase C (hierarchical rules v2) 반영 — `RuleUiModel.overrideOf` 필드 추가, `RuleStorageCodec` 8-column 포맷, `RuleOverrideValidator` 가 self-reference / circular chain reject (#148). Rule editor AlertDialog 에 "예외 규칙" 섹션 + base dropdown + `RuleOverrideSupersetValidator` helper text (#151). Rules 탭이 `RuleListHierarchyBuilder` 로 base/override tree 렌더, `RuleRow` 에 `Base` / `Override` / `BrokenOverride` presentation 분기 (#150). Drag-to-reorder 가 `RuleOrdering` 으로 tier-aware swap 만 허용 (#152). 1-level override 체인만 지원 — Known gaps 참고. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase C Task 1/3/4/5. `last-verified` 는 실제 recipe 재실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: Fresh APK (build 2026-04-22) re-verify on emulator-5554. PASS — recipe 의 키워드 "인증번호" 포스트 → Home StatPill 즉시 카운트 16→17 로 반영 확인. Phase C 관측: 편집 다이얼로그 "예외 규칙" switch 토글 시 base dropdown 이 9개 candidate (`중요 알림`, `프로모션 알림`, `반복 알림`, `엄마`, `TestSender_0421_T11`, `TestSender_0421_T12`, `SilentTest_0421_T1`, `광고`, `IgnoreTestRule`) 를 채워 렌더. Action dropdown 에 IGNORE "무시 (즉시 삭제)" chip 노출, 리스트 상단 filter 에 "무시 1" chip 및 최하단 tier 섹션 "무시 / 규칙 1개" 렌더. 그룹 순서: 즉시 전달 3 → Digest 4 → 조용히 1 → 무시 1 (doc 과 일치). Override pill / nested indent / drag-reorder 는 이번 세션에서 end-to-end exercise 불가 — 현 DB 에 override 룰이 없고, strict-equality 타입 (PERSON/APP/SCHEDULE/REPEAT_BUNDLE) 은 same-value override 시 `RuleOverrideValidator` 가 SELF_REFERENCE 로 reject 되므로 (base.id == draft.id), KEYWORD superset 또는 한글 입력이 가능한 instrumentation 환경에서 재검증 필요. Known gaps 에 observability 제한 기록.
