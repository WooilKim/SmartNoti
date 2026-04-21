---
id: priority-inbox
title: 검토 대기 알림 (passthrough review)
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

PRIORITY 로 분류된 알림을 **"SmartNoti 가 건드리지 않은 알림"** 으로 재프레이밍해서, 사용자가 이 판단이 맞는지 검토하고 필요하면 즉시 Digest / 조용히 / 규칙으로 재분류할 수 있게 한다. 시스템 알림센터의 원본은 **절대 건드리지 않아** 사용자가 기대하는 즉시 전달(소리 / 헤즈업 / 잠금화면)이 정상 동작하게 한다.

> 2026-04-21 Rules UX v2 Phase A 이후, 이 화면은 더 이상 BottomNav 상의 독립 탭이 아니다. Home 의 "검토 대기" 패스스루 카드 탭을 통해서만 진입한다. Route (`Routes.Priority`) 는 유지되어 replacement notification deep-link 의 parent 로도 계속 사용된다.

## Preconditions

- 알림이 PRIORITY 로 분류되어 DB 에 저장됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료 상태 (→ [onboarding-bootstrap](onboarding-bootstrap.md))

## Trigger

- Home 의 `HomePassthroughReviewCard` ("SmartNoti 가 건드리지 않은 알림 N건 — 검토하기") 탭, 또는
- replacement 알림의 deep-link 가 `Routes.Priority.route` 를 parent 로 지정한 경우

(Priority 가 BottomNav 탭에서 제거됐으므로 탭 직접 진입 경로는 더 이상 없음.)

## Observable steps

1. Home 의 `HomePassthroughReviewCard` 를 탭 → `navigateToTopLevel(Routes.Priority.route)` → NavController 가 Priority composable 렌더링.
2. `PriorityScreen` 이 `SettingsRepository.observeSettings()` 와 `NotificationRepository.observePriorityFiltered(hidePersistentNotifications)` 를 collect.
3. 상단 `ScreenHeader` — eyebrow "검토" + 제목 "SmartNoti 가 건드리지 않은 알림" + 서브타이틀 "이 판단이 맞는지 확인하고, 필요하면 바로 Digest / 조용히 / 규칙으로 보낼 수 있어요." (empty 상태에서는 제목이 "검토 대기 알림" 으로 바뀌고 `EmptyState` 카드가 보임.)
4. 리스트 상단에 `SmartSurfaceCard` — "검토 대기 N건" + 보조 설명.
5. LazyColumn 이 status = PRIORITY 인 `NotificationUiModel` 리스트를 카드로 표시. IGNORE row 는 `observePriorityFiltered` 가 `status == PRIORITY` 로 선필터하므로 여기에 포함되지 않음 — IGNORE 는 [ignored-archive](ignored-archive.md) 전용. 각 카드 아래에는 `PassthroughReclassifyActions` 영역이 함께 렌더 — "이 판단을 바꿀까요?" 라벨 + `→ Digest` / `→ 조용히` OutlinedButton 두 개 + `→ 규칙 만들기` TextButton.
6. 사용자가 `→ Digest` / `→ 조용히` 탭 → `PassthroughReviewReclassifyDispatcher.reclassify` 가 `NotificationFeedbackPolicy` 로 상태를 바꿔 `NotificationRepository.updateNotification()` 수행 (규칙 생성은 하지 않음).
7. 사용자가 `→ 규칙 만들기` 탭 → `onCreateRuleClick(notification)` 을 통해 rules feedback flow 로 연결.
8. 사용자가 카드 본문 탭 → `Routes.Detail.create(notificationId)` 로 navigate (→ notification-detail).

## 시스템 tray 처리

- `SourceNotificationRoutingPolicy.route(PRIORITY, *, *)` → `SourceNotificationRouting(cancelSourceNotification = false, notifyReplacementNotification = false)` 고정.
- 원본 알림은 항상 시스템 tray 에 유지됨. SmartNoti 가 cancel / replace 를 시도하지 않음.
- `suppressSourceForDigestAndSilent` 등 사용자 설정과 무관하게 PRIORITY 는 건드리지 않는 것이 불변조건. 재분류 버튼으로 상태를 바꾸면 그때부터 해당 분류 (DIGEST/SILENT) 의 routing 이 적용됨.

## Delivery profile

- `DeliveryProfilePolicy` 가 각 알림에 대해 AlertLevel / VibrationMode / HeadsUp / LockScreen visibility 조합 프로파일을 계산.
- 채널은 `ReplacementNotificationChannelRegistry` 가 프로파일 조합마다 사전 생성해 둠 (PRIORITY 의 경우 LOUD / SOFT / QUIET × STRONG / LIGHT / OFF × headsup on/off 등).

## Exit state

