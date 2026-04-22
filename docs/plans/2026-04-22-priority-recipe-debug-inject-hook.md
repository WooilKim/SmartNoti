---
status: planned
---

# Priority Recipe Debug-Inject Hook + Rule-Oblivious Verification

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `journey-tester` 가 priority-inbox recipe 를 돌릴 때, 누적된 user rules (현재 emulator 의 `person:엄마 → DIGEST`, `인증번호 → SILENT` 등) 에 의해 테스트 sender 가 강등되는 현상 없이 **항상 PRIORITY 1건을 관측** 할 수 있게 한다. 사용자 입장에서 관측되는 동작은 바뀌지 않는다 (release build 에는 존재하지 않는 debug-only 경로). 결과적으로 priority-inbox journey 의 `last-verified` 가 일주일에 한 번은 실제 ADB recipe 기반으로 bump 되고, tester 가 반복적으로 SKIP 하는 상태를 끝낸다.

**Architecture:** `SmartNotiNotificationListenerService.processNotification` 은 이미 `sbn.notification.extras` 를 읽어 classifier 로 흘린다. 여기에 **debug build 에서만 해석되는 sentinel marker** (예: `extras.getString("com.smartnoti.debug.FORCE_STATUS") == "PRIORITY"`) 를 추가한다. marker 가 설정되어 있고 `BuildConfig.DEBUG == true` 이면 classifier 결과를 덮어써 `ClassificationResult(status = PRIORITY, reasonTags = ["디버그 주입"])` 로 확정. release build 는 marker 를 읽는 코드 자체가 dead-strip 되도록 `if (BuildConfig.DEBUG)` 로 감싼다. Recipe 측은 `cmd notification post -S bigtext --es com.smartnoti.debug.FORCE_STATUS PRIORITY -t <uniqueTag> <appName> <body>` 로 호출 — 누적 rule 이 어떻든 classifier 이전 단계에서 결과가 pin 된다.

**Tech Stack:** Kotlin, Android NotificationListenerService, Gradle unit tests, Bash ADB recipe, journey markdown.

---

## Product intent / assumptions

- 이 hook 은 오직 debug build 의 verification 편의를 위한 것이다. release 에는 코드 경로 자체가 존재하지 않아야 한다 (`BuildConfig.DEBUG` 가드).
- marker 이름은 SmartNoti 의 고유 namespace 를 유지 (`com.smartnoti.debug.FORCE_STATUS`) — 다른 앱이나 Android framework 가 같은 키를 우연히 세팅할 가능성은 없다.
- marker 값은 `ClassificationStatus` enum 이름 (`PRIORITY` / `DIGEST` / `SILENT` / `IGNORE`) 만 허용. 잘못된 값은 무시하고 정상 분류 경로로 fall-through.
- marker 가 적용되면 `reasonTags` 에 `"디버그 주입"` 만 세팅 — user rule / category matching 신호를 섞지 않는다. journey-tester 가 UI 에서 PRIORITY 를 확인할 때 "이 PRIORITY 는 debug-injected 가 아니라 실제 classifier 경로" 인지 헷갈리지 않게 한다.
- Recipe 는 `PRIORITY` marker 를 최소한의 hook 으로만 사용하고, DIGEST/SILENT 검증 recipe 는 기존 경로 (실제 분류) 를 그대로 쓴다. 즉 이 hook 은 priority-inbox recipe 전용이며, 다른 journey 가 "쉬운 분류 pin" 으로 남용하지 않는 것이 의도.
- **Open question for user (Risks 섹션에 남김):** hook 을 `BuildConfig.DEBUG` 로만 막을지, 또는 `SettingsRepository` 에 별도 `debugInjectionEnabled` 플래그를 둘지. 전자는 구현이 얕고, 후자는 QA 빌드에서도 off 가능하지만 runtime 스위치만큼 추가 UI 가 필요.

---

## Task 1: Failing unit test for debug-inject override

