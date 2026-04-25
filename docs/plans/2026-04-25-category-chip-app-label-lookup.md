---
status: planned
---

# Category Chip APP-Token Label Lookup Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** APP 타입 Rule 이 소속된 Category 카드/Detail/Editor 의 `CategoryConditionChips` 가 더 이상 `앱=com.kakao.talk` 같은 raw packageName 을 노출하지 않고 `앱=카카오톡` 처럼 사용자 친화 라벨로 보이게 한다. 이름 해석에 실패한 경우 (앱 미설치 등) 만 packageName 으로 graceful fallback. 본 PR 은 chip 표면 한 곳 (formatter 입력) 에만 lookup 을 주입해 다른 surface (카드 metadata line, Detail "연결된 앱 · ${packageName}") 에는 영향을 주지 않는다 — 동일한 lookup 은 후속 PR 에서 더 넓게 재사용할 수 있도록 reusable 하게 둔다.

**Architecture:**
- 신규 `AppLabelLookup` 단일 메서드 인터페이스 (`fun labelFor(packageName: String): String?`) — `null` 반환 = 라벨 미상 (PackageManager `NameNotFoundException` 등). 순수 인터페이스라 unit-test 용 in-memory fake / production `PackageManagerAppLabelLookup` 두 구현으로 분리.
- `CategoryConditionChipFormatter.format(...)` 시그니처에 `appLabelLookup: AppLabelLookup = AppLabelLookup.Identity` 옵션 파라미터를 추가. Default 는 기존 동작 그대로 (raw matchValue). APP 타입 Rule 만 lookup 을 거치고 결과가 `null/blank` 이면 raw matchValue 로 fallback.
- Production wiring 은 Compose `LocalContext.current` 에서 `PackageManagerAppLabelLookup(context.packageManager)` 인스턴스를 만들고, 두 `CategoryConditionChips` 오버로드 (text 버전 / rules 버전) 가 모두 lookup 을 받아 formatter 로 forward. 호출 사이트 (Categories/Detail/Editor 화면) 는 단순히 `LocalAppLabelLookup.current` (CompositionLocal) 만 참조하거나, 각 호출 사이트에서 `rememberAppLabelLookup()` 으로 가져온다. 둘 중 어느 패턴이 더 간결한지 implementer 가 task 3 에서 결정.

**Tech Stack:** Kotlin, Jetpack Compose, Android `PackageManager`, JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `CategoryConditionChips` 의 APP 토큰을 사용자 친화 라벨로 바꾸는 것은 plan `2026-04-24-categories-condition-chips.md` Risks/open question 항목에서 후속 plan 으로 분리됨. 본 PR 이 그 후속.
- **결정 필요**: lookup 결과가 빈 문자열일 때 (제조사 / 시스템 앱 일부) → 본 PR 은 packageName 으로 fallback. "라벨 = packageName 이면 차라리 packageName 만 보이는 게 덜 헷갈린다" 는 가정. 다른 휴리스틱 (예: 마지막 segment 만 표시) 은 out-of-scope.
- **결정 필요**: Lookup 미설치 앱 / 비활성 앱 처리 → `NameNotFoundException` catch 후 `null` 반환 → raw packageName 사용. 사용자가 룰 만들 당시엔 설치돼 있었지만 그 사이 삭제한 케이스 — 본 PR 은 cosmetic 만 처리, GC 는 별도 과제.
- **결정 필요**: REPEAT_BUNDLE 토큰 (formatter 의 `반복묶음`) 은 packageName 무관 — lookup 영향 없음 확인.
- **결정 필요**: APP-token 외에 PERSON / KEYWORD / SCHEDULE / REPEAT_BUNDLE 토큰은 lookup 적용 대상 아님. Only RuleTypeUi.APP gets lookup.
- 본 plan 은 Compose 의존성을 formatter 에 주입하지 않는다 — `AppLabelLookup` 은 순수 Kotlin SAM. Production 인스턴스만 PackageManager 에 의존. Unit test 는 in-memory map 으로 검증.
- 카피/접근성: `toPlainString()` (semantics contentDescription) 도 자연히 라벨로 바뀐다 (formatter 의 token 자체가 바뀌므로). 별도 카피 변경 없음.

---

## Task 1: Add failing tests for `AppLabelLookup` injection in formatter

