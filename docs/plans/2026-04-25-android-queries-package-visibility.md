---
status: planned
---

# Android `<queries>` Package Visibility for App Label Lookup

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. UI surface change is a one-line manifest edit; the bulk of the diff is a failing test + journey doc sync. Do NOT touch listener-side label lookup (it already works via implicit notification-access visibility).

**Goal:** Categories 화면의 condition chip 이 더 이상 raw `com.android.youtube` 같은 packageName 을 노출하지 않고, 사용자가 인지할 수 있는 앱 이름 ("YouTube") 으로 렌더되도록 한다. 사용자가 보고한 증상은 #320 (`AppLabelLookup` 도입) 이후에도 일부 앱(특히 Android 11+ 의 launcher 가시성 제약 대상) 에서 chip 이 packageName 으로 fallback 되던 것 — 그 fallback 의 진입 빈도를 manifest `<queries>` 선언으로 크게 줄인다.

**Architecture:** Android 11 (API 30) 부터 `PackageManager.getApplicationInfo(packageName, 0)` 는 호출 앱 manifest 의 `<queries>` 또는 `QUERY_ALL_PACKAGES` 권한 선언이 없으면 같은 UID 가 아닌 임의 패키지에 대해 `NameNotFoundException` 을 던진다. SmartNoti 의 `PackageManagerAppLabelLookup` (`ui/screens/categories/components/AppLabelLookup.kt:50-64`) 는 그 예외를 의도대로 swallow → `null` 을 반환 → `CategoryConditionChipFormatter` 가 raw `matchValue` 로 fallback 한다 (이는 v1 디자인 — chip 이 빈 문자열로 렌더되는 일을 막기 위함). 즉 lookup 호출 자체가 가시성 제약 때문에 항상 실패하던 것이 근본 원인. `AndroidManifest.xml` 의 `<application>` 형제로 `<queries><intent><action android:name="android.intent.action.MAIN" /></intent></queries>` 를 한 번 선언하면, 런처 활동을 가진 모든 앱이 SmartNoti 의 `PackageManager` 에 가시화되어 lookup 이 정상 성공한다. listener 쪽 `SmartNotiNotificationListenerService` 가 같은 lookup 을 이미 성공시키고 있던 이유는 `BIND_NOTIFICATION_LISTENER_SERVICE` 가 implicit visibility 를 부여하기 때문 — UI activity 에는 그 grant 가 없으므로 manifest 선언이 필요.

**Tech Stack:** AndroidManifest XML, JUnit (instrumented or pure), `PackageManager`, Jetpack Compose (read-only).

---

## Product intent / assumptions

- Lookup 결과는 사용자에게 즉시 보이는 텍스트 이므로 Play Store 에서 가장 보수적인 가시성 패턴 (`MAIN` intent 만 declare) 으로 한정한다. `QUERY_ALL_PACKAGES` 같은 광범위 권한은 정책 검토를 수반 → 본 plan scope 밖.
- `MAIN` intent 만 선언하면 **launcher activity 를 가진 앱만** 가시화된다. "YouTube" 같은 일반 사용자 앱은 모두 포함된다. 반면 launcher 활동이 없는 service-only / agent 앱은 여전히 lookup 실패 → raw packageName fallback. 이 잔여 케이스는 의도된 trade-off.
- Lookup 성공의 부수효과는 chip 렌더 텍스트 1줄에 한정 — Category 데이터 모델 / persistence / matcher 동작은 변하지 않는다.
- `last-verified` 는 ADB 시나리오 (Task 3) 가 실제로 실행되어 chip 이 "YouTube" 로 보이는 것을 확인한 날짜만 반영한다. 단순 manifest 머지일 만으로 갱신하지 않는다.

---

## Task 1: Add a failing test that asserts a known package resolves under the new visibility

**Objective:** Manifest 변경 전후의 동작 차이를 코드로 고정한다. 테스트가 빨간 상태에서 시작해 manifest 추가 후 초록이 되도록.

**Files:**
- 신규: `app/src/androidTest/java/com/smartnoti/app/ui/screens/categories/components/PackageManagerAppLabelLookupInstrumentedTest.kt` (instrumented — 진짜 `PackageManager` 가 필요)
  - 만약 instrumented 환경 추가가 부담스럽다면 fallback: `app/src/test/.../PackageManagerVisibilityContractTest.kt` 에서 manifest XML 을 텍스트로 읽어 `<queries>` 블록 + `MAIN` action 의 존재만 검증. 이 fallback 은 행동을 직접 보지 못하지만 manifest 회귀를 막는 안전망 역할.