**Objective:** marker 가 있으면 classifier 결과를 덮어쓰고, 없으면 그대로 통과시키는 계약을 단위 테스트로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/notification/DebugClassificationOverrideTest.kt`
- (제안) 신규 policy 객체: `app/src/main/java/com/smartnoti/app/notification/DebugClassificationOverride.kt` — `object DebugClassificationOverride { fun resolve(extras: Bundle, fallback: ClassificationResult): ClassificationResult }` 형태 (또는 `Bundle?.forcedStatus()` extension).

**Steps:**
1. marker 키 상수 / 허용 enum 값 목록 확인.
2. 테스트 케이스:
   - marker 없음 → fallback 을 그대로 반환.
   - marker = `"PRIORITY"` → 반환값의 `status == PRIORITY` 이고 `reasonTags == listOf("디버그 주입")`.
   - marker = `"digest"` (소문자) → case 처리 결정 (대문자만 허용 권장) + 테스트.
   - marker = `"BOGUS"` → fallback 유지.
3. 테스트는 현재 구현 부재 상태에서 빨간불이어야 함.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.DebugClassificationOverrideTest"` 로 실패 확인.

## Task 2: Implement the override policy

**Objective:** Task 1 테스트를 초록으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/DebugClassificationOverride.kt`

**Steps:**
1. pure Kotlin object 또는 function — Android Bundle 의존도 최소화 (테스트에서 mocking 하기 쉽게). 필요하다면 테스트에서 Robolectric 없이 `Bundle` 을 직접 생성 (이미 Robolectric 포함이므로 허용).
2. allowed status 집합을 enum values 로 고정.
3. `reasonTags` 를 `listOf("디버그 주입")` 하드코딩 (i18n 은 verification marker 라 무관).
4. 전체 테스트 실행 — 기존 분류 테스트 깨지는 것 없는지 확인.

## Task 3: Wire into processNotification behind BuildConfig.DEBUG

**Objective:** listener 가 실제 알림을 처리할 때 marker 가 있으면 classifier 결과를 override.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- 기존 classifier 호출 site (현재 `Classifier` / `NotificationCapturePolicy` 근처). 정확한 위치는 구현 시 `processNotification` 본문 안 classification 직후.

**Steps:**
1. classifier 가 `ClassificationResult` 를 반환한 직후에 `if (BuildConfig.DEBUG) DebugClassificationOverride.resolve(extras, result) else result` 로 감싼다.
2. release build 에서는 `BuildConfig.DEBUG == false` 로 상수 폴딩되어 override 호출 자체가 dead code — R8 이 제거.
3. 빌드 확인: `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease` 둘 다 성공.

## Task 4: Update priority-inbox verification recipe

**Objective:** recipe 가 더 이상 user rule 누적에 취약하지 않도록 marker 기반으로 재작성.

**Files:**
- `docs/journeys/priority-inbox.md` — Verification recipe 섹션 갱신. Known gap 의 2026-04-22 항목을 "→ plan: docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md" annotation 으로 보강 (bullet 원문은 유지).

**Steps:**
1. Recipe step 1 을 다음처럼 변경:
   ```bash
   # Unique tag + debug-inject marker 로 rule 무관하게 PRIORITY 확정
   UNIQ="PriDbg$(date +%s%N | tail -c 6)"
   adb -s emulator-5554 shell cmd notification post \
     -S bigtext \
     --es com.smartnoti.debug.FORCE_STATUS PRIORITY \
     -t "$UNIQ" TestApp "검토 대기 시드"
   ```
2. 나머지 step (tray 원본 유지 확인, Home 카드 진입, 인라인 재분류) 은 그대로.
3. Known gap 원문 ("2026-04-22: 위 fragility 가 ...") 은 절대 수정하지 않고, 그 아래 줄에 `→ plan: docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md` 만 추가.

## Task 5: Journey Change log + sync across related journeys

**Objective:** 다른 journey 가 이 hook 의 존재를 알고 오남용하지 않게.

**Files:**
- `docs/journeys/priority-inbox.md` — Change log 에 2026-04-22 entry: "Debug-only `FORCE_STATUS` extras marker 추가 (BuildConfig.DEBUG 하에서만 classifier 결과 override). Recipe 를 이 marker 기반으로 재작성해 누적 user rule 의 영향을 받지 않게 함. Plan: docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md". `last-verified` 는 ADB 검증 완료 후 bump.
- `docs/journeys/notification-capture-classify.md` — Known gaps 또는 Code pointers 에 짧게 "debug-only classifier override (`DebugClassificationOverride`, BuildConfig.DEBUG 한정) 존재" 한 줄 추가.

## Task 6: End-to-end ADB verification + last-verified bump

**Objective:** 실제 emulator 에서 recipe 가 SKIP 없이 PASS 하는지 관측.

**Steps:**
```bash
# Fresh APK 설치
cd /Users/wooil/source/SmartNoti
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# Unique tag + marker 로 시드
UNIQ="PriDbg$(date +%s%N | tail -c 6)"
adb -s emulator-5554 shell cmd notification post \
  -S bigtext \
  --es com.smartnoti.debug.FORCE_STATUS PRIORITY \
  -t "$UNIQ" TestApp "검토 대기 시드"

