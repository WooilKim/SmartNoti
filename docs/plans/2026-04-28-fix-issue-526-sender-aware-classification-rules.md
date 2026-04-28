---
status: planned
fixes: 526
priority: P1
last-updated: 2026-04-28
---

# Fix #526 — Sender-aware classification rules (특정 발신자 always PRIORITY)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Source issue: https://github.com/WooilKim/SmartNoti/issues/526 (P1 release-prep, 3-issue cohort #511 / #524 / #525 / #526). Loop is in release-prep / issue-driven mode per merged meta-plan #479. The user's anchor case is Microsoft Teams 1:1 DM `com.sds.teams | 김동대(Special Recon) | … → SILENT` — no current rule type can express "이 발신자가 보낸 모든 알림을 PRIORITY 로". Plan introduces a new `RuleTypeUi.SENDER` matcher (matches notification **title**, substring + ignoreCase) plus a Detail-screen one-tap CTA so the user can convert "방금 본 발신자" → "always PRIORITY" without entering Settings.

**Goal:** Teams 1:1 DM "김동대(Special Recon)" 같은 알림이 SILENT 분류되었을 때, 사용자가 그 알림 Detail 로 진입하면 LazyColumn 첫 카드 위치에 단일 `SenderRuleSuggestionCard` 가 노출된다 — 카드 본문은 `이 발신자를 항상 [중요]로 분류할까요? "김동대(Special Recon)"` + 두 버튼 (`예, 중요로` / `무시`). `[예]` 탭 시: (1) `RuleTypeUi.SENDER` 룰이 즉시 생성되어 (matchValue = title 텍스트 전체) DataStore 에 영속화, (2) 사용자의 `important_priority` Category (없으면 일회성 picker 로 사용자가 destination Category 선택) 의 `ruleIds` 에 신규 ruleId 추가, (3) 토스트 한 줄 (`"같은 발신자의 다음 알림부터 중요로 분류돼요"`). 이후 같은 title 을 가진 다음 알림은 `NotificationClassifier` 가 SENDER 룰 매치 → 소속 Category (PRIORITY action) → `NotificationDecision.PRIORITY` 로 라우팅. Settings → 룰 관리 의 RuleEditor 에도 type dropdown 에 "SENDER" 옵션이 추가되어 manual 작성도 가능 (matchValue label = "발신자 이름").