**Steps:**
1. 옵션 A (instrumented, 권장): 테스트 디바이스에 반드시 설치돼 있는 시스템 패키지 한 개 (예: `com.android.settings`) 를 `PackageManagerAppLabelLookup(context.packageManager).labelFor(...)` 로 조회 → `null` 이 아니어야 함을 assert. 같은 lookup 으로 무작위 가짜 packageName (`fake.pkg.does.not.exist`) → `null` 을 assert (NameNotFoundException swallow 검증 유지).
2. 옵션 B (pure JVM fallback): `app/src/main/AndroidManifest.xml` 를 `Files.readString` 으로 읽어 `<queries>` 와 `<action android:name="android.intent.action.MAIN" />` 가 모두 존재하는지 정규식으로 검증. Note 로 "이 테스트는 행동이 아니라 manifest contract 를 지킨다 — 진짜 가시성 동작은 ADB recipe (Task 3) 로 확인" 를 코드 주석에 남긴다.
3. `./gradlew :app:connectedDebugAndroidTest --tests "...PackageManagerAppLabelLookupInstrumentedTest"` (옵션 A) 또는 `./gradlew :app:testDebugUnitTest --tests "...PackageManagerVisibilityContractTest"` (옵션 B) 로 RED 확인.

## Task 2: Add `<queries>` block to AndroidManifest

**Objective:** Task 1 의 테스트를 GREEN 으로 만든다.

**Files:**
- `app/src/main/AndroidManifest.xml`

**Steps:**
1. `<application>` 형제(즉 `<manifest>` 직속, `<uses-permission>` 아래) 로 다음 블록을 추가:
   ```xml
   <queries>
       <intent>
           <action android:name="android.intent.action.MAIN" />
       </intent>
   </queries>
   ```
2. 다른 `<queries>` 항목은 이번 PR 에서 추가하지 않는다 — 필요해지면 후속 plan.
3. Task 1 의 테스트 재실행 → GREEN 확인.
4. 풀 unit test (`./gradlew :app:testDebugUnitTest`) 통과 — 다른 테스트가 manifest 파싱에 의존하지 않으므로 영향 없을 것.

## Task 3: ADB end-to-end verification

**Objective:** 사용자가 보고한 시나리오 — Category 의 APP-rule chip 이 raw `com.android.youtube` 로 보이던 것이 "YouTube" 로 바뀌는 것을 실제 디바이스/에뮬레이터에서 확인.

**Steps:**
```bash
# 1. 새 manifest 가 들어간 빌드 install
./gradlew :app:installDebug

# 2. 앱 강제 종료 후 재기동 (Application 의 PackageManager 캐시 갱신)
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity

# 3. 분류 탭 진입 — 사전 조건: APP rule 이 com.android.youtube (또는 디바이스에
#    설치된 다른 사용자 앱) 으로 들어 있는 Category 가 1건 이상 있어야 한다.
#    없으면 editor 로 새 Category 생성 후 APP rule 추가.

# 4. uiautomator 덤프로 chip 텍스트 확인
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -oE 'text="조건: 앱=[^"]+' | head

# 기대: 출력 라인이 "text=\"조건: 앱=YouTube" 형태 (= 사용자-친화 라벨)
# 회귀: "text=\"조건: 앱=com.android.youtube" 라면 manifest 가 install 본에 반영 안 됐거나
#       앱 프로세스가 stale — Step 2 의 force-stop 부터 재실행.
```

5. 결과 chip 스크린샷 + uiautomator 라인을 PR 본문에 첨부.

## Task 4: Sync `categories-management` journey

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. **Known gaps**: APP-토큰 라벨 fallback 이 자주 발생하던 잠재 케이스를 직접 명시한 항목이 현재 없음. 새 Known gap (그러나 즉시 resolved 표기) 한 줄을 추가하거나, Change log 만 남겨도 충분 — 현재 Known gaps 는 실제 미해결 항목 위주이므로 후자가 더 깔끔. 본 plan 은 후자 채택.
2. **Change log**: 다음 형태로 한 줄 추가 (날짜는 구현 PR 머지 시각 기준):
   ```
   - 2026-04-25: APP-토큰 라벨 가시성 보강 — plan `docs/plans/2026-04-25-android-queries-package-visibility.md`. AndroidManifest 에 `<queries><intent action=MAIN></queries>` 선언 → `PackageManagerAppLabelLookup` 가 Android 11+ 에서도 launcher 활동을 가진 앱 (예: YouTube) 의 라벨을 정상 해석. chip 이 더 이상 raw `com.android.youtube` 로 fallback 되지 않음. ADB 검증: `중요 알림` Category 의 APP rule (`com.android.youtube`) 이 카드 chip 에서 `앱=YouTube` 로 노출. listener 쪽 `SmartNotiNotificationListenerService` 의 라벨 해석은 변경 없음 (notification-access grant 로 이미 성공하던 경로).
   ```
