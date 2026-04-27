---
status: shipped
owner: @wooilkim
created: 2026-04-27
shipped: 2026-04-27
superseded-by: ../journeys/home-uncategorized-prompt.md
---

# Home "새 앱 분류 유도 카드" — APP-Rule coverage 확장 plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 이미 어떤 앱의 알림을 분류하기 위해 Category 를 만들었다면 (예: "쿠팡 → DIGEST" Category 안에 `APP:com.coupang.mobile` Rule 이 들어 있음), Home 의 "새 앱 분류 유도 카드" 가 그 앱을 더 이상 "uncovered" 로 세지 않는다. 사용자 입장의 관측 동작은 단순하다 — 분류 탭에서 어떤 앱을 다루는 분류를 만들었다면 Home 카드는 그 앱을 다시 잔소리하지 않는다.

**Architecture:** `UncategorizedAppsDetector.detect(...)` 의 `coveredPackages` 계산이 현재는 `Category.appPackageName` 만 본다. 두 번째 source 를 추가해 `category.ruleIds` 를 따라가서 owning Category 안에 있는 `RuleTypeUi.APP` Rule 의 `matchValue` (packageName) 도 coverage 집합에 합친다. 호출자(`HomeViewModel`)가 이미 collect 중인 `RulesRepository.observeRules()` 를 detector 에 추가 인자로 흘려서 pure function 으로 유지한다. KEYWORD / PERSON / SCHEDULE / REPEAT_BUNDLE Rule 은 coverage 에 기여하지 않는다 — 이는 의도이며 Open question 으로 남긴다 (사용자가 키워드 Category 를 만들었다고 해서 특정 앱 자체가 분류된 것은 아니므로, 같은 카드 메시지로는 부정확한 안심을 줄 수 있다).

**Tech Stack:** Kotlin (pure domain), Jetpack Compose (no UI change required — wiring 만), Gradle unit tests.

---

## Product intent / assumptions

- (intent) 사용자가 어떤 앱을 분류하려고 명시적으로 그 앱을 가리키는 Rule 을 만든 경우 ("쿠팡 → DIGEST"), 그 앱은 "분류됨" 으로 친다. `appPackageName` pin 이 없어도 의도는 충분히 명확.
- (intent) KEYWORD Rule 이 들어 있는 Category 는 coverage 에 기여하지 않는다. "쿠팡" 키워드가 매치되는 앱은 여러 개일 수 있고, 사용자가 "쿠팡" 키워드 Category 를 만들었다고 해서 임의의 새 앱이 분류된 것은 아님. 이 결정은 명시적으로 plan 에 박아둔다 — 향후 사용자 피드백으로 뒤집히면 detector 에 옵션 추가.
- (assumption) `RulesRepository.observeRules()` 가 Home 화면에서 이미 hot stream 으로 collect 되고 있거나 추가 collect 비용이 무시 가능하다. (구현 시 확인 — 만약 Home 이 Rules 를 collect 하지 않는다면 별도 cold flow 추가가 필요하나, 이미 다른 카드 (예: HomePassthroughReviewCard) 가 RulesRepository 를 거치므로 비용은 작을 것으로 예상.)
- (assumption) APP-type Rule 의 `matchValue` 는 항상 packageName 형태 (`com.coupang.mobile` 등) 이며 case-sensitive 비교는 lower-case 정규화로 안전하게 처리한다. (production 에서 이미 `NotificationClassifier.kt:161` 가 `equals(rule.matchValue, ignoreCase = true)` 로 매칭하므로 같은 normalize 적용.)
- **Open question (must answer before implementation)** — Category 의 멤버십이 `ruleIds` 인데, "어느 Category 도 소유하지 않은 orphan APP Rule" 이 있을 수 있다 (rules-management 의 "미분류" 섹션 참조; `2026-04-26-rule-explicit-draft-flag` 이후 영속 draft flag 도입). 이런 orphan APP Rule 은 coverage 에 포함하지 않는 것이 옳음 — 분류기 자체가 owning Category 가 없으면 SILENT fall-through 시키므로 사용자 의도가 "이 앱을 어디로 분류한다" 가 아니다. 구현은 "Category 가 ruleIds 로 참조 중인 APP Rule 만" 으로 한정한다.

---

## Task 1: Add failing tests for APP-Rule coverage [SHIPPED via PR #429]

**Objective:** 현재 detector 가 KEYWORD-only / APP-rule-via-Category coverage 를 어떻게 다루는지 테스트로 고정.

