---
status: planned
fixes: 510
last-updated: 2026-04-27
---

# Fix issue #510 — replacement notification icons distinguish source app + action

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P1 release-prep gate; do not skip the failing-test step or the ADB end-to-end re-verification on R3CY2058DLJ. Follow Option C (source large + action small) — Options A/B are documented in the issue body for context but are NOT in scope.

**Goal:** SmartNoti 가 source 알림을 replacement 으로 대체할 때 (DIGEST replacement / SILENT group summary / SILENT group child / PRIORITY mark), 트레이의 한 화면에서 사용자가 (a) 어느 source 앱 알림이었는지 (b) SmartNoti 가 어떤 액션을 했는지 즉시 시각적으로 구분할 수 있다. 구체적으로: **large icon = source 앱 launcher 아이콘**, **small icon = SmartNoti 액션별 글리프** (DIGEST / SILENT / PRIORITY). 한 행만 봐도 "쿠팡 (large) + DIGEST (small) | 3건 묶음" 같은 시각 메시지가 즉시 전달된다.

**Architecture:** 결함의 진원지는 두 notifier 가 모두 정적 SmartNoti drawable 한 장만 `setSmallIcon()` 으로 사용하고 `setLargeIcon()` 호출이 전혀 없다는 것 (`SmartNotiNotifier.kt:82` `android.R.drawable.ic_dialog_info`, `SilentHiddenSummaryNotifier.kt:53/149/209` `android.R.drawable.ic_menu_view`). 본 plan 은 (a) `AppLabelResolver` (issue #503, PR #507) 와 동일한 PackageManager 캐시 패턴을 따르는 새 `AppIconResolver` (sibling seam, packageName → Bitmap) 를 도입하고, (b) `ReplacementActionIcon` enum (DIGEST / SILENT / PRIORITY) + 3 개의 vector drawable asset 을 추가하며, (c) 두 notifier 가 source packageName 을 알고 있는 경로에서 `large = AppIconResolver.resolve(pkg)`, `small = ReplacementActionIcon.drawableRes` 를 일관되게 wire-in 한다. 캐시 무효화는 `AppLabelResolver` 가 등록한 BroadcastReceiver 가 함께 호출하도록 시그널을 공유 (또는 별도 receiver — Task 3 에서 결정).

**Tech Stack:** Kotlin, Android `PackageManager` / `Drawable.toBitmap`, AndroidX Core notification builder (`NotificationCompat.Builder.setLargeIcon(Bitmap)` / `setSmallIcon(Int)`), Vector Drawable XML, Gradle JUnit/Robolectric unit tests, ADB end-to-end on R3CY2058DLJ.

---

## Product intent / assumptions

- **Decision (no user input needed)**: Option C 가 채택. Source large + action small 은 Android tray 의 표준 컨벤션 (메신저 / 이메일 클라이언트 다수가 large=avatar/sender, small=app brand 으로 사용) 을 그대로 활용하므로 사용자 학습 비용 최소.
- **Decision (no user input needed)**: Source 앱이 launcher 아이콘을 노출하지 않거나 (system service / plugin / disabled), `pm.getApplicationInfo(pkg, 0).loadIcon(pm)` 이 빈 결과를 반환하면 large icon 을 비우고 (omit `setLargeIcon` 호출) small 만 action 글리프로 표시. SmartNoti default brand 아이콘으로 대체하지 않는다 — large 비어 있는 행도 small 로는 액션이 식별되며, source 가 정말 없는 케이스를 거짓 SmartNoti 브랜딩으로 가리지 않는다.
- **Decision (no user input needed)**: 캐시는 `AppLabelResolver` 와 동일한 in-process per-package 메모이즈 + install/upgrade/remove 시 invalidation. 다만 Bitmap 은 라벨 String 보다 메모리 비싸므로 `LruCache<String, Bitmap>` 으로 크기 상한 (예: 64 entries — 사용자 설치 앱 중 알림 보내는 패키지는 보통 20~40개) 을 둔다.
- **Decision (no user input needed)**: SILENT group summary 가 여러 source 앱을 묶을 때 (다른 패키지의 알림이 한 group 에 들어가는 케이스) 는 large icon 을 비운다 — 어느 한 앱을 대표로 골라 표시하면 오해 소지. Group children 은 각자 자신의 source large 를 가진다.
- **Open question (surface to user)**: 액션별 vector drawable 의 시각 모티브. 본 plan 의 default 추천은 **DIGEST=Material outlined "inbox"**, **SILENT=Material outlined "volume_off"**, **PRIORITY=Material outlined "notifications" (or "priority_high")**. 사용자가 다른 글리프 (예: "archive", "do_not_disturb_on", "star") 를 선호하면 Task 3 의 asset 단계에서 교체.
- **Open question (low-stakes, doc-only)**: `ic_replacement_*` prefix 작명 — 본 plan default. Alternative: `ic_action_digest` 등 짧은 이름. 사용자 선호 없으면 prefix 유지.

---

## Task 1: Failing UI tests for replacement notifier icon sites

**Objective:** 회귀를 코드로 고정. 5단계 P1 gate 의 "tests-first" 칸. Asserts (a) `setLargeIcon(Bitmap)` 이 source packageName 으로 resolve 한 결과로 호출되고, (b) `setSmallIcon(Int)` 이 `ReplacementActionIcon` 의 drawableRes 로 호출되며, (c) source resolver 가 null 을 반환할 때 `setLargeIcon` 은 호출되지 않는다.

**Files (new):**
- `app/src/test/java/com/smartnoti/app/notification/SmartNotiNotifierIconTest.kt`
- `app/src/test/java/com/smartnoti/app/notification/SilentHiddenSummaryNotifierIconTest.kt`

**Steps:**
1. Robolectric (또는 fake `NotificationManager`) 환경에서 두 notifier 를 호출하고, 마지막으로 build 된 `Notification` 의 `largeIcon` 과 `getSmallIcon().resId` 를 검사. (Robolectric `ShadowNotification` 은 `Notification.extras.getParcelable(EXTRA_LARGE_ICON)` 으로 접근 가능.)
2. 케이스:
   - **DIGEST replacement, source resolves**: `AppIconResolver.resolve("com.coupang.mobile")` 이 fake 32×32 Bitmap 반환 → `notification.largeIcon == bitmap`, `smallIcon == R.drawable.ic_replacement_digest`.
   - **SILENT group summary, mixed sources**: 그룹 children 의 packageName 이 2종 이상 → summary 의 largeIcon == null, smallIcon == `R.drawable.ic_replacement_silent`.
   - **SILENT group child, single source**: child 의 source resolves → largeIcon == bitmap, smallIcon == `R.drawable.ic_replacement_silent`.
   - **Source resolver returns null**: `AppIconResolver.resolve(pkg)` == null → `setLargeIcon` 호출 횟수 0.
3. 각 케이스마다 expected vs actual 을 assert 하는 한 줄 명명: `digest replacement carries source large icon and digest small icon`, `silent summary omits large icon when sources mixed`, …
4. `./gradlew :app:testDebugUnitTest --tests "*NotifierIconTest"` → RED 4건 확인.

## Task 2: Implement AppIconResolver (sibling of AppLabelResolver)

**Objective:** PackageManager 호출 + Drawable→Bitmap 변환 + per-package memoization 을 한 곳에 캡슐화. Task 1 케이스 GREEN 으로 가는 첫 단계.

**Files (new):**
- `app/src/main/java/com/smartnoti/app/notification/AppIconResolver.kt`
- `app/src/test/java/com/smartnoti/app/notification/AppIconResolverTest.kt`

**Steps:**
1. `class AppIconResolver(private val packageManager: PackageManager, private val cacheCap: Int = 64)` — 시그니처 `fun resolve(packageName: String): Bitmap?`. Null 반환은 "source 가 표시 가능한 launcher 아이콘이 없음" 신호.
2. Resolution chain (각 단계에서 결과가 null/blank 이면 다음):
   1. `pm.getApplicationInfo(packageName, 0).loadIcon(pm)` — 가장 흔한 path.
   2. (Fallback) `pm.getApplicationIcon(packageName)` — 일부 디바이스에서 다른 결과.
3. Drawable → Bitmap 변환: `androidx.core.graphics.drawable.toBitmap(width, height)` — width/height 는 `ContextCompat.getDrawable(...)?.intrinsicWidth/Height` 또는 default `48dp` × `density`. AdaptiveIconDrawable 도 자동 처리됨.
4. 캐시: `LruCache<String, Bitmap>(cacheCap)`. miss → resolve → put → return. hit → 즉시 반환.
5. `fun invalidate(packageName: String)` / `fun clearAll()` API.
6. Catch 는 `NameNotFoundException`, `Resources.NotFoundException`, `RuntimeException` 분리 — `AppLabelResolver` 패턴 그대로 mirror.
7. 단위 테스트:
   - Happy path: `loadIcon` 이 fake Drawable 반환 → resolve 가 non-null Bitmap.
   - `NameNotFoundException` → resolve 가 null + 던지지 않음.
   - 같은 packageName 두 번 resolve → PackageManager 호출 1회 (verify).
   - `invalidate(pkg)` 후 다시 resolve → 호출 2회 + 새 Bitmap.
8. `./gradlew :app:testDebugUnitTest --tests "*AppIconResolverTest"` GREEN.

## Task 3: ReplacementActionIcon enum + 3 vector drawables

**Objective:** Action small icon 을 컴파일타임 enum 으로 안전하게 선택. Magic int 산포 방지.

**Files (new):**
- `app/src/main/java/com/smartnoti/app/notification/ReplacementActionIcon.kt` — `enum class ReplacementActionIcon(val drawableRes: Int) { DIGEST(R.drawable.ic_replacement_digest), SILENT(R.drawable.ic_replacement_silent), PRIORITY(R.drawable.ic_replacement_priority) }`
- `app/src/main/res/drawable/ic_replacement_digest.xml` — Material outlined "inbox" 24dp, white tint (notification small icon 권장 spec: monochrome white on transparent).
- `app/src/main/res/drawable/ic_replacement_silent.xml` — Material outlined "volume_off" 24dp.
- `app/src/main/res/drawable/ic_replacement_priority.xml` — Material outlined "notifications" or "priority_high" 24dp.

**Steps:**
1. Vector drawable XML 작성 — Material Symbols outlined glyph path data 사용. 각 파일은 `<vector android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24" android:tint="?attr/colorControlNormal">` + 단일 `<path android:fillColor="@android:color/white" android:pathData="..."/>`.
2. Notification small icon spec: API 21+ 는 alpha-only 가 안전 (color 가 status bar 에서 무시됨). vector 의 fillColor 를 white 로 두고 `Notification.color` 로 accent 부여.
3. Enum 자체 단위 테스트는 트리비얼 — Task 1 의 notifier 테스트가 enum 매핑을 covers.

## Task 4: Wire AppIconResolver + ReplacementActionIcon into both notifier sites

**Objective:** Task 1 의 4 케이스 GREEN.

**Files (touched):**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotifier.kt`
  - 생성자에 `appIconResolver: AppIconResolver` 추가 (DI seam — `SmartNotiNotificationListenerService` / `MainActivity` wiring 지점에서 주입).
  - `setSmallIcon(android.R.drawable.ic_dialog_info)` (line 82) → `setSmallIcon(ReplacementActionIcon.DIGEST.drawableRes)` (또는 호출 컨텍스트의 `NotificationDecision` 에 따라 PRIORITY/DIGEST 분기).
  - 같은 빌더 chain 에서 `appIconResolver.resolve(sourcePackageName)?.let { builder.setLargeIcon(it) }` 추가.
- `app/src/main/java/com/smartnoti/app/notification/SilentHiddenSummaryNotifier.kt`
  - 생성자에 `appIconResolver: AppIconResolver` 추가.
  - 3 곳 (`line 53/149/209`) 의 `setSmallIcon(ic_menu_view)` → `setSmallIcon(ReplacementActionIcon.SILENT.drawableRes)`.
  - Group child 빌더: `appIconResolver.resolve(child.sourcePackageName)?.let { setLargeIcon(it) }`.
  - Group summary 빌더: children 의 packageName set 크기가 1 일 때만 그 source 로 large 세팅, 2+ 면 omit (Product intent 결정 근거).
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
  - 기존 `AppLabelResolver` 가 만들어진 자리 (`onCreate` 부근) 에서 `AppIconResolver` 도 instantiate, 두 notifier 에 같은 instance 주입.
  - 기존 `PackageReceiver` 의 broadcast handler (PR #507 Task 3) 에서 `appLabelResolver.invalidate(pkg)` 다음에 `appIconResolver.invalidate(pkg)` 도 호출하도록 한 줄 추가. (Receiver 생명주기는 그대로.)

**Steps:**
1. 생성자 + DI wiring 갱신.
2. 두 notifier 의 builder chain 갱신 — small icon enum 분기 + large icon optional set.
3. PackageReceiver 에 invalidate 한 줄 추가.
4. Task 1 의 RED 4건 → GREEN 확인.
5. `./gradlew :app:testDebugUnitTest` 전체 → 신규 GREEN, 기존 회귀 0건.

## Task 5: ADB end-to-end verification on R3CY2058DLJ

**Objective:** P1 5-step gate 의 마지막 칸 — 실제 트레이에서 source large + action small 이 보이는지 시각 검증.

**Steps:**
```bash
# 1. Fresh debug-build install
./gradlew :app:installDebug
adb -s R3CY2058DLJ shell am force-stop com.smartnoti.app
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
sleep 3

# 2. SILENT 액션을 트리거할 fake 알림 발생 (Coupang 패키지로 위장하지 않고
#    실제 Coupang 앱에서 발생한 알림을 SmartNoti 가 SILENT 로 분류한 행을
#    재현하는 것이 가장 정확함). 대안:
adb -s R3CY2058DLJ shell cmd notification post -S bigtext -t "Issue510Silent" \
    Issue510SilentTag "fake source app silent test"

# 3. DIGEST 액션 트리거 — 분류 룰에 DIGEST 로 매핑된 패키지의 알림을 한 건 발생.

# 4. 트레이 스크린샷 캡처 (PR 본문 첨부):
adb -s R3CY2058DLJ shell screencap -p /sdcard/issue510-tray.png
adb -s R3CY2058DLJ pull /sdcard/issue510-tray.png /tmp/

# 5. 시각 검증 체크리스트 (PR 본문에 표로):
#    - [ ] DIGEST 행: large = 쿠팡 (또는 source) 런처 아이콘, small = inbox 글리프
#    - [ ] SILENT child: large = source 런처 아이콘, small = volume_off 글리프
#    - [ ] SILENT summary (mixed sources): large = 비어 있음, small = volume_off
#    - [ ] PRIORITY mark (있다면): large = source, small = notifications
#    - [ ] launcher 아이콘이 없는 system 패키지의 알림: large 비어 있음, small 만 표시 (앱 안 깨짐)
```

기록물 (PR 본문 첨부): 트레이 스크린샷 1~2장 + 위 체크리스트 결과.

## Task 6: Update affected journey docs

**Files:**
- `docs/journeys/notification-capture-classify.md`
  - **Code pointers** 절에 `notification/AppIconResolver` 추가 (resolver + cache 설명, 본 plan 링크).
  - **Code pointers** 절에 `notification/ReplacementActionIcon` 추가 (enum + drawable map, 본 plan 링크).
  - **Change log** 에 한 줄: "Issue #510 — replacement notifier 가 source app icon (large) + action glyph (small) 을 분리해 트레이 분별성 회복. plan: docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md".
- `docs/journeys/protected-source-notifications.md`
  - **Observable steps** 의 replacement 표시 step 에 "source 앱 large icon + 액션별 small icon" 한 줄 추가.
  - **Change log** 에 동일 1줄.
- `docs/journeys/silent-auto-hide.md`
  - **Observable steps** group summary / child step 에 large/small 시각 분리 한 줄.
  - **Change log** 1줄.
- `docs/journeys/digest-suppression.md`
  - **Observable steps** DIGEST replacement step 에 시각 분리 한 줄.
  - **Change log** 1줄.
- 모든 4개 journey 의 **`last-verified`** frontmatter 는 ADB recipe (Task 5) GREEN 시점에만 시스템 시계 기준으로 bump.

## Task 7: Self-review + PR

- 모든 단위 테스트 GREEN (resolver + notifier × 2 + 기존 회귀 0건).
- ADB end-to-end 증거 (Task 5 스크린샷 + 체크리스트) PR 본문 첨부.
- PR 제목: `fix(#510): replacement notifications carry source large + action small icons`.
- Journey doc 변경 4건 포함.
- Closes #510.

---

## Scope

**In scope:**
- `AppIconResolver` 추출 (sibling of `AppLabelResolver`) + LruCache + invalidation.
- `ReplacementActionIcon` enum + 3 vector drawable assets.
- `SmartNotiNotifier` + `SilentHiddenSummaryNotifier` 두 notifier 의 setSmallIcon / setLargeIcon wiring.
- 4개 journey doc 갱신.
- ADB end-to-end 시각 검증.

**Out of scope:**
- Option A (source-only) / Option B (action-only) — 채택 안 함.
- 사용자가 source 앱 아이콘 대신 사용자 정의 아바타를 지정하는 기능.
- AdaptiveIcon foreground/background 분리 렌더링 (Bitmap 변환으로 평탄화).
- Notification color/accent 변경 (별도 plan — F-series UX 와 함께).
- Multi-locale 글리프 (액션 글리프는 locale 무관).

---

## Risks / open questions

- **Bitmap 메모리**: 64-entry LruCache × 평균 96×96 ARGB Bitmap ≈ 2.4 MB. 합리적이지만 OEM 에 따라 큰 adaptive icon 이 들어오면 더 커질 수 있음 — `cacheCap` 을 향후 조정 가능한 상수로 노출.
- **API-level 차이**: `setLargeIcon` 은 API 23+ 에서 `Icon` 시그니처 추가 / 그 이전은 `Bitmap` 만. AndroidX `NotificationCompat.Builder.setLargeIcon(Bitmap)` 을 사용하면 호환 처리됨.
- **Adaptive icon 변환**: `Drawable.toBitmap` 은 AdaptiveIconDrawable 의 foreground/background 합성을 자동 처리하지만, OEM 마스크 모양 (원/스쿼클/등) 은 시스템 launcher 와 다를 수 있음. 트레이에서는 시스템이 자체 마스크를 다시 적용하므로 수용 가능.
- **System 패키지 large 비어 있음**: Product intent 에 명시한 대로 fallback 없이 large 를 omit. 사용자가 SmartNoti brand 로 채우길 원하면 Task 3 의 enum 에 default Bitmap 을 추가하면 됨 — 한 곳만 변경.
- **Mixed-source SILENT summary**: large 를 비우는 결정이 사용자 직관에 어긋날 수 있음 (한 행만 보고 어떤 source 인지 모름). 향후 group summary 가 source 별로 분리되도록 SilentGroupTrayPlanner 자체를 손대는 별도 plan 이 필요할 수 있음 — 본 plan 범위 밖.
- **Open question (surfaced for user judgment)**: 액션 vector drawable 글리프 — DIGEST=inbox, SILENT=volume_off, PRIORITY=notifications 가 default. 사용자가 다른 모티브 (DIGEST=archive, SILENT=do_not_disturb_on, PRIORITY=star/priority_high) 선호하면 Task 3 단계에서 교체.
- **Issue 본문의 `DigestReplacementNotifier` 명명 불일치**: 실제 코드베이스에서는 digest replacement 가 별도 클래스가 아니라 `SmartNotiNotifier` 안의 `NotificationDecision.DIGEST` 분기로 구현돼 있음. 본 plan 은 실제 클래스 이름 (`SmartNotiNotifier` + `SilentHiddenSummaryNotifier`) 만 명시하므로 이름 불일치는 저절로 해소.

---

## Related journey

- [`notification-capture-classify`](../journeys/notification-capture-classify.md) — replacement notifier 의 시각 표시는 capture-classify 의 마지막 step (replacement 발행) 에 해당.
- [`protected-source-notifications`](../journeys/protected-source-notifications.md) — protected source 카테고리 알림이 PRIORITY mark 로 표시될 때 source large + priority small 로 시각 식별.
- [`silent-auto-hide`](../journeys/silent-auto-hide.md) — SILENT group summary / child 의 시각 표시.
- [`digest-suppression`](../journeys/digest-suppression.md) — DIGEST replacement 의 시각 표시.

본 plan 이 shipped 되면 위 4개 journey 의 Change log 에 본 plan 링크 + 시스템 시계 날짜로 한 줄씩 기록한다.
