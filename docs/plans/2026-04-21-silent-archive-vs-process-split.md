---
id: silent-archive-vs-process-split
title: 조용히 보관 vs 조용히 처리 — 두 하위 상태로 분리
status: planned
owner: @wooilkim
journey: silent-auto-hide
created: 2026-04-21
---

## Motivation

현재 SmartNoti 의 `SILENT` 분류는 **한 종류뿐이고, 결과적으로 두 가지 다른 UX 기대를 충돌시킴**:

- **"조용히 처리"** — "이미 봐도 되는 것, 나중에 묶어서 훑어도 되는 것". 알림센터에서 치우고 SmartNoti 안에서만 열람하는 게 자연스러움.
- **"조용히 보관"** — "지금 중요한 건 아니지만 아직 열어보진 않음". 알림센터에서 아예 사라지면 사용자가 존재 자체를 잊음. 시스템 tray 의 낮은 중요도 슬롯에 그대로 두는 편이 "확인 대기 중" 이라는 상태를 유지시켜 줌.

지금 `silent-auto-hide` 는 SILENT 로 분류되자마자 원본을 무조건 cancel 하기 때문에, 두 의도가 한 동작으로 뭉개져 있다. 사용자가 "이건 아직 지우기 싫은데" 하고 tray 에서 참고용으로 남겨두고 싶어도 방법이 없다.

사용자 제안: **조용히 처리** (in-app only) 와 **조용히 보관** (tray 에 낮은 중요도로 유지) 을 분리.

## Goal

- `SILENT` 에 **두 하위 상태** `SILENT_ARCHIVED` / `SILENT_PROCESSED` 도입 (또는 `NotificationEntity` 에 `silentMode` 필드 추가).
- 기본 분류 경로: 새 SILENT → `SILENT_ARCHIVED` (시스템 tray 에 낮은 중요도로 유지, Home/Hidden 인박스에도 표시).
- 사용자가 "처리 완료" 의도를 보이면 → `SILENT_PROCESSED` 로 전이 (tray 에서만 제거, 앱 내부 이력으론 유지).
- 전이 트리거 후보 (plan-implementer 가 prototype 해보고 ui-ux-inspector 감사로 결정):
  1. Hidden 화면에서 사용자가 카드를 탭해 Detail 을 열었을 때 — 본 것이므로 "처리됨".
  2. Hidden 화면 상단의 "모두 처리 완료" 액션 — 명시적 bulk 처리.
  3. 시스템 tray 에서 알림을 swipe-dismiss — 사용자가 tray 에서 치웠으므로 의도적 처리.
  4. (보류) 시간 기반 자동 처리 (N일 경과 후 자동 전이) — 이번 plan 에선 제외, Known gap 으로만 기록.
- Hidden 화면 UX:
  - 상단 두 탭 / 세그먼트: "보관 중" (기본) / "처리됨". "보관 중" 이 비어 있을 땐 "모두 처리됐어요" 빈 상태.
  - "처리됨" 은 읽기 전용 아카이브 뷰 — 검색/삭제/되돌리기 가능.
- Home/Insight 카운트는 **보관 + 처리** 합계를 그대로 사용 (현재 동작 유지). StatPill 은 필요하면 세부 쪼개기까지 검토하되 이번 plan 에선 건드리지 않음.

## Non-goals

- 분류 알고리즘 자체 변경 — 어떤 알림이 SILENT 가 되는지는 별건. 이번 plan 은 **SILENT 된 후의 저장/표시 방식**만 다룸.
- `DIGEST` 에 대응하는 분리 도입 — Digest 는 이미 "묶어서 한 번에 보여주기" 로 목적이 뚜렷, 하위 상태 분리 불필요.
- 기존 `silent-auto-hide` 의 요약 알림 (`숨겨진 알림 N건`) 제거 — 요약은 여전히 유용. 다만 count 는 "보관 중" 만 집계하도록 바뀌어야 함 (처리됨은 이미 tray 바깥).
- 시간 기반 자동 아카이브/삭제.

## Scope

### 건드리는 파일

