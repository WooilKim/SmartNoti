---
id: digest-inbox
title: 정리함 인박스 (legacy — Digest 전용)
status: deprecated
superseded-by: docs/journeys/inbox-unified.md
owner: @wooilkim
last-verified: 2026-04-26
---

> **Deprecated 2026-04-22** — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 이후 Digest 전용 화면은 `정리함` 통합 탭 ([inbox-unified](inbox-unified.md)) 의 "Digest" 서브탭으로 호스팅된다. `Routes.Digest` 는 replacement notification contentIntent deep-link 타겟으로 계속 사용되어 `DigestScreen` composable 자체는 유지되지만, 1차 진입 경로 + Observable steps 의 owner 는 inbox-unified 다. 이 문서는 레거시 탭 직접 진입 경로와 DigestScreen 단독 동작 계약에 대한 기록으로 남긴다.

## Goal

DIGEST 로 분류된 알림을 **앱 단위로 묶어** 하나의 카드로 보여주고, 카드 안에서 최근 항목을 빠르게 훑어 상세로 진입할 수 있게 한다.

## Preconditions

- 알림이 DIGEST 로 분류되어 DB 에 저장됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료

## Trigger

- 사용자가 하단 네비의 "정리함" 탭 탭, 또는
- Home 의 "정리함 … 열기" 카드 탭

## Observable steps

1. `navigateToTopLevel(Routes.Digest.route)` → NavController 가 Digest composable 렌더링.
2. `DigestScreen` 이 `observeSettings()` + `NotificationRepository.observeDigestGroupsFiltered(hidePersistentNotifications)` 를 collect.
3. 저장소 쪽에서 `toDigestGroups()` 변환:
   - DIGEST 상태 알림만 필터 — IGNORE 는 `status != DIGEST` 이므로 자동 제외, 정리함 탭에는 절대 포함되지 않음 (→ [ignored-archive](ignored-archive.md))
   - `packageName` 으로 grouping
   - 각 그룹을 `DigestGroupUiModel(id="digest:${pkg}", appName, count, summary="{app} 관련 알림 {N}건", items)` 로 매핑
4. LazyColumn 이 `DigestGroupCard` 렌더링:
   - 앱명 + 카운트 badge
   - 요약 문구
   - "최근 묶음 미리보기" 섹션 + 최근 3건 `NotificationCard`
5. preview 카드 탭 → `Routes.Detail.create(notificationId)` 로 이동 (→ [notification-detail](notification-detail.md)).

## Exit state

- 정리함 탭에 앱별 DIGEST 묶음이 렌더링됨.
- 시스템 tray 원본: opt-in 된 앱은 제거 + replacement 교체 (→ [digest-suppression](digest-suppression.md)), 그 외는 원본 유지.

## Out of scope

- 원본 숨김/교체 라우팅 (→ [digest-suppression](digest-suppression.md))
- 그룹 내 일괄 처리 (현재 개별 카드 탭 → Detail 만 가능)
- 그룹 삭제/무시 (미구현)

## Code pointers

- `ui/screens/digest/DigestScreen`
- `ui/components/DigestGroupCard`
- `data/local/NotificationRepository#observeDigestGroupsFiltered` + `toDigestGroups`
- `domain/model/DigestGroupUiModel`
- `navigation/Routes#Digest`, `navigation/BottomNavItem` ("정리함" 라벨)

## Tests

- 분류/저장 레이어가 간접 커버. DigestScreen 직접 테스트는 현재 없음.

## Verification recipe

```bash
# 1. Digest 로 분류될 알림 여러 건을 한 앱에서 게시 (중복 signature)
for i in 1 2 3 4; do
  adb shell cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 ${i}건"
done

# 2. 정리함 탭 진입 → 한 개 그룹 카드에 count 4 로 묶여 보이는지 확인
adb shell am start -n com.smartnoti.app/.MainActivity
# (1080x2400 에뮬레이터 기준 "정리함" 탭 좌표: 539, 2232)
```

## Known gaps

- 그룹별 "모두 중요로 변경" / "모두 무시" 액션 부재. → plan: `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` ("모두 중요로 변경" + "모두 지우기" 두 액션; "모두 무시" 는 별도 plan 후속)
- DigestScreen 직접 UI 테스트 부재.
- Digest 알림이 0건이면 empty-state 만 보여주는데, suppress opt-in 유도 같은 교육용 문구 없음.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 Digest 기본 뷰에서 제외됨을 명시 — `observeDigestGroupsFiltered` 는 `status == DIGEST` 로만 필터하므로 IGNORE row 는 자동 배제, [ignored-archive](ignored-archive.md) 화면에서만 노출. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6 (#185 `9a5b4b9`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
- 2026-04-22: Fresh APK (`lastUpdateTime=2026-04-22 03:46:30`, post-IGNORE) ADB end-to-end 검증 — Observable steps 1–5 + Exit state 전부 일치. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신.
- 2026-04-22: **Deprecated** — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 이후 정리함 통합 탭 ([inbox-unified](inbox-unified.md)) 이 Digest + 보관 중 + 처리됨 세그먼트를 통합 소유한다. `Routes.Digest` 는 tray deep-link 용으로 유지. 문서는 참조용으로 남김.
- 2026-04-26: Plan `2026-04-26-inbox-bundle-preview-see-all` ship — `DigestGroupCard` preview 가 inline expansion 지원 ("전체 보기 · ${remaining}건 더" CTA). Legacy Digest 진입 경로 (`Routes.Digest` deep-link) 도 동일 컴포넌트를 공유하므로 `items.size > 3` 그룹의 4번째 이후 row Detail 진입 가능. 활성 contract 는 [inbox-unified](inbox-unified.md).
- 2026-04-26: Rotation sweep (deprecated journey, oldest by `last-verified`). emulator-5554, APK `lastUpdateTime=2026-04-26 15:43:43`. Recipe: 4× `cmd notification post -S bigtext -t Coupang Deal{i}` (모두 `pkg=com.android.shell` 로 posting → 기존 Shell 그룹에 흡수, count 13 → 17) → BottomNav `정리함` (407,2274) → inbox-unified host 의 Digest 서브탭 default. `Shell 17건` `DigestGroupCard` 렌더 (앱명 + count badge + summary `Shell 관련 알림 17건` + `최근 묶음 미리보기` + `탭하면 원본 알림 상세를 확인할 수 있어요`). 다른 그룹 (`SmartNoti Test Notifier 3건`) 도 같은 컴포넌트로 렌더. preview row tap (540,1000) → `알림 상세` + `왜 이렇게 처리됐나요?` + `전달 모드 · Digest 묶음 전달` (Routes.Detail.create deep-link). Observable steps 1–5 + Exit state 모두 일치 (legacy `Routes.Digest` composable + `DigestGroupCard` contract 무손상). DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-26.
