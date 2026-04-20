---
id: hidden-inbox-collapsible-groups
title: 숨긴 알림 인박스 — 앱별 그룹 접기/펼치기
status: planned
owner: @wooilkim
journey: hidden-inbox
created: 2026-04-20
---

## Motivation

`HiddenNotificationsScreen` 의 앱별 그룹 카드 (`DigestGroupCard`) 는 현재 **펼쳐진 상태가 기본**이고, 각 그룹마다 `items.take(3)` 프리뷰가 항상 표시됨. 숨긴 알림이 여러 앱에 걸쳐 누적되면 (현재 emulator 기준 이미 13건, 실사용에서는 수십~수백 건까지 쉽게 쌓임) 화면이 세로로 길어져 스캔이 어렵고, "숨긴 알림을 빠르게 훑고 싶다" 는 원래 의도가 희석된다.

목표: **그룹은 기본적으로 접힌 상태** (앱명 + 건수 배지 + 최신 1건 요약만), 탭하면 펼쳐져 프리뷰 items 가 나타나는 UX 로 바꾼다.

## Goal

- Hidden 화면 진입 직후 각 그룹이 **collapsed** 상태로 렌더 (높이 ~72dp 수준)
- 그룹 헤더 탭 → expanded 상태로 전환 (기존 `items.take(3)` 프리뷰 + "최근 묶음 미리보기" 라벨 표시)
- 한 번에 여러 그룹이 펼쳐질 수 있음 (accordion 아님 — 복수 선택 가능)
- 전체 수평 spacing rhythm, 다크 테마 계층감, 배지/라벨 treatment 는 `ui-improvement.md` 의 "calm, trustworthy, efficient" 방향성 유지
- Digest 탭(`DigestScreen`) 은 **영향 없음** — 현재처럼 펼쳐진 상태 유지 (거기서는 요약 자체가 핵심이라 프리뷰가 바로 보여야 함)

## Non-goals

- `DigestGroupCard` 를 다른 소비자 (DigestScreen) 까지 바꾸지 않는다 — 카드 옵션으로 분기
- 접힌 상태에서의 스와이프 액션 / swipe-to-restore 같은 신규 인터랙션은 이번 plan 에 포함하지 않는다
- 일괄 재분류는 기존 bulk action (헤더의 "전체 모두 지우기", 그룹별 "모두 중요로 복구" / "모두 지우기") 를 그대로 유지 — 접기 state 와 독립
- 애니메이션 커스터마이징 (커스텀 spring 등) — Compose 의 `AnimatedVisibility` 기본값 그대로

## Scope

**건드리는 파일:**
- `ui/components/DigestGroupCard.kt` — `collapsible: Boolean = false` 파라미터 추가. true 면 rememberSaveable 로 expanded state 유지, 헤더 Row 에 chevron 아이콘 + `Modifier.clickable` 추가. false 면 기존 동작 유지 (기본값 — DigestScreen 은 무변경).
- `ui/screens/hidden/HiddenNotificationsScreen.kt` — `DigestGroupCard` 호출부에 `collapsible = true` 전달.
- (필요 시) `HiddenGroupCardWithBulkActions` 래퍼 — bulk action row 는 그룹이 expanded 일 때만 나타나도록 (collapsed 일 때는 chevron 만 보이게) 조정할지 결정 → **결정**: bulk action row 는 collapsed 상태에서는 숨기고, expand 시 함께 노출. 그렇지 않으면 접힌 목적 (수직 공간 회수) 이 퇴색.

**건드리지 않는 파일:**
- `ui/screens/digest/DigestScreen.kt` (DigestGroupCard 를 쓰지만 `collapsible` 미지정 → 기본값 false 로 현재와 동일)
- `domain/model/DigestGroupUiModel` (모델 변경 없음 — state 는 순수 UI)
- `NotificationRepository.toHiddenGroups` 등 데이터 레이어

## Tasks

