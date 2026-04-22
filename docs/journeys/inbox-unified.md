---
id: inbox-unified
title: 정리함 통합 탭 (Digest + 보관 중 + 처리됨)
status: shipped
owner: @wooilkim
last-verified:
---

## Goal

Digest 로 묶인 알림과 SILENT 로 분류되어 숨겨진 알림을 **한 탭** (`정리함`) 아래 세 개의 서브탭 (`Digest / 보관 중 / 처리됨`) 으로 통합해, 사용자가 "나중에 훑어볼 알림" 을 한 화면에서 모두 관리할 수 있게 한다. Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 이 기존의 분리된 `Digest` 탭 + `Hidden` 딥링크 진입점을 하나의 `Routes.Inbox` 로 묶은 결과.

## Preconditions

- 온보딩 완료
- 현 BottomNav 4-tab 구성 (`홈 / 정리함 / 분류 / 설정`)

## Trigger

- 사용자가 하단 네비의 "정리함" 탭 탭 → `Routes.Inbox` 진입
- Home 의 `HomeNotificationAccessInlineRow` / 기타 Home 에서 정리함 진입 링크 탭
- 기존 딥링크 (`Routes.Digest` 의 replacement notification contentIntent, `Routes.Hidden` 의 silent summary contentIntent) 는 여전히 **레거시 route 를 직접 열어** Detail 까지 연결 — 정리함 통합 탭을 우회. AppNavHost 가 `Routes.Digest` / `Routes.Hidden` 을 계속 등록 유지.

## Observable steps

1. `InboxScreen` 마운트, 다음 상태 구독:
   - `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` — 필터된 전체 feed
   - `NotificationRepository.observeDigestGroupsFiltered(hidePersistentNotifications)` — Digest 앱 그룹
   - `SettingsRepository.observeSettings()` — persistent 필터
2. `ScreenHeader` — eyebrow "정리함" + title "알림 정리함" + subtitle "Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요."
3. `InboxTabRow` — 3개 세그먼트 (`Digest · N건 / 보관 중 · N건 / 처리됨 · N건`). 선택된 세그먼트는 `primaryContainer` 배경 + SemiBold.
4. 선택된 탭에 따라 기존 화면 composable 을 **재호스팅**:
   - `InboxTab.Digest` → `DigestScreen` 본체 (그룹 카드 LazyColumn). `observeDigestGroupsFiltered` + `toDigestGroups` 의 packageName grouping 계약 그대로.
   - `InboxTab.Archived` → `HiddenNotificationsScreen` (기본 탭 Archived 로 마운트). 내부에 `HiddenTabRow` 가 자체 ARCHIVED/PROCESSED 세그먼트를 또 유지하지만, 통합 탭에서는 사용자가 상위 `InboxTab.Archived` 를 탭해 진입.
   - `InboxTab.Processed` → 동일 `HiddenNotificationsScreen` 이 마운트되며 상위 탭만 "처리됨" 으로 표시 (현재 구현: `HiddenNotificationsScreen` 이 자체 HiddenTabRow 에서 Archived/Processed 를 다시 선택 가능 — 이는 의도된 중첩이며 후속 polish 대상).
5. 카운트 집계:
   - `digestCount` = `observeDigestGroupsFiltered` 의 모든 그룹 `count` 합.
   - `archivedCount` = `filteredNotifications.toHiddenGroups(SilentMode.ARCHIVED)` 의 `count` 합.
   - `processedCount` = `filteredNotifications.toHiddenGroups(SilentMode.PROCESSED)` 의 `count` 합.
6. Digest 탭 — `DigestGroupCard` preview 카드 탭 → `Routes.Detail.create(id)` 로 이동 (→ [notification-detail](notification-detail.md)).
7. 보관 중 / 처리됨 탭 — 그룹 헤더 탭으로 expand → preview + bulk action (모두 중요로 복구 / 모두 지우기) 노출, preview 카드 탭 → Detail 진입. (→ [hidden-inbox](hidden-inbox.md) `status: deprecated` — 관측 계약은 이 journey 로 이동, 세부 UI behavior 은 `HiddenNotificationsScreen` 에서 보존)
8. BottomNav "정리함" 탭 활성 표시 + Detail 진입 후 뒤로가기 시 Inbox 탭으로 복귀.

## Exit state

- `InboxScreen` 에 현재 DB 의 DIGEST / SILENT-ARCHIVED / SILENT-PROCESSED 알림이 선택된 서브탭에 따라 렌더됨.
- IGNORE row 는 Digest / 보관 중 / 처리됨 어느 서브탭에도 포함되지 않음 (`observeDigestGroupsFiltered` + `toHiddenGroups` 가 `status` 로 선필터). 오직 [ignored-archive](ignored-archive.md) 에서만 노출.
- 시스템 tray 원본의 교체 / 숨김 동작은 각 분류별 journey 의 라우팅 계약을 그대로 따름 (→ [digest-suppression](digest-suppression.md), [silent-auto-hide](silent-auto-hide.md)).

## Out of scope

