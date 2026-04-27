---
id: priority-inbox
title: 검토 대기 알림 (passthrough review)
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
---

## Goal

PRIORITY 로 분류된 알림을 **"SmartNoti 가 건드리지 않은 알림"** 으로 재프레이밍해서, 사용자가 이 판단이 맞는지 검토하고 필요하면 즉시 Digest / 조용히 / 규칙 / 분류로 재분류할 수 있게 한다. 시스템 알림센터의 원본은 **절대 건드리지 않아** 사용자가 기대하는 즉시 전달(소리 / 헤즈업 / 잠금화면)이 정상 동작하게 한다.

> 2026-04-21 Rules UX v2 Phase A 이후 + 2026-04-22 plan `categories-split-rules-actions` Phase P3 Task 11 이후, 이 화면은 BottomNav 상의 독립 탭이 아니다. Home 의 "검토 대기" 패스스루 카드 탭을 통해서만 진입한다. Route (`Routes.Priority`) 는 유지되어 replacement notification deep-link 의 parent 로도 계속 사용된다. PRIORITY 는 Rule 매치 후 winning `Category.action == PRIORITY` 로 결정되는 상태다 (→ [notification-capture-classify](notification-capture-classify.md), [categories-management](categories-management.md)).

## Preconditions

- 알림이 PRIORITY 로 분류되어 DB 에 저장됨 — `Category.action = PRIORITY` 인 분류에 속한 Rule 매치 또는 classifier heuristic (VIP 발신자 / 우선순위 키워드) 로 결정 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료 상태 (→ [onboarding-bootstrap](onboarding-bootstrap.md))

## Trigger

- Home 의 `HomePassthroughReviewCard` ("SmartNoti 가 건드리지 않은 알림 N건 — 검토하기") 탭, 또는
- replacement 알림의 deep-link 가 `Routes.Priority.route` 를 parent 로 지정한 경우

(Priority 가 BottomNav 탭에서 제거됐으므로 탭 직접 진입 경로는 더 이상 없음.)

## Observable steps

1. Home 의 `HomePassthroughReviewCard` 를 탭 → `navigateToTopLevel(Routes.Priority.route)` → NavController 가 Priority composable 렌더링.
2. `PriorityScreen` 이 `SettingsRepository.observeSettings()` 와 `NotificationRepository.observePriorityFiltered(hidePersistentNotifications)` 를 collect.
3. 상단 `ScreenHeader` — eyebrow "검토" + 제목 "SmartNoti 가 건드리지 않은 알림" + 서브타이틀 "이 판단이 맞는지 확인하고, 필요하면 바로 Digest / 조용히 / 규칙으로 보낼 수 있어요." (empty 상태에서는 제목이 "검토 대기 알림" 으로 바뀌고 `EmptyState` 카드가 보임.)
4. 리스트 상단에 `SmartSurfaceCard` — "검토 대기 N건" + 보조 설명.
5. LazyColumn 이 status = PRIORITY 인 `NotificationUiModel` 리스트를 카드로 표시. IGNORE row 는 `observePriorityFiltered` 가 `status == PRIORITY` 로 선필터하므로 여기에 포함되지 않음 — IGNORE 는 [ignored-archive](ignored-archive.md) 전용. 각 카드 아래에는 `PassthroughReclassifyActions` 영역이 함께 렌더 — "이 판단을 바꿀까요?" 라벨 + `→ Digest` / `→ 조용히` OutlinedButton 두 개 + `→ 규칙 만들기` TextButton.
6. 사용자가 `→ Digest` / `→ 조용히` 탭 → `PassthroughReviewReclassifyDispatcher.reclassify` 가 `NotificationFeedbackPolicy` 로 상태를 바꿔 `NotificationRepository.updateNotification()` 수행 (규칙 생성은 하지 않음).
6a. 사용자가 카드를 long-press → `PriorityScreenMultiSelectState.enterSelection` 으로 multi-select 모드 진입. 상단에 `PriorityMultiSelectActionBar` (`"N개 선택됨"` + `→ Digest` + `→ 조용히` + `취소`) 가 mount 되고 카드별 인라인 `PassthroughReclassifyActions` 는 hidden. 활성 모드에서 카드 본문 탭은 toggle (Detail navigation 은 모드 종료 후로 미룸). `→ Digest` / `→ 조용히` 탭 → `BulkPassthroughReviewReclassifyDispatcher.bulk` 가 선택된 row 마다 단일 dispatcher 를 ordered 호출 → snackbar `"알림 N건을 Digest 로 옮겼어요"` / `"알림 N건을 조용히로 옮겼어요"` (persistedCount > 0 인 경우만) → selection clear + ActionBar 사라짐 + persisted row 가 list 에서 동시 사라짐. NOOP-only (모든 row 가 이미 target status) 케이스는 snackbar 미등장 + selection 유지로 사용자가 다시 시도 가능.
7. 사용자가 `→ 규칙 만들기` 탭 → `onCreateRuleClick(notification)` 을 통해 rules feedback flow 로 연결.
8. 사용자가 카드 본문 탭 → `Routes.Detail.create(notificationId)` 로 navigate (→ notification-detail). multi-select 활성 시에는 toggle 로 분기되어 Detail 진입 안 함.

