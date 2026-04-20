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
| [notification-capture-classify](notification-capture-classify.md) | 알림 캡처 및 분류 | shipped | 2026-04-21 |
| [duplicate-suppression](duplicate-suppression.md) | 중복 알림 감지 및 DIGEST 강등 | shipped | 2026-04-21 |
| [quiet-hours](quiet-hours.md) | 조용한 시간 | shipped | 2026-04-20 |

### Source notification routing (시스템 tray 조작)
| ID | Title | Status | Last verified |
|---|---|---|---|
| [silent-auto-hide](silent-auto-hide.md) | 조용히 분류된 알림 자동 숨김 | shipped | 2026-04-21 |
| [digest-suppression](digest-suppression.md) | 디제스트 자동 묶음 및 원본 교체 | shipped | 2026-04-21 |
| [protected-source-notifications](protected-source-notifications.md) | 미디어/통화/포그라운드 서비스 보호 | shipped | 2026-04-21 |
| [persistent-notification-protection](persistent-notification-protection.md) | 지속 알림 키워드 기반 보호 | shipped | 2026-04-20 |

### Inboxes & UI
| ID | Title | Status | Last verified |
|---|---|---|---|
| [home-overview](home-overview.md) | 홈 개요 (요약 + 인사이트) | shipped | 2026-04-21 |
| [priority-inbox](priority-inbox.md) | 중요 알림 인박스 | shipped | 2026-04-21 |
| [digest-inbox](digest-inbox.md) | 정리함 인박스 | shipped | 2026-04-21 |
| [hidden-inbox](hidden-inbox.md) | 숨긴 알림 인박스 (Hidden 화면) | shipped | 2026-04-21 |
| [notification-detail](notification-detail.md) | 알림 상세 및 피드백 액션 | shipped | 2026-04-21 |
| [insight-drilldown](insight-drilldown.md) | 인사이트 드릴다운 | shipped | 2026-04-21 |

### Rules & onboarding
| ID | Title | Status | Last verified |
|---|---|---|---|
| [onboarding-bootstrap](onboarding-bootstrap.md) | 첫 온보딩 및 기존 알림 부트스트랩 | shipped | 2026-04-20 |
| [rules-management](rules-management.md) | 규칙 CRUD | shipped | 2026-04-21 |
| [rules-feedback-loop](rules-feedback-loop.md) | 알림 피드백 → 룰 저장 | shipped | 2026-04-21 |

## 아직 문서화하지 않은 영역

다음 기능들은 구현되어 있으나 별도 journey 로 분리하지 않았습니다 (위 journey 들의 out-of-scope 에서 언급). 후속 PR 에서 필요해지면 추가:

- Settings 화면 전반 (개별 토글/옵션 각각은 연관 journey 가 커버)
- Quick-start 적용 결과 카드 (`QuickStartAppliedCard`) 자체 — `home-overview` 안에서 일부 커버
- Notification access 권한 재요청 UX — `onboarding-bootstrap` 이 일부 커버

## Verification log


### 2026-04-21 (v1 loop tick — hidden-inbox re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| hidden-inbox | ✅ PASS | Deep-link 경로 재검증. `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden` 필요 — Activity 가 top-most 로 이미 떠있을 때 단일 `am start` 는 `Intent { has extras }` 가 delivered 로만 기록되고 `AppNavHost` 의 `LaunchedEffect(deepLinkRoute)` 가 재발화하지 않아 새 화면으로 네비게이트되지 않음 (기존 top composable 유지). Force-stop 후 cold start 로 재기동하면 정상. 이 관측은 재현 단계에서 중요하므로 recipe fragility 로 Known gaps 에 추가. Cold start 후 `uiautomator dump` 에서 eyebrow `숨긴 알림`, title `숨겨진 알림 31건`, subtitle (그룹 카드 복구/삭제 안내), summary `3개 앱에서 31건을 숨겼어요.`, helper `같은 앱의 여러 알림은 한 카드로 모아서 보여줘요...`, `전체 숨긴 알림 모두 지우기` 버튼, 앱별 그룹 카드 `Shell / 29건 / Shell 숨긴 알림 29건 / 최근 묶음 미리보기` + preview rows (`Shell · 방금`, `Coupang · 오늘의` 등) 관측. 시스템 tray 의 `smartnoti_silent_summary` 요약 알림 title `숨겨진 알림 31건` + content `탭해서 숨겨진 알림 보기` + action `숨겨진 알림 보기 → PendingIntent` (Routes.Hidden) 이 화면 title 과 완전 일치 — silent-auto-hide 와 hidden-inbox 간 count 컨트랙트 유지 확인 (31 = 31 = 요약 알림 count). Observable steps 1–9 (HiddenNotificationsScreen 마운트 → observeSettings → observeAllFiltered → toHiddenGroups → 헤더/ScreenHeader → EmptyState 분기 (비활성) → SmartSurfaceCard + DigestGroupCard 렌더 → preview 탭 가능 상태 → popBackStack 경로) 및 Exit state (DB SILENT = 화면 = 요약 알림 count 일치) 모두 충족. DRIFT 없음 |


### 2026-04-21 (v1 loop tick — priority-inbox re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|29, PRIORITY\|10, SILENT\|32` (총 71). Rules DataStore 확인: `person:엄마\|PERSON\|DIGEST` + `person:TestSender_0421_T11\|PERSON\|DIGEST` 등 오염 규칙 잔존 — recipe 의 `"엄마"` posting 경로는 DIGEST 로 떨어질 것이므로, Known gaps 의 권장대로 keyword 기반 PRIORITY 경로로 우회. Fresh unique tag posting: `cmd notification post -S bigtext -t "은행" PriIn0421T3 "인증번호 778899을 입력하세요"` → `dumpsys notification --noredact \| grep -B1 -A3 PriIn0421T3` 에서 `NotificationRecord(… pkg=com.android.shell id=2020 tag=PriIn0421T3 importance=3 key=0\|com.android.shell\|2020\|PriIn0421T3\|2000)` 관측 — 원본 tray 유지 (`SourceNotificationRoutingPolicy.route(PRIORITY,*,*) → cancelSourceNotification=false` 불변조건 충족, 시스템 장치 음 `mSoundNotificationKey` 도 이 key 로 설정). DB row `com.android.shell:2020:PriIn0421T3 \| 은행 \| PRIORITY \| reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드` — 키워드 `인증번호` ALWAYS_PRIORITY 규칙 경로를 통해 classifier 가 즉시 PRIORITY 분기. DB 총계 `PRIORITY 10→11`, SILENT/DIGEST 불변. `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity` → 중요 탭 (327, 2232) 탭 → uiautomator dump 에서 header `중요 알림` + summary `총 11건의 알림이 즉시 전달 대기 중이에요.` + helper copy `알림 이유와 상태를 함께 보여줘서 왜 중요한지 빠르게 판단할 수 있어요.` 렌더. 신규 카드 `Shell / 은행 / 인증번호 778899을 입력하세요 / 즉시 전달` + reason chips (`중요 알림 / 중요 키워드 / 인증번호`) bounds `[42,732][1038,1248]` 관측 (첫 번째 카드로 렌더 = postedAtMillis DESC 순서 정합). 동일 signature 의 기존 PRIORITY 카드 `은행 / 인증번호 445566를 입력하세요` (어제 tick #54 의 posting) 가 아래 card slot 에 잔존 = inbox 가 PRIORITY 누적을 유지함 확인. 카드 중앙 (540, 990) 탭 → `NotificationDetailScreen` 진입 (top bar `알림 상세` + 요약 카드 `Shell / 은행 / 인증번호 778899을 입력하세요 / 즉시 전달` + `왜 이렇게 처리됐나요?` 섹션 + reason chips 6개 `발신자 있음 / 사용자 규칙 / 중요 알림 / 온보딩 추천 / 조용한 시간 / 중요 키워드` — DB reasonTags 와 1:1 정합). Observable steps 1–4 (navigateToTopLevel → PriorityScreen → LazyColumn of PRIORITY `NotificationUiModel` → 카드 탭 → Routes.Detail.create) 및 Exit state (Priority 탭에 모든 PRIORITY 알림 렌더 + 시스템 tray 원본 유지 + Detail 진입 가능) 전부 충족. 직전 tick (#54, PR 기록상 priority-inbox re-verify #1) 이 recipe fragility (`엄마` PERSON DIGEST 오염) 를 발견하고 Known gap 에 keyword 우회를 기록했으며, 이번 tick 은 그 우회 경로를 더 큰 baseline (71건, PRIORITY 10건) + 다른 unique tag 로 재현 — contract 안정성 및 recipe 가이드의 유효성을 재확증. Known gaps 변경 없음 (keyword 우회 권고는 유지, recipe 는 차후 리라이트가 안전) |


