---
id: silent-tray-sender-grouping
title: 시스템 tray 에서 숨긴 알림을 발신자 기준으로 그룹핑
status: planned
owner: @wooilkim
journey: silent-auto-hide
created: 2026-04-21
---

## Motivation

현재 SmartNoti 는 `SILENT` 로 분류된 알림의 원본을 tray 에서 cancel 하고 대신 요약 알림 한 건 `숨겨진 알림 N건` 을 게시한다. "모든 숨긴 알림" 하나의 버킷으로만 보여 주므로, 어떤 맥락에서 온 건지 tray 에서만 보고는 알 수 없다.

사용자의 요청: "카톡 · 문자 · 전화" 처럼 **같은 발신자가 여러 앱으로 남긴 조용한 알림이 tray 에서 하나의 그룹으로 묶이고, 그 그룹을 탭하면 Android 가 children 을 펼쳐서 개별 내용을 볼 수 있어야 한다.** 현재 단일 요약 방식은 이 세분화를 내재적으로 막는다.

핵심 통찰: Android 7.0+ 의 `Notification.Builder.setGroup(key) + setGroupSummary(true)` 는 같은 group key 의 children 을 자동으로 접었다가 summary 를 탭하면 펼치는 동작을 system UI 수준에서 지원한다. SmartNoti 가 이미 소유한 low-importance 채널에 children 을 게시하면, 여전히 소리/heads-up 없이 **"사용자가 명시적으로 펼쳤을 때만 보이는"** 조용한 특성을 유지하면서도 발신자별 세분을 제공할 수 있다.

## Goal

- SILENT 로 분류된 알림은 tray 에서 **발신자 (또는 fallback 으로 앱) 단위** 로 그룹화되어 보인다.
- 그룹 summary 는 `"{발신자} · 조용히 {N}건"` 형식의 title 과 최근 1~3건 preview 로 표현된다.
- 그룹에 2건 이상 쌓였을 때만 summary 가 생성되고, 그 이하는 기존 단일 notification 으로만 존재 (tray 에 올리지 않음 — SILENT 의 "숨김" 계약 유지).
- summary 탭 → Android system UI 가 children 을 in-tray 에서 펼친다 (추가 SmartNoti UI 진입은 선택 — 기존 `숨겨진 알림 보기` action 은 group 기반 deep-link 로 업데이트).
- 기존 `숨겨진 알림 N건` **루트 summary 는 유지** 하되, 그룹 summary 들의 상위 표시 역할로 의미를 조정 (Android 는 group-of-groups 를 직접 지원하지 않으므로, 루트 요약은 단일 그룹으로 떨어진 알림들 + 전체 개수를 담당).

## Non-goals

- 분류 알고리즘 변경 — 어떤 알림이 SILENT 가 되는지는 별건.
- PRIORITY / DIGEST 알림의 tray 그룹핑 — 이번 plan 은 SILENT 만. 후속 plan 에서 "같은 발신자의 모든 상태가 하나로 묶이는" cross-status 그룹핑 고려 (e.g., 엄마 PRIORITY 1 + DIGEST 2 + SILENT 3 → "엄마 6건" 단일 그룹).
- 사용자 정의 grouping condition UI — v1 은 자동 sender 추출만.
- 그룹 내부 재정렬 / 필터링 — children 순서는 posting 시점 기준 Android 기본값.
- 기존 Hidden 화면(`HiddenNotificationsScreen`) 의 in-app 그룹핑 변경 — 그 화면은 이미 `DigestGroupCard` 로 앱 단위 그룹핑 + #89 에서 collapse/expand 구현. 이 plan 은 **tray 측만** 건드림.

## Scope

### 건드리는 파일

- `domain/usecase/SilentNotificationGroupingPolicy.kt` (신규) — 그룹 key 결정 로직.
- `notification/SilentHiddenSummaryNotifier.kt` — 단일 요약 → 그룹 summaries + 루트 요약 multiple posting 으로 확장.
- `notification/SmartNotiNotificationListenerService.kt#silentSummaryJob` — counting 을 `Map<GroupKey, List<NotificationEntity>>` 로 확장, 전이 시점에 그룹별 summary post/cancel.
- `notification/SmartNotiNotifier.kt` — 새 channel `smartnoti_silent_group`  (IMPORTANCE_MIN) 추가 + 그룹 summary builder helper.
- `navigation/Routes.kt` + `navigation/AppNavHost.kt` — `Routes.Hidden` 에 optional `sender` / `packageName` query param 추가 (tap deep-link 가 Hidden 을 해당 그룹으로 필터링해서 열 수 있게).
- `ui/screens/hidden/HiddenNotificationsScreen.kt` — deep-link 로 sender/package 필터 prop 받아서 렌더링. UI 는 기존 그대로 (필터만).
- `docs/journeys/silent-auto-hide.md` — Observable steps / Exit state 전면 재작성.
- `docs/journeys/hidden-inbox.md` — Trigger 섹션 갱신 (deep-link 가 sender-filtered 경로 지원).

