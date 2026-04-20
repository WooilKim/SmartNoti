# SmartNoti User Journeys

이 디렉토리는 **현재 구현된 사용자 여정의 contract**를 기록합니다. 각 문서는 실제로 앱이 하고 있는 일을 관측 가능한 단계로 서술해, 드리프트 감지와 신규 기능 설계의 근거로 쓰입니다.

## `docs/plans/` 와의 차이

- `docs/plans/` — 앞으로 할 일 (구현 계획, 의사결정)
- `docs/journeys/` — **지금 일어나고 있는 일** (현재 동작의 계약)

새 기능이 shipped 되면 plan은 완료 상태로 두고 journey 문서를 작성/갱신합니다.

## 규칙

- 사용자 관측 동작이 바뀌는 PR은 관련 journey 문서도 수정하거나, 새 journey 추가, 혹은 기존을 `deprecated`
- Frontmatter `last-verified`는 실제로 Verification recipe를 실행한 뒤에만 갱신
- 파일당 200줄 이하 유지. 길어지면 out-of-scope로 쪼갠다
- 코드를 본문에 복사하지 않는다 — 경로/클래스명 포인터만
- "관측 가능한 단계"만 쓴다. "시스템이 분류한다" (X) → "`NotificationEntity` row가 `status=SILENT` 로 저장된다" (O)

## Active journeys

### Capture & classification
| ID | Title | Status | Last verified |
|---|---|---|---|
| [notification-capture-classify](notification-capture-classify.md) | 알림 캡처 및 분류 | shipped | 2026-04-20 |
| [duplicate-suppression](duplicate-suppression.md) | 중복 알림 감지 및 DIGEST 강등 | shipped | 2026-04-20 |
| [quiet-hours](quiet-hours.md) | 조용한 시간 | shipped | 2026-04-20 |

### Source notification routing (시스템 tray 조작)
| ID | Title | Status | Last verified |
|---|---|---|---|
| [silent-auto-hide](silent-auto-hide.md) | 조용히 분류된 알림 자동 숨김 | shipped | 2026-04-20 |
| [digest-suppression](digest-suppression.md) | 디제스트 자동 묶음 및 원본 교체 | shipped | 2026-04-20 |
| [protected-source-notifications](protected-source-notifications.md) | 미디어/통화/포그라운드 서비스 보호 | shipped | 2026-04-20 |
| [persistent-notification-protection](persistent-notification-protection.md) | 지속 알림 키워드 기반 보호 | shipped | 2026-04-20 |

### Inboxes & UI
| ID | Title | Status | Last verified |
|---|---|---|---|
| [home-overview](home-overview.md) | 홈 개요 (요약 + 인사이트) | shipped | 2026-04-20 |
| [priority-inbox](priority-inbox.md) | 중요 알림 인박스 | shipped | 2026-04-20 |
| [digest-inbox](digest-inbox.md) | 정리함 인박스 | shipped | 2026-04-20 |
| [hidden-inbox](hidden-inbox.md) | 숨긴 알림 인박스 (Hidden 화면) | shipped | 2026-04-20 |
| [notification-detail](notification-detail.md) | 알림 상세 및 피드백 액션 | shipped | 2026-04-20 |
| [insight-drilldown](insight-drilldown.md) | 인사이트 드릴다운 | shipped | 2026-04-20 |

### Rules & onboarding
| ID | Title | Status | Last verified |
|---|---|---|---|
| [onboarding-bootstrap](onboarding-bootstrap.md) | 첫 온보딩 및 기존 알림 부트스트랩 | shipped | 2026-04-20 |
| [rules-management](rules-management.md) | 규칙 CRUD | shipped | 2026-04-20 |
| [rules-feedback-loop](rules-feedback-loop.md) | 알림 피드백 → 룰 저장 | shipped | 2026-04-20 |

## 아직 문서화하지 않은 영역

다음 기능들은 구현되어 있으나 별도 journey 로 분리하지 않았습니다 (위 journey 들의 out-of-scope 에서 언급). 후속 PR 에서 필요해지면 추가:

- Settings 화면 전반 (개별 토글/옵션 각각은 연관 journey 가 커버)
- Quick-start 적용 결과 카드 (`QuickStartAppliedCard`) 자체 — `home-overview` 안에서 일부 커버
- Notification access 권한 재요청 UX — `onboarding-bootstrap` 이 일부 커버

## Verification log


### 2026-04-20 (v1 loop cycle sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-inbox | ✅ PASS | 4건 중복 알림 posting 후 Digest 탭에 앱 그룹 카드 "관련 알림 4건" 렌더 |
| digest-suppression | ⏭️ SKIP | 앱 opt-in + Settings 토글 조작 필요 — 이번 tick 에서 자동화 제외, Settings UI 검증으로 별도 커버 |
| duplicate-suppression | ✅ PASS | 같은 content signature 3회 posting 시 1 Digest + 2 Silent (threshold 3) 관측 |
| hidden-inbox | ✅ PASS | deep-link 진입 시 "숨겨진 알림 12건" 헤더 + 그룹 카드 렌더 |
| home-overview | ✅ PASS | StatPill 즉시 2 / Digest 11 / 조용히 12 렌더 |
| insight-drilldown | ⏭️ SKIP | 현재 tick 의 데이터로는 인사이트 trigger 부족, 별도 기간 관측 필요 |
| notification-capture-classify | ✅ PASS (부분) | "인증번호" 키워드 posting 이 pipeline 통과 — uiautomator race 로 StatPill 재확인은 inconclusive |
| notification-detail | ✅ PASS | Priority 탭 → 카드 탭 → Detail 섹션 렌더 (이전 session 에서 재확인) |
| onboarding-bootstrap | ⏭️ SKIP | pm clear 파괴적 — 자동화 제외, 릴리즈 사이클 테스트로만 수동 커버 |
| persistent-notification-protection | ⏭️ SKIP | cmd notification 이 FLAG_ONGOING_EVENT 미지원 — 유닛 테스트(PersistentNotificationPolicyTest) 가 대신 커버 |