**Objective:** APP 타입 토큰의 라벨 해석 규칙 (라벨 있음 → 라벨 사용, 라벨 없음/blank → raw fallback, 비-APP 타입 → lookup 영향 없음) 을 unit test 로 고정한 뒤 구현.

**Files:**
- `app/src/test/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChipFormatterTest.kt` (보강)
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/categories/components/AppLabelLookupTest.kt` (선택 — fake 동작 검증용. 실제 가치는 formatter 테스트가 모두 흡수하므로 옵션)

**Steps:**
1. 기존 formatter test 옆에 다음 시나리오 추가:
   - APP rule (`matchValue = "com.kakao.talk"`) + lookup 이 `"카카오톡"` 반환 → 토큰 `앱=카카오톡` (라벨 사용).
   - APP rule + lookup 이 `null` → 토큰 `앱=com.kakao.talk` (raw fallback).
   - APP rule + lookup 이 `""` (blank) → 토큰 `앱=com.kakao.talk` (raw fallback — blank 은 미상으로 취급).
   - APP rule + matchValue 가 blank → 토큰 `앱` (lookup 호출조차 안 함). 기존 빈 matchValue 동작 유지.
   - PERSON rule (`matchValue = "엄마"`) + lookup 이 임의 매핑 보유 → 토큰 `보낸이=엄마` (lookup 무시).
   - KEYWORD / SCHEDULE / REPEAT_BUNDLE rule + lookup 보유 → 기존 토큰과 동일 (lookup 무시).
   - 혼합: APP + PERSON + KEYWORD 3개 rule, lookup 이 APP packageName 만 매핑 → APP 토큰만 라벨로 바뀌고 나머지는 그대로.
2. lookup 미주입 (default `Identity`) 호출 → 모든 기존 테스트 그대로 GREEN 이어야 함 (backward-compat).
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.components.CategoryConditionChipFormatterTest"` → 신규 케이스 RED 확인.

## Task 2: Implement `AppLabelLookup` interface + formatter wiring

