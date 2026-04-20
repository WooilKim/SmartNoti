# Hidden Inbox Dedup + Grouping + Promo Auto-Suppression Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 관찰한 세 가지 문제를 같은 PR 에서 해결한다.

1. 음악/미디어처럼 빠르게 업데이트되는 알림이 DB 에 여러 row 로 쌓여 Hidden 화면 count 가 부풀려지는 문제.
2. Hidden 화면이 flat list 여서 같은 앱의 여러 알림이 연달아 보여 정보 전달력이 떨어지는 문제.
3. onboarding 의 PROMO_QUIETING 프리셋이 KEYWORD → DIGEST 룰을 만들지만, 그 키워드가 포함된 알림을 보내는 앱이 onboarding 당시 tray 에 없었다면 `suppressedSourceApps` 에 포함되지 못해 원본이 계속 알림센터에 남는 문제 ("(광고)" 알림 잔존).

**Architecture:**
- (1) `buildNotificationId` 에서 `postedAtMillis` 의존을 제거하고 `sourceEntryKey` 가 있을 때는 `packageName:suffix` 로 안정된 PK 를 만든다. 같은 알림 slot 의 업데이트가 Room `@Insert(OnConflictStrategy.REPLACE)` 를 통해 실제 replace 되게 한다.
- (2) `HiddenNotificationsScreen` 을 `DigestGroupCard` 패턴으로 앱 단위 그룹 카드로 재구성한다. 그룹 내 상위 몇 건만 preview, 모두 탭하면 Detail 로 이동.
- (3) 리스너의 `processNotification` 에서 decision 이 DIGEST 이고 `suppressSourceForDigestAndSilent=true` 일 때, `packageName` 이 `suppressedSourceApps` 에 없으면 **현재 처리 중에 동기적으로 추가**해 이번 알림도 suppression 이 적용되게 한다.

**Tech Stack:** Kotlin, Room, Jetpack Compose, DataStore, Gradle unit tests, Claude Code implementation.

---

## Product intent / assumptions

- (1) 은 엄연한 버그 — 같은 알림 slot 이 Room 의 upsert 로 replace 되는 것이 원래 기대치. 수정 후 `postedAtMillis` 는 여전히 entity 의 별도 필드로 저장되므로 "마지막으로 본 시각" 정보는 보존된다.
- (2) 은 UX 결정 — 일단 앱 그룹 카드로 정리, 그룹 내 dedup 은 (1) 로 이미 상당 부분 완화되므로 MVP 에서는 단순 그룹핑만.
- (3) 은 제품 결정 — `suppressedSourceApps` 를 self-expanding 리스트로 만든다. 사용자가 `suppressSourceForDigestAndSilent=true` 로 전역 opt-in 했고 DIGEST 룰이 활성이라면, 그 룰에 걸린 앱은 자동으로 opt-in 된다. 사용자는 여전히 Settings 에서 개별 앱을 제거 가능.
- 이 변화들은 모두 기존 journey 의 Observable steps / Known gaps 에 반영한다.

---

## Task 1: Add failing tests for stable notification id

**Objective:** (1) 의 버그를 테스트로 고정.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/model/NotificationIdTest.kt`

**Steps:**
1. `sourceEntryKey` 가 있으면 `postedAtMillis` 값이 달라져도 같은 id 가 생성되는지 검증.
2. `sourceEntryKey` 가 `null` 인 경우 기존 `packageName:postedAtMillis` fallback 유지.
3. `sourceEntryKey` 에서 tag/id 추출 규칙이 이전과 동일하게 suffix 부분을 유지하는지 검증.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.model.NotificationIdTest"` 로 실패 확인.

## Task 2: Make `buildNotificationId` stable across updates

**Objective:** Task 1 의 테스트가 초록이 되게.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/model/NotificationId.kt`

**Steps:**
1. `sourceEntryKey` 가 non-null 이면 id 를 `packageName:suffix` 로 변경 (timestamp 제거).
2. 기존 동작 유지가 필요한 사례(sourceEntryKey=null) 는 fallback.
3. 전체 테스트 실행 — 기존 테스트 깨지는 게 없는지 확인. `NotificationRepository` / DAO 레벨 테스트가 이 id 를 기대한다면 재검토.

## Task 3: Verify dedup end-to-end

**Objective:** 실제로 음악/미디어 알림이 한 row 로 유지되는지 관측.

**Steps:**
```bash
# MediaStyle 합성으로 여러 번 업데이트 게시
for i in 1 2 3 4 5; do
  adb shell cmd notification post -S media -t "Player" MediaDedup "재생 $i"
  sleep 1
done

# Home StatPill 의 조용히 카운트가 +1 만 되는지 확인 (이전 +5)
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -oE 'text="조용히 [0-9]+"'
```

## Task 4: Group Hidden screen by package

**Objective:** (2) 의 UX 개선.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/hidden/HiddenNotificationsScreen.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/components/` 에 `HiddenGroupCard` 신규

**Design:**
- 앱별 그룹 카드 — 앱명, 전체 count 배지, 최근 body 1~3건 preview, 각 preview 탭하면 Detail.
- 단일 건인 앱은 기존 `NotificationCard` 그대로 유지 (단일 건을 그룹 카드로 감싸면 오히려 장식 과잉).
- Header 의 `숨겨진 알림 N건` 은 원래 총 건수 유지. 그룹 개수는 subtitle 로 병기 ("{앱 수}개 앱 · {총 건수}건" 같은 형태) — 최종 카피는 구현 시 결정.