**Files:**
- 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/UncategorizedAppsDetectorTest.kt`

**Steps:**
1. 신규 케이스 `category_with_app_rule_covers_that_package_returns_none` — Category A 가 `appPackageName=null` 이지만 `ruleIds=["app:com.example.foo"]` 이고 해당 Rule 이 `RuleTypeUi.APP, matchValue="com.example.foo"` 일 때, `com.example.foo` 알림은 coverage 에 포함되어 uncovered 카운트에서 제외되어야 함. 임계 미달 → `None`.
2. 신규 케이스 `category_with_keyword_rule_does_not_cover_that_package_returns_prompt` — Category B 가 `appPackageName=null`, `ruleIds=["kw:promo"]`, Rule 은 `RuleTypeUi.KEYWORD, matchValue="광고"` 일 때 `com.example.bar` 알림 3개는 여전히 uncovered 로 카운트되어야 함. → `Prompt`.
3. 신규 케이스 `orphan_app_rule_not_referenced_by_any_category_does_not_cover` — `RulesRepository` 에 `app:com.example.qux` 가 있지만 어느 Category 의 `ruleIds` 에도 들어 있지 않으면 coverage 에 포함되지 않음.
4. 신규 케이스 `app_rule_match_value_case_insensitive` — `matchValue="COM.EXAMPLE.FOO"` vs notification `packageName="com.example.foo"` → covered.
5. 기존 8 케이스의 시그니처 변경 보정 — `detect(...)` 가 새 인자 `rules: List<RuleUiModel>` 을 받도록 모두 빈 리스트로 호출.
6. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.UncategorizedAppsDetectorTest"` → RED 확인.

## Task 2: Extend `UncategorizedAppsDetector.detect` signature + coverage logic [SHIPPED via PR #429]

**Objective:** Task 1 의 RED 케이스를 GREEN 으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/usecase/UncategorizedAppsDetector.kt`

**Steps:**
1. `detect(...)` 의 인자 목록 끝에 `rules: List<RuleUiModel>` 추가. KDoc 에 "Only `RuleTypeUi.APP` rules referenced by some Category's `ruleIds` contribute to coverage" 명시.
2. `coveredPackages` 계산을 두 source 의 합집합으로 확장:
   - 기존: `categories.mapNotNull { it.appPackageName?.trim()?.takeIf(String::isNotEmpty) }`
   - 추가: `categories.flatMap { it.ruleIds }.toSet()` 으로 referenced rule id 집합을 만들고, `rules.filter { it.id in referenced && it.type == RuleTypeUi.APP }.map { it.matchValue.trim().lowercase() }.filter(String::isNotEmpty)`
3. Notification 비교 시 packageName 도 `trim().lowercase()` 로 정규화한 뒤 set membership 체크 (case-insensitive). 기존 `appPackageName` 비교 경로도 같은 정규화로 일관성 유지.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.UncategorizedAppsDetectorTest"` → 12 케이스 GREEN.

## Task 3: Wire `RulesRepository` snapshot through `HomeViewModel` to detector [SHIPPED via PR #TBD]

**Objective:** Production 호출 사이트가 새 인자를 채워주게.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeViewModel.kt` (또는 detector 호출 site — 구현자가 `Grep "UncategorizedAppsDetector"` 로 confirm)
- 필요 시 `app/src/main/java/com/smartnoti/app/di/...` (RulesRepository 가 이미 injected 가능한지 확인)

**Steps:**
1. `RulesRepository.observeRules()` 를 `combine(...)` 에 추가해 detector 호출에 흘려줌. 다른 Home 카드 (예: `HomePassthroughReviewCard` / `HomeQuickStartAppliedCard`) 가 이미 RulesRepository 를 사용하면 같은 stream 을 reuse.
2. detector 호출 시 `rules` 인자에 collected 값 전달.
3. 컴파일 확인. Existing UI 테스트 (있다면) 가 깨지지 않는지 확인.

## Task 4: ADB end-to-end verification on emulator-5554 [SHIPPED via PR #TBD]

**Objective:** Detector 가 분류 탭에 "쿠팡 → DIGEST" APP-Rule Category 를 만든 뒤 Home 카드가 사라지는지 사용자 관측 레벨에서 확인.

**Steps:**
```bash
# 0. baseline — Home 카드 떠 있는지 확인 (3+ uncovered 앱 필요).
#    필요 시 com.smartnoti.testnotifier + 다른 테스트 앱으로 알림 게시.

# 1. Categories 탭 → FAB → 새 Category "쇼핑" 만들고
#    "연결할 앱" picker 는 비워두되, 소속 규칙 multi-select 에서
#    APP:com.coupang.mobile (또는 testnotifier 의 packageName) Rule 을
#    pre-create + 선택 → 저장.
#    (또는 debug-inject 로 com.coupang.mobile 알림 게시 후
#     Detail "분류 변경" → 새 분류 만들기 경로로도 가능. 어느 경로든
#     완성 상태가 "Category 의 ruleIds 에 APP:com.coupang.mobile Rule 이
#     포함됨, appPackageName 은 null" 이 되도록 만든다.)

# 2. 분류 탭에서 새 Category 의 detail 진입 → ruleIds 에 APP-Rule
#    1건이 들어 있고 appPackageName 칩이 비어 있는 상태인지 확인.

# 3. Home 탭으로 복귀.
#    - 기존 동작: "새 앱 N개" 카드가 com.coupang.mobile 을 sample 로
#      여전히 노출하고 N >= 3 면 prompt 유지.
#    - 신규 동작: com.coupang.mobile 이 coverage 에 포함되어 N 이 1 감소.
#      N < 3 이면 카드 unmount, N >= 3 이면 sample 라벨에서 해당 앱 제외.