## 시스템 tray 처리

- `SourceNotificationRoutingPolicy.route(PRIORITY, *, *)` → `SourceNotificationRouting(cancelSourceNotification = false, notifyReplacementNotification = false)` 고정.
- 원본 알림은 항상 시스템 tray 에 유지됨. SmartNoti 가 cancel / replace 를 시도하지 않음.
- `suppressSourceForDigestAndSilent` 등 사용자 설정과 무관하게 PRIORITY 는 건드리지 않는 것이 불변조건. 재분류 버튼으로 상태를 바꾸면 그때부터 해당 분류 (DIGEST/SILENT) 의 routing 이 적용됨.

## Delivery profile

- `DeliveryProfilePolicy` 가 각 알림에 대해 AlertLevel / VibrationMode / HeadsUp / LockScreen visibility 조합 프로파일을 계산.
- 채널은 `ReplacementNotificationChannelRegistry` 가 프로파일 조합마다 사전 생성해 둠 (PRIORITY 의 경우 LOUD / SOFT / QUIET × STRONG / LIGHT / OFF × headsup on/off 등).

## Exit state

- 검토 화면에 현재 PRIORITY 분류된 알림이 모두 표시됨.
- 시스템 tray 의 원본 그대로 유지.
- 사용자는 인라인 액션으로 즉시 재분류하거나, Detail 로 이동해 상세 재분류/룰 고정 등 후속 액션 수행 가능 (→ rules-feedback-loop).
- 재분류 후에는 해당 알림이 PRIORITY 에서 빠져 Home 카드 count 및 이 화면의 리스트에서 동시에 사라짐.

## Out of scope

- 분류 로직 자체 (→ [notification-capture-classify](notification-capture-classify.md))
- "중요로 고정" feedback action 의 룰 저장 경로 (→ rules-feedback-loop)
- Priority 알림의 사운드/헤즈업 실제 재생 (플랫폼/DeliveryProfile 책임)
- Home 의 `HomePassthroughReviewCard` 자체의 렌더링/카운트 집계 (→ [home-overview](home-overview.md))

## Code pointers

- `ui/screens/priority/PriorityScreen` — 검토 화면 본체, 인라인 액션 포함, multi-select 모드 지원
- `ui/screens/priority/PriorityMultiSelectActionBar` — multi-select 활성 시 상단 ActionBar (`N개 선택됨` + bulk CTA + 취소)
- `ui/screens/priority/PriorityScreenMultiSelectState` — pure state machine (enterSelection / toggle / cancel / clear)
- `ui/components/HomePassthroughReviewCard` — Home 진입 카드
- `domain/usecase/PassthroughReviewReclassifyDispatcher` — 단일 row 인라인 재분류 로직
- `domain/usecase/BulkPassthroughReviewReclassifyDispatcher` — N 건 ordered loop wrapper, NOOP/IGNORED/UPDATED 카운터 반환
- `domain/usecase/NotificationFeedbackPolicy` — 상태 전이
- `data/local/NotificationRepository#observePriorityFiltered`
- `domain/usecase/DeliveryProfilePolicy`
- `notification/SourceNotificationRoutingPolicy` — PRIORITY 분기
- `notification/ReplacementNotificationChannelRegistry` — 채널 정의
- `navigation/Routes.Priority` — route 유지 (탭 아님, 카드 tap + replacement parent 용)
- `navigation/BottomNavItem` — Priority 엔트리 **없음** (4-tab: 홈 / 정리함 / 분류 / 설정)

