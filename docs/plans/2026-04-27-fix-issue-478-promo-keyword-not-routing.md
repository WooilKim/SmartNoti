---
status: planned
fixes: 478
---

# Fix #478: "(광고)" 알림이 분류되지 않고 시스템 트레이에 그대로 남음

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P0 release-blocker — ADB e2e on `emulator-5554` is mandatory (deferred not allowed).

**Goal:** 사용자가 onboarding 의 PROMO_QUIETING preset 을 선택한 상태에서 "(광고)" 라고 시작하는 외부 알림이 들어오면, SmartNoti 가 즉시 KEYWORD rule 매치 → DIGEST 분류 → 원본 tray cancel 까지 수행해서, 시스템 알림 센터에서 사라지고 SmartNoti 의 Hidden / Digest 뷰에 옮겨진다. 사용자 신고 (#478) 의 회귀를 끝낸다.

**Architecture:** 회귀의 root cause 가 6개 가설 중 어느 분기인지 모르므로, Task 1 의 failing test suite 가 6개 가설 각각을 distinct assertion 으로 reproduce 한다. 가장 유력한 hot-path candidate 는 `NotificationDecisionPipeline` (PR #469 에서 listener `processNotification` 에서 추출됨) 의 IGNORE/SILENT/DIGEST 분기 또는 `SourceNotificationRoutingPolicy` 의 cancel 결정. 가설 1 (Rule 미설치) / 가설 2 (`enabled=false`) / 가설 3 (Category.action ≠ DIGEST) 는 onboarding wiring + Categories 시드, 가설 4 (`suppressSourceForDigestAndSilent=false`) / 가설 5 (`SourceNotificationRoutingPolicy` regression) / 가설 6 (`NotificationDecisionPipeline` extraction regression) 는 routing/suppression policy. Task 1 결과로 fix 위치가 결정된다 — Task 2 는 그 위치에 정확한 수정을 적용한다.

**Tech Stack:** Kotlin, Room, Jetpack Compose, DataStore, Gradle unit tests, ADB on `emulator-5554`.

---

## PR title format: `fix(#478): <one-line>`
## PR body must include `Closes #478`

---

## Product intent / assumptions

- "(광고)" prefix 알림은 PROMO_QUIETING preset 선택자에게 **항상** DIGEST 로 라우팅돼야 한다 — onboarding 의 광고/프로모션/쿠폰/세일/특가/이벤트/혜택 키워드 룰이 contains-ignoreCase 매치로 잡아야 함.
- 사용자가 onboarding 에서 preset 을 명시 선택했다면 "광고" keyword Rule 의 owning Category 는 DIGEST action 이 default 여야 한다 (onboarding 의 1:1 Category 시드가 그렇게 깔린 가정).
- DIGEST 결정이 떨어진 알림의 원본 tray entry 는 `suppressSourceForDigestAndSilent` global toggle 이 ON (2026-04-24 default ON) 일 때 `SmartNotiNotificationListenerService.cancelNotification` 으로 즉시 cancel 돼야 한다.
- 회귀의 root cause 식별 전까지는 6개 가설 모두 distinct test 로 격리해 reproduce 한다 — debug-by-coincidence 금지.
- Fix 후 [notification-capture-classify](../journeys/notification-capture-classify.md) journey 의 `last-verified` 를 bump 해서 contract 갱신을 audit 가능하게 남긴다.

---

## Task 1: Failing test suite — 6개 가설 각각 distinct reproduction

**Objective:** 어느 hypothesis 가 실제 root cause 인지 test 결과로 식별. 모든 test 는 RED 로 시작. Task 2 후에는 root-cause test 가 GREEN 으로, 나머지는 "이미 PASS 인 contract test" 로 남아 회귀 가드 역할.

**Files (신규 또는 보강):**
- `app/src/test/java/com/smartnoti/app/notification/PromoKeywordRoutingRegressionTest.kt` (신규, 메인 reproduction suite)
- `app/src/test/java/com/smartnoti/app/notification/NotificationDecisionPipelineCharacterizationTest.kt` (보강 — "(광고)" + DIGEST + suppression ON 분기 추가)
- `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplierTest.kt` (보강 — PROMO_QUIETING preset 의 매치값 + RuleType + enabled=true 회귀 가드)

**Steps (각 sub-step 은 distinct hypothesis 를 격리):**

1. **H1 — Rule 미설치/미적용:** `OnboardingQuickStartRuleApplier.buildRules(setOf(PROMO_QUIETING))` 가 `RuleTypeUi.KEYWORD` + `matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택"` 의 RuleUiModel 을 반환하는지. Onboarding 의 Categories 시드 helper (`OnboardingQuickStartCategorySeeder` 또는 동등) 가 이 rule 을 owning Category 의 `ruleIds` 에 포함시키는지 (대응 helper 이름은 코드 탐색으로 식별).
2. **H2 — Rule `enabled=false`:** `RuleDraftFactory.create(...)` 결과 `RuleUiModel.isEnabled == true` 인지. `OnboardingQuickStartRuleApplier.mergeRules` 가 기존 rule 을 reuse 할 때 `enabled` 를 보존하는지.
3. **H3 — owning Category.action ≠ DIGEST:** Onboarding seed 가 PROMO_QUIETING preset 의 owning Category 를 `CategoryAction.DIGEST` 로 만드는지. `CategoryConflictResolver.resolve` 가 keyword-only match 일 때 이 Category 를 winner 로 뽑는지 (app-pin 보너스 없음, rule-type 사다리는 KEYWORD).
4. **H4 — `suppressSourceForDigestAndSilent=false`:** `SmartNotiSettings` default 가 `suppressSourceForDigestAndSilent = true` (2026-04-24 변경) 인지 회귀 가드. `SettingsRepositoryFacadeContractTest` 와 동등한 단언을 `PromoKeywordRoutingRegressionTest` 에도 명시.
5. **H5 — `SourceNotificationRoutingPolicy` regression:** Policy 가 `decision = DIGEST`, `packageName ∈ suppressedSourceApps`, `suppressSourceForDigestAndSilent=true` 입력에서 `RouteDecision.CancelSource` (또는 동등) 를 반환하는지. **추가**: `SuppressedSourceAppsAutoExpansionPolicy.expandIfNeeded` 가 첫 번째 (광고) 알림에서 packageName 을 `suppressedSourceApps` 에 자동 추가해 같은 처리 cycle 에서 cancel 이 적용되는지.
6. **H6 — `NotificationDecisionPipeline` extraction regression (PR #469):** `NotificationDecisionPipelineCharacterizationTest` 에 새 시나리오 추가 — `CapturedNotificationInput(title="(광고) 오늘만 특가", body="세일 안내", packageName="com.smartnoti.testnotifier")` + PROMO_QUIETING 룰 + DIGEST Category + suppression ON → pipeline 호출 결과:
   - `decision = DIGEST`
   - `SourceTrayActions.cancelSource` 가 정확히 1회 호출됨 (mock verify)
   - `NotificationRepository.save` 가 DIGEST status 로 호출됨
   기존 5-branch characterization 이 어느 분기에서도 cancel 을 trigger 하지 않는 회귀 path 가 있다면 이 test 가 RED.

7. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.PromoKeywordRoutingRegressionTest" --tests "com.smartnoti.app.notification.NotificationDecisionPipelineCharacterizationTest" --tests "com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartRuleApplierTest"` 로 RED 확인. **RED 인 test 의 이름/메시지를 commit message + PR body 에 그대로 인용** — 이게 root cause evidence.

## Task 2: Code fix at the identified root cause

**Objective:** Task 1 의 RED test 를 GREEN 으로. Fix 위치는 Task 1 결과로 결정 — 추정 X.

**Files (가설별 수정 위치, 실제 수정은 Task 1 결과에 의해 결정됨):**
- H1/H2: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartRuleApplier.kt`, onboarding category seeder.
- H3: onboarding category seeder의 default action.
- H4: `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt`.
- H5: `app/src/main/java/com/smartnoti/app/notification/SourceNotificationRoutingPolicy.kt` (또는 `SuppressedSourceAppsAutoExpansionPolicy.kt`).
- H6: `app/src/main/java/com/smartnoti/app/notification/NotificationDecisionPipeline.kt` (PR #469 extraction).

**Steps:**
1. Task 1 의 RED 결과 1줄 요약을 commit message 첫 줄로 사용.
2. 정확히 그 분기만 수정 — 다른 가설은 건드리지 않는다 (scope creep 금지).
3. 수정이 다른 분기에 부수효과가 있다면 회귀 test 를 같이 추가.

## Task 3: Test green

**Objective:** Task 1 의 모든 test + 전체 unit test suite GREEN.

**Steps:**
1. `./gradlew :app:testDebugUnitTest` 전체 GREEN. RED 0건.
2. `./gradlew :app:assembleDebug` 빌드 통과.
3. PR body 에 RED → GREEN diff (test 이름 list) 첨부.

## Task 4: ADB e2e on `emulator-5554` (P0 — deferred 불가)

**Objective:** 사용자 신고 시나리오를 emulator 에서 실측 재현 + 사라짐 확인.

**Steps:**
```bash
# 0. Fresh debug APK 설치 + onboarding 완료 + PROMO_QUIETING 선택
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm clear com.smartnoti.app
# (onboarding 수동 통과 — PROMO_QUIETING 체크)

# 1. Rule 시드 확인
adb -s emulator-5554 shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT id,type,matchValue,enabled,categoryId FROM rules WHERE matchValue LIKE '%광고%';"
# expected: 1+ row, type=KEYWORD, enabled=1, categoryId 비어있지 않음

# 2. (광고) 시작 알림 게시
adb -s emulator-5554 shell cmd notification post -S bigtext \
  -t "(광고) 오늘만 특가" PromoTest_478 "세일 안내 본문"

# 3. DB 캡처 결과 확인
adb -s emulator-5554 shell run-as com.smartnoti.app sqlite3 databases/smartnoti.db \
  "SELECT title,status,reasonTags,ruleHitIds FROM notifications \
   WHERE title LIKE '(광고)%' ORDER BY postedAtMillis DESC LIMIT 3;"
# expected: status=DIGEST; ruleHitIds 에 keyword:광고,... 포함

# 4. 시스템 tray 에서 원본 cancel 확인
adb -s emulator-5554 shell dumpsys notification --noredact | grep -A3 "PromoTest_478"
# expected: tag=PromoTest_478 항목이 (광고) 게시 후 부재 (cancelled)
# 만약 SmartNoti replacement digest 가 보이면 그건 OK — 원본만 사라지면 됨

# 5. SmartNoti 앱 Hidden/Digest 뷰에 표시되는지 UI 확인
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 pull /sdcard/ui.xml /tmp/ui.xml
grep -oE '(광고)[^"]*' /tmp/ui.xml
# expected: (광고) 오늘만 특가 가 SmartNoti 화면 어딘가에 등장
```

전부 PASS 면 Task 5 진행. 어느 한 단계라도 expected 와 다르면 plan-implementer 가 멈추고 보고.

## Task 5: Bump `notification-capture-classify` `last-verified`

**Objective:** journey 문서 갱신으로 fix 가 contract 상 반영됐음을 audit 에 남김.

**Files:**
- `docs/journeys/notification-capture-classify.md`

**Steps:**
1. Frontmatter `last-verified:` 를 Task 4 ADB sweep 을 실행한 날짜 (system clock `date -u +%Y-%m-%d`) 로 갱신.
2. Change log 에 row 추가:
   - `YYYY-MM-DD: fix #478 — "(광고)" prefix 알림 routing 회귀 해소. Root cause: <H1~H6 중 식별된 것>. Fix: <한 줄 요약>. Recipe: PR body 의 Task 4 ADB log 참고. `last-verified` bump 동반 (이 PR).`
3. Known gaps 섹션에 새 gap 을 만들지 않는다 — 회귀가 닫혔으므로.
4. (선택) `digest-suppression.md` Change log 에도 cross-link 한 줄.

---

## Scope

**In:**
- `OnboardingQuickStartRuleApplier`, onboarding Category seed, `NotificationDecisionPipeline`, `SourceNotificationRoutingPolicy`, `SuppressedSourceAppsAutoExpansionPolicy`, `SettingsModels` 중 **Task 1 이 지목한 단일 위치**.
- 위 6개 가설을 distinct 하게 reproduce 하는 test.
- `notification-capture-classify` journey `last-verified` + Change log.

**Out:**
- Hidden 화면 UX (그룹 카드 등 — 별도 plan).
- Digest empty-state CTA (별도 plan).
- 새 keyword 추가 / preset 변경.
- onboarding flow 의 다른 preset 동작.
- Sticky exclude list (별도 plan `2026-04-26-digest-suppression-sticky-exclude-list.md`).

---

## Risks / open questions

- **Multiple hypotheses both RED?** Task 1 결과 H1~H6 중 두 개 이상이 RED 면 fix 가 둘을 모두 닫아야 하는지, 아니면 hidden coupling 인지 plan-implementer 가 보고하고 사용자 확인 (둘 다 fix 하면 PR scope 비대화 우려).
- **사용자의 onboarding 상태가 의심:** 사용자가 PROMO_QUIETING 을 실제로 선택했는지 issue 본문은 가정만 함. Task 4 의 step 1 (rules table SELECT) 결과가 0 row 면 root cause 는 onboarding flow / 사용자 행동 — 그 경우 plan-implementer 는 멈추고 보고 (이 plan 의 fix 는 의미 없음, 별도 onboarding UX plan 필요).
- **Replacement notification 정책:** DIGEST 결정 후 SmartNoti 가 자체 replacement notification 을 다시 posting 하는 정책이 `(광고)` 케이스에 대해서도 의도된 동작인지 — 사용자 신고 본문은 "트레이에 그대로 남음" 만 언급, replacement 자체에 대한 불만은 없으므로 기존 정책 유지로 가정.
- **시스템 시계 (clock-discipline):** Task 5 의 `last-verified` 와 Change log 날짜는 반드시 `date -u +%Y-%m-%d` 결과 사용 — 모델 컨텍스트 날짜 신뢰 금지 (`.claude/rules/clock-discipline.md`).
- **No-code-write boundary:** 이 plan 은 gap-planner 의 산출물 — plan-implementer 가 다음 단계로 코드 작성. plan-implementer 가 Task 1 RED 결과를 PR body 에 인용하지 않으면 PM 의 traceability gate 에서 reject.

---

## Related journey

- [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — 분류 cascade 와 Categories 주입의 hot-path. 이 plan 의 fix 후 `last-verified` 를 bump 한다.
- 보조: [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — DIGEST 분류 결과의 source tray cancel 정책.
- 보조: [`docs/journeys/onboarding-bootstrap.md`](../journeys/onboarding-bootstrap.md) — PROMO_QUIETING preset 시드 경로.