1. **DigestGroupCard 확장 (tests-first)**
   - `DigestGroupCardTest` (Compose UI test 또는 robolectric — 현재 UI 테스트 인프라 확인 필요) 로 `collapsible = true` + 초기 expanded=false 시 `items.take(3)` 가 렌더되지 않음을 assert. 클릭 후 렌더됨을 assert.
   - 현재 `app/src/test` 에 UI 테스트가 거의 없으므로 (Journey 문서 tests 섹션 참고) 단위 테스트로 커버할 수 없다면 plan-implementer 가 수동 스크린샷 + uiautomator dump 로 대체 verification 할 것.
2. **구현**
   - `DigestGroupCard(..., collapsible: Boolean = false)` 파라미터 추가.
   - `collapsible = true` 일 때: `rememberSaveable { mutableStateOf(false) }` 로 expanded 보관. 헤더 Row 에 끝 쪽 chevron (회전 애니메이션) 추가 + Row 를 `Modifier.clickable` 로 감쌈.
   - Preview 부분 (`Text("최근 묶음 미리보기")` 부터 `items.take(3)`) 을 `AnimatedVisibility(visible = expanded)` 로 감쌈.
   - 헤더 접힘 상태에서 건 요약 한 줄 (`model.summary`) 은 표시, "최근 묶음 미리보기" 라벨과 3건 카드는 숨김.
3. **HiddenNotificationsScreen 호출부 변경**
   - 현재 `HiddenGroupCardWithBulkActions` wrapper 가 bulk actions + DigestGroupCard 를 묶어서 렌더 중인지 확인.
   - Bulk action row 도 `expanded` 상태에서만 보이도록 wrapper 가 expanded state 를 공유하거나 `DigestGroupCard` 가 slot 로 bulk row 를 받도록 리팩토링.
4. **Visual audit**
   - `ui-ux-inspector` agent 로 emulator 스크린샷 캡처 → 다크 테마 대비, chevron rotation, 접힌 높이 일관성 검증.
5. **Journey 문서 갱신**
   - `docs/journeys/hidden-inbox.md` Observable steps 6~8 갱신 — 그룹 카드가 기본 collapsed 임을 반영. Change log 에 이번 PR 링크 추가.
   - Verification recipe 에 `uiautomator dump 후 "최근 묶음 미리보기" 문자열 부재 → 카드 탭 후 존재` 체크 포함.

## Verification recipe (post-implementation)

```bash
# 1. Hidden 진입
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 2. 초기 상태 — collapsed: "최근 묶음 미리보기" 문자열이 없어야 함
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -c "최근 묶음 미리보기"
# expected: 0

# 3. 첫 그룹 헤더 탭 → expanded
adb shell input tap <x> <y>  # 그룹 헤더 중앙
sleep 1
adb shell uiautomator dump /sdcard/ui2.xml && adb shell cat /sdcard/ui2.xml | grep -c "최근 묶음 미리보기"
# expected: >= 1
```

## Open questions

- **Chevron icon 방향/회전**: Material 기본 `KeyboardArrowDown` 을 90° 회전 + Crossfade 으로 충분한지, 아니면 `ExpandMore` / `ExpandLess` 스위칭이 더 명확한지. — 구현 시 `ui-ux-inspector` 감사로 결정.
- **Bulk action row 위치**: expanded 에서 items 아래에 두는 게 기존 패턴인지, 헤더 바로 아래가 UX 상 더 자연스러운지. 현재 코드 (HiddenGroupCardWithBulkActions) 의 구조를 plan-implementer 가 먼저 읽고 판단.
- **상태 보존**: 화면 회전 / config change 시 expanded 상태 유지? `rememberSaveable` 로 처리 가능하나, DB emission 으로 인한 recomposition 시 state 가 새 그룹에 이어지는지 주의 — 그룹 id 는 packageName 이므로 stable.

## Relationships

- Source journey: [hidden-inbox](../journeys/hidden-inbox.md) — 현재 "기본 펼쳐짐" 동작이 observable step 7 에 명시되어 있음. 구현 후 이 journey 문서를 반드시 갱신.
- 관련 컴포넌트 사용처: [digest-inbox](../journeys/digest-inbox.md) — 같은 `DigestGroupCard` 를 쓰지만 이번 plan 에서는 **영향 없음** (기본값 collapsible=false 유지).