3. **Code pointers**: `PackageManagerAppLabelLookup` 의 manifest 의존성을 한 줄 보강. 예:
   - `app/src/main/AndroidManifest.xml` — `<queries><intent action=MAIN></queries>` 선언 (Android 11+ package visibility) 으로 `PackageManagerAppLabelLookup` 의 `getApplicationInfo` lookup 이 임의 사용자 앱에도 성공.
4. **last-verified**: ADB recipe (Task 3) 가 GREEN 으로 실행된 날짜로 갱신. PR 머지일과 다를 수 있다 — recipe 실행일이 우선.

## Task 5: Self-review + PR

- 단위 / 계약 테스트 통과 + ADB 결과 첨부.
- PR 제목: `fix(visibility): declare <queries> so APP chip shows app name not packageName`.
- PR 본문: 사용자 보고 → 진단 (manifest 누락 → Android 11+ visibility) → 수정 한 줄 + 테스트 + ADB 결과 → journey 동기화. Risks 섹션의 Option D 후속 가능성 명시.

---

## Scope

**In:**
- `AndroidManifest.xml` 한 곳에 `<queries>` 블록 추가.
- 가시성 회귀를 막는 instrumented 또는 manifest-contract 단위 테스트 1건.
- `categories-management` journey 의 Code pointers / Change log / `last-verified` 갱신.
- Task 3 의 ADB 결과 기록.

**Out:**
- `QUERY_ALL_PACKAGES` 권한 선언 (Play Store 정책 검토 동반 — 별도 plan).
- listener 가 해석한 `packageName → appName` 캐시를 SettingsDataStore / Room 에 저장하고 UI 가 cache → manifest lookup 순으로 폴백하는 Option D — 본 plan Risks 의 follow-up 후보로만 보존.
- chip 외 다른 surface (Detail metadata line `APP · com.android.shell` raw 라벨 등). 동일 lookup 으로 후속 PR 가능하지만 본 plan 은 chip 한 곳에 한정.
- Listener `SmartNotiNotificationListenerService` 의 라벨 해석 코드 변경 (이미 동작 중).
- `last-verified` 를 manifest 머지일로만 올리는 행위 — recipe 실행일 기준만 허용.

## Risks / open questions

- **Option D (cache 도입) 의 잔여 가치**: launcher activity 가 없는 service-only 앱 (일부 Android 시스템 앱, 일부 SDK 동반 패키지) 은 `MAIN` intent 선언만으로는 여전히 가시화되지 않는다. listener 는 `SmartNotiNotificationListenerService.processNotification` 시점에 이미 그 앱 packageName + label 을 알고 있으므로, `Map<String, String>` 형태의 캐시를 `SettingsRepository` 또는 신규 `AppLabelCache` 테이블에 저장하면 UI 가 cache → manifest lookup 순으로 fall through 해서 잔여 fallback 을 더 줄일 수 있다. 본 plan scope 밖 — 후속 plan 후보 (`docs/plans/YYYY-MM-DD-app-label-cache-from-listener.md`).
- **Play Store 정책 리스크**: `<queries>` + `MAIN` intent 패턴은 Google 의 가이드라인 (`https://developer.android.com/training/package-visibility/declaring`) 에 명시된 표준 패턴이므로 추가 정책 신청은 불필요. 단 후속에서 `QUERY_ALL_PACKAGES` 로 확장한다면 Play Console 의 sensitive permission 신청이 필요 — 본 plan scope 밖.
- **테스트 옵션 선택 (A vs B)**: instrumented 테스트 (옵션 A) 가 더 정확하지만 CI 에 instrumented 러너가 없다면 옵션 B 로 진행. 둘 중 어느 쪽을 채택할지는 `app/src/androidTest/` 가 이미 존재하는지 / CI workflow 가 `connectedDebugAndroidTest` 를 돌리는지 implementer 가 1차 확인 후 결정 — 둘 다 부재하면 옵션 B + Task 3 의 ADB recipe 가 함께 행동을 보장한다.
- **Manifest 가 머지된 뒤에도 chip 이 여전히 raw 라면**: (a) 디바이스 캐시 stale → `adb shell am force-stop` + 재기동, (b) 실제로 launcher activity 가 없는 packageName → Option D 후속이 필요한 케이스. PR 검토 시 두 경우를 구분해서 보고.
- **`last-verified` 와 머지일의 분리**: docs-sync 규칙상 `last-verified` 는 recipe 실행일 — implementer 가 Task 3 을 실제로 돌린 날짜를 사용해야 한다. 머지가 다음날로 미뤄지면 머지일이 아니라 recipe 실행일을 그대로 둔다.

## Related journey

- [`categories-management`](../journeys/categories-management.md) — 본 plan 머지 시 Change log 한 줄 추가. (Known gaps 에는 별도 항목 추가하지 않음 — 사용자 보고는 chip lookup 의 일반 fallback 빈도였고 그 fallback 자체는 설계 의도상 유지된다. 빈도 감소만 기록.)
