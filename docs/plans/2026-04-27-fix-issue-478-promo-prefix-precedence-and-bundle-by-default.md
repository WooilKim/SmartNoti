---
status: planned
fixes: 478
supersedes: docs/plans/2026-04-27-fix-issue-478-promo-keyword-not-routing.md, docs/plans/2026-04-27-fix-issue-478-promo-extended-content-fields.md
---

# Fix #478 (3차 진단): "(광고)" prefix precedence + PROMO_QUIETING bundle-by-default

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P0 release-blocker — 5-step gate (failing tests → impl → regression → ADB e2e → journey bump) is non-negotiable. ADB e2e MUST run on the user's real device `R3CY2058DLJ` (Galaxy S24), not emulator — emulator could not reproduce because the conflict requires the real apps' (광고) bodies.

**Goal:** Onboarding 의 PROMO_QUIETING preset 을 선택한 사용자에게 한국 KCC-mandated `(광고)` prefix 로 시작하는 외부 알림이 들어오면, SmartNoti 가 항상 PROMO Category 로 분류 (IMPORTANT 키워드와 본문이 겹쳐도) 하고, 그 결과 원본 tray entry 가 즉시 cancel + DIGEST 로 번들된다. AliExpress / 멤버십데이 / 하나카드 광고가 priority 트레이를 침범하던 사용자 신고 (#478) 의 회귀를 끝낸다.

**Architecture:** PR #486 의 real-device DB 분석 (issuecomment-4327574021) 으로 root cause 두 개가 식별됨:

- **Bug A (precedence):** `(광고) ... 무료배송`, `(광고) ... 결제`, `(광고) ... 장기카드대출` 같은 본문은 PROMO 키워드와 IMPORTANT 키워드가 둘 다 hit. 현재 `CategoryConflictResolver` 는 `sortOrder` 기준으로 IMPORTANT(0) > PROMO(1) 라 PRIORITY 로 라우팅. KCC 가 강제하는 `(광고)` prefix 는 본문이 광고임을 unambiguous 하게 선언하므로, 이 prefix 가 detect 되면 resolver 의 ordering 을 무시하고 PROMO 가 winner.
- **Bug B (bundle-by-default):** 정상 PROMO 분류 (catchtable / ssfshop / coupang / navershopping / starbucks 등) 도 `sourceSuppressionState=NOT_CONFIGURED` 인 채로 SILENT 처리되어 원본이 tray 에 잔존. `SuppressedSourceAppsAutoExpansionPolicy.expandIfNeeded` 가 DIGEST 만 자동 확장하기 때문. 사용자는 B2 를 선택 — `OnboardingQuickStartCategorySeeder.PROMO_QUIETING` 의 default `categoryAction` 을 SILENT → DIGEST 로 바꿔서 자동 확장이 fire 하고 원본이 cancel 되며 정리함에 번들됨. preset 이름 "프로모션 알림" 의 사용자 의도 (조용히/번들) 와 align.

**Fix 위치 두 곳:**

1. `app/src/main/java/com/smartnoti/app/notification/NotificationClassifier.kt` (또는 `CategoryConflictResolver.kt`) — `(광고)` prefix detector 를 cascade 의 pre-resolver 단계에 추가. 매치 시 PROMO Category 가 winner 인 짧은 경로로 분기.
2. `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartCategorySeeder.kt` (또는 동등 seeder) — PROMO_QUIETING 의 `categoryAction` default 를 `SILENT` → `DIGEST`. 기존 사용자에게는 `SettingsRepository.applyPendingMigrations()` 의 v3 one-shot 마이그레이션 (DataStore 키 `promo_quieting_action_migration_v3_applied`) 으로 자동 bump — 단, `Category.userModifiedAction` 이 false 인 경우만. 새 필드 `userModifiedAction: Boolean` (default false) 를 Category 에 추가하고, UI 의 모든 action edit 진입점 (Categories 화면 / NotificationDetail 의 action 변경 / Rules 편집기 등) 에서 true 로 flip.

**Tech Stack:** Kotlin, Room (Category schema 변경 → migration), Jetpack Compose, DataStore (migration flag), Gradle unit tests, ADB on real device `R3CY2058DLJ`. DiagnosticLogger (#480 just-shipped) 를 e2e 검증의 1차 evidence 로 사용.

---

## PR title format: `fix(#478): <one-line>`
## PR body must include `Closes #478` + 사용자의 product judgment 인용 (B2 선택 / B1·B3 reject)

---

## Product intent / assumptions

- **B2 chosen by user (`B2 로 진행해줘`, 2026-04-27)**: PROMO_QUIETING preset 의 default action 을 DIGEST 로 변경. SmartNoti 가 광고를 사용자의 정리함 (digest inbox) 에 번들해 일괄 검토하게 한다. 원본 tray 는 비워진다.
- **B1 rejected (sweep risk)**: SILENT 도 자동 확장하면 사용자가 quiet-keep 하려던 같은 앱의 비-광고 SILENT 알림까지 tray 에서 쓸려나갈 우려. PROMO 의 ad-only 의도와 SILENT 의 quiet-keep 의도가 충돌.
- **B3 rejected (UX overhead)**: 첫 광고 알림에 prompt 띄우는 안. onboarding 에서 이미 preset 을 선택한 사용자에게 또다시 묻는 건 redundant.
- `(광고)` prefix 는 KCC 의 정보통신망법 제50조의8 시행령상 광고 표시 의무 — `[광고]` / `(AD)` / `[AD]` 도 동등 marker 로 취급. 본문 시작 (`^\s*`) 에 anchor 해 ad copy 안에 자연어로 "광고" 가 등장하는 케이스 (e.g. "광고주 미팅 알림") 와 분리.
- `userModifiedAction` 새 필드는 신규 install 부터 정확히 추적 가능하지만 기존 install 은 historical record 가 없음 → 기존 install 에서 PROMO Category 의 `userModifiedAction` 은 일단 false 로 가정하고 v3 마이그레이션이 SILENT → DIGEST bump 를 수행. **사용자 확인 필요 (Risks 참고).**
- Bug A 와 Bug B 는 둘 다 닫혀야 사용자 신고가 해결. 분리 PR 도 가능하지만 같은 issue 의 distinct path 라 single PR 권장 — Task 결과로 scope 비대화 우려가 보이면 plan-implementer 가 보고.
- Fix 후 [notification-capture-classify](../journeys/notification-capture-classify.md) 와 [digest-suppression](../journeys/digest-suppression.md) 두 journey 의 `last-verified` + Change log bump.

---

## Task 1: Failing tests for both bugs (RED first) [IN PROGRESS via PR #489]

**Objective:** Bug A 의 precedence 회귀와 Bug B 의 seeder/migration 동작을 distinct test 로 격리. 모든 test 는 RED 로 시작.

**Files (신규 또는 보강):**

- `app/src/test/java/com/smartnoti/app/notification/KoreanAdvertisingPrefixDetectorTest.kt` (신규) — prefix regex 단위 테스트.
- `app/src/test/java/com/smartnoti/app/notification/CategoryConflictResolverPromoPrecedenceTest.kt` (신규 또는 기존 `CategoryConflictResolverTest` 확장) — `(광고)` prefix detect 시 PROMO 가 IMPORTANT 를 이기는 분기.
- `app/src/test/java/com/smartnoti/app/notification/PromoPrefixRoutingFixturesTest.kt` (신규) — 3개 real fixture 재현:
  1. AliExpress (gmail) — `title="배송 출발 알림", body="(광고) 무료배송 진행 중!"` → DIGEST.
  2. NaverShopping 멤버십데이 — `title="멤버십데이", body="(광고) 무료배송 + 5% 적립"` → DIGEST.
  3. SamsungMessaging 하나카드 — `title="[하나카드]", body="(광고) 장기카드대출 안내"` → DIGEST.
- `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartCategorySeederPromoActionTest.kt` (신규 또는 기존 보강) — PROMO_QUIETING preset seeding 결과 owning Category 의 `categoryAction == DIGEST`.
- `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryPromoActionMigrationTest.kt` (신규) — `applyPendingMigrations` v3:
  - 기존 PROMO Category(`action=SILENT, userModifiedAction=false`) → `action=DIGEST` + flag set.
  - `userModifiedAction=true` 인 경우 → 변경 없음.
  - flag 가 이미 set 인 경우 → 두 번째 호출은 noop.
  - PROMO 가 아닌 다른 Category 는 영향 없음.
- `app/src/test/java/com/smartnoti/app/notification/NotificationDecisionPipelineCharacterizationTest.kt` (보강) — `(광고)` prefix + PROMO Category(action=DIGEST) + `suppressSourceForDigestAndSilent=true` 입력에서 `SourceTrayActions.cancelSource` 가 호출되고 decision=DIGEST.

**Steps:**

1. `KoreanAdvertisingPrefixDetector` (가칭) 의 contract — 본문 시작 anchor `^\s*` + 4개 marker (`(광고)`, `[광고]`, `(AD)`, `[AD]`, case-insensitive on AD), max input length 32 chars 만 봐서 mid-body 매치 방지. Configurable: detector 가 regex pattern list 를 받는 ctor, default = KCC patterns. 향후 i18n 확장점.
2. CategoryConflictResolver 보강 — resolver entry 에 새 input `hasAdvertisingPrefix: Boolean` 추가, true 면 PROMO Category (rule type / sortOrder 무관) 가 winner. 다중 PROMO Category 매치 시는 기존 sortOrder/order tie-break 적용.
3. 3개 real fixture 의 정확한 title/body 는 PR #486 의 issuecomment-4327574021 분석 표 그대로 사용.
4. `Category` 모델에 `userModifiedAction: Boolean = false` 필드 추가 (Room schema migration v9 → v10 — 새 column nullable=false default=0).
5. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.KoreanAdvertisingPrefixDetectorTest" --tests "com.smartnoti.app.notification.CategoryConflictResolverPromoPrecedenceTest" --tests "com.smartnoti.app.notification.PromoPrefixRoutingFixturesTest" --tests "com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartCategorySeederPromoActionTest" --tests "com.smartnoti.app.data.settings.SettingsRepositoryPromoActionMigrationTest" --tests "com.smartnoti.app.notification.NotificationDecisionPipelineCharacterizationTest"` 로 RED 확인. RED test 이름을 commit message + PR body 에 인용.

## Task 2: Bug A fix — `(광고)` prefix detector + resolver precedence override

**Objective:** Task 1 의 KoreanAdvertisingPrefixDetectorTest / CategoryConflictResolverPromoPrecedenceTest / PromoPrefixRoutingFixturesTest 가 GREEN.

**Files:**

- `app/src/main/java/com/smartnoti/app/notification/KoreanAdvertisingPrefixDetector.kt` (신규).
- `app/src/main/java/com/smartnoti/app/notification/CategoryConflictResolver.kt` (수정).
- `app/src/main/java/com/smartnoti/app/notification/NotificationClassifier.kt` (수정 — detector 호출, resolver 입력에 prefix flag 전달).
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt` 또는 새 파일 — `advertisingPrefixPatterns: List<String>` setting (default = KCC, max 8 patterns, max 32 chars each).

**Steps:**

1. Detector 구현 — body 의 leading 32 chars 를 보고 trim/normalize 후 4개 marker 중 어느 것으로 시작하는지 boolean 반환. mid-body 매치 안 함 (regex anchor `^`).
2. Classifier cascade 안에 detector 결과를 resolver entry 로 전달.
3. Resolver: `hasAdvertisingPrefix=true` 입력에서 PROMO Category 매치를 우선시 (PROMO 가 매치 안 했으면 normal cascade). PROMO 가 여러 개면 기존 sortOrder/order tie-break.
4. Settings 노출은 일단 코드 default 만 — UI 노출은 out-of-scope (i18n 확장은 future plan).
5. `(광고)` 가 본문이 아니라 title 에만 있는 케이스도 detector 가 본문 fallback 으로 title 을 검사 (title 에 prefix 박는 앱도 있음). PR #486 의 fixture 표를 보고 결정.

## Task 3: Bug B2 fix — PROMO_QUIETING seeder action change + migration v3

**Objective:** Task 1 의 OnboardingQuickStartCategorySeederPromoActionTest / SettingsRepositoryPromoActionMigrationTest 가 GREEN. 신규 install 의 PROMO_QUIETING owning Category = DIGEST. 기존 install 도 (수동 변경 안 했으면) DIGEST 로 자동 bump.

**Files:**

- `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartCategorySeeder.kt` (또는 동등 — 코드 탐색으로 식별).
- `app/src/main/java/com/smartnoti/app/data/Category.kt` (또는 동등) — `userModifiedAction: Boolean = false` 필드 추가.
- `app/src/main/java/com/smartnoti/app/data/local/CategoryDao.kt` — Room migration v9 → v10 (column add).
- `app/src/main/java/com/smartnoti/app/data/local/AppDatabase.kt` — `Migration(9, 10)` 추가, `version = 10`.
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — `applyPendingMigrations()` 에 v3 분기 추가.
- DataStore key `promo_quieting_action_migration_v3_applied`.
- UI 의 action edit 진입점 (`CategoriesScreen`, `NotificationDetailScreen` 의 action picker, Rules 편집기의 1:1 Category 자동생성 경로 등) — 모든 action 변경에 `userModifiedAction = true` flip. 코드 탐색으로 진입점 enumerate.

**Steps:**

1. Seeder 의 `PROMO_QUIETING` enum 의 `categoryAction` literal 변경 SILENT → DIGEST. 다른 preset 의 action 은 변경하지 않는다.
2. Category 모델 + Room schema 추가. Migration v9→v10 은 `ALTER TABLE categories ADD COLUMN userModifiedAction INTEGER NOT NULL DEFAULT 0`.
3. `applyPendingMigrations()` v3: flag 안 set → PROMO Category 들 (preset id 또는 marker 로 식별) 중 `userModifiedAction=false AND action=SILENT` 인 row 만 `action=DIGEST` 로 update + flag set.
4. UI 진입점 audit — 모든 action mutator 경로에서 `userModifiedAction=true` 같이 set. `CategoryRepository.updateAction` 같은 단일 helper 로 묶을 수 있으면 더 좋음 (refactor scope 는 최소).

## Task 4: Regression guards

**Objective:** 기존 IMPORTANT classification 이 회귀 없이 유지.

**Files:**

- 기존 `NotificationClassifierTest`, `CategoryConflictResolverTest`, `OnboardingQuickStartRuleApplierTest`, `NotificationDecisionPipelineCharacterizationTest` 등.

**Steps:**

1. **Real "배송 출발했어요" (no `(광고)` prefix) 가 PRIORITY 로 남는지** 명시 test 추가 — body=`"배송이 출발했어요. 송장번호 1234"`, no prefix → IMPORTANT keyword path 그대로 PRIORITY.
2. **`(광고)` prefix 인데 PROMO Category 매치가 없는 경우** (사용자가 PROMO_QUIETING preset 을 선택 안 했음) → cascade 의 fallback path 그대로 (resolver override 가 PROMO 매치를 요구하므로).
3. **본문 mid 의 "광고" 자연어** (e.g. body=`"광고주 미팅이 곧 시작됩니다"`) → detector RED, IMPORTANT path 그대로.
4. **`(광고)` prefix 인데 IMPORTANT 키워드가 없는 PURE PROMO** → 기존 PROMO path 그대로 (회귀 없음 단언).
5. `./gradlew :app:testDebugUnitTest` 전체 GREEN.
6. `./gradlew :app:assembleDebug` 빌드 통과.

## Task 5: ADB e2e on real device `R3CY2058DLJ` (P0 — deferred 불가)

**Objective:** 사용자 신고 시나리오를 실제 device 에서 실측 재현 + DiagnosticLogger 로 evidence 캡처.

**Steps:**

```bash
# 0. 실제 device 연결 확인
adb devices | grep R3CY2058DLJ
# expected: R3CY2058DLJ device

# 1. Fresh debug APK 설치 + onboarding 완료 + PROMO_QUIETING 선택
./gradlew :app:assembleDebug
adb -s R3CY2058DLJ install -r app/build/outputs/apk/debug/app-debug.apk
adb -s R3CY2058DLJ shell pm clear com.smartnoti.app
# (onboarding 수동 통과 — PROMO_QUIETING 체크)

# 2. 신규 install 의 PROMO Category 가 DIGEST 인지 DB 확인
adb -s R3CY2058DLJ shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT id,name,action,userModifiedAction FROM categories;"
# expected: PROMO Category row, action='DIGEST', userModifiedAction=0

# 3. 사용자가 보낸 실제 (광고) 알림 도착 대기 (AliExpress/멤버십데이/하나카드 중 하나)
#    또는 시뮬레이션:
adb -s R3CY2058DLJ shell cmd notification post -S bigtext \
  -t "[하나카드]" Promo478Test "(광고) 장기카드대출 안내 — 무료배송 + 결제 혜택"

# 4. DiagnosticLogger 결과 확인 (#480 just-shipped)
adb -s R3CY2058DLJ shell run-as com.smartnoti.app cat files/diagnostic.log | grep "광고"
# expected: detector 가 광고 prefix detect 한 라인 + classifier 가 PROMO Category 로 라우팅한 라인

# 5. DB 분류 결과 확인
adb -s R3CY2058DLJ shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT title,body,status,sourceSuppressionState,reasonTags FROM notifications \
   WHERE body LIKE '(광고)%' ORDER BY postedAtMillis DESC LIMIT 5;"
# expected: status=DIGEST, sourceSuppressionState=PRIORITY_KEPT 가 아니라 정상 suppression 적용된 상태

# 6. 시스템 tray 에서 원본 cancel 확인
adb -s R3CY2058DLJ shell dumpsys notification --noredact | grep "Promo478Test"
# expected: tag=Promo478Test 항목 부재 (원본 cancel)

# 7. SmartNoti 의 정리함에 entry 등장 확인
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s R3CY2058DLJ pull /sdcard/ui.xml /tmp/ui.xml
grep -oE '광고[^"]*' /tmp/ui.xml
# expected: SmartNoti 정리함 카드에 광고 알림 표시
```

전부 PASS 면 Task 6 진행. 어느 한 단계라도 expected 와 다르면 plan-implementer 가 멈추고 보고. PR body 에 DiagnosticLogger 라인 발췌 + DB row 결과 + dumpsys diff 첨부.

## Task 6: Bump journey `last-verified` + Change log

**Objective:** `notification-capture-classify` 와 `digest-suppression` 두 journey 의 contract 갱신을 audit 에 남김.

**Files:**

- `docs/journeys/notification-capture-classify.md`
- `docs/journeys/digest-suppression.md`

**Steps:**

1. 두 journey 의 frontmatter `last-verified:` 를 Task 5 ADB sweep 실행 날짜 (system clock `date -u +%Y-%m-%d`) 로 갱신.
2. Change log 에 row 추가:
   - notification-capture-classify: `YYYY-MM-DD: fix #478 (3차 진단) — "(광고)" prefix precedence override 도입. KoreanAdvertisingPrefixDetector + CategoryConflictResolver 의 prefix-aware path 로 IMPORTANT 키워드와 본문이 겹쳐도 PROMO 가 winner. Recipe 는 PR body Task 5 참고.`
   - digest-suppression: `YYYY-MM-DD: fix #478 (3차 진단) — PROMO_QUIETING preset 의 default action 을 SILENT → DIGEST 로 변경 (B2). Auto-expansion 이 fire 해 광고 알림이 정리함에 번들. 기존 install 은 SettingsRepository.applyPendingMigrations v3 으로 자동 bump (userModifiedAction=false 인 경우만).`
3. Known gaps 섹션에 새 gap 을 만들지 않는다 — 회귀가 닫혔으므로.
4. (선택) `onboarding-bootstrap.md` Change log 에도 cross-link 한 줄.

---

## Scope

**In:**

- `KoreanAdvertisingPrefixDetector` 신규 + `CategoryConflictResolver` precedence 가지 추가 + `NotificationClassifier` cascade 호출.
- `OnboardingQuickStartCategorySeeder.PROMO_QUIETING` action 변경.
- `Category` 모델에 `userModifiedAction` 필드 + Room v9→v10 migration.
- `SettingsRepository.applyPendingMigrations` v3.
- UI 의 action edit 진입점에서 `userModifiedAction=true` flip.
- `notification-capture-classify` + `digest-suppression` journey `last-verified` + Change log.

**Out:**

- `(광고)` 외 marker 의 i18n 사용자 설정 UI (코드 default 만 노출, 향후 plan).
- 다른 onboarding preset 의 default action 변경 (PROMO_QUIETING 만).
- B1 (SILENT 자동 확장) 또는 B3 (first-time prompt) — 사용자가 명시적으로 reject.
- replacement notification 정책 변경.
- Hidden / Digest 화면 UX 변경.

---

## Risks / open questions

- **기존 install 의 `userModifiedAction` 이 false 라는 가정이 깨질 가능성.** 사용자가 onboarding 후 PROMO Category action 을 SILENT 로 본인이 선택했을 수도 있음. v3 마이그레이션이 그 의지를 덮어쓸 위험. **Open question — 사용자 확인 필요**: (a) 기존 install 도 일괄 bump 하나 (현재 plan default), (b) 기존 install 은 안 건드리고 신규만 DIGEST 로 (안전), (c) 첫 광고 알림 도착 시 1회 prompt (B3 reject 와 충돌). plan-implementer 는 이 question 을 PR body 에 raise 하고 사용자 답을 기다린다.
- **Regex bound.** `advertisingPrefixPatterns` 가 사용자 설정 지원 시 ReDoS / 성능 우려. 코드 default 만 노출하는 이번 scope 에서는 안전, 향후 UI 노출 plan 에서 max 8 patterns × max 32 chars 강제.
- **Mid-body `(광고)` 매치 회귀.** detector 는 `^\s*` anchor + leading 32 chars 만 봐서 mid-body 자연어 "광고" 와 분리. 이 anchor 가 빠지면 false positive 폭증 — Task 1 의 regression guard test 가 이 boundary 고정.
- **`(광고)` 가 title 에만 있는 앱.** 일부 앱은 prefix 를 title 에 박을 수 있음. detector 가 body 가 매치 안 하면 title fallback 검사 — 단 title 매치는 PROMO precedence override 를 trigger 하지 않고 단순 PROMO Category 매치만 가산 (title 은 sender display 일 수도 있어 본문보다 신호가 약함). plan-implementer 가 PR #486 fixture 표 보고 최종 결정.
- **Multi-Category PROMO 의 경우.** 사용자가 PROMO 외에 자체 PROMO-like Category 를 만들었으면 어느 게 winner 인지 — 기존 sortOrder/order tie-break 그대로 적용. 새 동작 분기 X.
- **Real device dependency.** Task 5 가 `R3CY2058DLJ` 에 의존 — 다른 emulator/device 에서는 reproduction 불완전. plan-implementer 가 device 접근 못 하면 즉시 보고.
- **시스템 시계 (clock-discipline).** Task 6 의 `last-verified` 와 Change log 날짜는 반드시 `date -u +%Y-%m-%d` 결과 사용 (`.claude/rules/clock-discipline.md`).

---

## Related journey

- [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — 분류 cascade 의 Bug A 진입점. 이 plan 의 fix 후 `last-verified` 를 bump 한다.
- [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — DIGEST 분류 결과의 source tray cancel + auto-expansion. Bug B2 의 결과 surface. 이 plan 의 fix 후 `last-verified` 를 bump 한다.
- 보조: [`docs/journeys/onboarding-bootstrap.md`](../journeys/onboarding-bootstrap.md) — PROMO_QUIETING preset 시드 경로.