### 2026-04-21 (v1 loop tick — notification-capture-classify re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|27, PRIORITY\|10, SILENT\|27` (총 64). Recipe 그대로 `cmd notification post -S bigtext -t 'Bank' BankAuth0421B '인증번호 778899을 입력하세요'`. (Step 1) `dumpsys notification --noredact \| grep BankAuth0421B` 에서 `NotificationRecord(… pkg=com.android.shell id=2020 tag=BankAuth0421B importance=3 key=0\|com.android.shell\|2020\|BankAuth0421B\|2000)` 관측 — 리스너가 즉시 게시 수신. (Steps 2–9 composite) SmartNoti DB row `id=com.android.shell:2020:BankAuth0421B \| pkg=com.android.shell \| title=Bank \| status=PRIORITY \| reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드` — `processNotification` → extras 파싱 → `currentRules()/observeSettings().first()` 스냅샷 → `NotificationCaptureProcessor.process` → `NotificationClassifier.classify` 에서 `중요 키워드` heuristic 경로가 PRIORITY 결정 → `NotificationRepository.save` 가 즉시 upsert (DB 총계 `PRIORITY 10→11`). (Step 10) source routing: SILENT/DIGEST 분기 아니므로 원본 유지 (Exit state 와 `SourceNotificationRoutingPolicy.route(PRIORITY,*,*) → cancelSource=false` 계약 정합). UI reflect 검증: `am force-stop && am start MainActivity` → Home dump 에서 StatPill `즉시 11 / Digest 27 / 조용히 26` + "실제 알림 상태" summary `최근 실제 알림 64개가 Home에 반영됐어요` 관측. SILENT 27 → UI `조용히 26` 은 `hidePersistentNotifications=true` 가 persistent 1건을 인박스에서 숨기는 통상 동작 (cross-journey convention, silent-auto-hide #46/#62 과 동일). Observable steps 1–10 및 Exit state (DB row + `observeAll()` 반영 + 후속 routing) 전부 충족. 어제 tick (#48) 의 "단일 posting 으로 StatPill 즉시 반영" 확증을 오늘 재현 — 더 큰 baseline (64건) 에서도 capture→classify→save→UI 파이프라인 latency 가 사용자 관측 범위에서 안정적임을 확인 |


### 2026-04-21 (v1 loop tick — silent-auto-hide deep re-verify + env-noise correction, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ✅ PASS | Baseline DB `SELECT status, isPersistent, COUNT(*) FROM notifications GROUP BY status, isPersistent` → `DIGEST\|0\|27, PRIORITY\|0\|9, SILENT\|0\|25, SILENT\|1\|1`. Summary key `0\|com.smartnoti.app\|23057` (channel `smartnoti_silent_summary`, importance 1, category=status, vis=SECRET, flags=0x18 = AUTO_CANCEL+ONLY_ALERT_ONCE, actions=1), title `숨겨진 알림 25건` = SILENT isPersistent=0 count (hidePersistentNotifications=true filter). Fresh posting `cmd notification post -S bigtext -t 'Promo' SilentSweep0421_A '오늘만 30% 할인'` → (1) `dumpsys notification \| grep -ci SilentSweep0421_A` = 0 (원본 cancel 확인, step 1–2), (2) DB row `com.android.shell:2020:SilentSweep0421_A \| Promo \| SILENT` 저장 (step 3), (3) 25→26 변화에 따라 summary re-post — 새 `when=1776707093479`, 새 title `숨겨진 알림 26건` (step 4–5 combine + dedupe). Step 5 dedupe negative-case 검증: 후속 PRIORITY posting `cmd notification post … '인증번호 445566'` → PRIORITY 9→10 이지만 SILENT 카운트 불변, summary `when` 타임스탬프 `1776707156735 → 1776707156735` 으로 동일 — count 불변시 재게시 안 함 확인. 액션 버튼 `[0] "숨겨진 알림 보기" → PendingIntent` + contentIntent 도 step 6 계약대로 렌더. Step 7 deep-link: `am force-stop && am start … -e DEEP_LINK_ROUTE hidden` → Hidden 화면 렌더 (`숨긴 알림` 탑바 + `숨겨진 알림 26건` 헤더 + `Shell 숨긴 알림 24건` 카드 + 새 `Promo` 카드) — DB / summary title / 화면 헤더 세 지점 `26` 일치 (Exit state 충족). **환경 노이즈 발견**: 에뮬레이터 APK (v0.1.0, lastUpdateTime 2026-04-20 15:05) 가 `extras.android.text=탭해서 숨겨진 알림 보기` + bigText `탭하면 전체 목록을 확인할 수 있어요.` 를 serve — 이는 `acf7c39` (초기) 의 copy. 현재 `main` 의 `SilentHiddenSummaryNotifier#post` 는 `50e04ef` 이후 `탭: 목록 보기 · 스와이프: 확인으로 처리` + bigText `옆으로 밀어 없애면 확인한 것으로 처리돼요` 로 업데이트됨. 소스와 contract 는 일치, 설치된 APK 만 구판 — contract drift 아닌 env noise (릴리즈 빌드 재설치로 해소). #46 sweep 이 "APK 가 새 copy 반영" 으로 단정한 것은 title + action button 만 확인한 착오 (extras 의 text/bigText 미확인) — Known gaps 에 correction 과 함께 재확인 체크포인트 추가. auto-cancel (step 7.5) 은 `am start` 경로로는 트리거되지 않아 이번 tick 미검증 (요약 tap 시뮬 한계) |