# DB 에서 status=PRIORITY + reasonTags 에 "디버그 주입" 확인
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat databases/smartnoti.db > /tmp/check.db
python3 -c "import sqlite3; c=sqlite3.connect('/tmp/check.db').cursor(); \
  [print(r) for r in c.execute(\"select status,reasonTags from notifications where title like '$UNIQ%'\")]"

# App 진입 → Home passthrough 카드 → PriorityScreen 에 카드 1건 + 인라인 액션 확인 (스크린샷)
```
결과가 맞으면 `docs/journeys/priority-inbox.md` 의 `last-verified:` 를 오늘 날짜로 bump + Change log 에 "v1 loop tick re-verify (PASS via debug-inject marker)" 라인 추가.

## Task 7: Self-review + PR

- `./gradlew :app:testDebugUnitTest` 전체 PASS.
- `./gradlew :app:assembleRelease` PASS (dead-code stripping 동작).
- PR 제목: `fix(priority-inbox): debug-only FORCE_STATUS hook + recipe rewrite`.
- PR 본문에 ADB 검증 스크린샷/덤프 첨부.

---

## Scope

**In:**
- 새 policy 클래스 `DebugClassificationOverride` + 단위 테스트.
- Listener 에 `BuildConfig.DEBUG` 가드 override 호출.
- priority-inbox recipe 재작성 + journey Change log 동기화.
- `notification-capture-classify.md` 에 hook 존재 한 줄 주석.

**Out:**
- Runtime UI 토글 (`debugInjectionEnabled` Settings 필드) — Risks 에 open question 으로 남김.
- DIGEST / SILENT / IGNORE 전용 recipe 개조 — 이 plan 은 priority 만 대상.
- `cmd notification post` 외의 테스트 앱 (SmartNotiTestNotifier) 확장 — 별개 plan 으로.
- production classifier 변경 — classifier 로직은 touch 하지 않는다.

---

## Risks / open questions

- **사용자 결정 필요:** hook 을 `BuildConfig.DEBUG` guard 만으로 막을지, 별도 runtime flag 로 이중화할지. 기본 제안은 BuildConfig guard 단일 — release APK 에서 marker 가 있는 알림이 와도 dead-stripped. 반대 의견이 있으면 Task 3 전에 알려주기.
- **Marker 키 이름 충돌:** `com.smartnoti.debug.FORCE_STATUS` 는 SmartNoti namespace 하위이므로 외부 앱이 우연히 세팅할 가능성은 거의 0. 필요 시 extras 외에 특정 category 조합을 AND 조건으로 추가 검증 가능.
- **reasonTags 표기:** `"디버그 주입"` 을 쓰면 사용자가 debug build 에서 해당 알림을 볼 때 Detail 화면의 "왜 이렇게 분류됐는지" 설명에 이 문자열이 노출됨. Debug build 전용 문구라 문제 없지만, `debug-only` prefix 로 명시할지 구현 시 결정.
- **Other journeys 에 recipe drift 가 비슷하게 쌓일 여지:** digest-suppression (shell-quota), home-uncategorized-prompt (3-package 요구) 는 각자 별도 plan 필요. 이 plan 은 priority 만 해결.

---

## Related journey

- `docs/journeys/priority-inbox.md` — 해당 Known gap 의 2026-04-22 entry 가 이 plan 으로 drainage 됨. Shipping 시 Known gap 의 해당 bullet 은 Change log 항목으로 이동하고, `last-verified` 는 실제 ADB 검증 날짜로 bump.
- 참조: `docs/journeys/notification-capture-classify.md` (classifier 경로 인접).