**Objective:** Task 1 의 테스트가 GREEN 이 되게.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/AppLabelLookup.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChipFormatter.kt`

**Steps:**
1. `AppLabelLookup.kt`:
   - `fun interface AppLabelLookup { fun labelFor(packageName: String): String? }` SAM.
   - `companion object { val Identity = AppLabelLookup { null } }` — default = "라벨 미상" 으로 항상 fallback.
   - 동일 파일에 production 구현 `class PackageManagerAppLabelLookup(private val pm: PackageManager) : AppLabelLookup` 도 함께 (또는 production-only `.android.kt` 로 분리. implementer 판단). `labelFor` 안에서 `pm.getApplicationInfo(packageName, 0)` → `pm.getApplicationLabel(...).toString()`. `NameNotFoundException` catch 후 `null` 반환. `listener` 의 199 라인 패턴 (`SmartNotiNotificationListenerService.kt:197-202`) 을 그대로 참고.
2. `CategoryConditionChipFormatter.format(...)` 에 `appLabelLookup: AppLabelLookup = AppLabelLookup.Identity` 추가. `tokenFor(rule)` 헬퍼가 lookup 도 받게 시그니처 변경. APP 분기에서:
   ```
   val resolved = appLabelLookup.labelFor(value)?.takeIf { it.isNotBlank() } ?: value
   ```
   → `"앱=$resolved"`.
3. value 가 blank 이면 lookup 호출 없이 기존처럼 `"앱"` 만 반환 — 빈 matchValue 는 이미 형식 어긋난 케이스이므로 그대로.
4. `./gradlew :app:testDebugUnitTest` 전체 GREEN 확인.

## Task 3: Wire `AppLabelLookup` into the three Categories screens

**Objective:** 카드 / Detail / Editor 가 production lookup 을 formatter 에 전달하도록 한다. 다른 surface (카드 metadata line, Detail "연결된 앱 · ${packageName}") 는 본 PR scope 밖이므로 손대지 않는다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChips.kt` (두 오버로드)
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryDetailScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt`
- 선택: 신규 `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/LocalAppLabelLookup.kt` (CompositionLocal 패턴을 채택할 경우)

**Steps:**
1. `CategoryConditionChips` 의 두 오버로드에 `appLabelLookup: AppLabelLookup = AppLabelLookup.Identity` 옵션 파라미터 추가.
   - `rules` 오버로드: formatter 호출에 lookup 그대로 forward.
   - `text` 오버로드: 이미 토큰이 만들어진 상태이므로 lookup 무관 — 시그니처에 추가하되 사용 안 함 (호출 사이트의 일관성을 위해).
2. 호출 사이트 wiring 옵션 두 가지 중 하나를 implementer 가 선택:
   - **옵션 A (CompositionLocal)**: `LocalAppLabelLookup` CompositionLocal 정의 + `MainActivity` (혹은 `AppNavHost`) 에서 `CompositionLocalProvider(LocalAppLabelLookup provides PackageManagerAppLabelLookup(LocalContext.current.packageManager.applicationContext as PackageManager))` provider 한 번 설치. 화면들은 `val lookup = LocalAppLabelLookup.current` 만 호출.
   - **옵션 B (직접 호출)**: 각 화면에서 `val pm = LocalContext.current.packageManager; val lookup = remember(pm) { PackageManagerAppLabelLookup(pm) }` 로 만들고 chip 호출에 forward. 코드 중복은 약간 있으나 CompositionLocal 도입 비용 없음.
   PR 본문에 어느 쪽을 골랐고 왜인지 한 줄 명시.
3. `CategoriesScreen` row, `CategoryDetailScreen` 요약, `CategoryEditorScreen` 미리보기 — 세 곳의 `CategoryConditionChips(...)` 호출에 lookup 인자 추가.
4. ADB 시각 검증:
   - 사전 준비: APP 타입 Rule 1개 (`matchValue = com.kakao.talk` 또는 다른 설치된 packageName) 가 소속된 Category 가 있어야 함. 없다면 분류 탭 → 새 분류 → APP rule 신규 생성 후 검증.
   - 카드 → 토큰이 `앱=카카오톡` (혹은 해당 라벨) 로 보이는지 확인. uiautomator dump 의 content-desc 도 라벨 포함인지 확인.
   - Detail → 동일.
   - Editor → APP rule 토글 시 미리보기에 라벨로 즉시 반영.
5. `./gradlew :app:assembleDebug` + 단위 테스트 그린.

## Task 4: Update `categories-management` journey

**Objective:** Observable steps + Code pointers + Known gaps + Change log 동기화.

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. Observable step 3 에 chip 토큰 부분에 "APP 타입 토큰은 사용자 친화 라벨 (예: `앱=카카오톡`) 로 보이고, 라벨 해석에 실패하면 `앱=<packageName>` 으로 fallback." 한 문장 추가. 다른 토큰 형식 변경 없음.
2. Known gaps 의 "`CategoryConditionChips` 의 APP 토큰은 현재 `Rule.matchValue` (raw `com.kakao.talk` 같은 packageName) 를 그대로 노출 — `appLabelLookup` 주입으로 사용자 친화 라벨로 바꾸는 후속 plan 필요" bullet 을 **(resolved 2026-04-25, plan `2026-04-25-category-chip-app-label-lookup`)** prefix 로 마킹 — 본 plan 의 docs-sync 규칙 (Known gap 본문은 그대로, plan 링크만 annotate) 을 따른다.
3. Code pointers 에 `AppLabelLookup`, `PackageManagerAppLabelLookup` (혹은 옵션 A 채택 시 `LocalAppLabelLookup`) 추가.
4. Tests 에 `CategoryConditionChipFormatterTest` 의 신규 케이스 항목 추가.
5. Change log 에 본 PR 항목 append (날짜 2026-04-25 또는 implementer 가 작업한 실제 UTC 날짜, 요약, plan link, PR link merge 시 채움).
6. `last-verified` 는 **갱신하지 않음** — implementer 가 직접 ADB recipe 를 처음부터 끝까지 돌리지 않았다면 그대로 둔다.

## Task 5: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 4가지 명시:
  1. 채택한 wiring 옵션 (A=CompositionLocal / B=직접 호출) 과 이유.
  2. ADB 검증 시 사용한 APP rule (packageName + 화면에 노출된 라벨).
  3. 라벨 해석 실패 fallback 동작 (가능하면 미설치 packageName 으로 만든 rule 의 chip screenshot 1장).
  4. 미적용 surface (카드 metadata line, Detail "연결된 앱 · ${packageName}") — 본 PR scope 외 + 후속 plan 의 잠재 작업으로 표기.
- PR 제목 후보: `feat(categories): resolve APP-token packageNames to user-facing app labels`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/categories-management.md` 로 flip — 단, plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- 신규 `AppLabelLookup` SAM + production `PackageManagerAppLabelLookup`.
- `CategoryConditionChipFormatter.format` 에 lookup 옵션 추가 + APP 토큰 분기에 적용.
- `CategoryConditionChips` 두 오버로드에 lookup 옵션 추가.
- 세 화면 (`CategoriesScreen`, `CategoryDetailScreen`, `CategoryEditorScreen`) 에서 production lookup wiring.
- Journey 문서 갱신 (Observable / Code pointers / Known gaps annotate / Tests / Change log).