- 원본 tray 숨김 / 교체 라우팅 (→ [digest-suppression](digest-suppression.md), [silent-auto-hide](silent-auto-hide.md))
- 개별 알림 재분류 / 피드백 (→ [notification-detail](notification-detail.md), [rules-feedback-loop](rules-feedback-loop.md))
- IGNORE 아카이브 (→ [ignored-archive](ignored-archive.md))
- 통합 탭 내 bulk action 확장 (전체 Digest 삭제, 전체 SILENT 복구 등 — 현재 그룹별로만)

## Code pointers

- `ui/screens/inbox/InboxScreen` — 헤더 + 서브탭 세그먼트 + 본문 위임
- `ui/screens/digest/DigestScreen` — Digest 서브탭 본체 (재사용)
- `ui/screens/hidden/HiddenNotificationsScreen` — 보관 중 / 처리됨 서브탭 본체 (재사용)
- `data/local/NotificationRepository#observeDigestGroupsFiltered` + `toDigestGroups`
- `data/local/NotificationRepository#observeAllFiltered` + `toHiddenGroups(silentModeFilter = ...)`
- `domain/model/SilentMode` — ARCHIVED / PROCESSED
- `navigation/Routes#Inbox` + `navigation/Routes#Digest` + `navigation/Routes#Hidden` (후자 둘은 deep-link 호환용으로 유지)
- `navigation/BottomNavItem` — 4-tab (`홈 / 정리함 / 분류 / 설정`)

## Tests

- `InboxScreen` 직접 UI 테스트 부재 — 하위 composable 테스트 + 집계 로직 테스트로 간접 커버
- `HiddenGroupsSilentModeFilterTest` — `toHiddenGroups` 의 silentMode 필터 (Hidden 재사용분)
- `BottomNavItemsTest` — 4-tab 순서 + 분류/정리함 라벨 pin

## Verification recipe

```bash
# 1. Digest 로 분류될 반복 알림 다건 포스팅
for i in 1 2 3 4; do
  adb shell cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 ${i}건"
done

# 2. SILENT ARCHIVED 가 쌓일 정도로 일반 알림 몇 건 포스팅
for i in 1 2; do
  adb shell cmd notification post -S bigtext -t "일반" "Promo$i" "프로모션 테스트"
done

# 3. 앱 실행
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. BottomNav "정리함" 탭 탭 → `ScreenHeader 알림 정리함` + `InboxTabRow` 세그먼트 3개 (Digest / 보관 중 / 처리됨) 확인
# 5. "Digest" 세그먼트 기본 선택 상태에서 앱별 그룹 카드 렌더
# 6. "보관 중" 탭 → HiddenNotificationsScreen 마운트, ARCHIVED 카드 렌더
# 7. "처리됨" 탭 → HiddenNotificationsScreen 의 처리됨 세그먼트로 전환
# 8. 각 preview 카드 탭 → Detail 진입 → 뒤로가기로 동일 서브탭 복귀 확인
```

## Known gaps

- 보관 중 / 처리됨 서브탭을 선택하면 `HiddenNotificationsScreen` 이 자체 HiddenTabRow (ARCHIVED / PROCESSED) 를 다시 노출 — 상위/하위 세그먼트가 이중으로 보이는 nested tab UX. Task 11 구현은 재사용을 우선해 이를 수용했고, 후속 polish 에서 중복 제거 (상위 세그먼트 탭 선택 시 하위 세그먼트 숨기거나 사전 선택) 가 필요.
- Digest 서브탭 count badge 는 앱 그룹 내 item 수의 합으로 계산 — 묶기 전 개별 알림 수가 아님. UI 가 의도된 동작이나 copy 로 명시 안 함.
- IGNORE 카운트는 어느 서브탭 헤더에도 표시되지 않음 (의도) — Settings 의 `showIgnoredArchive` 토글을 켜야만 별도 경로로 노출.
- Recipe 는 journey-tester 가 ADB 로 아직 end-to-end 검증하지 않음 — `last-verified` 비어 있음.
- 기존 `digest-inbox` / `hidden-inbox` 문서는 `status: deprecated` + `superseded-by: inbox-unified` 로 전환되지만, tray deep-link (`Routes.Digest` / `Routes.Hidden` ) 는 legacy route 를 직접 열어 통합 탭을 우회하므로 두 문서의 "진입 경로 = 딥링크" 서브셋은 여전히 계약 — 해당 부분은 deprecated 표시와 함께 Code pointers + Verification 으로 남긴다.

## Change log

- 2026-04-22: 신규 문서화 — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 구현 결과물. `InboxScreen` 이 Digest + Hidden 내용을 세그먼트 서브탭으로 통합. 레거시 `digest-inbox` / `hidden-inbox` journey 는 `status: deprecated` 처리되고 이 문서가 현재 계약을 소유. `Routes.Digest` / `Routes.Hidden` 은 tray deep-link 호환성을 위해 등록 유지. `last-verified` 는 journey-tester 의 ADB recipe 실행 전까지 비워둠 (per `.claude/rules/docs-sync.md`).
