---
id: onboarding-bootstrap
title: 첫 온보딩 및 기존 알림 부트스트랩
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

최초 앱 진입 시 필요한 권한을 받고, 사용자가 고른 quick-start 추천을 즉시 규칙/설정으로 적용하고, 알림센터에 **이미 쌓여 있던** 알림까지 동일한 분류 파이프라인으로 한 번에 처리해 앱 진입 직후 바로 쓸 만한 상태를 만든다.

## Preconditions

- `SettingsRepository.observeOnboardingCompleted()` 값이 `false` (DataStore 기준)
- `AppNavHost` 가 `startDestination = Routes.Onboarding.route` 로 구성됨

## Trigger

앱 콜드 런치 → `AppNavHost` 가 onboarding 미완료를 감지해 `OnboardingScreen` 을 표시.

## Observable steps

1. `OnboardingScreen` 렌더링, 내부 state = `PERMISSIONS`.
2. 사용자가 "알림 접근 허용" 탭 → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` intent 로 이동.
3. 사용자가 POST_NOTIFICATIONS 권한 요청 수락 (Android 13+).
4. `OnboardingPermissions.currentStatus(context)` 재평가 → `allRequirementsMet = true` 이면 state → `QUICK_START`.
5. 사용자가 `PROMO_QUIETING / REPEAT_BUNDLING / IMPORTANT_PRIORITY` 중 하나 이상 선택.
6. "이대로 시작할게요" 탭:
   1. `OnboardingQuickStartSettingsApplier.applySelection()` — DataStore 플래그 업데이트
   2. `OnboardingQuickStartRuleApplier` — `RulesRepository` 에 프리셋 룰 삽입
   3. `SettingsRepository.setOnboardingCompleted(true)` — 온보딩 완료 플래그 저장
   4. `OnboardingActiveNotificationBootstrapCoordinator.requestBootstrapForFirstOnboardingCompletion()` — bootstrap 요청 플래그 영속화 (1회성)
7. `AppNavHost` 가 Home 라우트로 navigate, `popUpTo(Onboarding) { inclusive = true }`.
8. `SmartNotiNotificationListenerService.triggerOnboardingBootstrapIfConnected()` 가 즉시 bootstrap 을 시도. 서비스가 아직 바인딩 전이면 다음 `onListenerConnected` 콜백에서 `enqueueOnboardingBootstrapCheck()` 이 처리.
9. `ActiveStatusBarNotificationBootstrapper.bootstrap(activeNotifications)` 가 현재 tray 에 존재하는 `StatusBarNotification[]` 을 순회하며, SmartNoti 자체 알림을 제외하고 하나씩 `processNotification` 호출 (→ [notification-capture-classify](notification-capture-classify.md)). 각 처리 직전에 `reconnectSweepCoordinator.recordProcessedByBootstrap(dedupKeyFor(sbn))` 로 dedup Set 에 먼저 기록 — 같은 reconnect 에서 이어 돌 sweep 이 중복 처리하지 않게 한다.
10. 같은 `onListenerConnected` 콜백이 bootstrap 뒤에 `enqueueReconnectSweep()` 을 호출. 이 sweep 은 **매 재접속마다** 실행되며, 이미 저장된 알림은 `NotificationRepository.existsByContentSignature` + in-process `SweepDedupKey` Set 으로 skip 하고, 리스너가 꺼져 있는 동안 tray 에 쌓였다가 살아남은 미처리분만 `processNotification` 경로로 재주입한다. 온보딩 bootstrap pending flag 가 아직 raised 이면 sweep 은 no-op (bootstrap 이 첫 번째 pass 를 소유).

## Exit state

- `observeOnboardingCompleted()` 가 영구적으로 `true`.
- RulesRepository 에 quick-start 프리셋 룰 저장됨.
- Home/Priority/Digest/Hidden 탭에 기존 알림들이 분류 결과로 즉시 보임.
- bootstrap 요청 플래그는 consume 되어 재실행되지 않음.

## Out of scope

- 이후 새로 게시되는 알림 처리 (→ [notification-capture-classify](notification-capture-classify.md))
- 룰 관리/편집 UI (→ rules-management)
- Bootstrap 의 2차 재실행 (의도적으로 1회만 수행)
- Quick-start 선택을 되돌리는 UI (현재 Settings 의 개별 토글로만 가능)

## Code pointers

- `ui/screens/onboarding/OnboardingScreen` — step 관리 composable
- `onboarding/OnboardingPermissions` — 권한 상태 snapshot
- `ui/screens/onboarding/OnboardingQuickStartSettingsApplier`
- `ui/screens/onboarding/OnboardingQuickStartRuleApplier`
- `notification/OnboardingActiveNotificationBootstrapCoordinator`
- `notification/OnboardingActiveNotificationBootstrap` (`ActiveStatusBarNotificationBootstrapper`)
- `notification/ListenerReconnectActiveNotificationSweepCoordinator` — reconnect sweep 파트너, `SweepDedupKey` 기준 dedup
- `data/settings/SettingsRepository#setOnboardingCompleted`, `#isOnboardingBootstrapPending`
- `data/local/NotificationRepository#existsByContentSignature` — sweep 의 existsInStore hook
- `notification/SmartNotiNotificationListenerService#enqueueOnboardingBootstrapCheck`, `#enqueueReconnectSweep`