**Out:**
- `CategoriesScreen` row 의 metadata line `append(category.appPackageName)` (line ~300) 와 `CategoryDetailScreen` 의 `"연결된 앱 · ${category.appPackageName}"` (line ~108) — 본 PR 은 chip 한 surface 만 변경. 다른 surface 도 같은 패턴으로 reuse 하는 것은 후속 plan.
- `Category.appPackageName` (Rule.matchValue 가 아닌 Category 본체 필드) 의 라벨화 — 위와 동일 이유로 out.
- 라벨 캐싱 / 비동기 로딩 — `PackageManager.getApplicationLabel` 은 동기 호출이고 비교적 빠름. 성능 이슈가 관찰되면 후속.
- 다국어 / locale 별 라벨 전환 처리 — `PackageManager` 가 시스템 locale 따라 자동 반환.
- 라벨 해석 결과 = packageName 자체인 경우의 표시 정책 변경 (현재는 그대로 packageName 노출).

---

## Risks / open questions

- **CompositionLocal vs 직접 호출 — implementer 판단 가능 범위인가?** 두 옵션 모두 형식적으로 동등하지만, 향후 다른 surface (out-of-scope 로 둔 카드 metadata line 등) 에 재사용할 거라면 CompositionLocal 이 마찰을 줄임. 사용자가 명시 결정을 원하지 않으면 implementer 가 PR 본문에 결정 이유 한 줄 명시하는 것으로 충분. 사용자가 사전 확정 원할 시 별도 sign-off 필요.
- **라벨 해석 결과 = packageName 인 케이스**: 일부 시스템 앱 (예: `com.android.shell`) 은 라벨이 packageName 과 사실상 같다. 현재 plan 은 lookup 결과를 그대로 노출 → 사용자 입장에서 "라벨화 안 된 것처럼 보일" 수 있음. 사용자가 이 경우 raw fallback 처리를 선호하면 별도 휴리스틱 (`if label == packageName → null`) 추가 필요. open question.
- **APP rule 의 matchValue 가 packageName 이 아닌 임의 문자열인 경우**: `RuleEditorScreen` 가 APP 타입에 사용자 자유 입력을 허용하는지 확인 필요. 자유 입력이라면 lookup 이 항상 `null` 반환 → raw fallback 으로 graceful 동작. 별도 코드 변경 불필요.
- **PackageManager 호출 비용**: 매 chip 렌더마다 `getApplicationInfo` 호출. 카테고리 한 화면에 APP rule 이 N 개라면 N 번 호출. 보통 N≤5 수준이라 미체감이지만, 만약 Editor 의 미리보기가 매 키 입력마다 recompose 된다면 불필요한 호출이 누적될 수 있음 — `remember(packageName) { lookup.labelFor(packageName) }` 같은 micro-cache 가 필요할 수 있음. implementer 가 시각 검증 단계에서 lag 관측 시 보강.
- **Lookup 누락 surface 의 사용자 혼란**: 같은 화면 안에서 chip 은 `앱=카카오톡` 인데 metadata line 또는 Detail 의 "연결된 앱 ·" 옆은 `com.kakao.talk` 로 남는 inconsistent 상태가 일시적으로 생긴다. PR 본문에 그 점을 명시하고, 후속 plan 으로 동일 lookup 을 카드/Detail 의 다른 surface 에도 wire 하는 작업을 큐잉하도록 권고.

---

## Related journey

- [categories-management](../journeys/categories-management.md) — Known gaps 마지막 bullet ("`CategoryConditionChips` 의 APP 토큰은 현재 `Rule.matchValue` (raw `com.kakao.talk` 같은 packageName) 를 그대로 노출 — `appLabelLookup` 주입으로 사용자 친화 라벨로 바꾸는 후속 plan 필요") 을 해소. 본 PR ship 후 해당 bullet 을 (resolved …) 로 마킹하고 Change log 에 PR 링크 추가.
