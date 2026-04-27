---
status: shipped
shipped: 2026-04-27
superseded-by: docs/journeys/onboarding-bootstrap.md
---

# Onboarding-Bootstrap Non-Destructive Verification Sub-Recipe (debug-only rehearsal hook)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `journey-tester` 가 `onboarding-bootstrap` journey 를 매 rotation tick 에 실제로 PASS / FAIL 로 분류할 수 있게, `pm clear` 같은 파괴적 전제 (앱 데이터 + NotificationListener 권한 grant 일괄 wipe) 없이도 **bootstrap 파이프라인 자체** (active StatusBarNotification → `ActiveStatusBarNotificationBootstrapper.bootstrap` → `processNotification` → repository upsert) 가 정확히 한 번 실행됐고 dedup Set 이 `recordProcessedByBootstrap` 로 채워졌는지를 관측 가능한 신호로 확인할 수 있는 debug-build sub-recipe 를 만든다. 사용자 입장에서 관측되는 production 동작은 변하지 않는다 (release APK 에는 hook 자체가 dead-stripped). 결과적으로 `onboarding-bootstrap` 의 `last-verified` 가 더 이상 매 sweep 마다 SKIP 으로 정체되지 않는다 (2026-04-26, 2026-04-27 sweep 가 같은 사유로 SKIP 처리된 패턴 종결).