- `domain/model/NotificationUiModel.kt` — `NotificationStatusUi` 는 유지. UI 모델에 `silentMode: SilentMode?` 필드 추가 (`SilentMode.ARCHIVED` / `SilentMode.PROCESSED`, 혹은 `null` for non-SILENT).
- `data/local/NotificationEntity.kt` — Room entity 에 `silentMode: String?` 컬럼 추가 + 마이그레이션 (SCHEMA_VERSION 증가).
- `data/local/NotificationDao.kt` — observe/count 쿼리에 `silentMode` 필터 추가.
- `data/local/NotificationRepository.kt` — `toHiddenGroups` / `observeAllFiltered` 에 `silentMode` 파라미터 전달, `markSilentProcessed(ids)` 등 mutator 추가.
- `notification/SourceNotificationRoutingPolicy.kt` — SILENT 일 때 현재는 `cancelSourceNotification = true` 고정. **보관 초기 상태에서는 cancel = false** + 낮은 importance 채널로 바꾼다 (기존 source 알림을 IMPORTANCE_MIN 채널로 재게시하거나, 원본 유지하되 importance 조정 — 구현 시 Android API 제약 확인 필요).
- `notification/SmartNotiNotificationListenerService.kt` — 분류 결과가 SILENT_ARCHIVED 면 cancel 하지 말고, SILENT_PROCESSED 로 전이될 때 cancel 호출.
- `notification/SilentHiddenSummaryNotifier.kt` — 요약 count 를 "보관 중" 만 집계. 요약 자체의 의미 카피 업데이트 ("보관 중 N건 · 탭: 목록 보기").
- `ui/screens/hidden/HiddenNotificationsScreen.kt` — "보관 중" / "처리됨" 세그먼트 탭. 기본 탭 = 보관 중.
- `ui/screens/detail/NotificationDetailScreen.kt` — Detail 진입이 transition trigger 라면 여기서 `markSilentProcessed(id)` 호출 (SILENT_ARCHIVED 일 때만).
- `domain/usecase/NotificationFeedbackPolicy.kt` — "조용히 유지" 액션이 새 SILENT row 를 만들 때 default mode 결정.
- `docs/journeys/silent-auto-hide.md` — 관측 가능한 단계 재작성 (SILENT_ARCHIVED → SILENT_PROCESSED 전이 구간 포함).
- `docs/journeys/hidden-inbox.md` — "보관/처리" 탭 반영.

### 건드리지 않는 파일

- `NotificationClassifier` — 분류 결과 자체는 동일, 이후 상태 전이만 분기.
- Digest 경로 전체.
- Home StatPill 카운트 계산 (`HomeNotificationInsightsBuilder` 등) — 총 SILENT 수 그대로 유지.
- Rules/Rules-feedback-loop — sender/app 기반 룰 저장 경로 그대로.

## Open questions

- **Android API 로 "tray 에 낮은 importance 로 유지" 가 깔끔하게 가능한가?** 원본 앱 알림의 importance 는 `NotificationChannel` 에 묶여 있어서 listener service 가 런타임에 바꾸지 못한다. 현실적 옵션:
  1. 원본을 cancel 하지 않는다 (가장 단순). 그대로 tray 에 남음, heads-up 여부는 원본 채널 설정 그대로.
  2. 원본 cancel + `smartnoti_silent_archive` 채널 (IMPORTANCE_MIN) 로 SmartNoti 가 대체 알림을 게시. 사용자가 tap 하면 Detail 로 이동.
  3. 원본 유지 + SmartNoti 가 `smartnoti_silent_summary` 요약은 그대로 제공 (현재와 유사하되 count 는 보관 중만).
  → 첫 구현은 **옵션 1** 로 시작 권장 (복잡도 가장 낮음, 사용자 기대 "tray 에 그대로 보임"에 가장 잘 맞음). 추후 사용자 피드백으로 옵션 2 고려.
- **"보았다 = 처리 완료" 로 간주해도 되는가?** 사용자가 무심코 Detail 을 열어본 것뿐인데 tray 에서 사라지면 역으로 당황. 대안: Detail 에서 "처리 완료로 표시" 버튼을 명시적으로 두고, 탭만으로는 아카이브 상태 유지.
- **Swipe-dismiss 를 "처리됨" 으로 해석?** 현재 `silent-auto-hide` 요약 알림은 swipe 를 "확인함" 으로 해석한다. 개별 원본 알림 swipe 에서도 같은 규칙을 적용하려면 `NotificationListenerService` 의 `onNotificationRemoved` 를 fully 구독해야 한다. Android 14+ dismiss reason API 참조 필요.
- **기존 SILENT row 마이그레이션**: 업그레이드 시점 기존 SILENT 는 모두 `SILENT_PROCESSED` 로 매핑 (이미 tray 에서 사라진 상태이므로 현실과 일치). 빈 "보관 중" 탭 → 풀 리프레시 후 새 알림부터 아카이브 경로 진입.