### 건드리지 않는 파일

- `NotificationEntity` — `sender` 필드가 이미 있어 Room 마이그레이션 불필요 (확인: `NotificationEntity.kt:11 val sender: String?`).
- `NotificationClassifier` 및 분류 파이프라인.
- Digest 경로 전체 (`digest-suppression`).
- Priority 경로 전체.
- Rules 저장소.

## Open questions

### Q1. 루트 요약(`숨겨진 알림 N건`)을 유지할 것인가?

- **옵션 A — 유지**: 그룹 summary 들 위에 전체 개수 summary 도 함께. 단점: Android 는 group-summary 위에 별도 non-grouped notification 을 최상단으로 올리지 않음. 그냥 두 개의 독립 notification 이 같은 채널에 떠 있는 형태가 되며 시각적 중복 위험.
- **옵션 B — 제거**: 각 sender/app 그룹 summary 가 자기 자신의 "숨긴 알림" 을 드러내므로 통합 count 가 없어도 가독성 높음. 다만 기존 journey (`silent-auto-hide`) 의 `숨겨진 알림 N건` 문구에 의존해온 사용자는 변화를 느낌.
- **권고**: 옵션 B (제거). 다만 single-sender 그룹만 있을 때 (즉 그룹 하나만 있을 때) 요약 title 에 `전체 조용히 {N}건` 접미를 붙여 의미를 유지. plan-implementer 가 UI 시각 감사 후 결정.

### Q2. Sender 추출 정책 — messaging app 이 아닌 경우

- 현재 `SmartNotiNotificationListenerService` 의 sender 추출은 `EXTRA_CONVERSATION_TITLE` 우선 + `EXTRA_TITLE` fallback. 쇼핑/뉴스 앱은 title 이 곧 상품명/기사제목이라 "발신자" 의미가 아님.
- 제안: `SilentNotificationGroupingPolicy` 에서 sender 추출을 한 단계 더 강화:
  - `Notification.Style` 이 `MessagingStyle` 이면 title 을 sender 로.
  - 그 외는 `null` → fallback 으로 packageName (앱 단위 그룹).
- 사용자가 원하는 "카톡 · 문자 · 전화 같은 발신자" 는 MessagingStyle 조건에서 자연스럽게 얻어진다.

### Q3. 그룹 최소 크기

- Android 는 groupKey 가 있는 단일 notification 에도 group summary 를 요구하지 않지만, SmartNoti 가 항상 summary 를 posting 하면 N=1 에도 요약 카드가 뜬다. UX 상 과함.
- 제안: **N ≥ 2 일 때만 그룹 summary** 를 posting. N=1 으로 떨어지면 그 그룹 summary 를 cancel 하고 child 는 남겨둔다 (child 는 여전히 IMPORTANCE_MIN 채널 → tray 에서 조용히 존재).
- 단 "모든 SILENT 는 tray 에 보이지 말라" 가 원래 계약이므로, N=1 single child 가 tray 에 혼자 떠있는 것은 **기존 동작과 다름**. 옵션:
  - **옵션 A**: N=1 이면 child 도 cancel (현재 silent-auto-hide 와 동일). 그룹 summary 형성은 N≥2 에서만.
  - **옵션 B**: child 는 항상 게시 (IMPORTANCE_MIN 이라 조용함), N=1 일 때만 summary 생략.
- 옵션 A 가 기존 "숨김" 계약을 유지하므로 **A 권고**.

### Q4. silent-archive-vs-process (#50) 와의 관계

- #50 은 SILENT 를 ARCHIVED(tray 유지) / PROCESSED(tray 제거) 두 하위 상태로 분리 제안.
- 이 plan 과 상호작용: ARCHIVED 만 그룹핑 대상, PROCESSED 는 이미 tray 밖.
- 둘은 서로 독립하게 구현 가능하지만 **merge 순서에 따라 rebase 필요**. 이 plan 이 먼저 ship 되면 #50 의 ARCHIVED 경로는 "이미 그룹핑된 tray representation 을 재사용" 하는 방식으로 간결해짐.
- plan-implementer 는 이 plan 이 shipped 되었을 때 #50 이 아직 `planned` 상태면 #50 본문에 이 interaction 을 링크.