관련 plan: `docs/plans/2026-04-19-onboarding-active-notification-bootstrap.md`, `docs/plans/2026-04-18-onboarding-quick-start-recommendations.md`, `docs/plans/2026-04-20-onboarding-quick-start-auto-suppression.md`, `docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md`.

## Tests

- `OnboardingQuickStartPresetBuilderTest`
- `OnboardingQuickStartRuleApplierTest`
- `OnboardingQuickStartSettingsApplierTest`
- `OnboardingActiveNotificationBootstrapTest`
- `ListenerReconnectActiveNotificationSweepTest`
- `ListenerReconnectSweepRepositoryIntegrationTest`
- `NotificationRepositoryExistsByContentSignatureTest`

## Verification recipe

⚠️ `pm clear` 는 **기기에 누적된 앱 상태를 전부 지웁니다** — 테스트 기기에서만 실행.
실행 후 NotificationListener 권한도 초기화되므로 Settings 에서 재허용해야 합니다.

```bash
# 1. 앱 데이터 초기화 → 온보딩 미완료 재현
adb shell pm clear com.smartnoti.app

# 2. 기존 알림 몇 건 미리 쌓기 (bootstrap 대상)
adb shell cmd notification post -S bigtext -t "Promo" Sample1 "광고 배너입니다"
adb shell cmd notification post -S bigtext -t "Bank"  Sample2 "인증번호 000000"

# 3. 앱 실행 → 알림 접근 권한 재허용 → POST_NOTIFICATIONS 허용 → quick-start 선택 → 시작
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. 온보딩 완료 후 Priority/Digest 탭에 기존 알림이 분류돼 보이는지 확인
#    (Sample2 는 "인증번호" 키워드로 PRIORITY, Sample1 은 prom_quieting 룰 기준으로 DIGEST/SILENT)

# 5. 다시 bootstrap 이 재실행되지 않는지 확인 — 앱 재시작해도 중복 캡처 없어야 함
```

## Known gaps

- Onboarding bootstrap 플래그 자체는 여전히 1회성 (consume 후 재실행되지 않음). 단, 매 `onListenerConnected` 에서 `enqueueReconnectSweep` 이 함께 돌기 때문에, 사용자가 앱 데이터를 보존한 채 리스너 권한을 토글하면 tray 에 남은 미처리 알림은 sweep 이 소급 캡처한다. 실제 "bootstrap 을 재실행" 하는 것은 아니고 누락 메움만 제공.
- 시스템 tray 가 비어 있는 상태로 온보딩이 완료되면 bootstrap 은 아무 것도 하지 않고 consume 됨. 이후 sweep 도 tray 가 비어 있는 한 no-op.
- 리스너가 꺼져 있는 동안 시스템이 이미 dismiss 한 알림은 `activeNotifications` 에 남지 않아 bootstrap / sweep 모두 복구할 수 없음.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Reconnect sweep 경로 추가 문서화. `onListenerConnected` 가 매번 `enqueueOnboardingBootstrapCheck` → `enqueueReconnectSweep` 순으로 발화하며, bootstrapper 가 처리한 `SweepDedupKey` 를 sweep coordinator 에 기록해 중복 처리를 막는다. 권한 토글 등 일반 재접속에서도 tray 에 남은 미처리 알림이 메꿔진다 (plan `docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md`, PR #94 / #102 / #104)
- 2026-04-22: **Launch crash 해소** — Categories Phase P1 이후 `LegacyRuleActionReader` 가 `preferencesDataStore("smartnoti_rules")` delegate 를 두 번 선언해 AndroidX 가 앱 기동 즉시 `IllegalStateException: There are multiple DataStores active for the same file` 로 크래시했고, 그 결과 `MigrateRulesToCategoriesRunner` 와 bootstrap 경로 전체가 실행 전에 사망. Fix: `RulesRepository` 를 단일 DataStore 소유자로 두고 `LegacyRuleActionReader` 가 그 handle 을 생성자 주입으로 받는다. Cold-launch 시 migration runner 가 크래시 없이 정상 수행 → bootstrap 재개. Robolectric regression (`RulesDataStoreSingleOwnerTest`). Plan: `docs/plans/2026-04-22-rules-datastore-dedup-fix.md` (this PR). `last-verified` 변경 없음 — onboarding recipe 전체 재실행은 별도 sweep 에서.
