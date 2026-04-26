---
id: digest-suppression
title: 디제스트 자동 묶음 및 원본 교체
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

DIGEST 로 분류된 알림 중, 사용자가 명시적으로 opt-in 한 앱에 한해 원본을 시스템 tray 에서 제거하고 대신 SmartNoti 가 게시하는 "요약+액션" replacement 알림으로 대체해서 반복 알림의 소음을 줄인다.

## Preconditions

- 알림이 DIGEST 로 분류 (→ [notification-capture-classify](notification-capture-classify.md))
- `SmartNotiSettings.suppressSourceForDigestAndSilent = true` — 전역 opt-in. **2026-04-24 부터 default = true** 이며 기존 설치자에게도 `SettingsRepository.applyPendingMigrations` 의 v1 one-shot 마이그레이션이 한 번 덮어쓴다 (의도적으로 OFF 했던 사용자는 다시 꺼야 함).
- `SmartNotiSettings.suppressedSourceApps` 의미: **빈 set = 캡처된 모든 앱이 opt-in (opt-out 의미)**. 비어있지 않으면 그 set 이 화이트리스트로 동작. 이 의미 분기는 runtime 정책 안에만 존재 — 저장된 set 은 그대로.
- 대상 알림이 protected 가 아님 (→ [protected-source-notifications](protected-source-notifications.md))

## Trigger

`processNotification(sbn)` 의 분류 결과 = DIGEST 이고 `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)` 가 true 를 반환.

## Observable steps

1. `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(...)` 가 decision=DIGEST 이고 전역 opt-in 이 켜졌으며 현재 리스트에 app 이 없으면 `currentApps + packageName` 반환. 리스너가 즉시 `settingsRepository.setSuppressedSourceApps(expanded)` 로 영속화. **`currentApps` 가 empty 인 경우는 이 단계가 skip 된다 — 빈 set 의 opt-out 의미가 이미 "모든 앱 포함" 을 보장하므로 굳이 단일 entry 로 좁힐 필요가 없음.** **사용자가 Settings 의 "숨길 앱 선택" 에서 명시적으로 uncheck 한 앱은 `suppressedSourceAppsExcluded` 에 sticky 하게 들어가 이 단계에서 자동 제외된다 — 다음 DIGEST 알림이 와도 다시 추가되지 않음.**
2. `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)` 가 확장된 리스트 기준으로 true 반환.
3. `SourceNotificationRoutingPolicy.route(DIGEST, hidePersistent=*, suppress=true)` → `cancelSourceNotification=true, notifyReplacementNotification=true`.
4. 리스너가 main thread 에서 `cancelNotification(sbn.key)` 호출 → 원본 알림 제거.
5. `SmartNotiNotifier.notifySuppressedNotification(DIGEST, ...)` 호출:
   - 채널: `ReplacementNotificationChannelRegistry.resolve(DIGEST, profile)` 가 반환하는 `smartnoti_replacement_digest_*` 중 하나
   - 제목: 원본 title (없으면 body / "{appName} 알림")
   - 본문: `ReplacementNotificationTextFormatter.explanationText(DIGEST, reasonTags)`
   - subText: "{appName} • Digest"
   - 액션: `중요로 고정`, `Digest로 유지`, `열기`
6. 사용자가 액션 탭 → `SmartNotiNotificationActionReceiver` 가 broadcast 수신 → feedback 적용 (→ [rules-feedback-loop](rules-feedback-loop.md)).
7. 사용자가 본문 탭 → `contentIntent` 가 `MainActivity` 를 열고 parent route = Digest, notification id 를 전달해 Detail 로 이동.

## Exit state

- 시스템 tray: 원본 제거, replacement 1건 잔존 (AutoCancel=true 이므로 탭 시 자동 해제).
- DB: 원본은 `status=DIGEST` 로 저장됨. `replacementNotificationIssued` 필드 true.
- 사용자가 액션으로 재분류한 경우 룰이 저장되고 status/태그 업데이트.

## Out of scope

- Silent 은 replacement 없이 요약 알림으로 처리 (→ [silent-auto-hide](silent-auto-hide.md))
- opt-in 하지 않은 앱의 DIGEST 는 원본 유지 + DB 기록만 (Digest 인박스에서 훑어봄, → digest-inbox)
- 액션 수신 이후 룰 저장 세부 (→ [rules-feedback-loop](rules-feedback-loop.md))

## Code pointers

