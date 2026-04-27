---
id: notification-capture-classify
title: 알림 캡처 및 분류
status: shipped
owner: @wooilkim
last-verified: 2026-04-28
---

## Goal

외부 앱이 게시한 모든 알림을 SmartNoti가 캡처하고, PRIORITY / DIGEST / SILENT 중 하나로 분류해 DB에 저장한다. 이 journey는 인박스/숨김/요약 등 모든 후속 journey의 공통 전제다.

## Preconditions

- 사용자가 NotificationListener 권한을 허용함 (→ [onboarding-bootstrap](onboarding-bootstrap.md))
- `SmartNotiNotificationListenerService`가 시스템에 바인딩되어 `onListenerConnected` 상태

## Trigger

외부 앱이 `NotificationManager.notify(...)` 호출 → 시스템이 리스너의 `onNotificationPosted(sbn)` 콜백을 발화.

## Observable steps

1. `SmartNotiNotificationListenerService.onNotificationPosted(sbn)` 수신.
   (대체 경로: 리스너가 방금 bind 되었다면 `onListenerConnected` 가 먼저 발화해
   `enqueueOnboardingBootstrapCheck` 뒤에 `enqueueReconnectSweep` 을 돌린다.
   sweep 은 `activeNotifications` 중 이미 `NotificationRepository` 에 저장된
   `(packageName, contentSignature, postTime)` 를 skip 하고 나머지를 아래
   `processNotification` 경로로 재주입한다. 자세한 계약은
   [onboarding-bootstrap](onboarding-bootstrap.md) 참고.)