## Tests

- 분류/룰 레이어 테스트로 간접 커버. `PriorityScreen` 단독 테스트는 현재 없음.
- `SourceNotificationRoutingPolicyTest#persistent_hidden_priority_keeps_source_notification_and_skips_replacement`
- `HomePassthroughReviewCardStateTest` — 카드 state machine (count=0 vs count>0).

## Verification recipe

```bash
# 1. Unique tag + debug-inject marker 로 rule 무관하게 PRIORITY 확정.
#    debug APK 에만 머지되는 `DebugInjectNotificationReceiver` (src/debug)
#    가 broadcast 를 받아 extras 에 `com.smartnoti.debug.FORCE_STATUS=PRIORITY`
#    를 baked-in 한 Notification 을 post → listener 의 onNotificationPosted
#    가 일반 경로로 받아 `DebugClassificationOverride` (BuildConfig.DEBUG 가드)
#    가 classifier 결과를 PRIORITY 로 pin. Release 빌드에는 receiver 자체와
#    override 호출 분기가 모두 absent / dead-strip 되어 있다.
#    Plan: docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md
UNIQ="PriDbg$(date +%s%N | tail -c 6)"
adb -s emulator-5554 shell am broadcast \
  -a com.smartnoti.debug.INJECT_NOTIFICATION \
  --es title "$UNIQ" \
  --es body "검토 대기 시드" \
  --es force_status PRIORITY

# 2. 시스템 tray 원본 유지 확인
adb -s emulator-5554 shell dumpsys notification --noredact | grep -B1 -A4 "$UNIQ"

# 3. 앱 진입 → Home 화면의 "검토 대기" 카드에 count 증가 확인
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity

# 4. Home 의 HomePassthroughReviewCard 탭 → 검토 화면 진입, 해당 알림이 카드로 보이고
#    카드 아래에 "→ Digest / → 조용히 / → 규칙 만들기" 인라인 액션이 보이는지 시각 확인
#    (reasonTags 에 "디버그 주입" 만 붙어 있으면 marker 경로로 들어온 알림임)
# 5. 예: "→ Digest" 탭 → 카드가 리스트에서 사라지고 Home count 가 감소, Digest 탭에 추가됨
```

## Bulk reclassify verification recipe

