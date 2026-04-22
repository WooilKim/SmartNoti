---
status: planned
created: 2026-04-22
---

# Categories (분류) — Split Rule = {condition + action} into Rule (condition) + Category (action container)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Scope is large; ship in Phase P1 → P2 → P3 order with each phase as its own PR sequence.

**Goal:** 사용자는 알림 처리 단위를 "**분류(Category)**" 라는 상위 개념으로 다루게 된다. 기존 Rule 은 "조건 매처" 로만 남고, 액션(PRIORITY/DIGEST/SILENT/IGNORE)은 Category 에 소속된다. 기존 데이터는 1:1 자동 마이그레이션으로 보존되며, 사용자가 새 앱을 감지하면 Home 에서 "**새 앱 분류 유도 카드**" 를 통해 자연스럽게 wizard 로 유입된다. BottomNav 는 Rules 탭을 없애고 4-tab (Home / 정리함 / 분류 / Settings) 로 단순화된다.

**Architecture:**
- `Rule` 도메인 모델에서 `action` 필드를 제거 (순수 조건 매처). `RuleUiModel` / `RuleActionUi` 도 액션 관련 필드를 걷어냄.
- 신규 `Category` 도메인 모델: `id`, `name` (한글 기본 "분류"), `appPackageName?`, `ruleIds: List<String>`, `action: CategoryAction (PRIORITY/DIGEST/SILENT/IGNORE)`. 영속은 DataStore 기반 `CategoriesRepository` (기존 `RulesRepository` 와 동일 패턴).
- Classifier 는 매칭된 Rule 집합 → 그 Rule 을 포함하는 Category 집합을 찾고, specificity tie-break (app > keyword > sender > time) 으로 최종 Category.action 적용.
- Room schema v9 → v10 bump: Rule 의 `action` column 제거, Category 테이블은 DataStore 이므로 Room 영향 없음 (단 Rule 테이블이 있으면 drop column).
- UI: 분류 탭 신규, Digest + Hidden 탭 → "정리함" 통합 탭, Rules 탭은 Settings 하위 "고급 규칙 편집" 으로 이동.
- IGNORE 는 `CategoryAction.IGNORE` 로 보존 — 기존 IGNORE 아카이브 토글 + `IgnoredArchiveScreen` 은 `Category.action == IGNORE` 를 읽는 방식으로 재배선.

**Tech Stack:** Kotlin, Room (schema v10), DataStore, Jetpack Compose, Gradle unit tests, Claude Code implementation.

---

## Product intent / assumptions

- **이름 "분류":** 사용자가 한글 UX 에서 읽는 noun 은 "분류" (확정). "그룹" 아님. 영문 코드는 `Category` / `CategoriesRepository`.
- **자동 마이그레이션 1:1:** 기존 Rule 하나당 Category 하나를 자동 생성. `name = rule.matchValue`, `ruleIds = [rule.id]`, `action` 은 Rule 이 갖고 있던 action 을 그대로 상속. 마이그레이션은 첫 실행 시 1회 실행, `migration_v2_complete` DataStore flag 로 gate.
- **IGNORE 는 유효한 Category action:** PRIORITY/DIGEST/SILENT 와 동등. IGNORE 아카이브 토글(`ignoredArchiveEnabled`)과 `IgnoredArchiveScreen` 은 Rule-level IGNORE 가 아니라 Category-level IGNORE 를 기준으로 필터링.
- **Home "새 앱 분류 유도 카드":** `NotificationRepository` 의 distinct `packageName` 집합과 Category 들이 커버하는 `appPackageName` 집합을 비교해, 커버되지 않은 앱이 N≥3 이상 감지되면 Home 카드로 노출. 탭하면 분류 생성 wizard 로 진입.
- **Rules 탭 제거의 UX 리스크:** 사용자는 지금까지 "Rules" 에서 수동 편집해 왔다. Onboarding 에서 "규칙은 이제 '분류' 안에서 편집됩니다" 안내 + Settings > 고급 규칙 편집 진입점 제공.
- **Classifier hot path:** 기존 `RuleConflictResolver` 가 action 결정의 중심. 이 로직이 Category 단위로 옮겨가되 specificity 정렬은 동일 — 테스트로 parity 를 먼저 고정한 뒤 교체.

---

## Phase P1 — Data model + migration

### Task 1: Failing tests for Category model + round-trip + Rule-without-action contract