## Tasks

1. **[shipped via PR #91]** **데이터 모델 확장** (tests-first)
   - `NotificationEntity` 컬럼 추가 + Room 마이그레이션. `NotificationRepositoryTest` 로 신규 필드 round-trip 확인.
   - `SilentMode` enum + UI 모델 필드 추가.
2. **[IN PROGRESS via PR #100]** **라우팅 정책 분기**
   - `SourceNotificationRoutingPolicy.route(SILENT, mode=ARCHIVED)` → cancel=false.
   - `SourceNotificationRoutingPolicyTest` 에 신규 케이스 추가 (SILENT × ARCHIVED / PROCESSED 2×2 매트릭스).
   - 호출부 (`SmartNotiNotificationListenerService`) 는 이번 Task 에서 건드리지 않음 — `silentMode` 기본값 `null` 으로 legacy cancel 동작 유지. 실제 mode 전달은 Task 3 (Detail 전이 경로) 에서 함께 배선.
3. **전이 경로 구현 (Detail 기반 1차)**
   - `NotificationFeedbackPolicy` / `NotificationDetailScreen` 에 "처리 완료로 표시" 액션 추가.
   - Repository mutator 구현.
4. **Hidden 탭 분리**
   - Compose 세그먼트 탭 추가, 각 탭 LazyColumn.
   - Empty states: "보관 중" 0건일 때, "처리됨" 0건일 때.
5. **요약 알림 카운트 재조정**
   - `silentSummaryJob` 에서 count 쿼리를 `silentMode == ARCHIVED` 로 필터.
   - 카피 업데이트.
6. **저널 문서 / 인박스 UX 저널 갱신**
   - `docs/journeys/silent-auto-hide.md` / `hidden-inbox.md` 관측 단계 재작성.
   - Verification log 추가 — 보관/처리 분리가 실제로 관측되는지 recipe.
7. **Visual audit**
   - `ui-ux-inspector` agent 로 Hidden 탭 디자인 감사.

## Verification recipe (post-implementation)

```bash
# 1. 보관(archive) 상태로 남는지 확인
adb shell cmd notification post -S bigtext -t "Promo" Promo1 "오늘만 30% 할인"
# → dumpsys notification --noredact | grep Promo1 에 원본 알림이 **여전히 존재**
#   (기존 silent-auto-hide 와 달리 cancel 되지 않음)
#   DB row 의 silentMode = ARCHIVED

# 2. 앱 내 Hidden → "보관 중" 탭에서 해당 카드 확인
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 3. Detail 진입 + "처리 완료로 표시"
#    → 원본 알림이 tray 에서 사라짐
#    → "처리됨" 탭으로 이동, "보관 중" count 감소
```

## Relationships

- Source journey: [silent-auto-hide](../journeys/silent-auto-hide.md) — 이 plan 이 shipped 되면 해당 journey 의 Observable steps 전면 개정 필요.
- 연관 journey: [hidden-inbox](../journeys/hidden-inbox.md) — "보관/처리" 탭으로 UX 구조 변경.
- 연관 journey: [notification-detail](../journeys/notification-detail.md) — "처리 완료로 표시" 액션 추가 지점.
- 선행 plan: [hidden-inbox-collapsible-groups](2026-04-20-hidden-inbox-collapsible-groups.md) — collapsible 작업이 먼저 들어가면 이 plan 의 UI 변경과 탭 레이아웃 설계에서 충돌 가능 → plan-implementer 는 순서를 의식해서 하나를 먼저 shipping 한 뒤 다른 하나를 rebase.

## Decision log

- 2026-04-21: 사용자 제안으로 plan 초안 작성. "tray 에 낮은 중요도로 유지" 의 구현 옵션은 open question 으로 남기고, 첫 구현은 가장 단순한 "cancel 생략" 경로 권장.