```bash
# 사전: emulator-5554 + debug APK + listener 권한, plan 2026-04-26-priority-inbox-bulk-reclassify.

# 1. 4 PRIORITY rows seed (sequential — rapid loops 가 dedup window 와 충돌 가능)
for i in 1 2 3 4; do
  adb -s emulator-5554 shell am broadcast \
    -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
    -a com.smartnoti.debug.INJECT_NOTIFICATION \
    --es title "BlkPri$i" --es body "bulk seed $i" --es force_status PRIORITY
  sleep 1
done

# 2. DB 확인 — 4건 모두 status=PRIORITY, reasonTags="디버그 주입"
adb -s emulator-5554 exec-out run-as com.smartnoti.app sh -c \
  "sqlite3 databases/smartnoti.db \"SELECT title,status,reasonTags FROM notifications WHERE title LIKE 'BlkPri%';\""

# 3. 앱 진입 → Home `검토 대기 N` 카드 → `검토하기` 탭 → PriorityScreen 진입.
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity

# 4. 첫 BlkPri1 카드 long-press → multi-select 진입 + ActionBar `1개 선택됨 / → Digest / → 조용히 / 취소` 노출.
#    (Compose `combinedClickable` 의 long-press 임계값 때문에 `adb input swipe` 로는 트리거가 어렵고
#    실기기/에뮬레이터 GUI 로 확인 권장. 자동화 시 Espresso `longClick()` 또는 uiautomator2 사용.)

# 5. 나머지 BlkPri2/3/4 본문 탭 toggle → ActionBar `4개 선택됨` 갱신 시각 확인.
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -oE '"[^"]*(선택됨|→ Digest|→ 조용히|취소)[^"]*"'

# 6. ActionBar `→ Digest` 탭 → 기대값:
#    - snackbar `"알림 4건을 Digest 로 옮겼어요"` (≈ 4s)
#    - 4개 row 가 PriorityScreen 에서 즉시 사라짐
#    - SmartSurfaceCard `검토 대기 ${N-4}건` 갱신
#    - Home `검토 대기 ${N-4}` count 동기 갱신
#    - 정리함 Digest 서브탭 진입 → 4 row DIGEST 라우팅 확인
adb -s emulator-5554 exec-out run-as com.smartnoti.app sh -c \
  "sqlite3 databases/smartnoti.db \"SELECT title,status FROM notifications WHERE title LIKE 'BlkPri%';\""
#    → 4 rows 모두 status=DIGEST

# 7. `→ 조용히` 경로 동일 패턴 — BlkSil1..4 seed → multi-select → `→ 조용히`
#    → snackbar `"알림 4건을 조용히로 옮겼어요"` + Hidden 보관 중 탭에서 4건 확인.

# 8. NOOP 케이스 — 이미 DIGEST 인 row 1건만 selection → `→ Digest`
#    → snackbar 미등장 + selection 유지 (사용자가 다시 시도 가능). DB row 변동 없음.

# 9. baseline 복원 — `cmd notification cancel` 또는 디버그 reset 으로 검증용 row 정리.
```

## Known gaps

- (resolved 2026-04-26, plan 2026-04-26-priority-inbox-bulk-reclassify) 검토 화면 자체에 일괄 처리(모두 읽음/모두 재분류) 액션 없음 — 여전히 카드별 처리. → plan: `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` (long-press multi-select + bulk → Digest / → 조용히 + snackbar 구현; 모두 읽음 / 모두 IGNORE 는 별도 plan)
- `PriorityScreen` / `PassthroughReclassifyActions` 의 Compose UI 테스트 부재.
- Verification recipe 의 `엄마` 고정값이 rules-feedback-loop sweep 이후 남은 `person:엄마 → DIGEST` rule 에 의해 DIGEST 로 라우팅되어 검증이 무효화될 수 있어 은행/인증번호 경로로 변경됨. 사용자 환경에 따라 다른 키워드가 필요할 수 있음.
- 2026-04-22: 위 fragility 가 `은행` + `인증번호` 경로에서도 재현됨 (journey-tester sweep). 누적된 user rules 가 `reasonTags=사용자 규칙|인증번호|중요 키워드` 매칭으로 status 를 SILENT 로 강등시켜 PRIORITY 가 0 건으로 떨어지면 `HomePassthroughReviewCard` 가 hidden 되어 recipe 의 Observable steps 1, 4–8 검증 불가. Recipe 를 unique non-keyword sender + per-sweep rule reset 또는 test-only PRIORITY injection hook 으로 재설계 필요.
  → plan: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop tick 재검증 (PASS, PR 추가). Recipe fragility 관련 Known gap 추가