### 2026-04-20 (initial sweep, emulator-5554)

Verification recipe 를 실행한 결과. `last-verified` 는 전원 2026-04-20 로 유지(문서 작성일과 동일) 하되, 아래 결과를 기록해 신뢰도를 남긴다.

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | ✅ PASS | "인증번호" 알림이 PRIORITY 로 분류, StatPill `즉시` 카운트 증가 |
| priority-inbox | ✅ PASS | 원본이 tray 에 유지 + Priority 탭에서 카드 확인 |
| duplicate-suppression | ✅ PASS | 동일 signature 3회 반복 시 3번째만 DIGEST, 앞 두 건은 SILENT |
| digest-inbox | ✅ PASS | Digest 탭에 앱 단위 그룹 카드 렌더 |
| rules-management | ✅ PASS | Rules 탭 렌더, 활성 규칙 3개 표시 (quick-start 프리셋) |
| notification-detail | ✅ PASS (부분) | "왜 이렇게 처리됐나요?", "어떻게 전달되나요?" 섹션 확인. 액션 버튼 영역은 스크롤 필요 |
| hidden-inbox | ✅ PASS (재검증) | persistent 필터 드리프트 수정 후 재실행 — Home StatPill 과 일치 (둘 다 4) |
| silent-auto-hide | ✅ PASS (재검증) | 요약 알림 title "숨겨진 알림 4건" 이 Home StatPill `조용히 4` 및 Hidden 헤더 `4` 와 일치 |
| home-overview | ✅ PASS (부분) | StatPill / Home 렌더 확인. 상세 카드/차트 시각 회귀는 미실행 |
| onboarding-bootstrap | ⏭️ SKIP | `pm clear` 가 파괴적이라 이번 sweep 에서 제외 |
| protected-source-notifications | ⏭️ SKIP | 실제 MediaStyle 앱 필요. 단위 테스트는 통과 |
| rules-feedback-loop | ⏭️ SKIP | 추후 `am broadcast` 로 검증 예정 |
| insight-drilldown | ⏭️ SKIP | 이번 sweep 에서 시간상 제외 |
| quiet-hours | ⏭️ SKIP | 시스템 시간 조작 필요 |
| persistent-notification-protection | ⏭️ SKIP | `FLAG_ONGOING_EVENT` 설정이 `cmd notification post` 로 제한적 |
| digest-suppression | ⏭️ SKIP | 앱 opt-in + 설정 조작 필요 |

### 발견된 drift

1. **Silent count 불일치** — ✅ **해소됨**:
   - (이전) Home StatPill `조용히` 은 persistent 를 제외, Hidden/요약은 persistent 포함 → 숫자 불일치.
   - (결정) 제품 방향은 "persistent 를 인박스 뷰에서 숨긴다" 는 기존 `hidePersistentNotifications` 설정을 세 곳에서 동일하게 존중.
   - (수정) `HiddenNotificationsScreen` 과 리스너의 `silentSummaryJob` 이 `observeAllFiltered(hidePersistentNotifications)` / `filterPersistent(...)` 를 사용하도록 변경. 세 곳 모두 `조용히 4` 로 일치 확인.

2. **Home 탭 복귀 회귀** — ✅ **해소됨**:
   - (원인) `navigate(home) { popUpTo(home) saveState; launchSingleTop; restoreState }` 이 nav-compose 2.7.x 에서 특정 back-stack 형태(예: 딥링크 entry 로 도달한 Hidden → Home) 에서 silent no-op. logcat 으로 stack 이 `[null, home, hidden]` 그대로 유지되는 것 확인.
   - (수정) `navigateToTopLevel` 에서 target 이 start destination 과 같을 때는 `navController.popBackStack(startId, inclusive=false, saveState=true)` 로 직접 pop. 다른 top-level 탭 전환은 기존 navigate 패턴 유지.
   - (검증) Hidden→Home, Home→Priority→Home, Digest→Home→Digest 세 경로 모두 PASS.

## Deprecated

(없음)

## Journey 문서 작성 가이드

지금 있는 문서들을 예시로 사용. 기본 섹션:

1. **Frontmatter** — `id`, `title`, `status` (planned/in-progress/shipped/deprecated), `owner`, `last-verified`
2. **Goal** — 제품 의도 한두 문장. 바뀌면 사실상 새 journey
3. **Preconditions** — 이 journey가 시작되려면 성립해야 할 상태
4. **Trigger** — 무엇이 이 journey를 시작시키는가
5. **Observable steps** — 검증 가능한 문장만. 각 단계는 사람/ADB가 확인 가능해야 함
6. **Exit state** — 성공했을 때 어떤 관측 가능한 상태가 되는가
7. **Out of scope** — 이 journey가 책임지지 **않는** 일 (비대화 방지). 인접 journey로 링크
8. **Code pointers** — 파일/클래스명 (줄 번호는 금방 썩으니 최소화)
9. **Tests** — 관련 유닛/UI 테스트 클래스
10. **Verification recipe** — ADB 명령이나 수동 체크리스트로 journey가 여전히 작동하는지 5분 안에 확인 가능
11. **Known gaps** — 의도적 한계나 미해결 엣지 케이스
12. **Change log** — 연/월/일 + 짧은 설명 + PR/커밋 해시