**Objective:** 새 모델 계약과 마이그레이션 parity 를 테스트로 먼저 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/model/CategoryTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/data/categories/CategoriesRepositoryTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/data/rules/RuleMigrationToCategoryTest.kt`

**Steps:**
1. `CategoryTest` — `Category` 가 필수 필드 (`id`, `name`, `ruleIds`, `action`) 와 선택 필드 (`appPackageName`) 를 갖고, action enum 이 PRIORITY/DIGEST/SILENT/IGNORE 4개로 제한되는지.
2. `CategoriesRepositoryTest` — CRUD + 순차 id 부여 + JSON round-trip (DataStore 직렬화 형식). 동일한 rule id 가 여러 Category 에 속할 수 있는지 (허용).
3. `RuleMigrationToCategoryTest` — 입력 `List<Rule (with action)>`, 출력 `List<Category>` 가 1:1, `ruleIds == [rule.id]`, `action` 상속, `name == rule.matchValue`, IGNORE 도 그대로 옮겨지는지.
4. `./gradlew :app:testDebugUnitTest` 실패 확인.

### Task 2: Add Category domain model + CategoriesRepository (Rule.action deprecated but retained)

**Objective:** Task 1 초록. 단 이 단계에서는 Rule 의 `action` 필드를 `@Deprecated` 로만 표기, 실제 제거는 Task 4 에서.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/model/Category.kt` (+ `CategoryAction`)
- 신규: `app/src/main/java/com/smartnoti/app/data/categories/CategoriesRepository.kt`
- 변경: `app/src/main/java/com/smartnoti/app/data/rules/RuleSerializer.kt` — 기존 Rule 직렬화는 그대로, 단 action 필드는 nullable 로 완화해도 됨.

**Steps:**
1. `Category` data class + `CategoryAction` enum 정의.
2. DataStore 기반 repository — key `categories_json_v1`. 쓰기/읽기/observe(Flow) API.
3. Rule 의 action 필드에 `@Deprecated("Moved to Category.action")` 추가.

### Task 3: Auto-migration on first launch post-upgrade

**Objective:** 기존 사용자의 Rule 들을 손실 없이 Category 로 옮긴다.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/data/categories/RuleToCategoryMigration.kt`
- 변경: Application 초기화 경로 (예: `SmartNotiApplication` 또는 ViewModel 초기 로드 지점) — 1회 실행 hook.
- 변경: `SettingsRepository` — `migrationV2CategoriesComplete: Boolean` flag 추가.

**Steps:**
1. `RuleToCategoryMigration.migrateIfNeeded(rulesRepo, categoriesRepo, settingsRepo)` — flag 가 false 면 실행. 각 Rule → Category 를 idempotent 하게 생성 (id 충돌 방지를 위해 `cat-from-rule-<ruleId>` 포맷).
2. 전체 저장은 transactional 에 가깝게 — 기존 DataStore 의 `updateData` 블록 안에서 batch commit.
3. 완료 후 flag = true. 도중 crash 시 다음 실행에서 재시도 가능 (idempotent).
4. 테스트: 마이그레이션 두 번 돌려도 Category 가 중복 생성되지 않는지.

### Task 4: Remove action field from Rule + Room schema v9 → v10

**Objective:** Rule 이 순수 조건 매처가 됨. 코드 전역의 `rule.action` 참조 제거.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/domain/model/Rule.kt`
- 변경: Room migration — `app/src/main/java/com/smartnoti/app/data/database/AppDatabase.kt` (v9→v10), `Rule` 관련 Entity 가 Room 에 있으면 drop column. DataStore-only 라면 JSON 스키마만 완화.
- 변경: 모든 `rule.action` 참조 지점 — `RuleConflictResolver`, `ClassifyNotificationUseCase`, `RulesViewModel`, `RulesScreen`, `RuleEditorScreen`, onboarding preset 생성기.
- 변경: `RuleUiModel`, `RuleActionUi` — action 관련 필드 제거.

**Steps:**
1. Room 이 Rule 을 가지고 있으면 v9→v10 migration 작성 (drop `action` column). DataStore-only 라면 Rule JSON 파서가 action 을 무시하게.
2. 컴파일 에러 따라가며 `rule.action` 참조를 모두 Category 조회로 전환 (이 단계는 임시 shim — 진짜 classifier 재배선은 Phase P2).
3. 단위 테스트 대대적 갱신. 기존 Rule action 기반 테스트는 Phase P2 에서 Category 기반으로 재작성 예정이므로 임시로 `@Ignore` 가능. 단 무시 처리는 최소화.