- `notification/SourceNotificationRoutingPolicy` — DIGEST 분기
- `notification/NotificationSuppressionPolicy` — opt-in 판정
- `notification/SuppressedSourceAppsAutoExpansionPolicy` — 새 앱 자동 추가 규칙 (sticky-exclude 가드 포함)
- `ui/screens/settings/SettingsSuppressedAppPresentationBuilder` — `suppressedSourceApps` × `suppressedSourceAppsExcluded` 의 effective `isSelected` 룰 (excluded 가 우선)
- `data/settings/SettingsRepository#setSuppressedSourceAppExcluded` / `#setSuppressedSourceAppsExcludedBulk` — sticky-exclude DataStore 토글
- `notification/SmartNotiNotifier#notifySuppressedNotification` — replacement 빌더
- `notification/ReplacementNotificationTextFormatter` — 본문 포맷
- `notification/ReplacementNotificationChannelRegistry` — 채널 선택
- `notification/SmartNotiNotificationActionReceiver` — 액션 수신
- `data/settings/SettingsModels` — `suppressSourceForDigestAndSilent`, `suppressedSourceApps`, `suppressedSourceAppsExcluded`

## Tests

- `SourceNotificationRoutingPolicyTest#digest_suppression_cancels_source_and_shows_replacement`
- `NotificationSuppressionPolicyTest`
- `SuppressedSourceAppsAutoExpansionPolicyTest`
- `SmartNotiNotificationActionReceiverTest`
- `ReplacementNotificationChannelRegistryTest`
- `ReplacementNotificationTextFormatterTest`

## Verification recipe

```bash
# 0. 선결:
#    - SmartNoti debug APK 설치 + onboarding 완료 시 PROMO_QUIETING 프리셋 선택
#      (기본 키워드 룰 "쿠폰/세일/이벤트" 가 DIGEST 분기로 동작해야 함).
#    - 전역 opt-in (suppressSourceForDigestAndSilent) 은 2026-04-24 이후 default ON.
#    ※ testnotifier (com.smartnoti.testnotifier) 로 게시하면 shell ranker-group 의
#      per-package quota 와 분리되어 누적 stale 알림 걱정 없이 검증 가능.

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
TESTNOTI="${TESTNOTI:-/Users/wooil/source/SmartNotiTestNotifier}"  # 본인 환경에 맞게 export

# 1. (최초 1회) testnotifier APK 빌드 + 설치
[ -f "$TESTNOTI/app/build/outputs/apk/debug/app-debug.apk" ] \
  || (cd "$TESTNOTI" && ./gradlew :app:assembleDebug)
adb -s emulator-5554 install -r "$TESTNOTI/app/build/outputs/apk/debug/app-debug.apk"

# 2. MainActivity 진입
adb -s emulator-5554 shell am start -n com.smartnoti.testnotifier/.MainActivity
sleep 2

# 3. PROMO_DIGEST 카드의 "이 시나리오 보내기" 버튼 탭
#    좌표는 emulator skin 에 따라 다름 — 다음 한 줄로 재계산:
#      adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && \
#        adb -s emulator-5554 shell cat /sdcard/ui.xml | tr '>' '\n' \
#        | grep -B1 '프로모션 알림 1건' -A5 | grep -oE 'bounds="\[[0-9,]+\]\[[0-9,]+\]"'
#    (참고: 기본 Pixel 에뮬레이터에서는 약 301,1062.)
adb -s emulator-5554 shell input tap 301 1062
sleep 2

# 4. 원본 testnotifier 알림이 tray 에서 제거되고 SmartNoti replacement 가 새로 게시됐는지 확인
adb -s emulator-5554 shell dumpsys notification --noredact \
  | grep -E "pkg=com.smartnoti.(testnotifier|app)|smartnoti_replacement_digest" \
  | head -20
# 기대: com.smartnoti.testnotifier 의 "오늘만 특가 안내" payload 는 없고,
#       com.smartnoti.app 의 smartnoti_replacement_digest_* 채널에 새 entry 가 생김.

# 5. replacement 의 "Digest로 유지" 액션 탭 → Rules 탭에 com.smartnoti.testnotifier 룰 추가 확인

# ※ 누적 quota 문제로 SKIP 하지 않는다 — testnotifier 는 shell 과 별개 quota budget 사용.
```

## Known gaps