**Steps:**
1. UI 변경 전에 간단한 테스트로 그룹 빌더 함수 단위 테스트 작성 (순수 Kotlin, Compose 빠짐).
2. 그룹핑 로직 — 이미 `NotificationRepository.toDigestGroups` 가 유사. 재사용 or 별도 함수 `toHiddenGroups`.
3. 화면 수정.
4. ADB 재검증 — 같은 앱이 여러 건일 때 하나의 카드로 표시되는지 확인.

## Task 5: Add failing test for rule-driven DIGEST auto-opt-in

**Objective:** (3) 의 동작을 테스트로 고정.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/notification/NotificationSuppressionPolicyTest.kt` (또는 별도 정책 클래스)

**Steps:**
1. 현재 `NotificationSuppressionPolicy.shouldSuppressSourceNotification` 는 `packageName in suppressedApps` 를 엄격히 확인. 자동 확장 로직은 여기에 넣지 않고, listener 에서 설정 업데이트 경로로 처리하는 게 분리된다.
2. 새 헬퍼 `SuppressedSourceAppsAutoExpansionPolicy` (가칭) 가: `(decision, suppressSourceForDigestAndSilent, packageName, currentApps) → updatedApps?` 를 반환. DIGEST 이고 global toggle 이 켜졌고 app 이 리스트에 없으면 확장본을 반환, 아니면 `null`.
3. 테스트로: DIGEST + global on + 미포함 → 확장, 이미 포함 → null, SILENT → null (SILENT 은 어차피 cancel), global off → null, PRIORITY → null.

## Task 6: Wire auto-expansion into the listener

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`

**Steps:**
1. `processNotification` 에서 decision 계산 후 `SuppressedSourceAppsAutoExpansionPolicy.expandIfNeeded(...)` 호출.
2. 반환값이 null 이 아니면 `settingsRepository.setSuppressedSourceApps(updated)` 호출 후 로컬 `settings` 인스턴스도 업데이트해 이번 처리에도 반영.
3. 이후 `NotificationSuppressionPolicy.shouldSuppressSourceNotification` 은 업데이트된 리스트로 평가 → cancel 이 이번 알림에도 적용됨.

## Task 7: Verify promo suppression end-to-end

**Steps:**
```bash
# 사전: onboarding 에서 PROMO_QUIETING 프리셋 적용됨 가정
# 새 앱 (com.android.shell) 으로 "(광고)" 키워드 알림 게시
adb shell cmd notification post -S bigtext -t "테스트샵" AdMigrate "(광고) 오늘만 할인"

# 원본이 tray 에서 즉시 제거되고 SmartNoti replacement 가 있는지 확인
adb shell dumpsys notification --noredact | grep -iE "AdMigrate|smartnoti_replacement_digest"

# Settings 의 suppressedSourceApps 가 com.android.shell 을 포함하도록 확장됐는지 확인
# (DataStore 직접 조회가 어려우면 다음 게시가 동일하게 cancel 되는 것으로 간접 검증)
```

## Task 8: Update journey docs

**Files:**
- `docs/journeys/silent-auto-hide.md` — Change log 에 dedup 수정 반영.
- `docs/journeys/hidden-inbox.md` — Observable steps 에 그룹 카드 흐름 추가, Known gaps 에서 "대량 처리 액션 미제공" 은 유지, "여러 건 쌓임" 우려는 해소로 표기, Change log 업데이트.
- `docs/journeys/digest-suppression.md` — Observable steps 에 auto-expansion 경로 추가, Known gaps 에서 "대량 선택/해제 편의 부족" 은 유지, "onboarding 이후 새 앱은 opt-in 에 안 들어간다" 우려는 해소. Change log 업데이트.
- `docs/journeys/notification-capture-classify.md` — 필요 시 id 생성 규칙 간단 문구 반영 (디테일은 notification-capture-classify 에 있지 않으므로 최소).

## Task 9: Self-review + PR

- 모든 단위 테스트 통과.
- ADB 시나리오 3건 (dedup, 그룹, 프로모 cancel) 결과 스크린샷/덤프 PR 본문에 첨부.
- PR 제목: `fix: dedupe hidden rows, group by app, auto-expand promo suppression`.

---

## Risks / open questions

- **Id 변경의 하위 호환성:** 기존 DB row 들은 `packageName:timestamp:suffix` 형태로 남아 있다. 새 코드가 `packageName:suffix` 로 조회하면 이전 row 는 조회되지 않고 그대로 남음. 실질 영향은 미미 — 다음 업데이트부터 신규 id 로 새 row 가 생기고, 이전 row 는 자연 정리 (사용자가 분류 변경 or 앱 재시작 시 재캡처되며). 심각하면 마이그레이션 필요하지만 이 PR 에서는 생략.
- **Auto-expansion 의 사용자 직관성:** 사용자가 `suppressedSourceApps` 를 Settings 에서 수동으로 비웠는데 다음 "(광고)" 가 오면 다시 자동 추가됨 → 사용자가 명시적으로 제외한 앱까지 재추가될 위험. 간단한 해결: 별도 "sticky 제외" 리스트 도입. 이 PR scope 에는 포함하지 않고 Known gap 으로 남김.
- **Task 4 의 그룹 카드가 preview 를 몇 건 보여줄지:** Digest 쪽은 3건. Hidden 은 더 간결하게 1~2건 preview + "더보기" 대신 그룹 카드 자체 탭으로 다 펼치는 방식도 고려 가능. 구현 시 빠르게 결정.