---

## Phase P2 — Classifier rewire

### Task 5: Classifier uses Category.action via specificity tie-break

**Objective:** 알림이 들어오면 매칭 Rule 집합 → 그 Rule 을 포함하는 Category 집합 → specificity 순(app > keyword > sender > time) 정렬 → 최상위 Category.action 적용.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/domain/usecase/ClassifyNotificationUseCase.kt`
- 변경: `app/src/main/java/com/smartnoti/app/domain/rules/RuleConflictResolver.kt` → `CategoryConflictResolver.kt` (rename + logic 재작성).
- 신규/변경 테스트: `ClassifyNotificationUseCaseTest`, `CategoryConflictResolverTest`.

**Steps:**
1. 먼저 기존 `RuleConflictResolver` 의 모든 케이스를 Category 기반으로 재현하는 failing tests 작성 (Phase P1 에서 임시 disable 한 테스트 복원 + 확장).
2. `CategoryConflictResolver` 구현 — Rule 매칭은 그대로, 그 결과를 Category 로 lift.
3. `ClassifyNotificationUseCase` 에서 `rulesRepo` + `categoriesRepo` 둘 다 읽어 합성.

### Task 6: CategoryConflictResolver (higher-specificity Category overrides)

**Objective:** 사용자가 만든 좁은 범위 Category (app-specific) 가 넓은 범위 Category (keyword-only) 의 action 을 override.

**Files:**
- 변경: `CategoryConflictResolver.kt`
- 테스트 보강.

**Steps:**
1. Category 의 specificity = max(member Rule specificity). Rule 과 달리 Category 자체는 app pin 여부가 있으므로 `appPackageName != null` 이면 bonus.
2. 동일 specificity 면 Category id 사전순 또는 생성 시각 최신 우선 (제품 결정 필요 — Risks 참조).
3. 테스트로 tie-break 엣지 케이스 (동일 specificity, IGNORE vs SILENT 충돌 등) 고정.

### Task 7: Notifier + NotificationFeedbackPolicy 가 Category.action 을 읽도록 rewire

**Objective:** 알림 소비 경로(표시/억제/숨김)가 모두 Category.action 을 기반으로 분기.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- 변경: `app/src/main/java/com/smartnoti/app/notification/NotificationFeedbackPolicy.kt`
- 변경: `app/src/main/java/com/smartnoti/app/notification/NotificationSuppressionPolicy.kt` — action 분기 지점.
- 테스트: 기존 policy 테스트들을 Category 기반 입력으로 재작성.

**Steps:**
1. 기존에 `decision.action` / `rule.action` 으로 분기하던 지점을 모두 Category.action 으로 통일.
2. IGNORE → Notifier 는 저장만 하고 사용자 tray 에 내보내지 않음. IGNORE 아카이브 토글이 켜져 있으면 `IgnoredArchiveScreen` 에서 조회 가능.
3. 단위 테스트: PRIORITY/DIGEST/SILENT/IGNORE 4개 경로 각각 smoke test.

---

## Phase P3 — UI + nav reshape

### Task 8: 분류 (Categories) primary tab + list + detail view

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryDetailScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesViewModel.kt`
- 변경: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`
- 변경: BottomNav — Categories 탭 icon/label 추가.

**Design:**
- List: Category 카드 — 이름, 앱 뱃지(있으면), action 뱃지(PRIORITY/DIGEST/SILENT/IGNORE 색상 구분), 소속 Rule 수.
- Detail: Category 기본 정보 + 소속 Rule 리스트 + 최근 이 Category 로 분류된 알림 preview + "편집" / "삭제" 액션.
- `.claude/rules/ui-improvement.md` 준수 — 다크 기본, 카드 톤 차분, accent 는 PRIORITY 등 urgent 상태에만.

**Steps:**
1. ViewModel 에서 `CategoriesRepository.observe()` + `RulesRepository.observe()` + `NotificationRepository.observeForCategory(id)` 합성.
2. Screen 은 state-driven. navigation argument 로 Category id.

### Task 9: Category editor (name + app association + rule multi-select + action dropdown)

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorViewModel.kt`