## Tasks

1. **`SilentNotificationGroupingPolicy` 도입 (tests-first)**
   - 신규 파일: `domain/usecase/SilentNotificationGroupingPolicy.kt`.
   - 공개 API: `groupKeyFor(entity: NotificationEntity): SilentGroupKey`. `SilentGroupKey` = `sealed class` (`Sender(normalizedName: String)` / `App(packageName: String)`).
   - 정규화: whitespace trim, 대소문자 보존 (한글 등 유니코드 안전).
   - 테스트: `SilentNotificationGroupingPolicyTest`
     - sender 존재 + MessagingStyle 힌트 → `Sender`
     - sender 없음 또는 비messaging → `App`
     - 동일 sender 로 여러 packageName → 하나의 `Sender` 키
     - 동일 packageName 으로 여러 sender → 각기 다른 `Sender` 키

2. **Notifier 확장 — 그룹 summary + child channel**
   - `SmartNotiNotifier.kt` 에 새 채널 상수 `CHANNEL_SILENT_GROUP = "smartnoti_silent_group"`, IMPORTANCE_MIN, 그룹 children 용.
   - `SilentHiddenSummaryNotifier.kt` 를 확장:
     - `postGroupSummary(key: SilentGroupKey, count: Int, preview: List<NotificationEntity>, rootDeepLink: String)` — 그룹 summary notification 을 key-specific notification id 로 post. `setGroup("smartnoti_silent_group_" + key.stableHash())`, `setGroupSummary(true)`.
     - `cancelGroupSummary(key)` — 해당 키의 summary cancel.
     - `postGroupChild(notificationId: Long, entity: NotificationEntity, key: SilentGroupKey)` — SmartNoti replacement notification with same group key, IMPORTANCE_MIN channel. contentIntent → Detail 화면.
   - 테스트: Robolectric 또는 실기기 ADB 검증 (현재 infra 제약 — plan-implementer 가 ADB 경로로 대체 가능, #89 precedent).

3. **Listener service 파이프라인 변경**
   - `silentSummaryJob` 의 collect 를 `(count) -> Unit` 에서 `(allSilent: List<NotificationEntity>) -> Unit` 로 확장.
   - 그룹핑: `allSilent.groupBy { SilentNotificationGroupingPolicy.groupKeyFor(it) }`.
   - 각 그룹에 대해:
     - 이전 tick 에 존재했지만 현재 count==0 → `cancelGroupSummary(key)` + children cancel.
     - 이전 tick 과 count 가 다르고 count≥2 → `postGroupSummary(key, count, preview)` 재게시.
     - count==1 → summary cancel + (옵션 Q3-A) child cancel (기존 단일-요약 flow 의 "N건" 축소 재현).
   - child 게시 여부: SILENT route 에서 기존 `cancelNotification(sbn.key)` 이후 `notifier.postGroupChild(...)` 추가. (ARCHIVED 전용 플래그는 #50 에 의존하지 않고, 이 plan 자체에서는 "SILENT 면 항상 child 게시" 로 시작 — 후속 plan 에서 ARCHIVED 조건 분기.)

4. **Hidden 화면 deep-link 필터**
   - `Routes.Hidden` 에 optional query param `sender: String?` / `packageName: String?` 추가 (둘 다 nullable, 둘 중 하나만 지정).
   - `AppNavHost` 의 `composable(Routes.Hidden)` 에서 query param 파싱 → `HiddenNotificationsScreen(initialFilter: SilentGroupKey?)`.
   - `HiddenNotificationsScreen` 내부 LazyColumn 은 기존 그룹 리스트 렌더 + `initialFilter` 가 있으면 해당 그룹만 스크롤/하이라이트.
   - 그룹 summary 의 contentIntent 는 이 filtered deep-link 를 사용.

5. **Journey 문서 갱신**
   - `docs/journeys/silent-auto-hide.md`:
     - Observable step 4~6 전면 재작성: 단일 요약 → 그룹별 요약 로직.
     - Exit state: tray 에 그룹 summary 들 + (선택) 루트 요약.
     - Known gaps 에 "cross-status 그룹핑 (PRIORITY/DIGEST 포함) 미구현" 추가.
     - Change log 엔트리.
   - `docs/journeys/hidden-inbox.md`:
     - Trigger 섹션에 sender/package 쿼리 기반 진입 추가.
     - Observable steps: initialFilter 가 있으면 해당 그룹을 강조하는 동작 추가.
     - Change log 엔트리.

6. **Verification recipe**
   - plan-implementer 는 구현 후 직접 ADB 로 검증:
     ```bash
     # 1. 같은 sender 로 3건, 다른 sender 로 2건, sender 없음 앱 알림 1건 posting
     adb -s emulator-5554 shell cmd notification post -S messaging -t "엄마" M1 "메시지 1"
     adb -s emulator-5554 shell cmd notification post -S messaging -t "엄마" M2 "메시지 2"
     adb -s emulator-5554 shell cmd notification post -S messaging -t "엄마" M3 "메시지 3"
     adb -s emulator-5554 shell cmd notification post -S messaging -t "광고" P1 "프로모션 1"
     adb -s emulator-5554 shell cmd notification post -S messaging -t "광고" P2 "프로모션 2"
     adb -s emulator-5554 shell cmd notification post -S bigtext -t "" Std1 "app-only"

     # 2. dumpsys 검증
     adb -s emulator-5554 shell dumpsys notification --noredact | grep -E "smartnoti_silent_group|setGroup"
     # 기대:
     # - "엄마" group summary 1건 + children 3건
     # - "광고" group summary 1건 + children 2건
     # - "Std1" 은 N=1 이므로 cancel (options Q3-A)

     # 3. summary 탭 시뮬레이션 (uiautomator 로 상단 shade 펼치고 "엄마 · 조용히 3건" 탭)
     #    → Android system UI 가 children expand. 확인은 수동.

     # 4. deep-link filter 검증
     adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity \
       -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden \
       -e com.smartnoti.app.extra.DEEP_LINK_SENDER "엄마"
     # → HiddenNotificationsScreen 이 엄마 그룹 스크롤/하이라이트
     ```

## Relationships

- Source journey: [silent-auto-hide](../journeys/silent-auto-hide.md). 본 plan 의 shipping 으로 Observable steps 전면 재작성 필요.
- 영향 받는 journey: [hidden-inbox](../journeys/hidden-inbox.md). deep-link trigger 확장.
- 인접 queued plan: [silent-archive-vs-process-split](2026-04-21-silent-archive-vs-process-split.md). 이 plan 과 #50 모두 `SilentHiddenSummaryNotifier.kt` / `silentSummaryJob` 를 건드림. 어느 쪽이 먼저 ship 되어도 다른 쪽은 rebase. 시너지: #50 의 ARCHIVED 상태는 이 plan 의 그룹핑 메커니즘을 재사용 가능.
- 관련 기능: [hidden-inbox-collapsible-groups](2026-04-20-hidden-inbox-collapsible-groups.md, shipped in #89). Hidden 화면의 in-app 그룹핑 UX. 이 plan 의 tray-side 와 독립이지만 그룹 정의 (sender vs package) 는 동일 정책을 공유하는 게 일관성 상 바람직. `SilentNotificationGroupingPolicy` 가 양쪽에서 재사용되도록 시작.

## Risks

1. **Notification rate limit**: 같은 앱이 짧은 시간에 10+ silent 알림을 게시하면 children 10개 + group summary 1개 = 11개가 거의 동시에 post 된다. Android 의 per-app rate limit (대략 10/sec) 에 근접. 완화: `silentSummaryJob` 의 combine + distinctUntilChanged 가 이미 debounce 역할. 필요 시 group summary 와 child 사이에 15ms 인터벌. plan-implementer 가 logcat 으로 `NotificationService: too many notifications` 경고 모니터.
2. **MessagingStyle detection false negative**: sender 추출이 style 이 아닌 extras 만 보기 때문에 일부 앱의 커스텀 style 은 놓칠 수 있음. Known gap 으로 기록.
3. **Group summary 갱신 race**: 그룹 카운트 변화와 summary repost 사이에 Android 가 group hierarchy 를 재구성하느라 flickering 할 수 있음. `postGroupSummary` 를 children posting 이후 150ms 지연으로 쉽게 완화.
4. **기존 "숨겨진 알림 N건" 사용자 혼동**: 기존 copy 기대. Change log + Known gaps 에 migration 노트 + 첫 실행시 설정 설명 추천.

## Decision log

- 2026-04-21: 사용자 요청으로 plan 초안 작성. 예시 "카톡 · 문자 · 전화 같은 발신자" → sender 기반 자동 grouping 이 v1 범위. 사용자 정의 조건 UI 는 후속 plan 으로 분리.