- 2026-04-21: v1 loop tick re-verify #2 (PASS). Keyword 기반 PRIORITY 경로 (은행/인증번호) 로 PERSON DIGEST 규칙 오염을 우회, fresh unique tag `PriIn0421T3` 로 DB+UI+Detail end-to-end 확증
- 2026-04-21: Rules UX v2 Phase A shipped (PR #140/#141/#142/#143). Priority 탭을 "검토 대기 알림" 으로 재프레이밍 — BottomNav 에서 제거, Home `HomePassthroughReviewCard` 탭으로만 진입. 화면 제목 "SmartNoti 가 건드리지 않은 알림" + 인라인 재분류 액션 (`→ Digest` / `→ 조용히` / `→ 규칙 만들기`) 추가. Route (`Routes.Priority`) 는 replacement notification parent 로 유지. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 검토 대기 리스트에서 제외됨을 명시 — `observePriorityFiltered` 는 `status == PRIORITY` 로만 필터하므로 IGNORE row 는 자동 배제, [ignored-archive](ignored-archive.md) 화면에서만 노출. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6 (#185 `9a5b4b9`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
- 2026-04-21: Post-IGNORE fresh APK ADB 검증 PASS on emulator-5554 (APK `lastUpdateTime=2026-04-22 03:46:30`). `cmd notification post -t '은행' PriFreshAPK_0421 '인증번호 778899...'` → DB `status=PRIORITY, reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드`. Tray 원본 유지 (`SourceNotificationRoutingPolicy` PRIORITY 분기 고정값). Home passthrough card "건드리지 않은 알림 18건 / 검토하기" 탭 → PriorityScreen eyebrow "검토" + title "SmartNoti 가 건드리지 않은 알림" + subtitle + SmartSurfaceCard "검토 대기 18건" + 카드별 "이 판단을 바꿀까요?" 헤더 + `→ Digest / → 조용히 / → 규칙 만들기` 3버튼 노출. 카드 탭 → NotificationDetailScreen "알림 상세" 진입 확인. BottomNav 4탭 (홈/정리함/규칙/설정, Priority 탭 부재) 재확인. Observable steps 1–8 + Exit state 전부 일치.
- 2026-04-22: **Rule/Category 분리 아키텍처** 반영 — Goal/Preconditions 에 "PRIORITY 는 `Category.action == PRIORITY` 의 결과" 를 명시. BottomNav 구성을 "홈/정리함/분류/설정" 으로 갱신 (plan `categories-split-rules-actions` Phase P3 Task 11 이후 `규칙` 탭 → Settings 서브메뉴로 이동). Observable steps / 시스템 tray 처리 / Delivery profile 섹션은 변경 없음 — PRIORITY routing 불변조건 (`cancelSource=false`) 은 아키텍처 교체와 무관하게 유지. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 (#236), P3 (#240). `last-verified` 변경 없음.
- 2026-04-22: Debug-only `FORCE_STATUS` extras marker 추가 (`BuildConfig.DEBUG` 하에서만 `DebugClassificationOverride` 가 classifier 결과 override). `cmd notification post` 가 `--es` 를 지원하지 않고 self-package 알림이 `OnboardingActiveNotificationBootstrapper.shouldProcess` 에서 필터되는 두 제약 때문에, recipe 는 `app/src/debug/` source set 의 `DebugInjectNotificationReceiver` 가 broadcast 를 받아 `NotificationCaptureProcessor.process` → `DebugClassificationOverride.resolve(extras, ...)` → `NotificationRepository.save` 를 직접 호출하는 경로로 재설계. 누적 user rule (`person:엄마 → DIGEST`, `인증번호 → SILENT` 등) 이 PRIORITY 검증을 무력화하던 fragility 해결. Release APK 에는 receiver 와 marker 분기가 모두 absent / dead-strip. Plan: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`.
- 2026-04-23: ADB end-to-end 검증 PASS on emulator-5554. Fresh debug APK 설치 후 `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg66000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB row `status=PRIORITY, reasonTags=디버그 주입` 저장 확인. body 에 `인증번호` 키워드 (누적 SILENT 룰 트리거) 를 포함한 두 번째 케이스도 동일하게 PRIORITY 로 pin. Home `검토 대기 7 / SmartNoti 가 건드리지 않은 알림 7건` 카드 visibility 회복 확인 (uiautomator dump). `last-verified` 2026-04-21 → 2026-04-23 으로 bump. v1 loop tick re-verify (PASS via debug-inject marker).
- 2026-04-24: v1 loop tick re-verify PASS on emulator-5554 (per `clock-discipline.md` ground-truth `date -u` = 2026-04-24). `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg43000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB row `PriDbg43000|PRIORITY|디버그 주입` 확인. Home 진입 후 passthrough card "검토 대기 22 / SmartNoti 가 건드리지 않은 알림 22건 / 검토하기" visible. `검토하기` 탭 → PriorityScreen eyebrow "검토" + title "SmartNoti 가 건드리지 않은 알림" + subtitle + SmartSurfaceCard "검토 대기 22건" + 카드별 "이 판단을 바꿀까요?" + `→ Digest / → 조용히 / → 규칙 만들기` 3 버튼 노출. BottomNav 4 탭 (홈/정리함/분류/설정) 재확인, Priority 탭 부재. Observable steps 1–7 + Exit state 일치, DRIFT 없음. `last-verified` 2026-04-23 → 2026-04-24 bump.
- 2026-04-26: **Bulk reclassify shipped** (plan `2026-04-26-priority-inbox-bulk-reclassify`, PRs #387 + #388 + #389 + this PR). PriorityScreen 카드 long-press → multi-select 모드 + 상단 `PriorityMultiSelectActionBar` (`N개 선택됨` + `→ Digest` + `→ 조용히` + `취소`) → 선택된 row 일괄 재분류 + snackbar `"알림 N건을 Digest 로 옮겼어요"` / `"알림 N건을 조용히로 옮겼어요"`. 신규 도메인 컴포넌트 `BulkPassthroughReviewReclassifyDispatcher` (단일 dispatcher 위임, NOOP/IGNORED/UPDATED 카운터 반환) + pure state `PriorityScreenMultiSelectState` (Compose 외부 회귀 비용 최소화). 카드 본문 탭은 multi-select 활성 시 toggle, 비활성 시 Detail navigation (Gmail 식 가구현 — Apple 식 leading-checkbox 후보는 PR 본문에서 사용자 결정 대기). Observable steps 6a + Bulk verification recipe 추가. Smoke test: 4 PRIORITY rows seed via debug-inject hook, PriorityScreen 진입 + 카드/액션 노출 확인. Compose `combinedClickable` long-press 는 `adb input swipe` 로 트리거 불가 — 실기기 GUI 또는 uiautomator2 검증 권장 (next journey-tester rotation).
- 2026-04-26: v1 loop tick re-verify PASS on emulator-5554 (`date -u` = 2026-04-26). `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg13000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB `PriDbg13000|PRIORITY|디버그 주입`. Home passthrough card "검토 대기 34 / SmartNoti 가 건드리지 않은 알림 34건 / 검토하기" visible. Tap → PriorityScreen eyebrow "검토" + title "SmartNoti 가 건드리지 않은 알림" + subtitle + SmartSurfaceCard "검토 대기 34건" + 카드별 "이 판단을 바꿀까요?" + `→ Digest / → 조용히 / → 규칙 만들기` 노출. BottomNav 4탭 (홈/정리함/분류/설정), Priority 탭 부재. Observable steps 1–7 + Exit state 일치, DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26 bump.
- 2026-04-27: v1 loop tick re-verify PASS on emulator-5554 (`date -u` = 2026-04-27). `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg88000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB `PriDbg88000|PRIORITY|디버그 주입`. Home tab → passthrough card "검토 대기 22 / SmartNoti 가 건드리지 않은 알림 22건 / 검토하기" visible. `검토하기` 탭 → PriorityScreen eyebrow "검토" + title "SmartNoti 가 건드리지 않은 알림" + subtitle + SmartSurfaceCard "검토 대기 22건" + 카드별 "이 판단을 바꿀까요?" + `→ Digest / → 조용히 / → 규칙 만들기` + reasonTags `디버그 주입` 노출. BottomNav 4탭 (홈/정리함/분류/설정), Priority 탭 부재. Observable steps 1–5 + 7 + Exit state 일치, DRIFT 없음. Step 6 (단일 인라인 재분류 actuation) 및 6a (long-press multi-select) 는 visual mount 만 검증 (combinedClickable long-press 는 `adb input` 으로 트리거 불가, Known gap 유지). `last-verified` 2026-04-26 → 2026-04-27 bump.