**Architecture:** 기존 debug source set (`app/src/debug/java/com/smartnoti/app/debug/`) 는 이미 `DebugInjectNotificationReceiver` 를 통해 "verification-only manifest entry + BroadcastReceiver pattern" 을 정착시켜 두었다 (PR #273, `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`). 같은 패턴을 따라 **`DebugBootstrapRehearsalReceiver`** (가칭) 을 추가한다. 이 receiver 가 받으면 (a) `SettingsRepository` 의 onboarding bootstrap pending flag 를 강제로 raise → (b) `OnboardingActiveNotificationBootstrapCoordinator.requestBootstrapForFirstOnboardingCompletion()` 또는 listener 의 `triggerOnboardingBootstrapIfConnected()` 를 호출해 동일 production 코드 경로를 다시 한 번 통과시킨다. (c) tester 가 관측할 수 있도록 처리 결과를 logcat 의 안정적인 태그 + count 로 emit (e.g. `Log.i("BootstrapRehearsal", "processed=N skipped=M reason=…")`). `BuildConfig.DEBUG` 가드 + debug-only manifest entry 로 release 에는 코드 자체가 들어가지 않는다.

**Tech Stack:** Kotlin, Android NotificationListenerService, BroadcastReceiver, Robolectric unit test, Bash ADB recipe, journey markdown.

---

## Product intent / assumptions

- 이 rehearsal hook 은 오직 debug build 에서 verification 목적이다. release APK 에 들어가지 않아야 한다 (`BuildConfig.DEBUG` + `app/src/debug/AndroidManifest.xml` 한정 manifest entry).
- "Bootstrap 이 이미 한 번 consume 됐다" 는 production 1회성 계약은 **유지**. rehearsal hook 은 그 계약을 위반하지 않고, 별도 debug entry point 로 동일 함수 (`ActiveStatusBarNotificationBootstrapper.bootstrap`) 를 강제 호출하는 형태. production flag 의 첫 consume 은 그대로.
- 관측 신호는 두 가지 중 하나로 결정 (open question — Risks 섹션 참조):
  - (A) logcat 안정 태그 (`adb logcat -d -s BootstrapRehearsal`) 한 줄 — 가장 가벼움
  - (B) DataStore 의 별도 debug-only counter key (`debug_last_rehearsal_processed_count`) — 영속적이라 dump 가능
- Recipe 가 검증할 항목 3개:
  1. tray 에 미리 쌓인 `cmd notification post` N건이 동일 dedup key 로 `processNotification` 을 정확히 N회 호출했는지
  2. 같은 호출이 `ListenerReconnectActiveNotificationSweepCoordinator.recordProcessedByBootstrap` 으로 dedup Set 에 N건 모두 기록됐는지 (sweep 의 후속 no-op 보장)
  3. SmartNoti 자체 알림은 `shouldProcess` 에서 제외됐는지 (false positive 없음)
- 이 plan 은 **production 동작 변경 0** — `OnboardingActiveNotificationBootstrap*` / `SettingsRepository` 의 production 메소드 시그니처는 건드리지 않는다. 필요한 noise 는 모두 debug source set 안에 격리.
- **Open question for user (Risks 섹션 참조):** rehearsal hook 호출 시 production 의 "1회성 consume" 계약을 어떻게 표현할지 — flag 를 일시적으로 raise → 직후 강제 consume 하는 round-trip 이 깔끔하지만, 만약 rehearsal 도중 시스템이 갑자기 죽으면 다음 cold-launch 가 의도치 않게 bootstrap 을 한 번 더 돌릴 위험이 미세하게 있다. 안전한 단순 우회는 `Bootstrapper` 를 직접 (flag 무시하고) 호출하는 것.

---

## Task 1: Failing unit/Robolectric test for rehearsal entry point [IN PROGRESS via PR #422]

**Objective:** rehearsal entry point 가 (a) `BuildConfig.DEBUG` 일 때만 의미 있는 일을 하고, (b) production bootstrap 1회성 flag 의 raised/consumed 상태와 무관하게 `ActiveStatusBarNotificationBootstrapper.bootstrap()` 을 정확히 한 번 호출한다는 계약을 단위 테스트로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/debug/DebugBootstrapRehearsalReceiverTest.kt` (또는 `app/src/testDebug/...` — debug source set 에 한정).
- (제안 신규) `app/src/debug/java/com/smartnoti/app/debug/DebugBootstrapRehearsal.kt` — pure Kotlin object, Android `BroadcastReceiver` 의존도 최소화 (test 에서 호출하기 쉽게).

**Steps:**
1. fake `ActiveStatusBarNotificationBootstrapper` (call counter) + fake `SettingsRepository` 주입.
2. 테스트 케이스:
   - rehearsal trigger 호출 → bootstrapper.bootstrap 정확히 1회 호출.
   - production flag 가 raised 상태든 consumed 상태든 동일하게 호출.
   - active StatusBarNotification 0건일 때도 함수가 던지지 않고 `processed=0` 신호 emit.
3. 테스트는 구현 부재 상태에서 빨간불.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.debug.DebugBootstrapRehearsalReceiverTest"` 로 실패 확인.

## Task 2: Implement rehearsal entry point + receiver [IN PROGRESS via PR #422]

**Objective:** Task 1 테스트 초록. Production 코드 경로는 변경 0.

**Files:**
- `app/src/debug/java/com/smartnoti/app/debug/DebugBootstrapRehearsal.kt` (entry-point object).
- `app/src/debug/java/com/smartnoti/app/debug/DebugBootstrapRehearsalReceiver.kt` (BroadcastReceiver thin wrapper).
- `app/src/debug/AndroidManifest.xml` — 신규 receiver 등록 (intent-filter `com.smartnoti.debug.REHEARSE_BOOTSTRAP`).

**Steps:**
1. receiver 가 `am broadcast -a com.smartnoti.debug.REHEARSE_BOOTSTRAP -p com.smartnoti.app` 를 받으면 entry-point 호출.
2. entry-point 는 listener 가 connected 일 때 `ActiveStatusBarNotificationBootstrapper.bootstrap(activeNotifications)` 를 직접 호출. `SweepDedupKey` 도 같은 production 경로 (`recordProcessedByBootstrap`) 를 통해 채움.
3. 결과를 logcat 안정 태그 (`Log.i("BootstrapRehearsal", "processed=$n skipped=$s")`) 로 emit.
4. (선택, open question) DataStore 에 `debug_last_rehearsal_processed_count` 도 함께 기록 — verification recipe 가 `run-as` + DataStore dump 로 확인.

## Task 3: BuildConfig.DEBUG guard + release dead-strip 확인 [DONE]

**Objective:** release APK 에는 rehearsal 코드가 일절 포함되지 않게.

**Files:**
- `app/src/debug/AndroidManifest.xml` (이미 debug-only — 추가 가드 불필요).
- 필요 시 `SmartNotiNotificationListenerService.kt` 안에 rehearsal 호출 site 가 생긴다면 `if (BuildConfig.DEBUG)` 가드.

**Steps:**
1. `./gradlew :app:assembleRelease` PASS — R8 mapping 에 `DebugBootstrapRehearsal*` 클래스가 없는지 확인 (`app/build/outputs/mapping/release/mapping.txt` 검색).
2. `./gradlew :app:assembleDebug` PASS.

## Task 4: Update onboarding-bootstrap journey verification recipe [DONE]

**Objective:** journey 의 Verification recipe 가 destructive `pm clear` 만 가지지 않고, **non-destructive sub-recipe** 도 함께 노출. journey-tester 의 default rotation 은 sub-recipe 를 쓴다.

**Files:**
- `docs/journeys/onboarding-bootstrap.md` — Verification recipe 섹션에 두 번째 하위 절 "Non-destructive bootstrap rehearsal (debug build)" 추가. 기존 destructive recipe 는 절대 삭제하지 않음 (release-cycle 수동 검증용).

**Steps:**
1. 새 sub-recipe 절 추가:
   ```bash
   # 1. 이미 onboarded 된 emulator 에서 tray 에 sample 알림 N건 게시
   adb -s emulator-5554 shell cmd notification post -S bigtext -t Bank Sample1 "인증번호 000000"
   adb -s emulator-5554 shell cmd notification post -S bigtext -t Promo Sample2 "광고 배너입니다"

   # 2. rehearsal trigger
   adb -s emulator-5554 shell am broadcast \
     -a com.smartnoti.debug.REHEARSE_BOOTSTRAP \
     -p com.smartnoti.app

   # 3. 결과 관측
   adb -s emulator-5554 logcat -d -s BootstrapRehearsal | tail
   #   기대: "processed=2 skipped=0" (또는 SmartNoti 자체 알림 수만큼 skipped)

   # 4. DB 에 두 알림 row 가 status 분류된 채 저장됐는지 확인
   #    (dedup 이 잘 동작했다면 같은 dedup key 로 동일 알림이 두 번 들어가지 않음)
   ```
2. 절 머리에 "production bootstrap 의 1회성 consume 계약은 변하지 않음 (rehearsal 은 별도 debug entry point)" 명시.
3. Known gap "시스템 tray 가 비어 있는 상태로 온보딩이 완료되면 …" / "리스너가 꺼져 있는 동안 시스템이 이미 dismiss 한 알림은 …" 두 줄은 그대로 유지.
4. Known gap 의 "destructive recipe — 자동화 SKIP" 사유에 한 줄 plan 링크 (`→ plan: docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`) 만 annotate. bullet 원문은 수정 금지.

## Task 5: Journey Change log + cross-references [DONE]

**Objective:** sweep 자동화가 새 sub-recipe 를 안다는 것을 명시 + sweep 결과를 첫 verify 로 기록.

**Files:**
- `docs/journeys/onboarding-bootstrap.md` — Change log 에 오늘 날짜 entry: "Debug-only rehearsal hook (`DebugBootstrapRehearsalReceiver`) 추가. Verification recipe 에 non-destructive sub-recipe 절 추가. Plan: `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`. `last-verified` 는 ADB 검증 완료 후 bump."
- `docs/journeys/notification-capture-classify.md` — Code pointers 에 짧게 "debug-only bootstrap rehearsal entry (`DebugBootstrapRehearsal`, BuildConfig.DEBUG 한정) 존재" 한 줄 추가 (오남용 방지 신호).

## Task 6: End-to-end ADB verification + last-verified bump [DONE]

**Objective:** 실제 emulator 에서 sub-recipe 가 PASS 로 분류되는지 관측.

**Steps:**
```bash
# Fresh debug APK
cd /Users/wooil/source/SmartNoti
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# 미리 tray 에 2건 시드
adb -s emulator-5554 shell cmd notification post -S bigtext -t Bank "Reh$(date +%s%N | tail -c 6)" "인증번호 000000"
adb -s emulator-5554 shell cmd notification post -S bigtext -t Promo "Reh$(date +%s%N | tail -c 6)" "광고 배너"

# rehearsal trigger
adb -s emulator-5554 shell am broadcast \
  -a com.smartnoti.debug.REHEARSE_BOOTSTRAP \
  -p com.smartnoti.app

# logcat + DB 양쪽 검증
adb -s emulator-5554 logcat -d -s BootstrapRehearsal | tail
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat databases/smartnoti.db > /tmp/check.db
python3 -c "import sqlite3; c=sqlite3.connect('/tmp/check.db').cursor(); \
  [print(r) for r in c.execute(\"select status,title from notifications where title like 'Reh%'\")]"
```
결과가 맞으면 `docs/journeys/onboarding-bootstrap.md` 의 `last-verified:` 를 오늘 (시스템 `date -u`) 날짜로 bump + Change log 에 "Non-destructive sub-recipe first PASS" 라인 추가.

## Task 7: Self-review + PR [DONE]

- `./gradlew :app:testDebugUnitTest` 전체 PASS.
- `./gradlew :app:assembleRelease` PASS (rehearsal dead-stripped).
- PR 제목: `feat(onboarding): debug-only bootstrap rehearsal hook + non-destructive sub-recipe`.
- PR 본문에 ADB 검증 logcat + DB dump 첨부.

---

## Scope

**In:**
- 새 debug-only entry point `DebugBootstrapRehearsal` + `DebugBootstrapRehearsalReceiver` + 단위 테스트.
- Debug AndroidManifest 에 receiver 등록.
- `onboarding-bootstrap` journey 의 Verification recipe 에 non-destructive sub-recipe 절 추가 + Change log + Code pointers 인접 cross-link.
- ADB 검증 + `last-verified` 한 차례 bump.

**Out:**
- Production bootstrap 1회성 계약 변경 (의도적으로 유지).
- DataStore 에 별도 debug counter key 추가 (Risks 의 open question — 결정 후 별 plan).
- Reconnect sweep 의 별도 rehearsal hook (필요 시 후속 plan, 본 plan 은 bootstrap path 만).
- `cmd notification post` 가 `FLAG_ONGOING_EVENT` 등 특수 flag 지원 못 하는 한계 우회 (별 journey).

---

## Risks / open questions

- **사용자 결정 필요:** rehearsal 결과 관측 신호로 (A) logcat 태그 / (B) DataStore counter key 중 어느 쪽을 쓸지. 기본 제안은 (A) — 가벼움 + 로그가 사라져도 production 영향 0. (B) 로 가면 Settings dump 까지 verify 항목이 늘어남.
- **Production 1회성 flag vs. rehearsal 호출 충돌:** rehearsal 이 production flag 를 일시 raise 하지 않고 `Bootstrapper.bootstrap()` 을 직접 호출하면, production cold-launch 의 첫 consume 경로와 별도로 동작. 이 단순 우회를 기본 설계로 채택. raise → consume round-trip 형태로 가면 안전성은 높지만 production race 노출 risk 미세 증가.
- **Manifest 충돌:** debug source set 에 receiver 가 한 개 더 추가됨. 기존 `DebugInjectNotificationReceiver` 와 intent action 이 다르므로 충돌 없음 (`com.smartnoti.debug.INJECT_NOTIFICATION` vs `com.smartnoti.debug.REHEARSE_BOOTSTRAP`).
- **Recipe 안에서 sender count 검증:** `cmd notification post` 가 `pkg=com.android.shell` 단일이라 dedup Set 의 cardinality 검증은 같은 pkg 안의 다른 tag 로만 가능. 별 sender 분포 검증이 필요하면 SmartNotiTestNotifier 시드 단계 추가 (out of scope, 별 plan).
- **journey-tester 안전 규칙:** rehearsal hook 도 broadcast trigger 라 `am broadcast` 권한이 필요. emulator 에서는 무관, 실기기에서는 debug APK 만 가능. release 사이클 수동 검증은 여전히 destructive recipe 로.

---

## Related journey

- `docs/journeys/onboarding-bootstrap.md` — Known gap "destructive recipe — 자동화 SKIP" 사유가 이 plan 으로 drainage 됨. Shipping 시 해당 bullet 옆 plan 링크는 Change log 항목으로 이동하고, `last-verified` 는 실제 ADB sub-recipe PASS 날짜로 bump.
- 참조: `docs/journeys/notification-capture-classify.md` (bootstrap 이 호출하는 `processNotification` 경로 — 같은 capture pipeline).
- 참조: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md` (debug-build verification entry point 의 선례).