- (resolved 2026-04-26, plan `2026-04-26-digest-suppression-sticky-exclude-list`) Auto-expansion 은 사용자가 Settings 에서 명시적으로 비운 앱도 DIGEST 가 다시 오면 재추가했었음. 이제는 `SmartNotiSettings.suppressedSourceAppsExcluded` 가 sticky 의지를 보존하고 `SuppressedSourceAppsAutoExpansionPolicy` 가 이 set 의 멤버에 대해 항상 expansion 을 차단한다. UI 토글 OFF/ON 은 `SettingsRepository#setSuppressedSourceAppExcluded` (atomic) 를 경유하며, "모두 해제" 도 `setSuppressedSourceAppsExcludedBulk` 로 sticky 의지를 보존한다.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-20: `SuppressedSourceAppsAutoExpansionPolicy` 추가 — 전역 opt-in 이 켜졌고 DIGEST 로 분류된 새 앱이 들어오면 자동으로 `suppressedSourceApps` 확장. onboarding 이후 게시되는 "(광고)" 류 알림이 원본 유지되던 문제 해소.
- 2026-04-20: `NotificationReplacementIds.idFor` 에 notificationId 포함 — 같은 앱에서 서로 다른 내용의 DIGEST 가 동시에 게시될 때 replacement 가 서로 덮어쓰던 충돌 해소.
- 2026-04-20: Settings 의 앱 선택 리스트에 "모두 선택" / "모두 해제" OutlinedButton 추가 — 대량 opt-in / opt-out 편의. 현재 필터로 보이는 앱만 영향 (숨겨진 앱의 선택 상태는 보존).
- 2026-04-22: Verification recipe 를 `com.smartnoti.testnotifier` 의 `PROMO_DIGEST` 시나리오 경유로 재작성 — `com.android.shell` ranker-group 의 NMS per-package 50건 quota 와 누적 stale 알림 때문에 recipe 가 연속 SKIP 되던 증상 해소. SmartNoti 측 코드 변경 없음, journey recipe 블록 + Known gap bullet drainage 뿐. Plan: `docs/plans/2026-04-22-digest-suppression-testnotifier-recipe.md`. Dry-run PASS (emulator-5554): tap 후 `com.smartnoti.testnotifier` 의 "오늘만 특가 안내" payload 는 tray 에 없고 `com.smartnoti.app` 의 `smartnoti_replacement_digest_default_light_private_noheadsup` 채널에 새 entry 생성 확인.
- 2026-04-26: ADB verification PASS (emulator-5554) — testnotifier `PROMO_DIGEST` 시나리오 tap 후 `com.smartnoti.testnotifier` 의 "오늘만 특가 안내" payload 가 active list 에서 사라지고 동일 title 이 `com.smartnoti.app` 의 `smartnoti_replacement_digest_default_light_private_noheadsup` 채널에 새 entry (importance=3, AutoCancel) 로 게시됨. Observable steps 1–7 + Exit state 일치, DRIFT 없음.
- 2026-04-24: **신규/미설정 사용자에게 DIGEST/SILENT 가 이중으로 뜨던 증상 해소** — `SmartNotiSettings.suppressSourceForDigestAndSilent` 의 default 를 `true` 로 flip 하고, `NotificationSuppressionPolicy` 가 빈 `suppressedSourceApps` 를 "모든 앱 opt-in" 으로 해석하도록 변경. 기존 사용자에게는 `SettingsRepository.applyPendingMigrations` 의 v1 one-shot 마이그레이션 (`suppress_source_migration_v1_applied` DataStore 키 게이트) 로 toggle 을 한 번 덮어씀. `SuppressedSourceAppsAutoExpansionPolicy` 는 `currentApps` 가 비어있을 때 no-op 가드를 추가해 의미 전환 (opt-out → 화이트리스트-of-one) 을 막음. 관련 plan: `docs/plans/2026-04-24-duplicate-notifications-suppress-defaults-ac.md`. 커밋: d9c3ff6 / e2a472d / 8aada48 / 704dfc7.
- 2026-04-26: **사용자가 Settings 에서 명시적으로 uncheck 한 앱은 sticky 제외** — `SmartNotiSettings.suppressedSourceAppsExcluded` 신규 set 도입. `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull` 이 이 set 의 멤버에 대해 항상 expansion 차단. Settings UI 토글 OFF 는 `SettingsRepository#setSuppressedSourceAppExcluded(_, true)` (atomic — 두 set 동시 update), ON 은 excluded clear + suppressedSourceApps add. "모두 해제" 는 `setSuppressedSourceAppsExcludedBulk` 로 sticky 의지 보존. 마이그레이션 noop (default emptySet). 관련 plan: `docs/plans/2026-04-26-digest-suppression-sticky-exclude-list.md` (shipped via #395 / #396 / 본 PR).