**Design:**
- 이름 입력, 앱 피커 (optional, 시스템에 등록된 앱 중 선택 또는 "특정 앱 없음"), Rule 멀티셀렉트 picker (기존 Rule 리스트에서 선택 + "새 규칙 만들기" 액션으로 `RuleEditorScreen` 으로 분기 후 복귀), action dropdown.
- 저장 전에 validation — 이름 필수, Rule 최소 1개 이상.

**Steps:**
1. 신규/편집 두 모드 (navigation argument 로 id null = 신규).
2. Rule multi-select 는 기존 `RulesScreen` 셀 렌더러 재사용.

### Task 10: Home "새 앱 분류 유도 카드" + detection

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt`
- 변경: `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeViewModel.kt`
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/DetectUncategorizedAppsUseCase.kt`
- 신규 wizard: `app/src/main/java/com/smartnoti/app/ui/screens/categories/wizard/CategoryWizardScreen.kt`

**Design:**
- `NotificationRepository` 에서 최근 N일 distinct `packageName` 집합 - Category 가 `appPackageName` 으로 커버하는 집합 = uncovered apps. `uncovered.size >= 3` 이면 Home 상단에 카드 노출.
- 카드 카피: "새 앱 {N}개가 알림을 보내고 있어요: {A}, {B}, {C}. 분류하시겠어요?" → 탭 시 wizard 진입.
- Wizard 단계: (1) 앱 선택 (pre-filled), (2) Category 이름 제안 (앱 라벨 기본), (3) action 선택 (PRIORITY/DIGEST/SILENT/IGNORE), (4) 확인 후 Category auto-create.

**Steps:**
1. UseCase 가 순수 함수 단위로 테스트 가능해야 함 — 먼저 테스트.
2. Home 카드는 기존 Home 의 "시작 가이드" 카드 자리 재활용 가능한지 검토.

### Task 11: Digest+Hidden → 정리함 통합 탭 + Rules 탭 → Settings 서브메뉴

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` — BottomNav 4-tab (Home / 정리함 / 분류 / Settings).
- 신규 또는 변경: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt` (정리함 = Digest + Hidden 상단 탭 또는 섹션 통합).
- 변경: `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` — "고급 규칙 편집" 항목 추가 → 기존 `RulesScreen` 으로 진입.

**Design:**
- 정리함 탭 안에서 Digest / Hidden 은 상단 세그먼트 또는 섹션 헤더로 분리. 건수 뱃지 유지.
- Rules 탭 자체는 BottomNav 에서 제거. Settings 에서 "고급 규칙 편집" 으로만 접근.
- Onboarding 마지막 스텝 또는 마이그레이션 직후 1회성 안내 modal — "규칙은 이제 '분류' 안에서 편집합니다. 기존 규칙은 '분류' 탭으로 자동 이동됐어요."

**Steps:**
1. BottomNav 개편 먼저 (destination 추가/제거 + start destination 유지).
2. 정리함 통합 화면은 기존 Digest/Hidden screen 을 호스팅 — 급격한 내부 재작성은 이 task 범위 밖.
3. 안내 modal 은 `SettingsRepository.categoriesMigrationAnnouncementSeen` flag 로 gate.

### Task 12: Journey doc overhaul + plan 상태 shipped 로 전환

**Files (journey 갱신):**
- `docs/journeys/rules-management.md` — Observable steps 를 Category-centric 으로 재작성, Rules 는 "고급 편집" 경로로 표기.
- `docs/journeys/notification-capture-classify.md` — Classifier 가 Category.action 을 적용하는 흐름 반영.
- `docs/journeys/notification-detail.md` — 피드백 액션이 Category 생성/편집으로 유도되는지 반영.
- `docs/journeys/rules-feedback-loop.md` — Category 기반으로 재작성 또는 deprecated.
- `docs/journeys/home-overview.md` — "새 앱 분류 유도 카드" Observable step 추가.
- `docs/journeys/digest-inbox.md` — 정리함 통합 탭 반영.
- `docs/journeys/hidden-inbox.md` — 정리함 통합 탭 반영.
- `docs/journeys/ignored-archive.md` — IGNORE 가 Category.action 기반임을 반영.
- `docs/journeys/priority-inbox.md` — PRIORITY 가 Category.action 기반임을 반영.
- `docs/journeys/README.md` — 필요 시 탭 구조 변화 반영.
- 신규 journey: `docs/journeys/categories-management.md` (필요 시).

**Files (plan frontmatter):**
- 이 파일 `status: shipped` + `superseded-by: docs/journeys/categories-management.md`.