**Architecture:**
- 신규 enum case `RuleTypeUi.SENDER` 를 `RuleUiModel.kt` 에 추가. `Rule` data class (`Rule.kt`) 는 동일 enum 을 reference 하므로 추가 변경 불필요. 기존 5 enum case (PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 는 그대로 — SENDER 는 6th case 로 append.
- `NotificationClassifier.matches()` 에 `RuleTypeUi.SENDER` branch 추가:
  ```kotlin
  RuleTypeUi.SENDER -> input.title.contains(rule.matchValue, ignoreCase = true) &&
      rule.matchValue.isNotBlank()
  ```
  `input.title` 은 항상 non-null (현재 ClassificationInput contract) 이므로 null-guard 불필요. `matchValue` 가 blank 이면 false (모든 알림 매치 방지). PERSON 과 차이: PERSON 은 `input.sender` 와 정확 일치, SENDER 는 `input.title` 의 **substring** + ignoreCase. Teams / Slack / KakaoTalk 1:1 DM 처럼 sender 메타데이터는 비어있고 title 이 발신자 이름인 메신저 패턴을 위해 도입.
- `CategoryConflictResolver` 의 specificity ladder 에 SENDER 추가. 이슈 본문은 "KEYWORD 와 동일 우선순위" 라고 하지만, **SENDER 가 KEYWORD 보다 더 specific** (SENDER 는 title 만 보고, KEYWORD 는 title+body 둘 다 봄 → SENDER 매치는 KEYWORD 매치보다 명확한 의지) — 그래서 ladder 를 `APP > SENDER > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE` 로 권장. 이 결정은 Risks / open questions 의 Open Question 으로 사용자 확인 필요.
- `RuleDraftFactory.create()` 는 SENDER 도 통과 (당연 — `when (type)` 의 `else -> trimmed` 분기가 SENDER 를 cover). 별도 normalize 룰 없음 (substring 매치이므로 trim 만 충분).
- `RuleStorageCodec` 는 `RuleTypeUi.valueOf(...)` 만 호출하므로 enum 추가만으로 자동 호환. 단, **forward-compat** — 이 빌드를 install 한 사용자가 SENDER 룰을 작성한 뒤 구버전으로 downgrade 하면 `RuleTypeUi.valueOf("SENDER")` 가 throw → decode mapNotNull 이 row 를 drop (전체 룰 리스트가 깨지지 않음, SENDER 룰만 invisible). `RuleStorageCodec.decode` 의 mapNotNull 이 이미 try/catch 없이 valueOf 를 직접 호출하므로 throw 가 row drop 으로 자연 변환되도록 try-catch wrap 필요 — Task 5 에서 명시.
- `RuleEditorTypeSection.kt` 의 두 헬퍼 (`typeLabel` / `matchLabelFor`) 에 SENDER case 추가:
  - `typeLabel(SENDER) = "발신자"`
  - `matchLabelFor(SENDER) = "발신자 이름"`
  RuleEditor 자체의 type dropdown 은 enum 전체를 iterate 하므로 SENDER 가 자동으로 옵션 list 에 등장 (RuleEditor source 확인 필요 — `RulesScreen.kt` 의 dropdown wiring; Task 6 에서 detail).
- 신규 `SenderRuleSuggestionCard` Composable (`ui/screens/detail/`) 는 `NotificationDetailScreen` 의 LazyColumn 의 `NotificationDetailReclassifyCta` 위에 단일 `item { }` 으로 호스팅. spec object (`SenderRuleSuggestionCardSpec`) 가 카피 / 노출 조건 / 버튼 라벨을 single source of truth 로 응축 — `SenderRuleSuggestionCardSpecTest` 가 회귀 가드.
- 노출 조건 (spec 가드) — title 이 PII-likely 일 때만 카드 노출:
  - title 이 비어있지 않음.
  - title 이 시스템 알림 sentinels 에 매치되지 않음 (`"Android System"`, `"시스템 알림"`, `"System notifications"` 등 — 명시적 deny list, Task 4 spec 정의).
  - title 길이 ≤ 60 자 (긴 헤드라인은 메신저 sender 가 아닐 가능성 높음 — heuristic, Open Question 으로 plan 에 명시).
  - 이미 그 title 에 매칭되는 SENDER 룰이 존재하지 않음 (사용자가 한번 만든 sender 는 카드 재노출 안 함).
  - 사용자가 Settings 에서 "발신자 분류 제안 활성화" 토글을 ON 으로 둠 (Open Question — v1 default ON 권장).
- `[예]` 의 SENDER rule 생성 + Category 부착 path:
  - `RuleDraftFactory.create(title = "${title} 발신자", matchValue = title, type = RuleTypeUi.SENDER, draft = false)` 로 룰 생성.
  - 사용자의 `important_priority` Category (existing `category.action == CategoryAction.PRIORITY` 중 첫 번째) 를 자동 destination 으로 사용 — 없으면 작은 picker bottom sheet 로 사용자가 선택 (또는 새 PRIORITY Category 를 즉시 생성). picker 노출 분기는 Task 7 에서 detail.
  - `RulesRepository.upsert(rule) → SettingsRepository.addRuleToCategory(categoryId, ruleId)` 두 호출이 같은 ViewModel 메서드 안에서 sequential. 부분 실패 (rule persisted but category attach fails) 는 다음 진입 시 spec 의 "이미 sender 룰 존재" 가드가 막아 중복 카드 노출은 방지하지만 룰이 categoryless 가 됨 → "draft = true" 로 표시되어 RulesScreen 의 "작업 필요" 버킷에 surface (사용자 follow-up 경로 자연).

**Tech Stack:** Kotlin, Jetpack Compose (Material3, Card + Row + 두 OutlinedButton), DataStore (rules + categories 기존 path 재사용 — 신규 key 없음), JUnit + MockK (NotificationClassifier sender branch + SenderRuleSuggestionCardSpec 테스트), Hilt (기존 module 재사용 — 신규 binding 없음), ADB e2e on R3CY2058DLJ.

---

## Product intent / assumptions

- **사용자가 원하는 affordance 는 "지금 본 알림의 발신자를 한 번에 중요로 올리기"** — Settings → 룰 관리 → 신규 → type 선택 → 매칭값 입력 → 저장 → Category 부착 의 5+ 탭 경로를 1 탭 (`[예]`) 로 단축. 메신저 1:1 DM 의 alarming-level miss (업무 / 가족 메시지 SILENT 분류) 가 본 issue 의 트리거.
- **새 RuleType 도입의 정당성 — 기존 PERSON 으로 안 되는 이유.** PERSON 은 `input.sender` 의 **정확 일치**. Teams / Slack 같은 메신저는 1:1 DM 의 sender 메타데이터가 비어있거나 자기 자신 (앱 본인) 이고 발신자 정보는 title 에만 들어옴. PERSON 은 그래서 안 잡힘. SENDER 는 `input.title` 의 **substring** 매치로 메신저 패턴을 cover.
- **matchValue 는 title 전체 (괄호 포함) 로 자동 채움.** `김동대` 단독으로 저장하면 다른 김동대 (system / 광고 등) 가 false-positive. `김동대(Special Recon)` 처럼 disambiguator 까지 포함하면 specificity ↑. UI 는 자동 채움하되 사용자가 textfield 에서 직접 편집 가능 — 일반 RuleEditor 진입 시에만 (Detail CTA 는 자동 채움 후 즉시 저장, 편집 단계 없음).
- **Detail CTA 의 노출 조건 — PII heuristic.** `system_alerts` / `Android System` / `시스템 알림` 등 시스템 sentinels 는 명시적 deny. 길이 ≤ 60 자 / 비어있지 않음 / 이미 매칭 SENDER 룰 부재 + Settings 토글 ON 의 5 가드. 한글 + 괄호 패턴 (`이름(부서)`) 만 허용하는 더 정교한 heuristic 도 가능하나 v1 은 단순 — false-negative 가 false-positive 보다 안전.
- **SENDER 룰의 Category 부착 default — `important_priority` 자동 선택.** 사용자에게 "어느 분류?" 를 묻지 않고 `category.action == CategoryAction.PRIORITY` 중 첫 번째 (또는 예약된 ID `important_priority`) 에 자동 부착. PRIORITY Category 가 하나도 없으면 picker 노출 또는 신규 PRIORITY Category 즉시 생성 (Task 7 에서 분기 결정).
- **Specificity ladder 변경 (SENDER vs KEYWORD).** 본 plan 은 ladder 를 `APP > SENDER > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE` 로 변경 권장 — SENDER 는 title 만 보므로 KEYWORD (title+body) 보다 매치 의지가 명확. 단 사용자 직관에 따라 "본문 안 키워드 매치 (예: 광고) 가 발신자 매치보다 우선" 이 더 자연스러울 수도 — Risks 의 Open Question. v1 default 결정: SENDER > KEYWORD.
- **Settings 토글 default = ON.** "발신자 분류 제안" 을 default ON 으로 두어 학습 가속. OFF 로 둘 사용자 (Detail 진입마다 카드 보기 싫어하는 사용자) 는 Settings 에서 끔. 토글 추가 비용 적음 (기존 settings repo 패턴 mirror).

---

## Task 1: Failing test — `NotificationClassifierSenderRuleTest` [IN PROGRESS via PR #536]

**Objective:** Issue body 의 정확한 fixture (`title="김동대(Special Recon)"` + SENDER rule `matchValue="김동대"` linked to PRIORITY Category) 로 classify 호출 시 `NotificationDecision.PRIORITY` 가 나오는지 RED 상태로 고정. PERSON 룰과 다른 매치 contract (substring vs exact + title vs sender) 도 함께 가드.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierSenderRuleTest.kt`

**Steps:**
1. Test fixture 5 case:
   - `senderRuleMatchesTitleSubstringCaseInsensitive`: title=`"김동대(Special Recon)"` + SENDER rule matchValue=`"김동대"` + PRIORITY Category → decision=PRIORITY.
   - `senderRuleDoesNotMatchWhenTitleEmpty`: title=`""` + SENDER rule matchValue=`"김동대"` → decision=SILENT (default fall-through).
   - `senderRuleDoesNotMatchWhenMatchValueBlank`: title=`"임의 알림"` + SENDER rule matchValue=`""` → false match (모든 알림 PRIORITY 방지) → decision=SILENT.
   - `senderRuleDistinctFromPersonRule`: title=`"홍길동(Team)"` + sender=null + PERSON rule matchValue=`"홍길동"` → no match (PERSON 은 sender 정확일치 요구). 같은 fixture + SENDER rule matchValue=`"홍길동"` → match → PRIORITY.
   - `senderRuleCaseInsensitiveAscii`: title=`"WOOIL KIM(Eng)"` + SENDER rule matchValue=`"wooil kim"` → match → PRIORITY.
2. Helper: `buildClassifier(rules, categories)` — 기존 classifier 단위테스트 패턴 (예: `NotificationClassifierKeywordRuleTest`) 와 일관. ClassificationInput 빌더는 `ClassificationInputFixtures` 가 있으면 재사용, 없으면 inline.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.NotificationClassifierSenderRuleTest"` 로 RED 확인.

## Task 2: Add `RuleTypeUi.SENDER` enum case + classifier branch [IN PROGRESS via PR #536]

**Objective:** Task 1 의 5 fixture 를 GREEN.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/domain/model/RuleUiModel.kt` — enum 에 `SENDER` 추가.
- 수정: `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationClassifier.kt` — `matches()` `when` 에 SENDER branch 추가.
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailReclassifyActions.kt` — `ruleMatches()` (private helper) `when` 에 SENDER branch 추가 (resolveOwningCategoryAction 일관성).
- 수정: `app/src/main/java/com/smartnoti/app/data/rules/RuleStorageCodec.kt` — decode 의 `RuleTypeUi.valueOf(...)` 호출을 try-catch 로 감싸 unknown enum (forward-compat 시) 을 row drop 으로 자연 변환.
- 수정: `app/src/main/java/com/smartnoti/app/domain/usecase/CategoryConflictResolver.kt` — specificity ladder 에 SENDER 추가 (v1 default 위치: APP > SENDER > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE).

**Steps:**
1. `enum class RuleTypeUi { PERSON, APP, KEYWORD, SCHEDULE, REPEAT_BUNDLE, SENDER }` — append 만, 기존 ordinal 유지.
2. `NotificationClassifier.matches()` 의 `when (rule.type)` 에:
   ```kotlin
   RuleTypeUi.SENDER -> rule.matchValue.isNotBlank() &&
       input.title.contains(rule.matchValue, ignoreCase = true)
   ```
3. `NotificationDetailReclassifyActions.ruleMatches()` 에 같은 SENDER branch 추가 (resolve owning category 도 SENDER 룰을 인식해야 Detail 의 "분류 변경" 시트가 destination 을 정확히 propose).
4. `RuleStorageCodec.decode` 의 `RuleTypeUi.valueOf(normalizedParts[3])` 호출을 `runCatching { RuleTypeUi.valueOf(normalizedParts[3]) }.getOrNull() ?: return@mapNotNull null` 로 감싸 unknown enum (예: 미래 RuleType 또는 삭제된 RuleType) 을 graceful skip.
5. `CategoryConflictResolver` 의 `RULE_TYPE_SPECIFICITY` (또는 동등 ranking) map 에 SENDER 추가, KEYWORD 보다 위에. 정확한 자료구조 위치는 구현 시 확인 — `CategoryConflictResolver.kt` 의 specificity ladder 정의부 (TODO: 파일 inspect 로 확정).
6. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.NotificationClassifierSenderRuleTest"` GREEN.
7. `./gradlew :app:testDebugUnitTest` 전체 — 기존 classifier / category resolver / rule editor 테스트가 enum 추가로 깨지지 않는지 확인. 깨지는 곳은 `when` 의 exhaustive branch 미스가 대부분 — 각 site 에 SENDER 의 자연 default 추가.

## Task 3: `RuleDraftFactory` SENDER support — failing test + impl confirmation

**Objective:** `RuleDraftFactory.create(type = SENDER, matchValue = "김동대(Special Recon)")` 가 정상 동작하는지 명시적 가드. 현재 `normalizeMatchValue` 의 `else -> trimmed` 분기로 자연 통과되지만, 회귀 방지로 explicit fixture.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/RuleDraftFactoryTest.kt` — 기존 파일에 SENDER fixture 추가 (없으면 신규).

**Steps:**
1. Fixture 3 case:
   - `senderRuleTrimsMatchValue`: matchValue=`"  김동대(Special Recon)  "` → 결과 matchValue=`"김동대(Special Recon)"`.
   - `senderRuleIdGenerationConsistent`: existingId=null → id=`"sender:김동대(Special Recon)"`.
   - `senderRuleEnabledByDefault`: enabled 파라미터 omit → enabled=true.
2. `RuleDraftFactory` 자체 변경은 (자연 통과로) 불필요 — 테스트가 GREEN 이면 그대로 둠.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.RuleDraftFactoryTest"` GREEN.

## Task 4: `SenderRuleSuggestionCard` Composable + spec contract test

**Objective:** Card 의 시각 / 카피 / 노출 조건 / PII heuristic 을 single source of truth (`SenderRuleSuggestionCardSpec`) 로 응축 + headless contract test.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/detail/SenderRuleSuggestionCard.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/detail/SenderRuleSuggestionCardSpec.kt`
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/detail/SenderRuleSuggestionCardSpecTest.kt`

**Steps:**
1. `object SenderRuleSuggestionCardSpec`:
   - `const val LABEL_ACCEPT = "예, 중요로"`
   - `const val LABEL_DISMISS = "무시"`
   - `fun bodyFor(title: String): String` → `"이 발신자를 항상 [중요]로 분류할까요?\n\"$title\""`.
   - `val SYSTEM_TITLE_DENY_LIST: Set<String> = setOf("Android System", "System notifications", "시스템 알림", "system_alerts")` (정확 일치, ignoreCase). 추가 sentinel 은 ADB e2e 에서 발견 시 보강.
   - `const val MAX_TITLE_LENGTH = 60`
   - `fun shouldShow(title: String, hasExistingSenderRule: Boolean, settingToggleOn: Boolean): Boolean` →
     ```kotlin
     settingToggleOn &&
         !hasExistingSenderRule &&
         title.isNotBlank() &&
         title.length <= MAX_TITLE_LENGTH &&
         SYSTEM_TITLE_DENY_LIST.none { it.equals(title, ignoreCase = true) }
     ```
2. `SenderRuleSuggestionCard(title, onAccept, onDismiss, modifier)` Composable:
   - Outer Card (`MaterialTheme.colorScheme.surface`, corner 16dp / padding 20dp) — Detail 의 다른 Card 와 시각 언어 통일.
   - Column { Text(`bodyFor(title)`, `bodyMedium`), Spacer 12dp, Row { Button(onAccept, LABEL_ACCEPT, weight 1f), OutlinedButton(onDismiss, LABEL_DISMISS, weight 1f) } }.
3. Spec test (8 case):
   - `bodyFor_inserts_title_in_quotes`
   - `shouldShow_returns_false_when_setting_off`
   - `shouldShow_returns_false_when_existing_rule`
   - `shouldShow_returns_false_when_title_blank`
   - `shouldShow_returns_false_when_title_exceeds_max_length`
   - `shouldShow_returns_false_when_title_in_deny_list_case_insensitive`
   - `shouldShow_returns_true_when_all_conditions_met`
   - `labels_are_distinct_and_nonempty`
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.detail.SenderRuleSuggestionCardSpecTest"` GREEN.

## Task 5: Settings toggle — `senderSuggestionEnabled`

**Objective:** "발신자 분류 제안 활성화" 토글을 SmartNotiSettings 에 추가, default ON. SettingsScreen 의 적절한 섹션에 토글 표시.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt` — `senderSuggestionEnabled: Boolean = true` 필드 추가.
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` (또는 적절한 sub-repository — 예: `SettingsClassificationRepository` 가 존재하면 거기) — `setSenderSuggestionEnabled(enabled)` method.
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/settings/...` — Settings 화면의 "분류" 또는 "추천 / 제안" 섹션에 SwitchPreference 한 줄 추가 ("발신자 분류 제안" + supporting text "알림 상세 화면에서 발신자별 중요 분류를 제안받습니다").
- 보강: `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryTest.kt` (또는 sub-repository 테스트) — 두 case (ON → OFF / OFF → ON) atomic edit 가드.

**Steps:**
1. DataStore Preferences key 명: `sender_suggestion_enabled_v1` (Boolean, default true). 기존 boolean toggle 패턴 (e.g. `suppressSourceForDigestAndSilent`) 와 일치.
2. `applyPendingMigrations` 에서 default true 이므로 신규 사용자 + 기존 사용자 모두 ON 으로 시작 — 학습 가속 의도.
3. Settings 화면에 토글 1 줄. supporting text 는 위 카피 그대로.
4. Test 2 case GREEN.

## Task 6: RuleEditor SENDER option + label updates

**Objective:** Settings → 룰 관리 → 신규 룰 의 type dropdown 에 "발신자" (SENDER) 옵션이 노출되고, 선택 시 matchValue label 이 "발신자 이름" 으로 바뀌는지 가드.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/rules/RuleEditorTypeSection.kt` — `typeLabel(SENDER) = "발신자"`, `matchLabelFor(SENDER) = "발신자 이름"` 두 case 추가.
- 수정 (RuleEditor 의 dropdown wiring 사이트): RuleEditor 의 type dropdown 이 `RuleTypeUi.values()` 를 iterate 하는지 또는 hard-coded list 인지 확인. Hard-coded 라면 SENDER 추가.
- 신규 또는 보강: RuleEditor 단위 테스트 (Robolectric 또는 spec test) — SENDER 선택 시 matchValue label = "발신자 이름" 가드.

**Steps:**
1. `typeLabel` / `matchLabelFor` 두 case 추가.
2. `RulesScreen.kt` 또는 RuleEditor 호스트 파일 grep: `RuleTypeUi\.(PERSON|APP|KEYWORD|SCHEDULE|REPEAT_BUNDLE)` 으로 hard-coded list 위치 찾고, 발견 시 SENDER 추가. `RuleTypeUi.values()` 또는 `entries` 사용 site 는 자동 cover.
3. Test: `RuleEditorTypeSection` 의 두 헬퍼 가 SENDER 에 대해 정확한 label 반환하는지 (단순 assertion).
4. 빌드 확인 — `when (type)` exhaustive 가드가 어디서 깨지는지 빌드 에러로 확인 후 SENDER branch 추가.

## Task 7: Wire `SenderRuleSuggestionCard` into `NotificationDetailScreen`

**Objective:** Card + spec + settings + rule generator + category attach 를 Detail 화면에 연결. Card 는 `NotificationDetailReclassifyCta` 위에 LazyColumn 의 별도 item 으로.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` (또는 split 후 host file)
- 수정 (또는 신규 viewmodel method): `NotificationDetailViewModel` 에 `acceptSenderSuggestion(notification)` + `dismissSenderSuggestion(notification)` 두 method 추가.
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailReclassifyActions.kt` — `[예]` 분기의 internal helper (`createSenderRuleAndAttachToPriority`) 추가 (또는 viewmodel 안).

**Steps:**
1. ViewModel collect: `settings.senderSuggestionEnabled` + `rules` (현재 SENDER 룰 중 title 매치 존재 여부) + `notification.title` → 세 값을 spec 의 `shouldShow` 에 전달 → derived state `showSenderSuggestion: Boolean` (Compose `derivedStateOf` 또는 viewmodel StateFlow).
2. LazyColumn 의 `item(key = "sender-suggestion-${notification.id}")` 위치 — `NotificationDetailReclassifyCta` 위. `showSenderSuggestion == false` 면 item 자체 emit 하지 않음.
3. `onAccept` 콜백 (viewmodel.acceptSenderSuggestion):
   - `RuleDraftFactory.create(title = "${notification.title} 발신자", matchValue = notification.title.trim(), type = RuleTypeUi.SENDER, draft = false)` 로 룰 생성.
   - `RulesRepository.upsert(rule)` 호출.
   - Destination Category 결정:
     - `categories.firstOrNull { it.id == "important_priority" }` 또는 (없으면) `categories.firstOrNull { it.action == CategoryAction.PRIORITY }`.
     - 둘 다 없으면 picker bottom sheet 노출 (사용자가 destination 선택, 기존 `CategoryAssignBottomSheet` 또는 신규 lightweight picker — Task 7 구현 시 결정).
     - 픽커도 cancel 되면 룰만 persisted 됨 → RulesScreen 의 "작업 필요" 버킷에 surface (draft=true 로 세팅하는 것이 더 명확 — 구현 시 결정).
   - `SettingsRepository.addRuleToCategory(categoryId, rule.id)` (또는 동등 method — 기존 `AssignNotificationToCategoryUseCase.assignToExisting` 패턴 mirror).
   - Snackbar: `"같은 발신자의 다음 알림부터 중요로 분류돼요"`.
4. `onDismiss` 콜백 (viewmodel.dismissSenderSuggestion):
   - 단순 in-memory state clear (이번 Detail 진입에서만 카드 hide). 다음 알림 진입 시 다시 노출 가능.
   - **NB:** 만약 사용자가 같은 알림에 대해 반복 dismiss 하는 패턴이 발견되면 v2 에서 per-title sticky-dismiss 추가 검토 — v1 은 단순.
5. **Out-of-scope: 설정 진입 deep-link.** Card body 옆 작은 "i" 아이콘으로 Settings 의 토글로 이동하는 deep-link 는 v1 에 포함 X (사용자가 토글을 못 찾으면 v2).

## Task 8: ADB e2e on R3CY2058DLJ

**Objective:** Teams-style 알림 (`com.sds.teams` + title=`"김동대(Special Recon)"`) 의 happy path 가 실제로 동작하는지 검증.

**Steps:**
```bash
# 0. 신규 빌드 install
./gradlew :app:assembleDebug
adb -s R3CY2058DLJ install -r app/build/outputs/apk/debug/app-debug.apk

# 1. Teams-style 알림 합성 → SILENT 분류 확인 (현재 동작)
adb -s R3CY2058DLJ shell cmd notification post \
  -S bigtext \
  -t "김동대(Special Recon)" \
  -i com.sds.teams \
  TeamsDM "회의 5분 뒤에 시작합니다"
sleep 2
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "조용히|숨김|김동대"
# 기대: 조용히 N 건 카운트 +1, Hidden 화면에 "김동대(Special Recon)" 알림

# 2. Detail 진입 → SenderRuleSuggestionCard 노출 확인
adb -s R3CY2058DLJ shell input tap <hidden_card_x> <hidden_card_y>
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "발신자를 항상|예, 중요로"
# 기대: card 본문 + 두 버튼 노출

# 3. [예, 중요로] tap → 룰 생성 + Category 부착 + 토스트
adb -s R3CY2058DLJ shell input tap <accept_button_x> <accept_button_y>
sleep 2
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "다음부터 중요로 분류"
# 기대: 토스트 또는 snackbar 노출

# 4. 같은 발신자 알림 재게시 → PRIORITY 분류 확인
adb -s R3CY2058DLJ shell cmd notification post \
  -S bigtext \
  -t "김동대(Special Recon)" \
  -i com.sds.teams \
  TeamsDM2 "두 번째 메시지입니다"
sleep 2
adb -s R3CY2058DLJ shell am force-stop com.smartnoti.app
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "중요|우선|priority"
# 기대: 우선 N 건 카운트 +1, Priority 화면에 두 번째 알림

# 5. Settings → 룰 관리 → 새로 생성된 SENDER 룰 가시 + RuleEditor 의 SENDER 옵션 가시
adb -s R3CY2058DLJ shell input tap <settings_x> <settings_y>
adb -s R3CY2058DLJ shell input tap <rules_x> <rules_y>
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "김동대|발신자"
# 기대: 룰 row 노출, 신규 룰 편집 버튼 → type dropdown 에 "발신자" 옵션
```

Plan frontmatter 를 `status: shipped` + `superseded-by:` 로 flip. 관련 journey 의 `last-verified` 는 verification recipe 를 다시 돌리지 않으므로 갱신 X — 다음 sweep 에서 일괄.

## Task 9: Journey docs sync

**Files:**
- `docs/journeys/notification-capture-classify.md` — Observable steps 에 "SENDER 룰 (title 기반 substring 매치) 도 분류기에 의해 평가됨" 한 줄 추가, Code pointers 에 `RuleTypeUi.SENDER` + classifier branch 추가, Tests 에 `NotificationClassifierSenderRuleTest` 추가, Change log 신규 line.
- `docs/journeys/rules-management.md` — Observable steps 에 "type dropdown 에 발신자 (SENDER) 옵션 존재" 한 줄 추가, Code pointers 에 `RuleEditorTypeSection.typeLabel/matchLabelFor` 갱신 표기, Change log 신규 line.
- `docs/journeys/notification-detail.md` — Observable steps 에 "발신자 분류 제안 카드 (조건부 노출)" 한 줄 추가, Code pointers 에 `SenderRuleSuggestionCard` + `SenderRuleSuggestionCardSpec` 추가, Known gaps 에서 "Detail 에서 발신자별 분류 단축 경로 부재" 가 있으면 해소 표기 + Change log 신규 line.
- (선택) `docs/journeys/categories-management.md` — 무관 (sender 룰은 기존 PRIORITY Category 에 자동 부착, Categories 화면 자체 변경 없음). 단 Known gaps 의 "신규 sender 룰의 Category 부착 경로" 한 줄을 cross-cut 로 추가 검토.

**Steps:**
1. 위 3-4 파일 갱신.
2. `last-verified` 는 verification recipe 를 다시 돌리지 않으므로 갱신 X — Change log 만.

---

## Scope

**In:**
- `RuleTypeUi.SENDER` enum case 1 개 + classifier 의 SENDER branch + `CategoryConflictResolver` specificity ladder 갱신 + RuleStorageCodec 의 unknown-enum graceful skip + RuleEditor 의 SENDER 옵션 / 라벨 + Settings 토글 1 개 + `SenderRuleSuggestionCard` Composable + spec + Detail 화면 wiring 1 점 + 룰 생성 + 자동 Category 부착 (PRIORITY default) + dismiss 분기 + journey 문서 3-4 파일 갱신.

**Out:**
- 발신자 별 다중 destination Category 선택 UX (예: "이 발신자를 [업무] / [가족] 어느 분류로?") — v1 은 PRIORITY 자동. v2 plan 으로 destination picker 풀 구현.
- 메시지 본문 (`input.body`) 까지 보는 SENDER 변형 (예: "발신자 + 키워드") — KEYWORD 룰로 cover 가능.
- Per-title sticky-dismiss (반복 dismiss 패턴 추적) — v2.
- Card 노출 deep-link "i" 아이콘 (Settings 토글로 이동) — v2.
- SENDER 룰의 OFF / 비활성화 UX — 기존 RulesScreen 의 enabled toggle 로 cover.
- SENDER 룰의 KEYWORD 와의 specificity 우선순위 사용자 설정 — v1 은 hard-coded `SENDER > KEYWORD`. 사용자 신고 시 v2 plan 으로 Settings preset 추가.

---

## Risks / open questions

- **Open question for user — Detail CTA 노출 정책.** v1 은 default ON 으로 (조건이 맞는) 모든 Detail 진입에 카드 노출. 학습 가속 의도. **사용자 확인 필요: 이 default 가 적절한가, 아니면 default OFF 로 두고 사용자가 Settings 에서 명시적으로 켜야 하는가?** Plan 결정: default ON (학습 가속 + 토글 추가 비용 적음). 반대 시 Task 5 의 default 만 false 로 변경.
- **Open question for user — Specificity ladder.** v1 은 `APP > SENDER > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE` 권장 (SENDER 가 KEYWORD 보다 specific — title 만 vs title+body). **사용자 직관 확인 필요: 본문 안 "광고" 키워드 매치가 발신자 매치보다 우선되어야 하는가, 아니면 발신자 매치가 우선?** Plan 결정: SENDER > KEYWORD (사용자가 sender 룰을 만든 의지가 광고 키워드 매치보다 명확). 반대 시 Task 2 의 ladder 변경.
- **PII heuristic false-negative.** `MAX_TITLE_LENGTH = 60` 가드는 긴 헤드라인 (예: 뉴스 타이틀) 을 발신자가 아니라고 판정하지만, 60 자 미만의 광고 헤드라인 / 시스템 알림 sentinel 이 아닌 시스템성 알림은 여전히 false-positive 가능. 사용자 신고 시 v2 plan 으로 한글+괄호 패턴 등 더 정교한 heuristic 추가.
- **Forward-compat — SENDER 룰의 구버전 downgrade.** SENDER 룰 작성 후 사용자가 구버전 APK 로 downgrade 하면 `RuleTypeUi.valueOf("SENDER")` throw → Task 2 의 try-catch 가 row drop 으로 자연 변환 → 그 사용자에게는 SENDER 룰이 invisible. 다시 신버전으로 upgrade 하면 storage 가 그대로이므로 룰 복귀. 이 동작이 사용자 직관에 맞는지는 확인 필요 — v1 은 graceful drop 으로 가정.
- **Race — rule persisted but Category attach fails.** Task 7 의 두 sequential 호출 (`upsert(rule)` → `addRuleToCategory`) 이 부분 실패하면 categoryless rule 이 남음. 본 plan 은 `draft=true` 로 마킹해 RulesScreen 의 "작업 필요" 버킷에 surface — 사용자 follow-up 자연. Atomic 보장이 필요하면 v2 에서 transaction-style use case 도입.
- **Snooze / dismiss 의 영속화 부재.** Card 의 `[무시]` 는 in-memory dismiss 만 — 다음 알림 진입에서 같은 발신자 카드 다시 노출. 사용자가 반복 dismiss 하면 noise. v1 은 의도된 단순 — 사용자 신고 시 v2 에서 per-title sticky-dismiss DataStore 영속화 추가.
- **Settings → 룰 관리 → SENDER 옵션의 자동 Category 부착 부재.** RuleEditor 에서 manual 작성한 SENDER 룰은 (Detail CTA 와 달리) 자동으로 PRIORITY Category 에 부착되지 않음 — 사용자가 post-save assignment sheet 에서 명시적으로 선택. 본 plan 은 일관 — RuleEditor 는 일반 룰 작성 경로, Detail CTA 는 단축 경로. 분리 유지.
- **Title-based 매치의 다국어.** `contains` + `ignoreCase` 는 Locale 의존성 미세하게 있음 (Turkish dotted i 등). 한국어 + 영어 fixture 는 cover, 그 외 locale 은 v1 무시.

---

## Related journey

- [`notification-capture-classify`](../journeys/notification-capture-classify.md) — SENDER 룰의 분류 evaluation 경로. shipped 후 Observable steps + Code pointers + Change log 갱신.
- [`rules-management`](../journeys/rules-management.md) — RuleEditor 의 SENDER 옵션 추가. shipped 후 Observable steps + Code pointers + Change log 갱신.
- [`notification-detail`](../journeys/notification-detail.md) — `SenderRuleSuggestionCard` 의 호스트 화면. shipped 후 Observable steps + Code pointers + Change log 갱신.
- [`categories-management`](../journeys/categories-management.md) — 무관 (sender 룰은 기존 PRIORITY Category 에 자동 부착, Categories 화면 자체 변경 없음). 선택적 cross-cut 한 줄.

Source issue: https://github.com/WooilKim/SmartNoti/issues/526
