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
| [digest-suppression](digest-suppression.md) | 디제스트 자동 묶음 및 원본 교체 | shipped | 2026-04-20 |
| [protected-source-notifications](protected-source-notifications.md) | 미디어/통화/포그라운드 서비스 보호 | shipped | 2026-04-20 |
| [persistent-notification-protection](persistent-notification-protection.md) | 지속 알림 키워드 기반 보호 | shipped | 2026-04-20 |

### Inboxes & UI
| ID | Title | Status | Last verified |
|---|---|---|---|
| [home-overview](home-overview.md) | 홈 개요 (요약 + 인사이트) | shipped | 2026-04-21 |
| [priority-inbox](priority-inbox.md) | 중요 알림 인박스 | shipped | 2026-04-21 |
| [digest-inbox](digest-inbox.md) | 정리함 인박스 | shipped | 2026-04-21 |
| [hidden-inbox](hidden-inbox.md) | 숨긴 알림 인박스 (Hidden 화면) | shipped | 2026-04-20 |
| [notification-detail](notification-detail.md) | 알림 상세 및 피드백 액션 | shipped | 2026-04-21 |
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


### 2026-04-21 (v1 loop tick — notification-detail re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | ✅ PASS | `cmd notification post -S bigtext -t DetailVerify0421 Promo1 "오늘의 이벤트 자세히 보기"` → Home 타임라인 "방금 정리된 알림" 에 `DetailVerify0421` Digest 카드 렌더. 카드 탭 → `NotificationDetailScreen` 진입: `DetailTopBar` `알림 상세`, 요약 카드 (`Shell` / `DetailVerify0421` / body / StatusBadge `Digest`), `왜 이렇게 처리됐나요?` + reasonTags (`발신자 있음 / 사용자 규칙 / 프로모션 알림 / 온보딩 추천 / 조용한 시간`), 온보딩 추천 반영 카드 (`빠른 시작 추천에서 추가된 규칙이에요`), `어떻게 전달되나요?` (`덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요. · 전달 모드 · Digest 묶음 전달 · 소리 · 부드럽게 알림 · 진동 · 가벼운 진동 · Heads-up · 꺼짐 · 잠금화면 · 내용 일부만 표시`). 스크롤 시 `원본 알림 처리 상태` (`원본 상태 · 원본 숨김 시도됨 · 대체 알림 · 표시됨`) + `이 알림 학습시키기` 3버튼 (`중요로 고정 · Digest로 보내기 · 조용히 처리`) 모두 관측 — 어제 `PASS (부분)` 의 "액션 버튼 영역 스크롤 필요" 제약을 스크롤 검증으로 해소. `중요로 고정` 탭 → StatusBadge `Digest → 즉시 전달`, 전달 모드 카피 `Digest 묶음 전달 → 즉시 전달` + description `중요 알림으로 바로 전달하지만 방해를 줄이기 위해 heads-up은 띄우지 않아요.` 로 전환. Home 복귀 시 StatPill `즉시 5 → 6` 반영. Rules 탭에서 `활성 규칙 5개 · 즉시 전달 2 · Digest 3` + 신규 `DetailVerify0421 연락은 바로 보여줘요 · 사람 · 즉시 전달 · 발신자 기준` PERSON 규칙 upsert 확인 — `NotificationFeedbackPolicy.toRule` + `RulesRepository.upsertRule` 경로 관측. 관측된 부가 동작: 동일 signature 의 2차 posting 은 `프로모션 알림` 키워드 Digest 규칙이 신규 PERSON Priority 규칙을 override 해 Digest 로 분류됨 (Home 카운트 `Digest 15→16`). Observable steps 1–6 및 Exit state (DB row 업데이트 · 규칙 insert · 뒤로가기로 Home 복귀) 전부 충족 — recipe Step 3 의 "자동 Priority" 는 journey 계약이 아니라 classifier 우선순위 규칙 (keyword Digest > PERSON Priority) 의 산물로, contract drift 아님 |

### 2026-04-21 (v1 loop tick — home-overview re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-overview | ✅ PASS | Recipe 3건 posting (`엄마0421` / `Coupang0421` / `광고0421`) 후 `MainActivity` 재개. uiautomator dump (`/tmp/ui_home.xml`) 에서 10개 observable step 모두 관측: ScreenHeader `SmartNoti` + `중요한 알림만 먼저 보여드리고 있어요`, StatPill `즉시 5 / Digest 15 / 조용히 17`, HomeNotificationAccessCard `연결됨 · 실제 알림이 연결되어 있어요 · 최근 실제 알림 37개가 Home에 반영됐어요 · 즉시 5개 · Digest 15개 · 조용히 17개로 분류됐어요`, QuickActionCard `중요 알림 지금 봐야 할 알림 5개` / `정리함 묶인 알림 15개`, QuickStartAppliedCard `추천 3개 적용됨 · 빠른 시작 추천이 적용되어 있어요` + 최근 효과 copy. 2번째 스크롤 dump 에서 InsightCard `지금까지 32개의 알림을 대신 정리했어요 · 86% · Shell 알림 26개가 가장 많이 정리` + 3 reason chip (`사용자 규칙 6건 / 조용한 시간 6건 / 프로모션 알림 5건`) 확인. 3번째 dump 에서 TimelineCard `최근 흐름 · 최근 3시간 / 최근 24시간` 탭 + `피크 · 2시간 전 · 정리 9건 · 즉시 1건` 로우 + 최근 알림 리스트 (`광고0421` Digest / `Coupang0421` 조용히 정리) 렌더. 어제 `PASS (부분)` 의 "상세 카드/차트 시각 회귀 미실행" 한계 해소 — 세 번 스크롤로 전체 스크롤 범위의 카드/차트/리스트가 정상 렌더됨을 dump 로 확증 |


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