2. `processNotification(sbn)`이 serviceScope(IO dispatcher)에서 launch 됨.
3. Extras 에서 `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_CONVERSATION_TITLE` 을 꺼내 title / body / sender 복원. PackageManager 로 appName 조회.
4. `NotificationProcessingCoordinator.process(input)` 에 위임 — coordinator 는 `RulesRepository.currentRules()` + **`CategoriesRepository.currentCategories()`** + `SettingsRepository.observeSettings().first()` 세 스냅샷을 동일 call site 에서 읽는다 (2026-04-22 plan `categories-runtime-wiring-fix` Task 4). Categories 가 더 이상 공란으로 분류기에 들어가지 않음 — classifier 가 실제 production 경로에서 `Category.action` 을 적용할 수 있다.
5. `PersistentNotificationPolicy.shouldTreatAsPersistent(isOngoing, isClearable)` 로 persistent 여부 판단.
6. `DuplicateNotificationPolicy` + `LiveDuplicateCountTracker` 로 최근 동일 content signature 발생 횟수 집계.
7. `CapturedNotificationInput` 을 조립해 `NotificationCaptureProcessor.process(input, rules, categories, settings)` 호출.
8. 내부에서 `NotificationClassifier.classify(input, rules, categories)` 가 분류 결정:
   - 룰 매치 (PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 최우선. 매치된 enabled rule 집합은 override collapse (Phase C) 를 거쳐 `matchedRuleIds` 로 축약.
   - **Decision cascade (Phase P2, plan `2026-04-22-categories-split-rules-actions.md`):**
     1. `matchedRuleIds` 에 포함된 rule 을 **소유한 Category 집합** 을 `categories.filter { it.ruleIds.any { ... in matchedRuleIds } }` 로 lift.
     2. 소유 Category 가 하나 이상이면 `CategoryConflictResolver.resolve(matched, allCategories, matchedRuleTypes)` 가 승자 선택:
        - **App-pin 보너스** (`appPackageName != null` → +16)
        - **Rule-type 사다리** (APP > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE) — 실제 매치된 rule 의 타입 중 최고 rank
        - **드래그 순서** (`order` 낮을수록 승) — specificity 동점 시 사용자가 분류 탭에서 정한 순서 그대로
     3. 승자 Category 의 `action` 이 `toDecision()` 으로 변환돼 최종 `NotificationDecision` 생성.
   - 매치된 Rule 이 어느 Category 에도 속하지 않는 경우 (orphan) → `NotificationDecision.SILENT` 로 안전 fallback (hot path 크래시 방지).
   - 룰 매치 자체가 없는 경우 → VIP 발신자 / 우선순위 키워드 / 중복 카운트 임계 / 쇼핑앱+Quiet hours / 기본 SILENT 의 heuristic cascade 로 진입 (Category 무관).
   - `NotificationClassification(decision, matchedRuleIds)` 반환 → `NotificationCaptureProcessor` 가 decision + matchedRuleIds 를 분리 소비, `NotificationEntityMapper` 가 `ruleHitIds` 컬럼에 comma-separated 로 영속화.
   - **IGNORE 는 Category.action = IGNORE 경로에서만 도달 가능.** Rule 모델에서 action 필드는 제거됐으므로 (Phase P1 Task 4) IGNORE 는 해당 Category 가 winning 했을 때만 결정된다. Classifier 의 VIP / 우선순위 키워드 / 반복 / Quiet hours / 기본 SILENT 분기는 **절대 IGNORE 를 만들지 않는다**.
9. `NotificationRepository.save(notification, postTime, contentSignature)` → Room `notifications` 테이블에 upsert.
10. 이어서 source routing 단계 진입 (→ [silent-auto-hide](silent-auto-hide.md), [priority-inbox](priority-inbox.md), digest-suppression).

## Exit state

- `NotificationEntity` row 한 건이 `status ∈ {PRIORITY, DIGEST, SILENT, IGNORE}` 로 저장됨. IGNORE 는 Category.action = IGNORE 경로에서만 생성되며, 저장 후 기본 뷰 (`observePriority/Digest/Silent`, `toHiddenGroups`) 에서는 필터 아웃된다 (→ [ignored-archive](ignored-archive.md)).
- `NotificationRepository.observeAll()` 을 구독하는 모든 구성 요소가 새 알림을 즉시 관측 가능.
- 분류 결과에 따른 후속 routing이 실행됨 (cancel / replacement / keep). IGNORE 는 tray cancel 만 수행하고 replacement alert 는 posting 하지 않음 — listener 의 early-return 분기가 책임.

## Out of scope

- 원본 알림의 숨김/교체/유지 동작 (→ 각 분류별 journey)
- Protected 알림 예외 (→ [protected-source-notifications](protected-source-notifications.md))
- 분류 결과 UI 렌더링 (→ priority-inbox / digest-inbox / hidden-inbox)
- 룰 자체의 CRUD (→ rules-management)

## Code pointers

- `SmartNotiNotificationListenerService#onNotificationPosted` — entry
- `SmartNotiNotificationListenerService#enqueueReconnectSweep` — 매 reconnect 시
  활성 알림 tray 재-스윕 트리거 (온보딩 bootstrap 이 pending 이면 대기)
- `notification/ListenerReconnectActiveNotificationSweepCoordinator` —
  `SweepDedupKey(packageName, contentSignature, postTimeMillis)` 로 in-process
  Set + `NotificationRepository.existsByContentSignature` 조합 dedup
- `SmartNotiNotificationListenerService#processNotification` — pipeline, `NotificationProcessingCoordinator` 에 위임
- `notification/NotificationProcessingCoordinator` — Rules + Categories + Settings 세 스냅샷을 동일 call site 에서 주입하는 pure Kotlin seam (plan `categories-runtime-wiring-fix` Task 4, #245)
- `domain/usecase/NotificationCaptureProcessor` — input → decision + matchedRuleIds, `categories: List<Category>` 파라미터 수용 (Phase P2)
- `domain/usecase/NotificationClassifier` — 분류 규칙 본체 (VIP/키워드/shopping 상수도 이곳 생성자에 주입), rule 매치 후 Category lift + `CategoryConflictResolver` 위임
- `domain/usecase/CategoryConflictResolver` — app-pin 보너스 + rule-type 사다리 + `order` 드래그 tie-break + **KCC `(광고)` prefix override** (PR #489): `KoreanAdvertisingPrefixDetector.hasAdvertisingPrefix(body, title)` 가 true 면 IMPORTANT/PRIORITY Category 가 아무리 매치돼도 비-PRIORITY (PROMO/DIGEST) Category 가 강제로 승리. KCC 정보통신망법 제50조의8 시행령 마커 (`(광고)` / `[광고]` / `(AD)` / `[AD]`, optional `[Web발신]` 접두 허용) 가 제품의 진짜 의도를 드러내는 신호이므로 키워드 overlap 보다 우선.
- `domain/usecase/KoreanAdvertisingPrefixDetector` — KCC marker 헤드 스캔기 (input head 64자 anchored regex). Body 우선 → title fallback. Rationale: 통신사 SMS gateway (`[Web발신](광고)…`) 와 카드사 프로모 (`(광고)[하나카드]…`) 패턴 모두 false-positive 없이 catch (mid-body `광고` 자연어는 anchor 로 거른다). Plan: `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md` Task 2 (Bug A 수정).
- `domain/model/Category` + `CategoryAction#toDecision` — Category.action → NotificationDecision 매핑
- `data/categories/CategoriesRepository#currentCategories` — 분류 스냅샷 (hot path)
- `notification/AppLabelResolver` (PR #507) — explicit fallback chain for `appName` resolution: `loadLabel` → `pm.getApplicationLabel` → `pm.getNameForUid` → packageName. Per-package memoization with install/upgrade/remove broadcast invalidation. Replaces the previous one-line `getApplicationLabel + Exception swallow` in listener which left 16 popular packages (Gmail, 쿠팡, 하나카드페이, 네이버, …) showing raw packageName as `appName`. Plan: `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`.
- `notification/AppIconResolver` (PR #521 / closure this PR) — sibling of `AppLabelResolver`: PackageManager → Drawable → Bitmap conversion with `LruCache<String, Bitmap>(64)` per-package memoization + install/upgrade/remove broadcast invalidation (shares the same `PackageReceiver` as `AppLabelResolver`). Returns `null` when source app has no displayable launcher icon — replacement notifier omits `setLargeIcon` rather than masking with SmartNoti brand. Plan: `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`.
- `notification/ReplacementActionIcon` (PR #521 / closure this PR) — compile-time enum mapping replacement action → small icon drawable (`DIGEST → ic_replacement_digest`, `SILENT → ic_replacement_silent`, `PRIORITY → ic_replacement_priority`). Eliminates magic-int sprawl across `SmartNotiNotifier` + `SilentHiddenSummaryNotifier`. Plan: same as `AppIconResolver`.
- `data/local/MigrateAppLabelRunner` (PR #507/#511) — M2 cold-start migration: re-resolves `appName == packageName` rows once via `AppLabelResolver`. Idempotent under Settings flag `app_label_resolution_migration_v1_applied`. Wired in `MainActivity.onCreate` alongside `MigratePromoCategoryActionRunner`. Same plan.
- `data/categories/MigratePromoCategoryActionRunner` — M1 cold-start migration (PR #491): PROMO Category 의 default action 이 SILENT → DIGEST 로 바뀐 결정 (Bug B2) 의 사용자 영향을 흡수. `userModifiedAction == false` row 만 in-place bump + Settings flag `promo_quieting_action_migration_v3_applied` 로 1회성 가드. Wired in `MainActivity.onCreate`. Plan: 같은 plan Task 3.
- `data/settings/SettingsRepository#isPromoQuietingActionMigrationV3Applied` — M1 idempotent guard flag.
- `notification/ContentSignatureNormalizer` (PR #492) — 동일 본문이 동적 텍스트 (jitter, timestamp, count) 만 다른 경우 contentSignature 정규화로 dedup 향상. **Settings 토글 default OFF** — 사용자가 명시적으로 켤 때만 적용되며 분류 결정 자체는 변경하지 않는다 (signature stability 만 영향).
- `diagnostic/DiagnosticLogger` (PR #485) — 분류 파이프라인 route 결정을 PIN-locked diagnostic.log (`files/diagnostic/`) 에 옵션으로 기록. 기본 OFF; production 분류 동작 변경 없음.
- `domain/model/NotificationClassification` — decision + matchedRuleIds wrapper
- `domain/usecase/DuplicateNotificationPolicy`, `LiveDuplicateCountTracker`
- `domain/usecase/PersistentNotificationPolicy`
- `data/local/NotificationRepository#save`
- `data/local/NotificationEntity` — Room 스키마
- `notification/DebugClassificationOverride` — **debug-only** classifier override (extras marker `com.smartnoti.debug.FORCE_STATUS` → `NotificationStatusUi`). `BuildConfig.DEBUG == true` 빌드의 `processNotification` 만 호출하며 release 빌드에서는 dead-strip. Recipe-side helper `app/src/debug/.../DebugInjectNotificationReceiver` 가 broadcast 를 받아 marker 가 baked-in 된 `Bundle` 로 resolver 를 호출 — production classifier path 는 변경 없음. Priority-inbox journey-tester recipe 전용 — 다른 journey 의 검증에 사용하지 말 것 (plan `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`)
- Debug-only inject receiver supports optional `--es package_name <pkg>` and `--es app_name <label>` extras (debug APK only) so verification recipes can pin the saved row's `packageName` / `appName` and exercise package-gated classifier branches (e.g. quiet-hours `shoppingPackages`). `force_status` 와 독립적 — 둘 중 하나, 둘 다, 또는 둘 다 omit 가능. See `app/src/debug/java/com/smartnoti/app/debug/DebugInjectNotificationReceiver.kt` and plan `docs/plans/2026-04-26-debug-inject-package-name-extra.md`.
- `app/src/debug/java/com/smartnoti/app/debug/DebugBootstrapRehearsal` (+ `DebugBootstrapRehearsalReceiver`) — debug-build 한정 verification entry. `BuildConfig.DEBUG` + debug source set 격리로 release APK 에는 dead-stripped (release `classes*.dex` 에서 부재 확인). `onboarding-bootstrap` journey 의 non-destructive sub-recipe (`com.smartnoti.debug.REHEARSE_BOOTSTRAP` broadcast) 가 호출하는 진입점 — production capture pipeline / production bootstrap 의 1회성 consume 계약 모두 변경 없음 (plan `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`).

## Tests

- `NotificationClassifierTest` — 룰 매칭, 스케줄 윈도우, repeat bundle
- `DuplicateNotificationPolicyTest`
- `QuietHoursPolicyTest`
- `NotificationCapturePolicyTest`
- `SmartNotiNotificationListenerServiceCategoryInjectionTest` — listener (coordinator seam 경유) 가 Categories 를 classifier 로 실제로 전달하는지

## Verification recipe

```bash
# 1. 리스너 권한 허용 상태인지 확인
adb shell cmd notification allow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService

# 2. 우선순위 키워드 알림 게시
adb shell cmd notification post -S bigtext -t "Bank" BankAuth \
  "인증번호 123456을 입력하세요"

# 3. DB 캡처 확인 — 앱 실행 후 Home 혹은 Priority 탭에 반영되는지
adb shell am start -n com.smartnoti.app/.MainActivity
```

## Known gaps

- 서비스가 disconnect 된 동안 시스템이 이미 dismiss 한 알림은 `activeNotifications`
  에 없으므로 SmartNoti 가 복구할 수 없음. 리스너가 재접속 시점에 tray 에 남아 있는
  알림만 [onboarding-bootstrap](onboarding-bootstrap.md) bootstrap + reconnect sweep
  이 소급 캡처.
- (resolved 2026-04-22, plan `categories-runtime-wiring-fix` Task 4 / #245) Listener 가 `categoriesRepository.currentCategories()` 를 읽지 않아 classifier 가 "orphan → SILENT" fallback 으로 빠지던 drift — `NotificationProcessingCoordinator` 가 Rules + Categories + Settings 세 스냅샷을 동일 call site 에서 주입하도록 수정돼 해소. journey-tester 가 recipe 재실행해 `last-verified` 를 갱신하면 최종 확인.
- (resolved 2026-04-27, plan `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`) **code health (2026-04-27)**: `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — file is 627 lines, largest fun `processNotification` is ~251 lines (line 215→466) covering capture / dedup / classify / route / IGNORE-cancel / replacement-notify / silent-summary all inline. Refactor candidate — extract per-decision branches into `NotificationProcessingCoordinator` helpers. Pin behavior with characterization tests first. → plan: `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`
- **code health (2026-04-27)**: `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt` — file is 450 lines (over 400 moderate threshold). Multiple observe-* + bulk action methods sharing the same Dao. Refactor candidate — split per-tier observers (priority / digest / silent / ignored) into separate query objects.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verification sweep — PRIORITY 키워드 posting 이 Home StatPill 까지 end-to-end 반영됨 확인 (자세한 증거는 `docs/journeys/README.md` Verification log)
- 2026-04-21: `onListenerConnected` 재접속마다 `enqueueReconnectSweep` 가 돌도록 파이프라인 확장. 리스너가 꺼져 있는 동안 tray 에 쌓였다가 살아남은 알림은 `ListenerReconnectActiveNotificationSweepCoordinator` 가 `SweepDedupKey` + `NotificationRepository.existsByContentSignature` 로 dedup 해 `processNotification` 에 재주입. 온보딩 bootstrap 경로는 그대로 유지 (plan `docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md`, PR #94 / #102 / #104)
- 2026-04-21: 분류 단계에서 rule 매치가 `RuleConflictResolver` 로 이동 — base rule 과 해당 base 를 가리키는 override rule 이 동시에 매치되면 override 가 승리, 단일 tier 는 `allRules` 인덱스 tie-break (earlier wins). Classifier 는 `NotificationClassification(decision, matchedRuleIds)` 를 반환하고 `NotificationCaptureProcessor` 가 이를 풀어 `NotificationEntityMapper` 경유로 `ruleHitIds` 컬럼에 영속 (PR #146, #149, plan `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase B Task 2 + Phase C Task 2). `last-verified` 는 recipe 재실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: journey-tester 재검증 sweep PASS — recipe end-to-end (`cmd notification post … CaptureClassifyTest_0421`) 로 PRIORITY 라우팅 + `사용자 규칙` 태그 관측, 5개 unit test 클래스 (`NotificationClassifierTest`, `RuleConflictResolverTest`, `DuplicateNotificationPolicyTest`, `QuietHoursPolicyTest`, `NotificationCapturePolicyTest`) 총 34건 green. 자세한 증거는 `docs/journeys/README.md` Verification log. DRIFT 없음. 설치된 APK 가 `ruleHitIds` 컬럼 이전 빌드 (`lastUpdateTime=2026-04-21 15:47:57`) 라 live schema 에서 해당 컬럼은 확인 불가 — pre-condition 제약이며 contract 는 code-level test 로 증명.
- 2026-04-21: 4번째 분류 tier **IGNORE (무시)** 추가 — `NotificationDecision.IGNORE` / `NotificationStatusUi.IGNORE` / `RuleActionUi.IGNORE` 및 Room v8→v9 no-op migration (#178 `6aad9d5`), `NotificationClassifier` 의 `RuleActionUi.IGNORE → NotificationDecision.IGNORE` 매핑 (#179 `5f516d6`). Classifier 의 rule-miss 분기는 IGNORE 를 생성하지 않으며, `ALWAYS_PRIORITY` override 로 base IGNORE 를 덮는 경로는 `RuleConflictResolver` 기존 계약 그대로. Exit state 의 status 집합에 IGNORE 포함. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Tasks 1-3. `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: journey-tester 재검증 sweep PASS (fresh APK `lastUpdateTime=2026-04-22 03:46:30`). IGNORE 분류 path end-to-end 최초 관측 — `keyword:IGNOREKEYr` IGNORE 룰 존재 상태에서 `cmd notification post -t 'IGNOREKEYr in title' IgnoreCaptureTest_0421` 포스팅 → DB row `status=IGNORE, reasonTags=발신자 있음|사용자 규칙|IgnoreTestRule|조용한 시간, ruleHitIds=keyword:IGNOREKEYr` 저장 + tray cancel 확인. PRIORITY 경로 + rule-miss SILENT 경로도 대조 재현. Home StatPill 이 IGNORE 1건을 3-bucket (`즉시 16 / Digest 2 / 조용히 12 = 30`) 에서 필터 아웃, total captured 31 과 차이 1 발생 — Exit state 의 "기본 뷰에서 필터 아웃" 계약 증명. `ruleHitIds` live schema 관측 재확인 (23-column v9). DRIFT 없음. 자세한 증거는 `docs/journeys/README.md` Verification log.
- 2026-04-22: **Rule/Category 분리 아키텍처** 반영 — Rule 모델에서 `action` 필드 제거 (Phase P1 Task 4), 액션은 Category 가 소유. Classifier cascade 는 "Rule 매치 → matched rules 를 소유한 Category 집합 → `CategoryConflictResolver` (app-pin 보너스 + rule-type 사다리 + `order` 드래그 tie-break) → winning Category.action → NotificationDecision". IGNORE 는 Category.action = IGNORE 경로에서만 도달. Rule orphan (어느 Category 에도 소속되지 않은 Rule 매치) 은 SILENT 로 안전 fallback. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Tasks 1-4 (#236), Phase P2 Tasks 5-7 (#239). `last-verified` 는 변경 없음 (Phase P2 이후 recipe 재실행 미수행; Known gap 참고 — listener 가 categories 스냅샷을 아직 주입하지 않아 실제 production 경로에서는 Category.action 적용 전). journey-tester 가 listener 주입 상태 확인 후 `last-verified` 갱신.
- 2026-04-22: **Listener 의 Categories 주입 drift 해소** — `SmartNotiNotificationListenerService.processNotification` 이 새 `NotificationProcessingCoordinator` 에 위임하도록 리팩터. Coordinator 가 `rulesRepository.currentRules()` + `categoriesRepository.currentCategories()` + `settingsRepository.observeSettings().first()` 세 스냅샷을 동일 call site 에서 읽어 `NotificationCaptureProcessor.process(input, rules, categories, settings)` 에 전달. 이전까지 categories 가 공란으로 내려가 "orphan → SILENT" fallback 으로 라우팅되던 hot path 는 이제 실제 `Category.action` 을 적용. `SmartNotiNotificationListenerServiceCategoryInjectionTest` 로 계약 고정. Plan: `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 4 (#245), Task 7 (this PR). `last-verified` 는 journey-tester 의 ADB sweep 에서 end-to-end 재검증 완료 후 갱신.
- 2026-04-22: **Debug-only classifier override 도입** — `notification/DebugClassificationOverride` (Bundle extras marker `com.smartnoti.debug.FORCE_STATUS` → `NotificationStatusUi`) 를 listener 가 `BuildConfig.DEBUG` 가드 하에서만 호출. classifier 결과 → marker override → 이후 routing/IGNORE 분기 순서로 결합. 일반 분류 경로는 변경 없음 — release APK 에는 marker 를 읽는 분기 자체가 dead-strip. priority-inbox journey-tester recipe 가 누적 user rule 영향 없이 PRIORITY 를 검증할 수 있도록 도입. Plan: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`. `last-verified` 변경 없음 (override 는 production 분류 경로의 기능을 바꾸지 않음).
- 2026-04-24: journey-tester 재검증 sweep PASS (APK `lastUpdateTime=2026-04-24 01:44:11`). Recipe end-to-end (`cmd notification post -t Bank CaptureClassifyTest_0424 "인증번호 123456을 입력하세요"`) → DB row `status=PRIORITY, ruleHitIds=keyword:인증번호,결제,배송,출발, reasonTags=발신자 있음|사용자 규칙|중요 알림|온보딩 추천|중요 키워드` 확인. Home StatPill `전체 61건 보기` 로 누적 캡처 반영. PRIORITY classification cascade (rule match → keyword pre-rule heuristic) 정상 동작. 최근 #292 (suppress defaults) / #287 (audit helper) / #285 (frontmatter rule) 머지 이후 capture path regression 없음. DRIFT 없음.
- 2026-04-24: **미분류 Rule 의 no-op 분기 doc-level 명시** — plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md`. 고급 규칙 편집의 액션 dropdown 제거 + 1:1 Category auto-upsert 제거 결과로, 사용자가 "나중에 분류에 추가" 를 누르면 Rule 이 owning Category 없이 "미분류" 상태로 살아남게 됨. classifier 동작은 변경 없음 — `NotificationClassifier.classify` 의 `findMatchingRules` 가 미분류 Rule 도 매치할 수 있지만, `categories.filter { ruleIds 교집합 }` 이 빈 리스트가 되어 winner 가 정해지지 않고 `NotificationDecision.SILENT` 로 fall-through. 즉 미분류 Rule 은 user-routed (PRIORITY/DIGEST/IGNORE) 결정을 만들지 않는다. 새 단위 테스트 `RuleWithoutCategoryNoOpTest` 가 이 contract 를 회귀 고정. `last-verified` 변경 없음.
- 2026-04-26: **Duplicate threshold / window 가 settings-driven 으로 일반화** — `DuplicateNotificationPolicy` 의 default window 제거 (caller-injected only), `NotificationClassifier` 의 하드코딩 `>= 3` 은 `ClassificationInput.duplicateThreshold` 비교로 변경. `CapturedNotificationInput.duplicateThreshold` (default 3) 가 listener 에서 `settings.duplicateDigestThreshold` 를 받아 classifier 까지 전달. base heuristic 만 영향 — Rule/Category cascade 와 priority keyword path 는 그대로 우선. Plan: `docs/plans/2026-04-26-duplicate-threshold-window-settings.md`. 자세한 사용자 관측 동작은 [duplicate-suppression](duplicate-suppression.md) 참고.
- 2026-04-26: journey-tester 재검증 sweep PASS (APK `lastUpdateTime=2026-04-26 15:43:43`). Recipe end-to-end (`cmd notification post -S bigtext -t Bank CaptureClassifyTest_0426 "인증번호 123456을 입력하세요"`) → DB row `status=PRIORITY, packageName=com.android.shell, ruleHitIds=keyword:인증번호,결제,배송,출발` 저장. 23-column v9 schema (`ruleHitIds` 컬럼) 재확인. Home StatPill `즉시 9 / Digest 20 / 조용히 21` 로 PRIORITY tier 반영, "오늘 알림 50개 중 중요한 9개를 먼저 전달했어요" surfaced. 최근 #295 (duplicate threshold settings-driven) 머지 후 capture/classification regression 없음. DRIFT 없음.
- 2026-04-27: journey-tester 재검증 sweep PASS (APK `lastUpdateTime=2026-04-27 11:56:47`). Recipe end-to-end (`cmd notification post -S bigtext -t Bank CaptureClassifyTest_0427 "인증번호 123456을 입력하세요"`) → DB row `status=PRIORITY, packageName=com.android.shell, ruleHitIds=keyword:인증번호,결제,배송,출발, reasonTags=사용자 규칙|중요 알림|온보딩 추천|중요 키워드` 저장. 23-column v9 schema 재확인 (`PRAGMA user_version=9`). 최근 #254 (categories empty-state CTA) 머지 후 capture/classification regression 없음. DRIFT 없음.
- 2026-04-27: **Listener `processNotification` 단계 추출 리팩터** — 251줄 핫패스를 세 helper 로 분해. `NotificationDecisionPipeline` 가 IGNORE early-return / 보호 알림 / auto-expansion / suppression 정책 / silent capture / routing / replacement / suppression-state / save 전부 책임 (side-effect 는 `SourceTrayActions` 인터페이스 경유). `NotificationDuplicateContextBuilder` 는 settings 기반 window/policy 재구성 + 중복 카운트, `NotificationPipelineInputBuilder` 는 extras → MessagingStyle sender → appName → persistent flags → CapturedNotificationInput 조립. listener `processNotification` 자체는 chain 호출 ~80줄로 축약. **사용자 가시 동작 변경 없음** — Task 1 의 5-branch characterization test 가 production helper 로 재포인트된 채 GREEN, `SmartNotiNotificationListenerServiceCategoryInjectionTest` 포함 전체 unit test suite GREEN. `last-verified` 변경 없음 (순수 리팩터). Plan: `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`.
- 2026-04-27: **Issue #510 closure — replacement notifications carry source-app large icon + action-specific small icon glyph**. Tasks 1-4 (AppIconResolver + ReplacementActionIcon enum + 3 vector drawables + builder wiring in both `SmartNotiNotifier` and `SilentHiddenSummaryNotifier`) shipped via PR #521 (commit `cfe60e5`). Tasks 5-7 (ADB e2e + journey bumps + plan flip status:shipped) shipped via this PR. Live ADB verification on R3CY2058DLJ (debug APK rebuilt 2026-04-27 22:30 UTC): `dumpsys notification --noredact` confirms every `smartnoti_silent_*` channel notification now carries `icon=Icon(typ=RESOURCE pkg=com.smartnoti.app id=0x7f070099)` (= `ic_replacement_silent` per `aapt dump --values resources`) and Gmail-grouped child rows carry `android.largeIcon=Icon (Icon(typ=BITMAP size=101x101))` (Gmail launcher icon resolved through `AppIconResolver`). Mixed-source root summary id=23057 (`smartnoti_silent_summary`) carries `android.largeIcon=null` confirming the omission contract for groups spanning multiple source packages. Plan: `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md` flipped to `status:shipped`. DRIFT 없음.
- 2026-04-28: **Issue #503 — appName fallback chain explicit + cold-start re-resolution migration**. End-to-end verified on R3CY2058DLJ (APK `lastUpdateTime=2026-04-28 05:43:21`, build with PRs #507 + #511 merged). DB pull (`run-as com.smartnoti.app cat databases/smartnoti.db`) → `SELECT COUNT(*) FROM notifications WHERE appName = packageName` returns **0** (down from 16 distinct broken packages). All 61 distinct packages render friendly labels: `com.google.android.gm → Gmail`, `com.coupang.mobile → 쿠팡`, `com.hanaskcard.paycla → 하나카드`, `com.nhn.android.search → NAVER`, `com.google.android.youtube → YouTube`, `com.linkedin.android → LinkedIn`, `com.kakao.talk → 카카오톡`, etc. `MigrateAppLabelRunner` ran at cold-start, flag `app_label_resolution_migration_v1_applied` flipped, runner is idempotent for subsequent launches. New captures via `AppLabelResolver` resolve labels through explicit chain (`loadLabel` → `getApplicationLabel` → `getNameForUid` → packageName). Plan: `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`. PRs: #507 (resolver + migration), #511 (follow-up). DRIFT 없음. `last-verified` 변경 없음 (오늘 ADB recipe 가 이미 위 entry 에서 GREEN).
- 2026-04-28: journey-tester end-to-end re-verification on R3CY2058DLJ (Samsung Galaxy S24, fresh APK `lastUpdateTime=2026-04-28 00:34:37` then re-installed local debug-build at 01:17 KST). Bug A KCC `(광고)` prefix precedence + Bug B2 PROMO default DIGEST + M1 migration verified end-to-end (commits c6d9451, 64b1382). **Bug A**: debug-injected `BugA_KCC_Test` (`packageName=com.example.adtest`, body=`(광고) 배송 결제 인증번호 이벤트`) → DB row `status=DIGEST, ruleHitIds=keyword:인증번호,결제,배송,출발,keyword:광고,프로모션,쿠폰,세일,특가,이벤트,혜택, reasonTags=… 중요 알림|온보딩 추천|… 중요 키워드 …` 저장. 두 Category (IMPORTANT + PROMO) 가 모두 매치됐음에도 KCC 마커가 head-anchored regex 에 잡혀 `CategoryConflictResolver` 가 PROMO Category 를 강제 승자로 선택 — IMPORTANT keyword (배송/결제/인증번호/이벤트) overlap 회귀 차단 입증. **Bug B2 + M1**: R3CY2058DLJ DataStore (`smartnoti_categories.preferences_pb`) 의 `cat-onboarding-promo_quieting` row 가 7-column codec 으로 `…|DIGEST|1|…|0` (action=DIGEST, order=1, userModifiedAction=false) 저장됨 + `smartnoti_settings.preferences_pb` 에 `promo_quieting_action_migration_v3_applied` flag 존재 — `MigratePromoCategoryActionRunner.run()` 이 cold-start 에서 idempotent 하게 실행되고 flag flip 까지 성공. 이전 `(광고) 배송` PRIORITY 라우팅 historical row 들 (Gmail 4-26 21:42, NaverShopping 4-27 17:08, Gmail 4-27 20:09) 은 모두 PR #489 머지 (15:32 UTC) **이전** 캡처라 contract drift 아님 — 새 빌드에서 동일 입력은 더 이상 PRIORITY 로 안 간다. DiagnosticLogger 는 `files/diagnostic/` 디렉토리는 존재하나 toggle OFF 라 log 파일 미생성 (검증 contract 무관). DRIFT 없음. `last-verified` 2026-04-27 → 2026-04-28.