| Journey | Result | Notes |
|---|---|---|
| digest-suppression | ✅ PASS | Baseline Settings DataStore (`run-as com.smartnoti.app strings files/datastore/smartnoti_settings.preferences_pb`) → `suppress_source_for_digest_and_silent` 토글 ON + `suppressed_source_apps = [com.smartnoti.testnotifier, com.android.shell]` — 전역 opt-in + 대상 앱 영속화 확인. Recipe 그대로 3회 posting: `for i in 1 2 3; do cmd notification post -S bigtext -t 'SuppSweep0421P' "SuppTest0421_$i" '오늘만 특가 할인 텍스트'; done`. (1) 원본 제거: `dumpsys notification --noredact | grep tag=SuppTest0421_` hit 0 — 세 건 모두 tray 에서 cancel. (2) Replacement 게시: `pkg=com.smartnoti.app id=-385633862 channel=smartnoti_replacement_digest_low_off_private_noheadsup importance=2 flags=0x18 category=status groupKey=silent actions=3` 관측, extras `android.title=SuppSweep0421P`, `android.subText=Shell • Digest`, `android.text=원본 알림 숨김을 시도하고 Digest에 모아뒀어요 · 사용자 규칙 · 프로모션 알림`, 3 actions 텍스트 `[0] 중요로 고정 / [1] Digest로 유지 / [2] 열기` (broadcastIntent → `SmartNotiNotificationActionReceiver` / contentIntent → `MainActivity`). (3) DB (`sqlite3 databases/smartnoti.db "SELECT id, title, status, replacementNotificationIssued FROM notifications WHERE title='SuppSweep0421P' ORDER BY postedAtMillis DESC LIMIT 5"`) → 3 rows 모두 `status=DIGEST, replacementNotificationIssued=1`. Observable steps 1–5 (auto-expansion / suppression 판정 / routing / cancelSource / notifySuppressedNotification) 및 Exit state (원본 제거 + replacement 1건 + DB DIGEST+replacement flag) 전부 충족. Step 6 액션 broadcast + Step 7 content-tap 경로는 별도 journey (rules-feedback-loop, notification-detail) 에서 커버 — 이번 tick 은 기본 경로에 집중. 2026-04-20 sweep (PR #40, SKIP→PASS 업그레이드) 이 frontmatter 갱신을 놓친 상태였고, 이번 tick 이 그 PASS 를 새 signature 로 재현하며 frontmatter 를 2026-04-21 로 맞춤 — 이로써 non-SKIP 13개 journey 전부 2026-04-21 evidence 확보 |


### 2026-04-21 (v1 loop tick — insight-drilldown re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| insight-drilldown | ✅ PASS | Recipe 실행: 5건 posting (`cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 $i"`) 후 DB 덤프 `SELECT title,status FROM notifications ORDER BY postedAtMillis DESC LIMIT 10` → `Coupang DIGEST x3, Coupang SILENT x2` (contentSignature `coupang 오늘의 딜` 로 duplicate heuristic 이 3번째부터 DIGEST 강등 — digest-drill 과 무관한 capture 계약). `am force-stop && am start MainActivity` → Home StatPill `즉시 9 / Digest 24 / 조용히 25`. 첫 번째 스크롤 (`input swipe 540 1800 540 600 400`) 후 dump 에서 InsightCard 확인: `일반 인사이트` 라벨 + header `SmartNoti 인사이트` + body `지금까지 49개의 알림을 대신 정리했어요 / 전체 알림 중 84%를 대신 정리했어요`, clickable app chip `Shell 알림 43개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'예요` bounds `[84,869][996,995]`, reason breakdown `조용한 시간 · 23건 / 사용자 규칙 · 11건 / 반복 알림 · 9건` 렌더. App chip (540,932) 탭 → `InsightDrillDownScreen` 진입 관측: (step 4) eyebrow `인사이트` + title `Shell 인사이트` + subtitle `Shell 알림 43건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.`, (step 5) `ContextBadge` `일반 인사이트` (GENERAL 톤), (step 6) range `FilterChip` 3개 (`최근 3시간 / 최근 24시간 / 전체`) 중 `최근 24시간` 이 default selection 으로 active — subtitle `최근 24시간 기준 Shell에서 정리된 알림 43건을 시간순으로 보여줘요.`, (step 7) breakdown 차트 `Digest 20 / 조용히 23` + helper copy `이 앱에서 가장 많이 보인 이유는 '조용한 시간'이에요.`, (step 8) reason nav rows `조용한 시간 · 23건 / 사용자 규칙 · 11건 / 반복 알림 · 9건` 각각 `탭해서 자세히 보기` hint, (step 9) 하단 `NotificationCard` 리스트 (`Shell / Shop / 테스트 / 조용히 정리` + reason chips `발신자 있음 / 조용한 시간`). Range chip `최근 3시간` (203,783) 탭 → subtitle 및 총계 `43→21`, breakdown `Digest 10 / 조용히 11`, reason rows `조용한 시간 · 21건 / 사용자 규칙 · 6건 / 반복 알림 · 4건` 으로 재계산 관측 (`InsightDrillDownBuilder` 의 range 분기 동작 확인). Reason nav 검증: `사용자 규칙 · 6건` row (200,1221) 탭 → 새 InsightDrillDown 진입 (`인사이트` eyebrow + title `사용자 규칙 이유` + subtitle `'사용자 규칙' 이유로 정리된 알림 6건을 모아봤어요.`), `Digest 6 / 조용히 0` breakdown, 현재 이유 row `사용자 규칙 · 6건` 옆에 marker `현재 보고 있는 항목`, 공기 reason `조용한 시간 · 6건 / 온보딩 추천 · 3건` 렌더, 카드 리스트에 `DetailVerify0421 / 두 번째 이벤트 안내 / Digest` + reason chips `발신자 있음 / 사용자 규칙 / 프로모션 알림 / 온보딩 추천 / 조용한 시간` 관측 — 이유 기반 재드릴다운 경로 end-to-end 동작 확증. Observable steps 1–10 및 Exit state (앱/이유 필터로 좁혀진 리스트 + 재드릴다운 + Detail 진입 가능) 전부 충족. 2026-04-20 sweep 의 `Home 인사이트 "반복 알림 · 5건" 카드 탭 → "반복 알림 이유"` 얕은 증거를 app-chip + range-chip + reason-nav 세 축으로 강화 |


### 2026-04-21 (v1 loop tick — rules-feedback-loop re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | ✅ PASS | Baseline DataStore `smartnoti_rules.preferences_pb` 에는 기존 PERSON 룰로 `person:엄마` 만 존재. Fresh unique sender 로 `cmd notification post -S bigtext -t 'TestSender_0421_T11' FbkLoop0421 '인증번호 112233을 입력하세요'` 게시 → DB row `com.android.shell:2020:FbkLoop0421 \| sender=TestSender_0421_T11 \| status=PRIORITY \| reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드` (키워드 `인증번호` ALWAYS_PRIORITY 규칙 경로 — journey 계약과 무관한 baseline). Priority 탭 (327,2232) 진입 후 카드 `Shell / TestSender_0421_T11 / 인증번호 112233을 입력하세요 / 즉시 전달` bounds `[42,732][1038,1236]` 관측 → 중앙 (540,984) 탭 → `NotificationDetailScreen` 진입 (`알림 상세` 탑바 + `TestSender_0421_T11` sender 라벨). 액션 영역까지 2회 스크롤 (`input swipe 540 1800 540 600 400` x2) 후 `이 알림 학습시키기` 섹션의 3버튼 (`중요로 고정 · Digest로 보내기 · 조용히 처리`) 관측, `Digest로 보내기` 텍스트 bounds `[196,1945][429,1994]` 중앙 (312,1969) 탭. Observable steps 2.3–2.6 end-to-end 관측: (2.3) `NotificationFeedbackPolicy.applyAction` 적용 → DB row `status=PRIORITY → DIGEST`, `reasonTags` 에 `사용자 규칙` 여전히 유지. (2.5–2.6) `NotificationFeedbackPolicy.toRule` + `RulesRepository.upsertRule` 경로 관측 — `smartnoti_rules.preferences_pb` 덤프에 신규 엔트리 `person:TestSender_0421_T11 \| TestSender_0421_T11 \| Digest로 묶기 \| PERSON \| DIGEST \| true \| TestSender_0421_T11` 영속화 (rule id=`person:TestSender_0421_T11`, matchValue=sender). Step 3 (후속 동일 sender 알림 자동 적용) 검증: 동일 sender 로 (a) `'인증번호 987654를 입력하세요'` posting → status=PRIORITY (키워드 ALWAYS_PRIORITY 가 PERSON DIGEST 를 override — classifier 우선순위 산물, 계약 무관), (b) 키워드 제외한 `'오늘 저녁에 만날까'` posting → status=DIGEST + reasonTags `발신자 있음\|사용자 규칙\|TestSender_0421_T11\|조용한 시간` 로 자동 분류 — PERSON DIGEST 룰이 사용자 개입 없이 매치 적용됨 확인. Exit state (DB status/reasonTags 업데이트 + Rules 영속화 + 후속 자동 적용) 전부 충족. Detail 경로는 replacement 알림 cancel step 은 타지 않음 (Broadcast 경로 전용 — journey 에 명시됨). 어제 `PASS ("엄마" 재사용)` 의 "rule id 충돌로 upsert 가 insert 인지 덮어쓰기인지 애매" 한 약점을 fresh unique sender 로 신규 insert 를 명확히 관측 — insert 케이스 증거 확보. (주의) upsert 된 `person:TestSender_0421_T11` 룰은 다음 tick 환경에 남아있음 — 재현 시 다른 unique sender 권장 |


### 2026-04-21 (v1 loop tick — rules-management re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | ✅ PASS | Baseline Rules 탭 렌더: `활성 규칙 5개 · 즉시 전달 2 · Digest 3 · 조용히 0`, KEYWORD 그룹 `규칙 2개` + PERSON 그룹 (이전 sweep 들로 upsert 된 `엄마` DIGEST / `DetailVerify0421` PRIORITY) 관측. `새 규칙 추가` 탭 → AlertDialog 열림 (`기본 정보 / 규칙 이름 / 이름 또는 발신자 / 규칙 타입 (사람/앱/키워드/시간/반복) / 처리 방식 (즉시 전달/Digest/조용히)`). 키워드 chip 탭 → 입력 라벨이 `키워드 / 쉼표로 여러 키워드를 입력할 수 있어요. 예: 배포,장애,긴급` 로 전환 — `RuleTypeUi.KEYWORD` 분기 확인. 이름="RuleMgmt0421" + 매치값="RuleMgmt0421K" + default 처리방식 "즉시 전달" 상태에서 `추가` 탭 → 헤더 `활성 규칙 6개 · 즉시 전달 3 · Digest 3` 로 즉시 증가, DataStore `smartnoti_rules.preferences_pb` 덤프에 `keyword:RuleMgmt0421K\|RuleMgmt0421\|항상 바로 보기\|KEYWORD\|ALWAYS_PRIORITY\|true\|RuleMgmt0421K` 신규 엔트리 영속화 관측. Exit state end-to-end 검증: `cmd notification post -S bigtext -t '은행' OtpMgmt0421 'RuleMgmt0421K 코드 99999 입력하세요'` → DB `com.android.shell:2020:OtpMgmt0421 \| 은행 \| RuleMgmt0421K \| PRIORITY`, `reasonTags=발신자 있음\|사용자 규칙\|RuleMgmt0421\|조용한 시간` — classifier 가 새 룰을 currentRules() 를 통해 바로 참조해 PRIORITY 분기. Priority 탭 dump 에서 카드 `은행 / RuleMgmt0421K` 렌더 확인. 삭제 플로우 (Observable step 6): DetailVerify0421 삭제 버튼 탭 → 6→5 (`즉시 전달 3→2`), RuleMgmt0421 삭제 → 5→4 로 카운트 감소 + DataStore 에서 두 rule key 모두 제거. Observable steps 1–6 (repository 구독 → ScreenHeader + 새 규칙 추가 → 활성 규칙 카드+FilterChip → action 그룹 + RuleRow → 편집 다이얼로그 + validator + factory + upsertRule → deleteRule) 및 Exit state (DataStore 영속화 + 신규 알림에 룰 적용) 전부 충족. 이전 `PASS (기본 렌더만)` 이었던 2026-04-20 sweep 의 얕은 증거를 CRUD + classifier 적용 end-to-end 로 강화 |


### 2026-04-21 (v1 loop tick — protected-source-notifications re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| protected-source-notifications | ✅ PASS | Recipe A 실행: `cmd notification post -S media -t MediaTest0421 Player "미디어 스타일 테스트"` → `dumpsys notification --noredact | grep -B1 -A3 MediaTest0421` 에서 `NotificationRecord(… pkg=com.android.shell id=2020 tag=MediaTest0421 importance=3 category=transport vis=PRIVATE key=0\|com.android.shell\|2020\|MediaTest0421\|2000)` 잔존 관측. SmartNoti DB (`SELECT id,packageName,title,status,sourceSuppressionState FROM notifications WHERE id LIKE '%MediaTest0421%'`) → `com.android.shell:2020:MediaTest0421 \| com.android.shell \| Player \| SILENT \| NOT_CONFIGURED`. Critical evidence: `SourceNotificationRoutingPolicy.route(SILENT,*,*)` 의 기본값은 `cancelSourceNotification=true` 인데 (SILENT 분기 = unconditional cancel), 원본 tray 에 여전히 살아있다는 것은 `SmartNotiNotificationListenerService#processNotification` 의 `isProtectedSourceNotification` 분기 (`ProtectedSourceNotificationDetector.isProtected(signals)` true → routing 강제 덮어쓰기) 가 동작해 `cancelSourceNotification=false` 로 라우팅됐다는 뜻. `-S media` 가 category=transport + MediaStyle template 을 세팅하므로 `signalsFrom(sbn).category=="transport"` 경로로 보호. Observable steps 1–4 (signals 추출 / isProtected 평가 / routing 덮어쓰기 / cancel 호출 안함) 과 Exit state (원본 tray 유지 + DB row 저장) 전부 충족. Recipe B (YouTube Music) 는 에뮬레이터에 앱 미설치로 SKIP — category 기반 보호 확증으로 sufficient. 이번 tick 이 journey 의 end-to-end 검증 첫 PASS (이전 두 sweep 은 "실제 MediaStyle 앱 필요" 로 SKIP 처리됐던 부분). Known gaps 변경 없음 |


### 2026-04-21 (v1 loop tick — hidden-inbox re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| hidden-inbox | ✅ PASS | Baseline: DB `SELECT status,isPersistent,COUNT(*) FROM notifications WHERE status='SILENT' GROUP BY isPersistent` → `SILENT\|0\|24`, `SILENT\|1\|1`. Silent summary notification (`dumpsys notification --noredact`) 타이틀 `숨겨진 알림 24건` (channel `smartnoti_silent_summary`, visibility SECRET, importance 1) — `hidePersistentNotifications=true` 기본값이 persistent 1건을 제외해 시스템 tray 요약은 24 로 맞음. Deep-link: `am force-stop com.smartnoti.app && am start … -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden` → uiautomator dump 에서 `HiddenNotificationsScreen` 헤더 `숨긴 알림` / `숨겨진 알림 24건` + summary copy `3개 앱에서 24건을 숨겼어요.` + bulk action `전체 숨긴 알림 모두 지우기` 렌더. 초기 viewport 에서 `Shell 숨긴 알림 22건` 카드 관측, 두 번 스크롤 후 `Android System 숨긴 알림 1건` + `Digital Wellbeing 숨긴 알림 1건` 카드 관측 — DB 그룹핑 `com.android.shell\|22`, `com.google.android.adservices.api\|1`, `com.google.android.apps.wellbeing\|1` 과 앱명/카운트 일치. Observable steps 1–7 (repository 구독 → `toHiddenGroups` → 헤더/요약 카피 → 앱별 `DigestGroupCard` 렌더) 및 Exit state (`DB SILENT 개수 == 화면 카드 합계 == 요약 알림 title` 세 지점 일치) 전부 충족. `hidePersistentNotifications` 경로 (persistent 1건은 화면/요약 둘 다에서 제외) 도 초기 sweep 의 드리프트 해소 이후 계속 정합 |


### 2026-04-21 (v1 loop tick — priority-inbox re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | ✅ PASS | 1차 시도: recipe 그대로 `cmd notification post -S bigtext -t "엄마" Mom "오늘 저녁에 올 수 있어?"` → DB `status=DIGEST` 로 저장됨. 원인 조사: `run-as com.smartnoti.app cat files/datastore/smartnoti_rules.preferences_pb` 덤프에 `person:엄마|PERSON|DIGEST|true` rule 존재 (기존 rules-feedback-loop sweep 에서 upsert 된 PERSON 규칙이 VIP heuristic 을 override). Journey 계약은 "PRIORITY 분류된 알림을 보여준다" 이므로 recipe 를 PRIORITY 를 확실히 유도하는 방식으로 대체: `cmd notification post -S bigtext -t '은행' PriorityVerify0421 '인증번호 123456를 입력하세요'` → DB `id=com.android.shell:2020:PriorityVerify0421 status=PRIORITY`. 시스템 tray 에서 `dumpsys notification --noredact` 로 원본 `pkg=com.android.shell tag=PriorityVerify0421 importance=3 flags=0x0` 잔존 확인 — `SourceNotificationRoutingPolicy.route(PRIORITY,*,*) → cancelSourceNotification=false` 불변조건 충족. `am start … MainActivity` → 중요 탭 (327,2232) 탭 → uiautomator dump 에 헤더 `중요 알림` + `총 7건의 알림이 즉시 전달 대기 중이에요.` + 신규 카드 `Shell / 은행 / 인증번호 123456를 입력하세요 / 즉시 전달` + reason chips (`발신자 있음 / 사용자 규칙 / 중요 알림 / 온보딩 추천 / 조용한 시간 / 중요 키워드`) 렌더. 카드 클릭 가능 영역 bounds `[42,732][1038,1248]` 중앙 (540,990) 탭 → `NotificationDetailScreen` 진입 (`알림 상세` top bar + 요약 카드 + `왜 이렇게 처리됐나요?` / `어떻게 전달되나요?` 섹션). Observable steps 1–4 및 Exit state 전부 충족. Known gap 추가 (recipe 의 `"엄마"` 고정값이 rules-feedback-loop 을 돌린 환경에서는 DIGEST 로 라우팅되어 verify 가 깨짐 — recipe 를 키워드 기반 priority 로 바꾸거나 recipe 전 `엄마` person rule 을 지우는 것이 안전) |


### 2026-04-21 (v1 loop tick — digest-inbox re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-inbox | ✅ PASS | Recipe 그대로 실행: `for i in 1 2 3 4; do cmd notification post -S bigtext -t "Coupang0421V" "Deal$i" "오늘의 딜 ${i}건"; done` → `MainActivity` 재개 후 정리함 탭 (539, 2232) 탭. `run-as com.smartnoti.app sqlite3 … 'SELECT packageName, COUNT(*) FROM notifications WHERE status="DIGEST" GROUP BY packageName;'` → `com.android.shell\|15`, `com.smartnoti.testnotifier\|4`. uiautomator dump 에서 정확히 두 개의 앱 그룹 카드 렌더: `SmartNoti Test Notifier 관련 알림 4건` + (스크롤 후) `Shell 관련 알림 15건`, 각 카드의 "최근 묶음 미리보기" 섹션에 `Coupang0421V` 프리뷰 카드 관측. Observable step 3 (`packageName` 으로 grouping, `summary="{app} 관련 알림 {N}건"` 매핑) 과 step 4 (앱명 + 카운트 + 요약 + 최근 3건 preview) 모두 DB count 와 UI 문자열이 일치. 포스팅한 4건 중 2건은 DIGEST, 2건은 SILENT 로 분류됐는데 (contentSignature 중복으로 3번째부터 DIGEST, duplicate-suppression 계약), 이는 digest-inbox Exit state (DIGEST 만 그룹 카드로 렌더) 와 정합 — SILENT 2건은 Hidden 으로 라우팅되고 Digest 탭에는 보이지 않음. Journey Observable steps 1–5 및 Exit state 전부 충족 |


### 2026-04-21 (v1 loop tick — duplicate-suppression re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| duplicate-suppression | ✅ PASS | Recipe 그대로 실행: `for i in 1 2 3; do cmd notification post -S bigtext -t "Shopping" "Repeat$i" "한정 특가"; done` — `-t` 가 TITLE, 위치인자 1=tag, 2=text 이므로 세 번 모두 title="Shopping" / body="한정 특가" (signature `shopping 한정`) 고정, tag `Repeat1/2/3` 만 달라 `sbn.key` 는 3개로 분리됨. `run-as com.smartnoti.app sqlite3 smartnoti.db "SELECT title, body, status, contentSignature FROM notifications WHERE title='Shopping' ORDER BY postedAtMillis DESC LIMIT 3"` 결과: 최신(3번째)=DIGEST / 두 번째=SILENT / 첫 번째=SILENT — contentSignature 세 건 모두 `shopping 한정` 일치. Digest 탭에서 Shell 그룹 `12건 → 13건` 으로 증가, 그룹 preview 에 `Shell / Shopping / 한정 / Digest` 카드 렌더 — journey Observable step 6 (`duplicateCountInWindow >= 3 → DIGEST`) 와 Exit state (`반복 알림 3번째부터는 DIGEST 로 저장`) 충족. 첫 두 건이 "default" 가 아닌 SILENT 로 떨어진 것은 classifier 가 sender 없는 Shell posting 을 기본 SILENT 로 분류한 산물로, duplicate-suppression 계약과는 무관 (dup heuristic 자체는 3번째에서 정확히 DIGEST 를 돌려줌). Hidden 인박스에서도 첫 두 개 silent 가 group card 에 누적되어 Shell 숨김 카운트 +2 반영 관측 |


### 2026-04-21 (v1 loop tick — digest-inbox re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-inbox | ✅ PASS | Recipe 그대로 재실행 (다음 oldest candidate queue 의 선두, 전 tick 대비 baseline 이 더 커진 상태에서 grouping 안정성 확인): `am force-stop com.smartnoti.app`, `for i in 1 2 3 4; do cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 ${i}건"; done`, `am start … MainActivity`, 정리함 탭 (539,2232) 탭. DB 베이스라인 `SELECT packageName, COUNT(*) FROM notifications WHERE status='DIGEST' GROUP BY packageName` → `com.android.shell\|25`, `com.smartnoti.testnotifier\|4` (총 2 그룹, 29건). uiautomator dump 에서 Digest 탭 top summary `현재 2개의 묶음이 준비되어 있어요.` + helper copy `요약과 최근 항목을 함께 보여줘서 반복 알림을 한눈에 스캔할 수 있어요.` 렌더. 첫 viewport 에서 그룹 A `SmartNoti Test Notifier / 4건 / SmartNoti Test Notifier 관련 알림 4건` (bounds `[84,836][970,978]`) + `최근 묶음 미리보기` 섹션 + 프리뷰 카드 3개 (모두 `배달 상태 업데이트` signature — digest-suppression 흐름의 replacement 산물) 관측. `input swipe 540 1800 540 600 400` 후 그룹 B `Shell / 25건 / Shell 관련 알림 25건` (bounds `[84,1495][970,1637]`) + 프리뷰 카드 `Shell / Coupang / 오늘의 / Digest` 렌더 — 이번 recipe 의 Coupang posting 이 Shell 그룹으로 합쳐짐 (`cmd notification post` 발신자 = `com.android.shell`). 각 그룹의 count badge 와 summary 문자열이 DB count 와 정확히 일치 (4↔4, 25↔25). Observable steps 1–5 (NavController→DigestScreen→observeDigestGroupsFiltered→toDigestGroups→LazyColumn) 및 Exit state (앱별 DIGEST 묶음 + 요약 + preview 3건) 전부 충족. 프리뷰 카드가 clickable (focusable + bounds 전체 영역) 로 렌더되어 step 5 (탭 → `Routes.Detail.create`) 진입 가능. 직전 tick (#53) 이 1그룹+4건 baseline 으로 기본 계약을 증명했다면, 이번 tick 은 2그룹·29건 baseline 에서 grouping/count/summary mapping 이 스케일에 관계없이 안정적임을 재확증 — group ordering 은 postedAtMillis DESC 로 최신 active 그룹이 위에 렌더됨도 관측 |


### 2026-04-21 (v1 loop tick — duplicate-suppression re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| duplicate-suppression | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|28, PRIORITY\|10, SILENT\|30` (총 68). Recipe 변형 실행 (고유 signature 로 증거 분리): `for i in 1 2 3; do cmd notification post -S bigtext -t "DupSweep0421V" "DupRepeat_$i" "재고 리마인드 알림"; sleep 1; done` — 세 건 모두 title="DupSweep0421V", body="재고" (bigtext 모드가 positional text 를 첫 토큰만 취함), `contentSignature=dupsweep0421v 재고` 동일, tag `DupRepeat_1/2/3` 만 상이해 `sbn.key` 는 3개. `run-as com.smartnoti.app sqlite3 databases/smartnoti.db "SELECT id, title, body, status, contentSignature FROM notifications WHERE title='DupSweep0421V' ORDER BY postedAtMillis ASC"` 결과 (postedAt ASC): `DupRepeat_1=SILENT / DupRepeat_2=SILENT / DupRepeat_3=DIGEST` — contentSignature 3개 모두 `dupsweep0421v 재고` 동일. DB 총계 `DIGEST 28→29 (+1) / SILENT 30→32 (+2) / PRIORITY 10 불변` 으로 classifier 배정과 정확히 일치 (2 SILENT + 1 DIGEST). 원본 tray 확인: `dumpsys notification --noredact \| grep -E "DupRepeat_"` hit 0 — SILENT 2건 + DIGEST 1건 모두 원본 cancel (silent-auto-hide / digest-suppression 라우팅 정상). `am force-stop && am start MainActivity` → 정리함 탭 (539,2232) 탭 → 1차 스크롤 후 uiautomator dump 에서 `Shell 관련 알림 25건` 그룹 카드 + `DupSweep0421V / 재고 / Digest` preview 카드 관측. DB `SELECT packageName, COUNT(*) FROM notifications WHERE status='DIGEST' GROUP BY packageName` → `com.android.shell\|25, com.smartnoti.testnotifier\|4` — UI 그룹 카운트와 일치. Observable steps 1–7 (contentSignature 생성 → windowStart 계산 → persisted count 조회 → LiveDuplicateCountTracker 병합 → classifier threshold 체크 → DIGEST 반환 → digest-suppression 경로) 및 Exit state (3번째부터 DIGEST 저장 + contentSignature 누적) 전부 충족. 04-21 tick #1 (PR #52) 의 `Shopping/한정 특가` 증거를 고유 signature 로 재현 — classifier 의 threshold=3 불변조건이 다른 baseline (68건) 및 다른 signature 에서도 안정적임을 확증. Known gaps 변경 없음 |


### 2026-04-21 (v1 loop tick — notification-detail re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | ✅ PASS | `cmd notification post -S bigtext -t DetailVerify0421 Promo1 "오늘의 이벤트 자세히 보기"` → Home 타임라인 "방금 정리된 알림" 에 `DetailVerify0421` Digest 카드 렌더. 카드 탭 → `NotificationDetailScreen` 진입: `DetailTopBar` `알림 상세`, 요약 카드 (`Shell` / `DetailVerify0421` / body / StatusBadge `Digest`), `왜 이렇게 처리됐나요?` + reasonTags (`발신자 있음 / 사용자 규칙 / 프로모션 알림 / 온보딩 추천 / 조용한 시간`), 온보딩 추천 반영 카드 (`빠른 시작 추천에서 추가된 규칙이에요`), `어떻게 전달되나요?` (`덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요. · 전달 모드 · Digest 묶음 전달 · 소리 · 부드럽게 알림 · 진동 · 가벼운 진동 · Heads-up · 꺼짐 · 잠금화면 · 내용 일부만 표시`). 스크롤 시 `원본 알림 처리 상태` (`원본 상태 · 원본 숨김 시도됨 · 대체 알림 · 표시됨`) + `이 알림 학습시키기` 3버튼 (`중요로 고정 · Digest로 보내기 · 조용히 처리`) 모두 관측 — 어제 `PASS (부분)` 의 "액션 버튼 영역 스크롤 필요" 제약을 스크롤 검증으로 해소. `중요로 고정` 탭 → StatusBadge `Digest → 즉시 전달`, 전달 모드 카피 `Digest 묶음 전달 → 즉시 전달` + description `중요 알림으로 바로 전달하지만 방해를 줄이기 위해 heads-up은 띄우지 않아요.` 로 전환. Home 복귀 시 StatPill `즉시 5 → 6` 반영. Rules 탭에서 `활성 규칙 5개 · 즉시 전달 2 · Digest 3` + 신규 `DetailVerify0421 연락은 바로 보여줘요 · 사람 · 즉시 전달 · 발신자 기준` PERSON 규칙 upsert 확인 — `NotificationFeedbackPolicy.toRule` + `RulesRepository.upsertRule` 경로 관측. 관측된 부가 동작: 동일 signature 의 2차 posting 은 `프로모션 알림` 키워드 Digest 규칙이 신규 PERSON Priority 규칙을 override 해 Digest 로 분류됨 (Home 카운트 `Digest 15→16`). Observable steps 1–6 및 Exit state (DB row 업데이트 · 규칙 insert · 뒤로가기로 Home 복귀) 전부 충족 — recipe Step 3 의 "자동 Priority" 는 journey 계약이 아니라 classifier 우선순위 규칙 (keyword Digest > PERSON Priority) 의 산물로, contract drift 아님 |

### 2026-04-21 (v1 loop tick — notification-detail re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | ✅ PASS | `cmd notification post -S bigtext -t PromoDetail0421 Promo1 "오늘의 이벤트"` → Home StatPill `즉시 10 / Digest 27 / 조용히 27`. 정리함 탭 진입 후 "SmartNoti Test Notifier 관련 알림 4건" 묶음 카드 확인. 첫 notification card (y=1250) 탭 → `NotificationDetailScreen` 마운트. uiautomator dump (`/tmp/ui_detail_nd.xml`) 에서 Observable steps 6개 모두 관측: DetailTopBar `알림 상세`, 요약 카드 (`SmartNoti Test Notifier` + `배달 상태 업데이트` + `라이더 위치가 갱신됐어요. 잠시 후 다시 확인해 주세요.` + StatusBadge `Digest`), `왜 이렇게 처리됐나요?` FlowRow (`발신자 있음` / `조용한 시간` / `반복 알림` 3 chip), `어떻게 전달되나요?` (`덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요.` + 5 rows `전달 모드 · Digest 묶음 전달` / `소리 · 조용히 표시` / `진동 · 진동 없음` / `Heads-up · 꺼짐` / `잠금화면 · 내용 일부만 표시`), `원본 알림 처리 상태` (`SmartNoti가 원본 알림 숨김을 시도했고 대체 알림도 표시했어요…` + `원본 상태 · 원본 숨김 시도됨` + `대체 알림 · 표시됨`). Scroll 후 2번째 dump (`/tmp/ui_detail2_nd.xml`) 에서 `이 알림 학습시키기` 카드 + 3개 액션 버튼 (`중요로 고정` / `Digest로 보내기` / `조용히 처리`) + helper copy `한 번 누르면 상태를 바꾸고 같은 유형의 규칙도 함께 저장해요` 렌더. 재분류 action 은 보조 (state 변경만) 라 이번 tick 에서는 observable step 1~4 중심 감사, 액션 버튼 존재 및 라벨은 journey 와 정합 |


### 2026-04-21 (v1 loop tick — home-overview re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-overview | ✅ PASS | Baseline DB 64건 → recipe 3건 posting (`엄마0421T` bigtext, `Coupang0421T` bigtext, `광고0421T` bigtext) 후 `am force-stop com.smartnoti.app && am start MainActivity`. DB 최종: `DIGEST\|28, PRIORITY\|10, SILENT\|30` (총 68 → 게시 직전 65개였으나 posting 트리거로 dedupe 전 67이 한 번 나타난 뒤 classifier/NotificationRepository.save 안정화까지 포함). uiautomator `/tmp/ui_home_t1.xml` dump 에서 **observable step 1-6 모두** 관측: ScreenHeader `SmartNoti` + 서브타이틀 `중요한 알림만 먼저 보여드리고 있어요` + 요약 로우 `오늘 알림 67개 중 중요한 10개를 먼저 전달했어요`, StatPill `즉시 10 / Digest 28 / 조용히 29` (SILENT 30 → UI 29 = `hidePersistentNotifications=true` 가 persistent 1건을 제외하는 기존 convention, silent-auto-hide/ hidden-inbox 와 동일 규약), HomeNotificationAccessCard `연결됨 · 실제 알림이 연결되어 있어요 · 최근 실제 알림 67개가 Home에 반영됐어요 · 즉시 10개 · Digest 28개 · 조용히 29개로 분류됐어요. · 설정에서 연결 상태 보기`, QuickActionCard 2개 (`중요 알림 지금 봐야 할 알림 10개 / 열기` + `정리함 묶인 알림 28개 / 열기`), QuickStartAppliedCard `추천 3개 적용됨 · 빠른 시작 추천이 적용되어 있어요 · 프로모션·반복 알림은 정리하고, 중요한 알림은 바로 보여주고 있어요 · 최근 효과: Shell 프로모션 알림 10건이 정리됐어요 · 반복 알림 13건이 Digest로 묶였어요 · 중요 알림 9건은 그대로 바로 보여줬어요`. 1차 스크롤 dump `/tmp/ui_home_t2.xml` 에서 **step 7 (InsightCard)** 관측: eyebrow `일반 인사이트`, title `SmartNoti 인사이트`, body `지금까지 57개의 알림을 대신 정리했어요 / 전체 알림 중 85%를 대신 정리했어요 / Shell 알림 51개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'예요`, reason chip 3개 (`조용한 시간 · 31건 / 사용자 규칙 · 15건 / 온보딩 추천 · 11건`) 각각 `탭해서 자세히 보기` hint. 2차 스크롤 dump `/tmp/ui_home_t3.xml` 에서 **step 8 (TimelineCard)** 관측: `최근 흐름` 헤더 + `최근 3시간 / 최근 24시간` range chip + subtitle `최근 3시간 기준 28개의 알림이 정리됐어요` + bucket labels (`2시간 전 / 1시간 전 / 방금 전`) + bucket rows `흐름 · 2시간 전 · 정리 10건 · 즉시 1건` / `흐름 · 1시간 전 · 정리 4건 · 즉시 2건` / `피크 · 방금 전 · 정리 14건 · 즉시 3건` (`HomeTimelineBarChartModelBuilder` render 모델 정상). **step 9 (최근 알림 리스트)** 도 3차 dump 에서 관측: `방금 정리된 알림` 헤더 + `NotificationCard` 렌더 (`Shell / 방금 / 광고0421T / 30% / Digest` + reason chips `발신자 있음 / 사용자 규칙 / 프로모션 알림 / 온보딩 추천`). Step 10 (카드 탭 → Detail / Priority / Digest / Insight 네비) 는 다른 journey (notification-detail, priority-inbox, digest-inbox, insight-drilldown) 들이 네 번째 vertex 에서 재검증 — 이번 tick 은 Home vertex 의 모든 카드가 동시에 렌더됨을 end-to-end 확증에 집중. 어제 tick (#49, `/tmp/ui_home.xml`) 과 비교: 당시 StatPill `즉시 5 / Digest 15 / 조용히 17` 에서 오늘 `즉시 10 / Digest 28 / 조용히 29` 로 2배 가까이 성장했음에도 6개 카드 전체가 동일 레이아웃 순서 (ScreenHeader → StatPill → Access → QuickAction×2 → QuickStart → Insight → Timeline → 최근리스트) 로 안정적 렌더 — spec observable steps 순서 계약 충족 |


### 2026-04-21 (v1 loop tick — notification-capture-classify re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | ✅ PASS | Baseline Home StatPill `즉시 4 / Digest 14 / 조용히 15` (총 33). `cmd notification post -S bigtext -t BankAuth0421 Bank "인증번호 555555를 입력하세요"` 게시 → `dumpsys notification --noredact | grep BankAuth0421` 에서 `NotificationRecord(… pkg=com.android.shell id=2020 tag=BankAuth0421 importance=3 key=0\|com.android.shell\|2020\|BankAuth0421\|2000)` 관측. 4초 대기 후 Home 재확인 시 StatPill `즉시 5 / Digest 14 / 조용히 15` + "실제 알림 상태" 섹션 요약문이 `최근 실제 알림 34개가 Home에 반영됐어요 · 즉시 5개 · Digest 14개 · 조용히 15개로 분류됐어요.` 로 전환 — 인증번호 키워드 heuristic 경로가 pipeline 을 통과해 `NotificationClassifier` 가 PRIORITY 로 분류, `NotificationRepository.save` 가 즉시 35->34 inbox reflect. 어제 sweep 의 "uiautomator race 로 StatPill 재확인은 inconclusive" 부분 해소 — 게시 후 단일 dump 로 즉시 카운트 일치 확인 |

### 2026-04-21 (v1 loop tick — silent-auto-hide re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ✅ PASS | Baseline Home StatPill `조용히 14` = silent summary title `숨겨진 알림 14건` (key `0\|com.smartnoti.app\|23057` on channel `smartnoti_silent_summary`, visibility SECRET, importance 1). `cmd notification post -S bigtext -t PromoSweep0421 PromoSweep0421` → source 즉시 tray 에서 제거 (`dumpsys notification --noredact | grep PromoSweep0421` hit 없음), summary 타이틀 `숨겨진 알림 15건` 으로 재게시, Home StatPill `조용히 15` 로 동기화 — 세 지점 숫자 일치. Deep-link: `am force-stop com.smartnoti.app && am start … -e DEEP_LINK_ROUTE hidden` → `HiddenNotificationsScreen` 헤더 `숨겨진 알림 15건` + `3개 앱에서 15건을 숨겼어요` + 새 카드 `PromoSweep0421 / 오늘만 / 조용히 정리` 렌더. 어제 기록된 "emulator APK 가 PR #8 보다 오래돼" 환경 노이즈 해소 — 현재 APK 가 요약 copy (`탭: 목록 보기 · 스와이프: 확인으로 처리`) 를 반영하며 journey Observable steps 와 정합 |


### 2026-04-20 (v1 loop tick — persistent-notification-protection policy re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| persistent-notification-protection | ⏭️ SKIP | End-to-end 경로는 여전히 `cmd notification post` 로 불가능 — `adb shell cmd notification post --help` 출력에 `-f|--flag` 등 ongoing 플래그 지정 옵션이 없음을 재확인 (flags: `-h`, `-v`, `-t`, `-i`, `-I`, `-S`, `-c` 만 존재). 현재 `dumpsys notification --noredact | grep -iE "flag.*ongoing\|no_clear"` 도 hit 없음 — 에뮬레이터에 실제 지속 알림 소스 없음. 대안으로 `PersistentNotificationPolicyTest` 를 신규 실행해 7 testcase 전부 PASS (`tests="7" skipped="0" failures="0" errors="0"`, 0.016s, timestamp 2026-04-20T14:43:45): `treats_ongoing_notifications_as_persistent`, `treats_non_clearable_notifications_as_persistent`, `keeps_call_related_persistent_notifications_visible`, `keeps_recording_and_navigation_persistent_notifications_visible`, `allows_charging_notifications_to_be_hidden`, `ignores_normal_clearable_notifications`, `disables_bypass_when_critical_persistent_protection_is_turned_off`. Policy 레이어는 건강 — end-to-end 은 여전히 실제 전화/내비/녹화 앱으로 릴리스 QA 에서만 커버 |


### 2026-04-20 (v1 loop tick — quiet-hours re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | ⏭️ SKIP | `com.coupang.mobile` 미설치 + `cmd notification post` 는 `com.android.shell` 로만 게시되어 `packageName in shoppingPackages` 분기를 adb 로 트리거 불가. 현재 22:20 KST (device hour=23) 로 default quiet hours [23,7) 안이라 `currentNotificationContext.quietHours=true` 여야 하지만, 쇼핑 패키지 주입 경로 없음. 릴리즈에서는 실제 Coupang 앱 또는 instrumented 테스트로만 end-to-end 커버. 단위 테스트 `QuietHoursPolicyTest` / `NotificationClassifierTest` 가 policy 단계를 커버. 이전 sweep 과 동일 SKIP — fresh evidence 확인 |


### 2026-04-20 (v1 loop cycle sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-inbox | ✅ PASS | 4건 중복 알림 posting 후 Digest 탭에 앱 그룹 카드 "관련 알림 4건" 렌더 |
| digest-suppression | ✅ PASS | `com.android.shell` 에서 동일 bigtext 3회 posting → 원본 tray 에서 제거됨. replacement 알림이 `smartnoti_replacement_digest_default_light_private_noheadsup` 채널 + `actions=3` + title `Promo` 로 live 확인. 기존 sweep 의 SKIP 해소 (opt-in 상태가 DataStore 에 이미 persist 돼 있어 자동 실행 가능) |
| duplicate-suppression | ✅ PASS | 같은 content signature 3회 posting 시 1 Digest + 2 Silent (threshold 3) 관측 |
| hidden-inbox | ✅ PASS | deep-link 진입 시 "숨겨진 알림 12건" 헤더 + 그룹 카드 렌더 |
| home-overview | ✅ PASS | StatPill 즉시 2 / Digest 11 / 조용히 12 렌더 |
| insight-drilldown | ⏭️ SKIP | 현재 tick 의 데이터로는 인사이트 trigger 부족, 별도 기간 관측 필요 |
| notification-capture-classify | ✅ PASS (부분) | "인증번호" 키워드 posting 이 pipeline 통과 — uiautomator race 로 StatPill 재확인은 inconclusive |
| notification-detail | ✅ PASS | Priority 탭 → 카드 탭 → Detail 섹션 렌더 (이전 session 에서 재확인) |
| onboarding-bootstrap | ⏭️ SKIP | pm clear 파괴적 — 자동화 제외, 릴리즈 사이클 테스트로만 수동 커버 |
| persistent-notification-protection | ⏭️ SKIP | cmd notification 이 FLAG_ONGOING_EVENT 미지원 — 유닛 테스트(PersistentNotificationPolicyTest) 가 대신 커버 |



### 2026-04-20 (v1 loop tick — manual fire, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ✅ PASS (부분) | 요약 알림 `숨겨진 알림 12건` = Home StatPill `조용히 12`. 단 emulator APK 가 PR #8 보다 오래돼 새 copy 미반영 (env noise, journey drift 아님) |
| priority-inbox | ✅ PASS | "엄마" VIP sender posting → Priority 탭에 "총 4건" + "엄마" 카드 렌더 |
| rules-management | ✅ PASS | Rules 탭 → "내 규칙" 헤더 + "직접 규칙 추가" 섹션 렌더 |
| protected-source-notifications | ✅ PASS | `cmd notification post -S media` (category=transport) posting 후 dumpsys 에 MediaTest 잔존 — SmartNoti 가 cancel 하지 않음 |
| notification-detail | ✅ PASS (재검증) | Priority 카드 탭 → Detail 진입. "왜 이렇게 처리됐나요?" / "어떻게 전달되나요?" / "원본 알림 처리 상태" 3 섹션 + reasonTags 렌더 |
| rules-feedback-loop | ✅ PASS | "엄마" Priority 카드 → Detail → "Digest로 보내기" 탭. Rules 탭에 "엄마" PERSON 규칙 추가됨 (활성 규칙 3→4, Digest 2→3, "발신자 기준" 표시) |
| notification-capture-classify | ✅ PASS (재검증) | "인증번호 999999" bigtext posting 직후 Home StatPill `즉시` 3→4 관측. 분류 키워드 경로 → UI 반영까지 end-to-end 확인 |
| home-overview | ✅ PASS (재검증) | Home 렌더: StatPill `즉시 3 / Digest 13 / 조용히 13`, "실제 알림 상태 · 연결됨", 인사이트 카드 (26개 정리, 89%, top-reason 3종 breakdown), 최근 흐름 차트 `최근 3시간 / 24시간` 탭 + peak/flow 로우 |
| insight-drilldown | ✅ PASS | Home 인사이트 "반복 알림 · 5건" 카드 탭 → "반복 알림 이유" 상세 (8건 시간순 + 공기 이유 `조용한 시간` + filtered 카드 리스트) 렌더 |

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