# 4. 회귀 — KEYWORD-only Category (예: "특가" 키워드) 만 만든 케이스에서는
#    카드가 그대로 유지되는지 별도 확인 (Open question 결정 검증).
```

기록은 `docs/journeys/home-uncategorized-prompt.md` Verification log 에 추가. `last-verified` bump 는 verification recipe 전체를 다시 실행했을 때만.

## Task 5: Update journey doc [SHIPPED via PR #TBD]

**Objective:** Known gap 해소를 journey 에 반영.

**Files:**
- `docs/journeys/home-uncategorized-prompt.md`

**Steps:**
1. Known gaps 섹션의 `UncategorizedAppsDetector 는 Category.appPackageName 으로만 커버 판정 …` bullet 을 `(resolved YYYY-MM-DD, plan ...)` 로 마킹하고 잔여 결정 (KEYWORD 미커버) 을 동일 bullet 의 후행 문장으로 보존.
2. Observable steps 2 의 `coveredPackages = categories.mapNotNull { it.appPackageName }` 줄을 두 source 합집합으로 정정 (`+ APP-type Rule.matchValue from referenced ruleIds`).
3. Code pointers 에 `domain/usecase/UncategorizedAppsDetector#detect(rules)` 추가 인자 명시.
4. Tests 섹션의 케이스 카운트 업데이트 (8 → 12).
5. Change log 에 ship 항목 추가.
6. Out of scope 의 "Category.appPackageName 이 설정돼야만 커버됨. 키워드/사람 기반 Category 만 만들면 여전히 uncovered 로 판단" 줄을 KEYWORD 한정으로 다시 적음 (APP-Rule 은 covered).

---

## Scope

**In scope:**
- `UncategorizedAppsDetector` signature + coverage 로직 확장.
- `HomeViewModel` (또는 동등한 호출 site) wiring.
- 단위 테스트 4건 추가, 기존 8건 시그니처 보정.
- ADB smoke verification.
- Journey doc 갱신.

**Out of scope:**
- KEYWORD / PERSON / SCHEDULE / REPEAT_BUNDLE Rule 을 coverage 에 포함하는 결정 (Open question 으로 별도 plan 후보).
- Card copy 변경 (현재 카피 그대로 유지 — uncovered 카운트만 정확해지면 사용자 체감은 자동으로 좋아짐).
- Detector 에서 IGNORE Category action 을 다르게 다루기 (현재처럼 packageName 만 보면 됨).
- 새 Category 생성 wizard / multi-step UI.

## Risks / open questions

- **R1 — coverage 정의 확장 범위.** 현재 plan 은 "owning Category 가 있는 APP Rule" 만 covered. 사용자 시나리오에 따라 PERSON Rule (특정 사람을 분류) 도 "그 사람이 보내는 앱" 을 covered 로 봐야 한다는 주장이 가능. PERSON Rule 의 매칭 대상은 `notification.sender` 이지 packageName 이 아니므로 1:1 매핑이 안 됨 → 별도 plan 으로 분리. 본 plan 에서는 결정 없음.
- **R2 — orphan APP Rule.** rules-management 의 미분류 ("작업 필요" / "보류") 섹션에 살아 있는 APP Rule 은 coverage 에 포함하지 않는다. 이는 plan 의 명시적 결정이며 Task 1.3 케이스로 고정. 사용자 피드백으로 뒤집힐 가능성 있음 — 그 경우 detector 에 `includeOrphanAppRules: Boolean = false` 옵션 추가 후속 plan.
- **R3 — 한 앱이 여러 Category 에 동시 멤버십.** 같은 packageName 이 여러 Category 의 APP Rule 로 등장하면 coverage 는 한 번만 카운트 (Set 합집합). Bug 아님 — 의도된 dedup.
- **Q1 — RulesRepository observe stream 비용.** `HomeViewModel` 이 `RulesRepository.observeRules()` 를 이미 collect 중인지 구현 시 확인 필요. 만약 추가 collect 가 필요하면 Home composition 에 부하 증가 — 무시 가능 수준일 것으로 추정하나 계측 후 결정.
- **Q2 — Verification 환경.** `cmd notification post` 가 단일 `com.android.shell` 패키지로 묶이는 한계 때문에 detector 의 multi-app coverage 검증은 emulator 의 `com.smartnoti.testnotifier` (또는 두 번째 테스트 앱) 가 필요. journey-tester rotation 에서 이미 사용 중인 패턴.

## Related journey

- [home-uncategorized-prompt](../journeys/home-uncategorized-prompt.md) — Known gap "`UncategorizedAppsDetector` 는 `Category.appPackageName` 으로만 커버 판정" 이 본 plan 으로 해소된다. Plan ship 시 해당 bullet 을 Change log 항목으로 옮긴다.
- [categories-management](../journeys/categories-management.md) — APP-type Rule 이 어떻게 Category 에 멤버십을 갖는지의 contract 가 본 plan 의 전제 (`category.ruleIds` 참조).
- [rules-management](../journeys/rules-management.md) — orphan APP Rule (draft / 보류) 정의가 본 plan 의 R2 결정과 직결.