- 검토 화면에 현재 PRIORITY 분류된 알림이 모두 표시됨.
- 시스템 tray 의 원본 그대로 유지.
- 사용자는 인라인 액션으로 즉시 재분류하거나, Detail 로 이동해 상세 재분류/룰 고정 등 후속 액션 수행 가능 (→ rules-feedback-loop).
- 재분류 후에는 해당 알림이 PRIORITY 에서 빠져 Home 카드 count 및 이 화면의 리스트에서 동시에 사라짐.

## Out of scope

- 분류 로직 자체 (→ [notification-capture-classify](notification-capture-classify.md))
- "중요로 고정" feedback action 의 룰 저장 경로 (→ rules-feedback-loop)
- Priority 알림의 사운드/헤즈업 실제 재생 (플랫폼/DeliveryProfile 책임)
- Home 의 `HomePassthroughReviewCard` 자체의 렌더링/카운트 집계 (→ [home-overview](home-overview.md))

## Code pointers

- `ui/screens/priority/PriorityScreen` — 검토 화면 본체, 인라인 액션 포함
- `ui/components/HomePassthroughReviewCard` — Home 진입 카드
- `domain/usecase/PassthroughReviewReclassifyDispatcher` — 인라인 재분류 로직
- `domain/usecase/NotificationFeedbackPolicy` — 상태 전이
- `data/local/NotificationRepository#observePriorityFiltered`
- `domain/usecase/DeliveryProfilePolicy`
- `notification/SourceNotificationRoutingPolicy` — PRIORITY 분기
- `notification/ReplacementNotificationChannelRegistry` — 채널 정의
- `navigation/Routes.Priority` — route 유지 (탭 아님, 카드 tap + replacement parent 용)
- `navigation/BottomNavItem` — Priority 엔트리 **없음** (홈 / 정리함 / 규칙 / 설정만)

## Tests

- 분류/룰 레이어 테스트로 간접 커버. `PriorityScreen` 단독 테스트는 현재 없음.
- `SourceNotificationRoutingPolicyTest#persistent_hidden_priority_keeps_source_notification_and_skips_replacement`
- `HomePassthroughReviewCardStateTest` — 카드 state machine (count=0 vs count>0).

## Verification recipe

```bash
# 1. VIP 발신자 또는 우선순위 키워드가 포함된 알림 게시
adb shell cmd notification post -S bigtext -t "은행" Bank "인증번호 123456"

# 2. 시스템 tray 원본 유지 확인
adb shell dumpsys notification --noredact | grep -B1 -A4 "인증번호"

# 3. 앱 진입 → Home 화면의 "검토 대기" 카드에 count 증가 확인
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. Home 의 HomePassthroughReviewCard 탭 → 검토 화면 진입, 해당 알림이 카드로 보이고
#    카드 아래에 "→ Digest / → 조용히 / → 규칙 만들기" 인라인 액션이 보이는지 시각 확인
# 5. 예: "→ Digest" 탭 → 카드가 리스트에서 사라지고 Home count 가 감소, Digest 탭에 추가됨
```

## Known gaps

- 검토 화면 자체에 일괄 처리(모두 읽음/모두 재분류) 액션 없음 — 여전히 카드별 처리.
- `PriorityScreen` / `PassthroughReclassifyActions` 의 Compose UI 테스트 부재.
- Verification recipe 의 `엄마` 고정값이 rules-feedback-loop sweep 이후 남은 `person:엄마 → DIGEST` rule 에 의해 DIGEST 로 라우팅되어 검증이 무효화될 수 있어 은행/인증번호 경로로 변경됨. 사용자 환경에 따라 다른 키워드가 필요할 수 있음.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop tick 재검증 (PASS, PR 추가). Recipe fragility 관련 Known gap 추가
- 2026-04-21: v1 loop tick re-verify #2 (PASS). Keyword 기반 PRIORITY 경로 (은행/인증번호) 로 PERSON DIGEST 규칙 오염을 우회, fresh unique tag `PriIn0421T3` 로 DB+UI+Detail end-to-end 확증
- 2026-04-21: Rules UX v2 Phase A shipped (PR #140/#141/#142/#143). Priority 탭을 "검토 대기 알림" 으로 재프레이밍 — BottomNav 에서 제거, Home `HomePassthroughReviewCard` 탭으로만 진입. 화면 제목 "SmartNoti 가 건드리지 않은 알림" + 인라인 재분류 액션 (`→ Digest` / `→ 조용히` / `→ 규칙 만들기`) 추가. Route (`Routes.Priority`) 는 replacement notification parent 로 유지. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 검토 대기 리스트에서 제외됨을 명시 — `observePriorityFiltered` 는 `status == PRIORITY` 로만 필터하므로 IGNORE row 는 자동 배제, [ignored-archive](ignored-archive.md) 화면에서만 노출. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6 (#185 `9a5b4b9`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