**Steps:**
1. 각 journey 의 Change log 에 날짜/요약/commit 해시 추가.
2. README 의 `last-verified` 는 실제 verification 을 돌린 뒤에만 갱신.

---

## Scope

**In:**
- Rule 에서 action 필드 제거, Category 신규 모델 + repository + auto-migration.
- Classifier 전체 hot path 를 Category.action 기준으로 rewire.
- 분류 탭 + editor + Home 유도 카드 + wizard.
- BottomNav 4-tab 재편, Rules 탭 Settings 서브메뉴로 이동.
- IGNORE action 의 Category-level 재배선.
- 관련 journey 문서 전면 갱신.

**Out:**
- Category 간 공유/템플릿 (예: "팀 공유 분류") — 향후 plan.
- Category 자동 추천 (ML/휴리스틱) — 향후 plan.
- Rule 에디터 자체의 대규모 리디자인 (이번엔 기존 `RuleEditorScreen` 최소 변경).
- 마이그레이션 UI (진행 중 화면) — 1회성, 무음으로 처리.

---

## Risks / open questions

- **마이그레이션 crash 내구성:** 마이그레이션 중간에 앱이 죽으면 부분 Category 만 생성될 수 있음 → `cat-from-rule-<ruleId>` 포맷으로 idempotent 하게 재시도 가능하게. 테스트로 2회 실행 불변 고정.
- **Classifier hot path regression:** Phase P2 는 가장 위험. Phase P1 마무리 후 `ClassifyNotificationUseCase` 전체 케이스 테이블을 Category 기반으로 먼저 녹색 만들고, **그 다음에** old path 제거. 한 PR 에 mixed 하지 않기.
- **IGNORE 연속성:** IGNORE 플랜이 이미 shipped (8/8) 되어 기존 IGNORE Rule 이 존재. 마이그레이션에서 IGNORE Category 로 옮기고, `IgnoredArchiveScreen` 이 Category.action == IGNORE 로 필터링하도록 변경. 변경 전/후로 IGNORE 건수가 동일해야 함 — verification recipe 필수.
- **Rules 탭 제거 UX:** 사용자가 "Rules 어디감?" 되기 쉬움. Settings 서브메뉴 진입점 + 1회 안내 modal 로 완화하되, 초기 공개 후 사용자 피드백에 따라 단축 진입점(Settings 상단 고정) 재검토.
- **Specificity tie-break (open question):** 동일 specificity 의 Category 가 충돌할 때 기준 — "생성 시각 최신 우선" vs "사용자가 Category detail 에서 우선순위 수동 지정" 중 어느 쪽인가? **Task 6 시작 전에 사용자 판단 필요.** 기본은 "최근 생성 우선" 으로 가되 사용자 피드백 위에서 재검토.
- **이름 충돌:** Category 이름은 사용자 자유 입력. 동일 이름 허용/금지? 현재는 허용 (id 가 primary key).
- **"새 앱 유도 카드" 스팸 가능성:** 앱을 많이 쓰는 사용자에게 카드가 계속 노출되면 피로. N ≥ 3 + 최근 7일 내 notification 이 있는 앱 + 사용자가 "나중에" 를 선택하면 24시간 snooze — 세부 튜닝은 Task 10 에서.

---

## Related journeys

거의 전체 journey 에 영향 — 이 plan 하나의 shipped 가 동시에 다음 journey 들을 갱신해야 함:

- [rules-management](../journeys/rules-management.md) — 가장 큰 변화.
- [notification-capture-classify](../journeys/notification-capture-classify.md) — classifier 동작.
- [notification-detail](../journeys/notification-detail.md) — 피드백 액션 → Category 유도.
- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — 재작성 또는 deprecated.
- [home-overview](../journeys/home-overview.md) — 새 앱 유도 카드.
- [digest-inbox](../journeys/digest-inbox.md) — 정리함 통합 탭.
- [hidden-inbox](../journeys/hidden-inbox.md) — 정리함 통합 탭.
- [ignored-archive](../journeys/ignored-archive.md) — IGNORE 는 Category.action.
- [priority-inbox](../journeys/priority-inbox.md) — PRIORITY 는 Category.action.

이 plan 이 shipped 되면 각 journey 문서의 해당 Known gaps / Observable steps 를 갱신하고 Change log 에 본 plan 링크를 남긴다.
