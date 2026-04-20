---
id: onboarding-bootstrap
title: 첫 온보딩 및 기존 알림 부트스트랩
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
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
9. `ActiveStatusBarNotificationBootstrapper.bootstrap(activeNotifications)` 가 현재 tray 에 존재하는 `StatusBarNotification[]` 을 순회하며, SmartNoti 자체 알림을 제외하고 하나씩 `processNotification` 호출 (→ [notification-capture-classify](notification-capture-classify.md)).

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
- `data/settings/SettingsRepository#setOnboardingCompleted`
- `notification/SmartNotiNotificationListenerService#enqueueOnboardingBootstrapCheck`

관련 plan: `docs/plans/2026-04-19-onboarding-active-notification-bootstrap.md`, `docs/plans/2026-04-18-onboarding-quick-start-recommendations.md`, `docs/plans/2026-04-20-onboarding-quick-start-auto-suppression.md`.

## Tests

- `OnboardingQuickStartPresetBuilderTest`
- `OnboardingQuickStartRuleApplierTest`
- `OnboardingQuickStartSettingsApplierTest`
- `OnboardingActiveNotificationBootstrapTest`

## Verification recipe

```bash
# 1. 앱 데이터 초기화 → 온보딩 미완료 재현
adb shell pm clear com.smartnoti.app

# 2. 기존 알림 몇 건 미리 쌓기 (bootstrap 대상)
adb shell cmd notification post -S bigtext -t "Promo" Sample1 "광고 배너입니다"
adb shell cmd notification post -S bigtext -t "Bank"  Sample2 "인증번호 000000"

# 3. 앱 실행 → 권한 허용 → quick-start 하나 선택 → 시작
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. 온보딩 완료 후 Priority/Digest 탭에 기존 알림이 분류돼 보이는지 확인
#    (Sample2 는 "인증번호" 키워드로 PRIORITY, Sample1 은 prom_quieting 룰 기준으로 DIGEST/SILENT)
```

## Known gaps

- Bootstrap 은 첫 완료 시 1회만 실행. 이후 사용자가 앱 데이터를 보존한 채 리스너 권한을 토글해도 재실행되지 않음.
- 시스템 tray 가 비어 있는 상태로 온보딩이 완료되면 bootstrap 은 아무 것도 하지 않고 consume 됨.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
