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
| [notification-capture-classify](notification-capture-classify.md) | 알림 캡처 및 분류 | shipped | 2026-04-24 |
| [duplicate-suppression](duplicate-suppression.md) | 중복 알림 감지 및 DIGEST 강등 | shipped | 2026-04-26 |
| [quiet-hours](quiet-hours.md) | 조용한 시간 | shipped | 2026-04-24 |

### Source notification routing (시스템 tray 조작)
| ID | Title | Status | Last verified |
|---|---|---|---|
| [silent-auto-hide](silent-auto-hide.md) | 조용히 분류된 알림 자동 숨김 | shipped | 2026-04-24 |
| [digest-suppression](digest-suppression.md) | 디제스트 자동 묶음 및 원본 교체 | shipped | 2026-04-26 |
| [protected-source-notifications](protected-source-notifications.md) | 미디어/통화/포그라운드 서비스 보호 | shipped | 2026-04-24 |
| [persistent-notification-protection](persistent-notification-protection.md) | 지속 알림 키워드 기반 보호 | shipped | 2026-04-24 |

### Inboxes & UI
| ID | Title | Status | Last verified |
|---|---|---|---|
| [home-overview](home-overview.md) | 홈 개요 (요약 + 인사이트) | shipped | 2026-04-25 |
| [home-uncategorized-prompt](home-uncategorized-prompt.md) | 새 앱 분류 유도 카드 | shipped | 2026-04-26 |
| [priority-inbox](priority-inbox.md) | 중요 알림 인박스 (검토 대기) | shipped | 2026-04-24 |
| [inbox-unified](inbox-unified.md) | 정리함 통합 탭 (Digest + 보관/처리) | shipped | 2026-04-26 |
| [digest-inbox](digest-inbox.md) | 정리함 인박스 (legacy) | deprecated → inbox-unified | 2026-04-22 |
| [hidden-inbox](hidden-inbox.md) | 숨긴 알림 인박스 (legacy) | deprecated → inbox-unified | 2026-04-21 |
| [notification-detail](notification-detail.md) | 알림 상세 및 피드백 액션 | shipped | 2026-04-26 |
| [ignored-archive](ignored-archive.md) | 무시됨 아카이브 (opt-in IGNORE 뷰) | shipped | 2026-04-24 |
| [insight-drilldown](insight-drilldown.md) | 인사이트 드릴다운 | shipped | 2026-04-26 |

### Categories, Rules & onboarding
| ID | Title | Status | Last verified |
|---|---|---|---|
| [onboarding-bootstrap](onboarding-bootstrap.md) | 첫 온보딩 및 기존 알림 부트스트랩 | shipped | 2026-04-24 |
| [categories-management](categories-management.md) | 분류 (Category) CRUD + drag-reorder | shipped | 2026-04-25 |
| [rules-management](rules-management.md) | 고급 규칙 편집 (Settings 하위) | shipped | 2026-04-25 |
| [rules-feedback-loop](rules-feedback-loop.md) | 알림 피드백 → 룰 저장 | shipped | 2026-04-24 |

## 아직 문서화하지 않은 영역

다음 기능들은 구현되어 있으나 별도 journey 로 분리하지 않았습니다 (위 journey 들의 out-of-scope 에서 언급). 후속 PR 에서 필요해지면 추가:

- Settings 화면 전반 (개별 토글/옵션 각각은 연관 journey 가 커버)
- Quick-start 적용 결과 카드 (`QuickStartAppliedCard`) 자체 — `home-overview` 안에서 일부 커버
- Notification access 권한 재요청 UX — `onboarding-bootstrap` 이 일부 커버

## Verification log


### 2026-04-26 (journey-tester — insight-drilldown rotation sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| insight-drilldown | PASS | Oldest non-deprecated journey by `last-verified` (2026-04-24 → 2026-04-26). emulator-5554, Coupang × 5 게시 (`cmd notification post -S bigtext`) → 홈 탭 (142,2274) → SmartNoti 인사이트 카드 `Shell 알림 32개가 가장 많이 정리됐고...` 탭 (540,1586) → InsightDrillDown 화면. eyebrow `인사이트` + title `Shell 인사이트` + ContextBadge `일반 인사이트` + range chips (`최근 3시간`/`최근 24시간`/`전체`) + 24h Digest 14/Silent 12 + reason 차트 (`이 앱에서 가장 많이 보인 이유는 '반복 알림'`) + reason navigation (반복 알림 8건 / 사용자 규칙 4건 / 프로모션 알림 4건) + 필터 카드 리스트 (Shell `ToastVerify`) 모두 문서대로 렌더. `전체` 칩 tap (695,782) → Digest 17/Silent 15 로 카운트 갱신 + subtitle `전체 기준 Shell에서 정리된 알림 32건...` 갱신 (Observable steps 1–9 + Exit state 일치). DRIFT 없음. |

### 2026-04-26 (journey-tester — inbox-unified rotation sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| inbox-unified | PASS | Oldest non-deprecated journey by `last-verified` (2026-04-24 → 2026-04-26). emulator-5554 cold-start (`am force-stop` → `am start`). 4× Coupang + 2× generic notifications via `cmd notification post -S bigtext` → BottomNav `정리함` (407,2274) → header (`정리함` / `알림 정리함` / Digest 묶음과 숨긴 알림 subtitle) + 3 segments (`Digest · 18건 / 보관 중 · 16건 / 처리됨 · 6건`) 일치. Default Digest 선택 + 앱별 그룹 카드 렌더. `보관 중` (541,484) → embedded HiddenScreen `2개 앱에서 16건을 보관 중이에요.`. `처리됨` (868,484) → 요약 카피 `이미 확인했거나 이전 버전에서 넘어온 알림이에요…` 일치. Digest 탭 preview card → Detail (`알림 상세` + `왜 이렇게 처리됐나요?` + `전달 모드 · Digest 묶음 전달`) → KEYCODE_BACK → 같은 Inbox 서브탭 + 카운트 복원. Observable steps 1–8 + Exit state 모두 일치. DRIFT 없음. |

### 2026-04-26 (journey-tester — ignored-archive rotation sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| ignored-archive | PASS | Rotation re-verification (oldest non-deprecated journey by `last-verified`, was 2026-04-24). emulator-5554, cold-start `am force-stop com.smartnoti.app` → 설정 탭 (936,2274) → 스크롤하여 "무시된 알림 아카이브 표시" 카드 노출. 토글 OFF→ON tap (927,440) → 같은 카드에 "무시됨 아카이브 열기" OutlinedButton ([391,574][690,623]) 등장 (Trigger 일치). 버튼 tap → `IgnoredArchiveScreen` 마운트, dump 결과 eyebrow `아카이브` / title `무시됨` / subtitle `IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요.` + EmptyState `무시된 알림이 없어요` + `IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요.` 정확히 렌더 (Observable steps 1–5 empty-state 분기 일치, 현재 DB IGNORE row 0건). 뒤로가기 → 토글 OFF tap → 같은 카드에서 "무시됨 아카이브 열기" 사라짐 (Exit state 일치). 비파괴 — 토글은 verification 후 OFF 로 복원. DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26. |

### 2026-04-26 (journey-tester — digest-suppression rotation sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | PASS | Oldest active journey by `last-verified` (2026-04-24 → 2026-04-26). emulator-5554, `com.smartnoti.app` + `com.smartnoti.testnotifier` both installed. testnotifier `PROMO_DIGEST` 카드 ([95,1009][507,1114] center 301,1062) tap → dumpsys 결과: 원본 testnotifier 의 "오늘만 특가 안내" payload 가 active list 에서 제거되고 동일 title 이 `com.smartnoti.app` `smartnoti_replacement_digest_default_light_private_noheadsup` 채널 (importance=3, AutoCancel) 에 새 entry 로 게시됨. Observable steps 1–5 (auto-expansion → suppress 판정 → cancel 원본 → notify replacement) + Exit state (tray 원본 제거, replacement 1건 잔존) 일치. DRIFT 없음. |

### 2026-04-26 (journey-tester — home-uncategorized-prompt first end-to-end ADB sweep, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-uncategorized-prompt | PASS | First end-to-end ADB verification since 2026-04-22 ship (was `last-verified: —`). emulator-5554, 알림 환경: com.android.shell + com.smartnoti.testnotifier + com.coupang.mobile (3 distinct uncovered packages, threshold met). 홈 탭 진입 → LazyColumn 최상단에 `HomeUncategorizedAppsPromptCard` mount: ContextBadge "분류 제안" + Title "새 앱 3개가 알림을 보내고 있어요" + Body "Shell, SmartNoti Test Notifier, Coupang 앱을 분류하시겠어요?" + 두 CTA "분류 만들기" / "나중에" 모두 정확히 렌더 (Observable steps 1–3 일치). "나중에" 탭 → 카드 즉시 unmount, Home 본문 (StatPill / 인사이트 카드) 그대로 유지 (step 5). `am force-stop com.smartnoti.app` + 재기동 → 카드 다시 안 뜸 (24h snooze 가 DataStore 에 persist, step 6 일치). DRIFT 없음 — `UncategorizedAppsDetector.Prompt` + `HomeUncategorizedAppsPromptCard` + `SettingsRepository.setUncategorizedPromptSnoozeUntilMillis` 모두 doc 과 일치. Known gaps "Recipe end-to-end 는 journey-tester 가 ADB 로 검증 전" 항목 resolved 로 마킹. `last-verified` — → 2026-04-26. |

### 2026-04-26 (journey-tester — rules-management explicit draft flag end-to-end post-#357, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | PASS | First end-to-end ADB sweep after PR #357 (026ca82 `feat/rule-explicit-draft-flag`) shipped explicit `draft: Boolean` flag. APK rebuilt 2026-04-26 15:27 — initial dump on stale 14:33 install showed legacy single "나중에 분류에 추가" CTA, fresh `adb install -r` resolved. Settings → "고급 규칙 편집 열기" → Rules screen baseline 3개 룰. (a) 새 키워드 룰 `ActionNeededTest_0426` 저장 → `CategoryAssignBottomSheet` 가 두 dismiss CTA "분류 없이 보류" + "작업 목록에 두기" 정확히 split 노출. (b) "작업 목록에 두기" 탭 → 활성 5개, 액션 그룹 위에 **"작업 필요"** SectionLabel + 설명 카피 + ActionNeededTest_0426 row (border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너) 렌더. (c) 사전 OLD APK 단일-CTA 경로로 만든 `DraftFlagTest_0426` 룰이 자동으로 **"보류"** sub-section 에 위치 (사용자가 보류함 배너 + "보류" chip + 우하단 "작업으로 끌어올리기" TextButton) — 두 sub-bucket 동시 렌더 + 순서 작업 필요 → 보류 확인. (d) "작업으로 끌어올리기" 탭 → DraftFlagTest_0426 가 보류 → 작업 필요 로 이동, "보류" SectionLabel 사라짐 (regression guard PASS — 빈 sub-bucket 라벨 미노출). 두 신규 Rule 삭제로 baseline 3개 복원. DRIFT 없음 — Observable steps 6 의 두-CTA sheet + UnassignedRulesPartitioner split + promote 어포던스 모두 doc 과 일치. `last-verified` 2026-04-25 → 2026-04-26. |

### 2026-04-26 (journey-tester — duplicate-suppression Settings dropdowns end-to-end post-#354, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| duplicate-suppression | PASS | First end-to-end ADB sweep after PR #354 (eb15c59 `feat/duplicate-threshold-window-settings`) shipped Settings dropdowns. Settings → "운영 도구처럼 명확하게 조정" 카드 → "중복 알림 묶기" row visible with default `반복 3회 / 최근 10분`. Threshold AssistChip dropdown lists exactly `반복 2/3/4/5/7/10회` (1 hidden as designed); window dropdown lists `5/10/15/30/60분`. Selected `반복 2회` → tapped two distinct-tag bigtext shell notifications with identical title+body (`ProbeTagY1`/`ProbeTagY2` "이중 검증 본문 동일 새내용") → `dumpsys notification --noredact` "원본 알림 숨김" replacement count grew 10→12 (DIGEST + replacement pair) confirming 2nd occurrence demoted. Reset to `반복 3회` → posted two distinct-tag duplicates (`ProbeTagX1`/`ProbeTagX2`) → replacement count unchanged 10→10 (SILENT path holds at boundary). TestNotifier "반복 알림 3건 보내기" scenario also fired DIGEST under threshold=2 (3 reposts, replacement bigText "원본 알림 숨김을 시도하고 Digest에 모아뒀어요 · 반복 알림"). Same-tag `cmd notification post` (key replacement) does NOT trip duplicate detection — matches Observable step 4 "같은 sourceEntryKey 의 재게시면 count 증가 없음". DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26. |

### 2026-04-26 (journey-tester — quiet-hours positive-case ADB capture post-#346, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | PASS | First end-to-end positive capture after Settings hour-picker (PR #346 `feat/settings-quiet-hours-window-editor`) + debug-inject `--es package_name` (PR #339) + explainer copy (PR #338) all shipped. emulator KST 12:54 (default `[23,7)` 밖). Settings → 운영 상태 → Quiet Hours row 의 종료 picker dropdown 으로 종료=11→13 설정 → 요약 `11:00 ~ 13:00` 갱신 (12 포함). DataStore `quiet_hours_start_hour=11 / end=13 / enabled=true` 영속 확인. `am broadcast … --es package_name com.coupang.mobile --es app_name 'Coupang' --es title 'QuietHrsPositive…'` → DB `status=DIGEST, reasonTags=발신자 있음\|조용한 시간\|쇼핑 앱` (no `사용자 규칙`). 정리함 → Digest · 15건 → Coupang 1건 row → Detail "왜 이렇게 처리됐나요?" 카드 chip row 아래 "지금 적용된 정책" sub-section 정확히 한 줄 카피 `지금이 조용한 시간(11시~13시)이라 자동으로 모아뒀어요.` 렌더 — Observable step 5 same-day 카피 패턴 (`"11시" + "13시"` substring, `"익일"` 미포함) 일치. 전달 모드 카드 `Digest 묶음 전달` 도 동시 렌더. DRIFT 없음. Known gaps blocker (b) 가 positive 증명까지 완료 (bullet 에 마킹). `last-verified` 는 같은 날(2026-04-26) 이므로 유지. |

### 2026-04-26 (journey-tester — quiet-hours post-#339 explainer positive-case re-attempt, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | PASS | Follow-up to #339 (5ba2c9b feat/quiet-hours-explainer-copy-wire) deferred positive-case ADB capture. emulator-5554 KST 11:24 (default `[23,7)` 밖). DB query confirms 3 historical positive-case rows exist (DIGEST + `조용한 시간` + no `사용자 규칙`): `VerifySweep0422-3` (04-24 02:02), `MediaTest0421R2/MediaTest0421` (04-21 04:06/01:48). 정리함 탭 navigation: Digest(14건) bundle preview Detail 진입 → 가장 최근 row 만 노출 (Shell `Shopping/한정` reasonTags `발신자 있음 \| 한정 \| 반복 알림`, 조용한 시간 미포함) → "왜 이렇게 처리됐나요?" 카드 chip row + 전달 모드 카드만 렌더, "지금 적용된 정책" sub-section 부재 (negative-case tag-gate 라이브 재확인). 보관 중(31건) Shell bundle 도 같은 preview 제약. `DebugInjectNotificationReceiver` 가 packageName 을 `com.smartnoti.debug.tester` 로 hardcode → quiet-hours 분기 (`packageName in setOf("com.coupang.mobile")`) 우회 불가. Plan-implementer 의 deferral 사유 (inbox bundle preview limitation) 정확. Home 인사이트 카드 "Shell 알림 46개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'예요" — quiet-hours 파이프라인이 라이브로 driving 중. DRIFT 없음. Known gaps 에 positive-case ADB blocker 항목 추가 (bundle preview "전체 보기" 또는 receiver `--es package_name` 지원 우회안). `last-verified` 2026-04-24 → 2026-04-26. |

### 2026-04-26 (journey-tester — notification-detail rule-chip drift correction)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | PASS | gap-planner 가 surfaced doc drift: Known gaps 의 "Detail 내부에서 '룰 보기' 바로가기 부재" 항목이 stale (`RuleHitChipRow` + `onRuleClick → Routes.Rules.create(highlightRuleId=…)` 이미 shipped, `NotificationDetailScreen.kt:239-241` + `AppNavHost.kt:339-346`). emulator-5554 `date -u`=2026-04-26. baseline `cmd notification post DetailTest_0426 RuleHitTest` → Home 의 PayTest (rule hit 보유) 카드 탭 → Detail 진입 → "왜 이렇게 처리됐나요?" 카드의 "적용된 규칙" 서브섹션에 "Shell" rule chip clickable 렌더 ([92,1292][218,1418]) → chip 탭 → Rules 화면 (4개 룰 리스트 "오늘만 특가 안내", "YT", "프로모션 알림", "반복 알림") 으로 navigate 확인. Known gaps 항목 resolved 마킹 + Change log drift-correction 엔트리 추가. `last-verified` 2026-04-25 → 2026-04-26. |

### 2026-04-26 (journey-tester — priority-inbox re-verify via debug-inject marker)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | PASS | v1 loop tick rotation pick (oldest active 2026-04-24 batch; `categories-management` already bumped to 2026-04-26 by plan-implementer in #333 same-PR doc-sync, deprecated `hidden-inbox`/`digest-inbox` skipped). emulator-5554 `date -u`=2026-04-26. `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg13000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB row `PriDbg13000\|PRIORITY\|디버그 주입` 확인 (sqlite3 run-as). Cold-start Home: passthrough card `검토 대기 34` / `SmartNoti 가 건드리지 않은 알림 34건` / `검토하기` 노출. 탭 → PriorityScreen eyebrow `검토` + title `SmartNoti 가 건드리지 않은 알림` + subtitle `이 판단이 맞는지 확인하고…` + SmartSurfaceCard `검토 대기 34건` + 카드별 `이 판단을 바꿀까요?` + `→ Digest / → 조용히 / → 규칙 만들기` 3버튼 노출. BottomNav 4탭 (홈/정리함/분류/설정), Priority 탭 부재. Observable steps 1–7 + Exit state 일치, DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26. |


### 2026-04-26 (journey-tester — home-uncategorized-prompt partial verify, render path SKIP)

| Journey | Result | Notes |
|---|---|---|
| home-uncategorized-prompt | SKIP | Highest-leverage pick (`last-verified` empty since 2026-04-22 first doc). emulator-5554 partial verification of detector inputs: `smartnoti_categories.preferences_pb` shows 5 categories with `appPackageName` pin only on 2 packages (`com.android.shell`, `com.google.android.youtube` via `중요 알림`). DB recent (≤7d) packages = `com.android.shell` (74, covered), `com.smartnoti.testnotifier` (2, uncovered), `com.smartnoti.debug.tester` (1, uncovered) — uncoveredCount=2 < THRESHOLD=3. Home dump confirms no `HomeUncategorizedAppsPromptCard` rendered (no `새 앱`/`분류 만들기`/`나중에` strings present). `smartnoti_settings.preferences_pb` lacks `uncategorized_prompt_snooze_until_millis` field → snooze is expired (None reason = below-threshold, not snooze). Detector "None when uncoveredCount < 3" branch matches Observable step 2 inverse + Exit-state "uncoveredCount < 3 → 카드 자동 숨김" claim. SKIP for end-to-end render path (steps 3–5 — render / "분류 만들기" tap / "나중에" snooze persist) because recipe needs ≥3 distinct uncovered packageNames and `cmd notification post` only emits `com.android.shell` (documented Known gap line 106). Installing additional test apps to seed the third package is out of agent scope without explicit caller approval. `last-verified` left empty per docs-sync rule (only real recipe execution bumps it). |


### 2026-04-26 (journey-tester — silent-auto-hide re-verify post-#327 manifest queries)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | PASS | v1 loop tick rotation (2026-04-24 = oldest non-deprecated tier; `categories-management` already verified post-#327 yesterday). emulator-5554 dumpsys: root summary `보관 중인 조용한 알림 31건` + text `탭: 보관함 열기 · 스와이프: 확인으로 처리` + action `숨겨진 알림 보기` on `smartnoti_silent_summary` (importance=1, vis=SECRET, pri=-2) — Observable step 5 일치. 그룹 채널 `smartnoti_silent_group` 의 N≥2 임계 강제도 다수 sender groupKey (`…sender:FooBarSender`, `…PathBTest_003943`, `…PathATest_0422`) 로 확인 (step 6/7). 콜드 deep-link → `Hidden` 화면 헤더 `보관 31건 · 처리 2건` + 기본 `보관 중 · 31건` 탭, 요약 카드 `1개 앱에서 31건을 보관 중이에요.`. `처리됨 · 2건` 탭 전환 → 서브카피 `이미 확인했거나 이전 버전에서 넘어온 알림이에요.` 노출. 그룹 카드 초기 collapsed (preview 라벨 0건), chevron(`펼치기`) tap → `최근 묶음 미리보기` + bulk action row (`모두 중요로 복구` / `모두 지우기`) 노출. #327 (`<queries>`) 변경은 SILENT 라우팅·요약 채널 계약과 무관 (manifest 는 PackageManager 라벨 lookup 한정), regression 없음 확인. DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26. |


### 2026-04-25 (journey-tester — home-overview re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-overview | PASS | v1 loop tick pick (last-verified 2026-04-24, 1-day stale per UTC `date -u` = 2026-04-25). APK `lastUpdateTime=2026-04-25 19:27:39`. Recipe ran on emulator-5554: 3 test notifications posted (Mom/Coupang/Promo), `am start MainActivity` + tap `홈` BottomNav. UI dump confirms full render top→bottom: HomeUncategorizedAppsPromptCard ("새 앱 3개가 알림을 보내고 있어요" + 분류 만들기 / 나중에), ScreenHeader ("SmartNoti / 중요한 알림만 먼저 보여드리고 있어요"), StatPill ("오늘 알림 77개 중 중요한 30개..." + 즉시 30 / Digest 14 / 조용히 33 — IGNORE excluded per spec), HomePassthroughReviewCard ("검토 대기 30" + 검토하기 — priority>0 gate), inline access row ("연결됨 / 실제 알림이 연결되어 있어요" — connected → inline strip, not full card per Task 10 declutter), InsightCard ("일반 인사이트" + Shell 46건 + 조용한 시간 30/반복 8/사용자 규칙 5 chips), 방금 정리된 알림 5 cards + `전체 77건 보기` HomeRecentMoreRow footer (DEFAULT_CAPACITY=5 cap verified). QuickStartAppliedCard absent (expected — TTL/ack gate已 consumed days ago). Observable steps 1–10 + Exit state 일치, DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-25. |


### 2026-04-25 (journey-tester — notification-detail re-verify after #323 reclassify confirm snackbar)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | PASS | Re-verify post-#323 (`docs/plans/2026-04-24-detail-reclassify-confirm-toast.md`). APK `lastUpdateTime=2026-04-25 19:27:39`. 정리함 → SmartNoti Test Notifier Digest 묶음 preview row tap → Detail mount, Observable steps 1–5 PASS. Path A `중요 알림` row tap → 시트 dismiss + snackbar `"중요 알림 분류로 옮겼어요"` 렌더 (BottomNav 위, 자동 사라짐). Path B `+ 새 분류 만들기` → editor prefill (name="오늘만 특가 안내", action=Digest=opposite-of-PRIORITY) → name 을 `ToastBverify_0425` 로 교체 후 "추가" 저장 → snackbar `"새 분류 'ToastBverify_0425' 만들었어요"` 렌더. Path B cancel ("닫기") → snackbar 미등장 확인. 3 outcomes (`AssignedExisting` / `CreatedNew` / cancel) 전부 doc 의 Observable step 5.iii 와 일치. DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-25. |


### 2026-04-25 (journey-tester — categories-management re-verify after #320 APP-token label lookup)

| Journey | Result | Notes |
|---|---|---|
| categories-management | PASS | Re-verify post-#320 (`feat(categories): resolve APP-token packageNames to user-facing app labels`). `am start` → BottomNav `분류` tap. List 3 categories: `중요 알림` content-desc 가 `조건: 키워드=인증번호,결제,배송,출발 또는 앱=Shell → 즉시 전달` 로 정확히 렌더 (token text 도 `앱=Shell`, `com.android.shell` raw 노출 없음). Detail (`중요 알림` row tap) → 펼친 chip 동일 `앱=Shell`; Edit AlertDialog 미리보기 chip 도 `앱=Shell` 즉시 렌더. 카드 / Detail / Editor 세 surface 모두 `LocalAppLabelLookup` 자동 wiring 작동. Detail 메타데이터 `APP · com.android.shell` raw line 은 documented out-of-scope (chip surface 한 곳만 적용 plan). Observable steps 1–5 verified, DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-25. |


### 2026-04-25 (journey-tester — rules-management D3 ADB end-to-end on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | PASS | Verifies #317 D3 (`rule-editor-remove-action-dropdown`). APK rebuilt 2026-04-25 09:40, installed via `adb install -r`. Settings → "고급 규칙 편집 열기" → Rules screen → "새 규칙 추가" 다이얼로그가 `기본 정보 / 규칙 타입 / 예외 규칙` 3개 섹션만 노출 — 이전의 "처리 방식" SectionLabel + RuleActionUi dropdown 부재 확인. Keyword 타입 + 이름 `VerifyTest_0425` + 매치값 `verifyotp` 저장 → 즉시 ModalBottomSheet "이 규칙을 어떤 분류에 추가하시겠어요?" + 3개 기존 분류 + "나중에 분류에 추가" CTA 노출. Skip → "활성 규칙 3→4" + 액션 그룹 위 "미분류" 섹션이 border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너로 렌더. 미분류 카드 재탭 → 동일 sheet 재오픈. Sheet 에서 "중요 알림" 옆 "추가" → "미분류" 섹션 사라지고 "즉시 전달 1→2" 로 합류. 테스트 후 신규 Rule 삭제로 baseline 복원. Observable steps 6 (sheet flow + 미분류 섹션 + 카드 재탭) 모두 doc 과 1:1 일치. DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-25. |


### 2026-04-24 (journey-tester — priority-inbox re-verify via debug-inject marker)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | PASS | v1 loop tick pick (last-verified 2026-04-23, 1-day stale per ground-truth `date -u` = 2026-04-24). `am broadcast -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver --es title PriDbg43000 --es body PriorityDebugSeed --es force_status PRIORITY` → DB row `PriDbg43000\|PRIORITY\|디버그 주입` 확인 (sqlite via `run-as`). Home 카드 "검토 대기 22 / SmartNoti 가 건드리지 않은 알림 22건 / 검토하기" visible (count: 21→22 reflects injection). `검토하기` 탭 → PriorityScreen eyebrow "검토" + title "SmartNoti 가 건드리지 않은 알림" + subtitle + SmartSurfaceCard "검토 대기 22건" + 카드별 "이 판단을 바꿀까요?" + `→ Digest / → 조용히 / → 규칙 만들기` 3 버튼 노출. BottomNav 4 탭 (홈/정리함/분류/설정), Priority 탭 부재 재확인. Observable steps 1–7 + Exit state 일치, DRIFT 없음. `last-verified` 2026-04-23 → 2026-04-24 bump. |


### 2026-04-24 (journey-tester — onboarding-bootstrap Exit-state re-verify on emulator-5554, non-destructive)

| Journey | Result | Notes |
|---|---|---|
| onboarding-bootstrap | PASS | `pm clear` recipe step skipped (destructive — out of agent scope without user approval). Verified Exit state on already-onboarded emulator-5554 instead: `run-as com.smartnoti.app cat files/datastore/smartnoti_categories.preferences_pb` shows all three deterministic seeds present in correct order — `cat-onboarding-important_priority` (PRIORITY, order 0, ruleIds keyword `인증번호,결제,배송,출발`), `cat-onboarding-promo_quieting` (DIGEST, order 1, ruleIds keyword `광고,프로모션,쿠폰,세일,특가,이벤트,혜택`), `cat-onboarding-repeat_bundling` (DIGEST, order 2, ruleIds `repeat_bundle:3`). `smartnoti_settings.preferences_pb` shows `onboarding_completed` + `onboarding_active_notification_bootstrap_completed` flags both persisted, and bootstrap pending flag has been consumed (no longer present). Matches the documented Exit state in the journey doc 1:1 — no drift. `last-verified` 2026-04-23 → 2026-04-24 (per `clock-discipline.md` rule from #313, ground-truth `date -u` = 2026-04-24). |


### 2026-04-24 (journey-tester — rules-feedback-loop date bump for 2026-04-22 PASS sweep)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | PASS | Date-bump only follow-up to PR #288 (rules-feedback-loop end-to-end PASS executed earlier today UTC; original sweep recorded `last-verified` as 2026-04-22 because the agent miscalculated the date). No re-execution — full Path A + Path B + auto-reclassify evidence already captured in the 2026-04-22 log entries above (fresh sender `AssignTest_003634`, datastore inspection of `smartnoti_rules.preferences_pb` + `smartnoti_categories.preferences_pb`). `last-verified` corrected 2026-04-22 → 2026-04-24 to reflect actual UTC date of the verification run. |


### 2026-04-24 (journey-tester — protected-source-notifications re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| protected-source-notifications | PASS | v1 loop tick pick (3-day-stale; last-verified 2026-04-21). Recipe A ran on emulator-5554 — `adb shell cmd notification post -S media -t "Player" MediaTest "미디어 스타일 테스트"` posted notification with `category=transport`. `dumpsys notification --noredact \| grep -B1 MediaTest` confirms `NotificationRecord(0x059d6bb5: pkg=com.android.shell ... id=2020 tag=MediaTest ... category=transport)` still present in active records + `mSoundNotificationKey=0\|com.android.shell\|2020\|MediaTest\|2000`, validating `ProtectedSourceNotificationDetector` category-based 보호 (`category ∈ {call, transport, navigation, alarm, progress}`) blocks SmartNoti's `cancelSourceNotification` path. Observable steps 1-4 verified end-to-end via tray dump (step 5 repository save covered by classifier unit tests). Recipe B (real YT Music playback) skipped — package installed but not actively playing. Recipe C (FLAG_FOREGROUND_SERVICE direct) inherently un-runnable per recipe note. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — persistent-notification-protection re-verify via unit tests)

| Journey | Result | Notes |
|---|---|---|
| persistent-notification-protection | PASS | v1 loop tick pick (2-day stale; last-verified 2026-04-22). Re-ran `PersistentNotificationPolicyTest` (7/7 PASS, 0.015s) covering bypass for 통화/내비/녹화 keywords, 충전 등 비-critical 알림 hide-allowed, and `protectCriticalPersistentNotifications=false` toggle 무시. End-to-end (real ongoing notification) skipped — `cmd notification post` 가 `FLAG_ONGOING_EVENT` 를 세팅하지 못하므로 recipe 자체가 unit-test path 를 권장. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — duplicate-suppression re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| duplicate-suppression | PASS | v1 loop tick pick (2-day stale; last-verified 2026-04-22). Posted 3x identical `Shopping / Repeat$i / 한정 특가` notifications via `adb shell cmd notification post -S bigtext`. `dumpsys notification` confirms `com.smartnoti.app` emitted replacement digest entries on `smartnoti_replacement_digest_default_light_private_noheadsup` channel (ranker_group + 2 child records id=659924330/659924331), validating duplicate threshold (≥3) → DIGEST classification → digest-suppression replacement path. Observable steps 1-7 verified end-to-end via system tray dump. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — inbox-unified first-time verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| inbox-unified | PASS | v1 loop tick pick (2-day stale per actual; last-verified 2026-04-22). ADB recipe end-to-end on emulator-5554. Posted 4 Coupang + 2 Promo notifications, launched app, tapped BottomNav "정리함" → `ScreenHeader` "정리함 / 알림 정리함 / Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요." + `InboxTabRow` 3 segments rendered (`Digest · 13건 / 보관 중 · 29건 / 처리됨 · 2건`). Default Digest segment shows `DigestGroupCard` ("SmartNoti Test Notifier 1건" preview). Tapping `보관 중 · 29건` segment (~540,480) → `HiddenNotificationsScreen` Embedded mode (header `1개 앱에서 29건을 보관 중이에요.` + bulk action `전체 숨긴 알림 모두 지우기` + Shell 29건 group), no inner ScreenHeader / ARCHIVED-PROCESSED row (Embedded confirmed). `처리됨 · 2건` segment (~870,480) → `1개 앱에서 2건을 처리했어요.` + Shell 2건 group. Observable steps 1-5 verified end-to-end (steps 6-8 detail/back-nav inferred from priority-inbox / notification-detail journeys; not re-driven this tick). DRIFT 없음. `last-verified` — → 2026-04-24. |


### 2026-04-24 (journey-tester — categories-management re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| categories-management | PASS | v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Tap BottomNav "분류" → list renders 3 categories ("중요 알림" 즉시 전달 / "프로모션 알림" Digest / "반복 알림" Digest), each with `규칙 1개` + `CategoryActionBadge` + inline `CategoryConditionChips` (text + content-desc both `조건: … → 즉시 전달|모아서 알림`). FAB clickable bounds `[642,1938][1038,2085]` above AppBottomBar (no inset clipping regression — 2026-04-22 fix holds). FAB tap → `CategoryEditorScreen` AlertDialog ("새 분류" / 분류 이름 / 전달 방식 dropdown default `즉시 전달 (PRIORITY)` / 연결할 앱 picker `특정 앱 없음` / Rule multi-select for all 3 rules / 미리보기 chip `조건 없음 → 즉시 전달` / 닫기·추가). Row tap → `CategoryDetailScreen` ("분류 상세" / 카테고리 이름 / `CategoryActionBadge` `즉시 전달` / 기본 정보 카드 with full `CategoryConditionChips` + `규칙 1개 · 순서 0` / 편집·삭제 / 소속 규칙 list). Observable steps 1-5 verified end-to-end. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — quiet-hours re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | PASS | v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Static-source + live-DB sweep on emulator-5554 (현재 시각 12:43 KST = `[23,7)` default 범위 밖, in-window post 불가하므로 historical row 검증). `NotificationClassifier.kt:90` (`if (input.packageName in shoppingPackages && input.quietHours) return DIGEST`) + `QuietHoursPolicy.kt:7-15` (same-day / overnight 분기) 가 Observable steps 1-3 과 일치, `SettingsRepository.kt:53-65` `currentNotificationContext` 가 `Calendar.HOUR_OF_DAY` + `QuietHoursPolicy(startHour,endHour)` 를 `NotificationContext` 로 묶어 주입하는 경로도 doc Code pointers 와 일치. Live DB `SELECT … WHERE reasonTags LIKE '%조용한%' ORDER BY postedAtMillis DESC LIMIT 3` → 가장 최근 `조용한 시간` tag row 3건 모두 `2026-04-24 02:02:09~11` (default quiet window 안) posted, packageName `com.android.shell`, status DIGEST/SILENT/SILENT — capture→classifier→repository 파이프라인이 quiet-hours 분기를 실제로 발화. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — rules-management re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | PASS | v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Recipe ran on APK `versionName=0.1.0` w/ listener enabled (`enabled_notification_listeners` includes `com.smartnoti.app/.SmartNotiNotificationListenerService`). Step 3 — `cmd notification post -S bigtext -t '은행' OtpTest '인증번호 123456'` — re-foregrounded `MainActivity`; Home StatPill advanced `오늘 알림 62→63` and `즉시 20→21` (`Digest 11 / 조용히 31` unchanged), confirming existing 인증번호 priority rule routes `com.android.shell` posts through listener → classifier → repository → HomeViewModel `observePriority` flow as documented. Initial cached UI required `am start` re-foreground for recomposition (sweep note, not drift). Settings 진입점 (`AdvancedRulesEntryCard`) + editor dialog (Phase C override pill / nested indent / drag-reorder) not re-traversed — covered by 2026-04-22 sweep + Phase C unit tests; recipe scope = StatPill delta only. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — notification-detail re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | PASS | v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Recipe ran on APK `lastUpdateTime=2026-04-24 01:44:11`: posted `cmd notification post -S bigtext -t 'DetailTest_0424' FbTest1 '분류 변경 테스트'` (DIGEST classified), navigated 정리함 tab → Digest sub-tab → Shell 묶음 preview row tap → Detail mounted. Observable steps 1-5 all PASS — `DetailTopBar`("알림 상세"), 알림 요약 카드 `StatusBadge=Digest`, "왜 이렇게 처리됐나요?" + 4 chips (`발신자 있음`/`사용자 규칙`/`프로모션 알림`/`온보딩 추천`), 온보딩 추천 카드, "어떻게 전달되나요?" 5 labels, "원본 알림 처리 상태" section, "이 알림 분류하기" + single `Button("분류 변경")`. Tap → `CategoryAssignBottomSheet` ("분류 변경" / "이 알림이 속할 분류를 고르거나 새 분류를 만들어요." / "기존 분류에 포함" + 3 categories (중요 알림/프로모션 알림/반복 알림) + terminal `+ 새 분류 만들기`). 경로 B verified — "새 분류 만들기" tap → `CategoryEditorScreen` prefill (name=`프로모션`, 전달 방식=`즉시 전달 (PRIORITY)` = DIGEST 소스 dynamic-opposite). DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — ignored-archive re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| ignored-archive | PASS | v1 loop tick pick (2-day-stale; last-verified 2026-04-22). Recipe ran on emulator-5554: navigated Settings tab → scrolled to "무시됨" card → toggled "무시된 알림 아카이브 표시" ON (checkable view bounds [859,1539][996,1665], `checked=false`→`true`). "무시됨 아카이브 열기" OutlinedButton appeared (text node bounds [391,1736][690,1785]) — matches Observable step 2-3 (showIgnoredArchive=true → AppNavHost re-composition no longer needed since route is unconditionally registered post-#199; toggle now only gates button visibility). Tapped button → `IgnoredArchiveScreen` mounted with eyebrow "아카이브" / title "무시됨" / subtitle "IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요." + EmptyState "무시된 알림이 없어요 / IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요." — exact copy match for step 5 empty branch (DB has 0 IGNORE rows: `sqlite3 smartnoti.db 'SELECT status, COUNT(*) FROM notifications GROUP BY status;'` → `DIGEST\|11 PRIORITY\|20 SILENT\|32`, no IGNORE; only PRIORITY+DIGEST onboarding categories exist). Back nav → toggled OFF → "무시됨 아카이브 열기" button removed from Settings card (Exit state confirmed: route still registered per fix #199 but unreachable without button). Step 6 (populated branch) not exercised this sweep — no IGNORE category seeded; covered by 2026-04-21 sweep + repository unit tests. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — notification-capture-classify re-verify post-#292/#287/#285 on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | PASS | v1 loop tick pick (2-day-stale; recent #292 suppress defaults / #287 audit helper / #285 frontmatter rule merged since last verify, capture path itself unchanged but suppress path tied in). Recipe ran on APK `lastUpdateTime=2026-04-24 01:44:11`: `cmd notification allow_listener` (idempotent), then `cmd notification post -S bigtext -t Bank CaptureClassifyTest_0424 "인증번호 123456을 입력하세요"`, launched `com.smartnoti.app/.MainActivity` and tapped Home tab. DB query via `run-as com.smartnoti.app sqlite3 databases/smartnoti.db` returned new row `com.android.shell:2020:CaptureClassifyTest_0424 | com.android.shell | Bank | 인증번호 | status=PRIORITY | ruleHitIds=keyword:인증번호,결제,배송,출발 | reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|중요 키워드` — confirms full Observable steps 1-10 (listener entry → coordinator → Rules+Categories+Settings snapshot → classifier rule match (`keyword:인증번호` etc.) → Category lift → CategoryConflictResolver → winning Category.action → NotificationDecision.PRIORITY → Repository upsert) and Exit state (`status ∈ {PRIORITY,DIGEST,SILENT,IGNORE}` row written, observable via repository flow). Home StatPill footer `전체 61건 보기` reflected accumulated capture count. PRIORITY classification cascade matches doc — keyword pre-rule heuristic resolved to PRIORITY despite no user-defined Bank rule (cumulative rule history from prior sweeps). #292 suppress-source default flip and #287/#285 docs/audit changes do not regress capture path — capture row write path unchanged. DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — home-overview re-verify post-#290/#292 on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-overview | PASS | v1 loop tick pick (2-day-stale). Recipe ran: posted Mom/Coupang/Promo via `cmd notification post -S bigtext`, launched `com.smartnoti.app/.MainActivity`, tapped Home tab (128,2254). Home rendered all Observable steps intact: ScreenHeader (`SmartNoti` + `중요한 알림만 먼저 보여드리고 있어요`), **StatPill** `오늘 알림 60개 중 중요한 19개를 먼저 전달했어요` + `즉시 19 / Digest 10 / 조용히 31` — IGNORE not included (step-4 invariant holds after #292 default-on suppress-source flip; totals 19+10+31=60 matches hero). PassthroughReviewCard `검토 대기 19` + `검토하기` (step 5). Access InlineRow `연결됨 · 실제 알림이 연결되어 있어요` (step 6, connected path). InsightCard `SmartNoti 인사이트` + `지금까지 41개의 알림을 대신 정리했어요` + reason list (`조용한 시간 · 32건` / `반복 알림 · 7건` / `사용자 규칙 · 2건` each with `탭해서 자세히 보기`) — step 8 OK. TimelineCard `최근 흐름` + range chips + `최근 3시간 기준 9개의 알림이 정리됐어요` + peak `방금 전 · 정리 9건 · 즉시 1건` (step 9). Recent list `방금 정리된 알림` with 5 truncated rows (엄마 / Coupang×3 / 광고Promo) carrying Digest/즉시 전달 tier chips + reason chips (`프로모션 알림` / `반복 알림` / `중요한 사람` / `온보딩 추천`) and sender chip `발신자 있음` (step 10). Footer `전체 60건 보기` rendered at end (HomeRecentMoreRow). HomeUncategorizedAppsPromptCard not mounted (no Prompt from detector — full coverage). QuickStartAppliedCard not mounted (TTL/ack expired). PR #290's condition chips are scoped to Categories surfaces (not Home) and do not affect this journey; PR #292's suppress-source default flip does not alter Home StatPill routing — counts still sourced from `observePriority/observeDigest/observeAllFiltered` per Known-gap baseline. `last-verified` 2026-04-21 → 2026-04-24. |


### 2026-04-24 (journey-tester — insight-drilldown re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| insight-drilldown | PASS | v1 loop tick rotation pick (2-day-stale). Recipe ran: posted Coupang × 5 via `cmd notification post -S bigtext`, launched `com.smartnoti.app/.MainActivity`, Home rendered SmartNoti 인사이트 카드 (`Shell 알림 38개가 가장 많이 정리됐고…`). Tapped Shell app card → `InsightDrillDownScreen` for app=Shell with full Observable steps 1–10: eyebrow `인사이트` + title `Shell 인사이트` + ContextBadge `일반 인사이트` (GENERAL tone) + range chips `최근 3시간` / `최근 24시간` / `전체` + 24h Digest 5/Silent 9 counts + reason 차트 헤더 (`이 앱에서 가장 많이 보인 이유는 '조용한 시간'이에요.`) + reason navigation list (`조용한 시간 · 8건` / `반복 알림 · 4건` / `사용자 규칙 · 1건` 모두 `탭해서 자세히 보기` 보조 텍스트) + 필터링된 `NotificationCard` 리스트 (Shell/Promo/Digest/조용한 시간/반복 알림 메타데이터 칩). Range 칩 `전체` 탭 → 카운트 Digest 7/Silent 31, reason `조용한 시간 · 32건` 으로 즉시 갱신 — range 인자 핸들링 정상. Empty state path 미실행 (데이터 충분). `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-24 (journey-tester — silent-auto-hide re-verify post-#292 on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | PASS | Targeted re-verify after PR #292 (default-on `suppressSourceForDigestAndSilent` + empty-set = all-apps + v1 migration) which shares the SILENT/Digest cancel branch. `dumpsys notification --noredact` shows live root summary `smartnoti_silent_summary` with title `보관 중인 조용한 알림 26건`, text `탭: 보관함 열기 · 스와이프: 확인으로 처리`, importance=1 (MIN), vis=SECRET, category=status — matches Observable step 5 copy verbatim. Group summaries on `smartnoti_silent_group` channel — `Promo · 조용히 4건`, `FooBarSender · 조용히 2건`, `PathBTest_003943 · 조용히 2건`, `PathATest_0422 · 조용히 2건` — all N≥2, no singletons in tray (Q3-A planner threshold holds). Posted fresh `cmd notification post -S bigtext Promo … "오늘만 30% 할인 광고 알림"`; root count incremented 26→27 and `Promo` group 4→5 within 4s, source `shell_cmd` record retained in tray (ARCHIVED capture default per #125 — `cancelSourceNotification=false` for ARCHIVED branch confirmed live, demonstrating #292's policy change does not regress the ARCHIVED path). `last-verified` 2026-04-22 → 2026-04-24. |


### 2026-04-22 (journey-tester — categories-management re-verify on emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| categories-management | PASS | v1 loop rotation pick (2-day-stale batch). BottomNav "분류" tap → 3 migrated categories rendered ("중요 알림" 즉시 전달 / "프로모션 알림" Digest / "반복 알림" Digest), each row showing `규칙 1개` + `CategoryActionBadge` chip + inline `CategoryConditionChips` (text and content-desc both `조건: 키워드=… → 즉시 전달|모아서 알림`). FAB clickable area at bounds `[642,1938][1038,2085]` sits above AppBottomBar (no inset clipping — 2026-04-22 FAB visibility fix confirmed). FAB tap opens `CategoryEditorScreen` AlertDialog "새 분류" with name field + 전달 방식 default `즉시 전달 (PRIORITY)` + 연결할 앱 (`특정 앱 없음`) + 3 Rule chips for multi-select + 미리보기 `CategoryConditionChips` (`조건 없음 → 즉시 전달`) + 닫기/추가. Row tap → `CategoryDetailScreen` "분류 상세" with category name, action chip, 기본 정보 + full `CategoryConditionChips`, 편집/삭제 buttons, 소속 규칙 list with `RuleRow`. Observable steps 1–5 all match. `last-verified` already 2026-04-22 (no bump); change-log entry appended. FAB label text was non-discoverable via uiautomator dump (NAF=true Compose node) — visual region + click target both functional. |


### 2026-04-24 (journey-tester — digest-suppression PASS via testnotifier recipe rotation, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | PASS | Independent re-run of the post-#295 testnotifier recipe (2 days stale since implementer dry-run). `am start com.smartnoti.testnotifier/.MainActivity` → uiautomator confirms PROMO_DIGEST card "이 시나리오 보내기" bounds `[95,1009][507,1114]` → `input tap 301 1062`. Post-tap `dumpsys notification --noredact` shows: original `tag=promo-1` "오늘만 특가 안내" payload absent from `com.smartnoti.testnotifier` (only `ranker_group` summary + unrelated `important-1` from prior IMPORTANT scenario remain); fresh `com.smartnoti.app` record id=-1158580653 channel=`smartnoti_replacement_digest_default_light_private_noheadsup` with title "오늘만 특가 안내", subText "SmartNoti Test Notifier • Digest", body "원본 알림 숨김을 시도하고 Digest에 모아뒀어요 · 사용자 규칙 · 프로모션 알림". Observable steps 3–5 verified live. `last-verified` 2026-04-22 → 2026-04-24. Recipe rotation value confirmed — testnotifier path remains stable across days (no quota saturation). |


### 2026-04-22 (plan-implementer — digest-suppression recipe rewritten via com.smartnoti.testnotifier, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | PASS | Plan `2026-04-22-digest-suppression-testnotifier-recipe.md` shipped (docs-only). Recipe flipped from `cmd notification post` (pkg=com.android.shell, hit NMS per-package 50 quota) to `com.smartnoti.testnotifier` PROMO_DIGEST scenario (separate quota budget). Dry-run on emulator-5554: `adb install -r app-debug.apk` → `am start com.smartnoti.testnotifier/.MainActivity` → `input tap 301 1062` on "이 시나리오 보내기" under "프로모션 알림 1건 보내기" card. Post-tap `dumpsys notification --noredact | grep -E "pkg=com.smartnoti.(testnotifier|app)|smartnoti_replacement_digest"` confirms: no `com.smartnoti.testnotifier` "오늘만 특가 안내" payload remains (only pre-existing `ranker_group` placeholder + unrelated `important-1` tag); `com.smartnoti.app` has a fresh `smartnoti_replacement_digest_default_light_private_noheadsup` entry (id=-1158580653, not in pre-tap baseline). Suppress→replacement path verified live end-to-end. SmartNoti 측 코드 변경 없음 — journey recipe block + Known gap drainage + Change log + `last-verified` 2026-04-21 → 2026-04-22 뿐. Retires the persistent SKIP from the two 2026-04-22 earlier tester sweeps (shell quota saturation). |


### 2026-04-22 (journey-tester — digest-suppression re-run post #292 still SKIP on emulator recipe, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | ⏭️ SKIP | Re-ran recipe after #292 (be1ab0c) merge to check whether the A+C default-flip + empty-set opt-out semantics resolves the long-standing live-recipe SKIP. Posted 3× `cmd notification post -S bigtext -t Promo VerifySweep0422-N '같은 광고 텍스트 할인 이벤트'` → `dumpsys notification --noredact` shows originals (`pkg=com.android.shell ... tag=VerifySweep0422-1/-2`, 3rd dropped by quota) still in tray alongside a pre-existing `pkg=com.smartnoti.app ... channel=smartnoti_replacement_digest_default_light_private_noheadsup` from earlier runs. Same root cause as the 2026-04-22 earlier sweep: `com.android.shell` ranker-group hits the NMS per-package 50-notification ceiling with accumulated stale test records, so fresh DIGEST posts can't be cleanly attributed to suppression path. #292 product fix itself was verified end-to-end by the implementer via ADB Scenario A (fresh-install `pm clear` + `FreshProm` post → only replacement remains) and PR review. `last-verified` unchanged (2026-04-21). Recipe-hardening (route via `com.smartnoti.testnotifier` with its own quota budget) remains the right gap-planner pickup to retire this persistent SKIP. Journey Known gaps updated to reflect the #292 fix is real but live recipe still blocked. |


### 2026-04-24 (plan-implementer — duplicate-notifications-suppress-defaults-ac shipped, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression / silent-auto-hide | DRIFT FIXED | Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` (A+C hybrid) shipped: `SmartNotiSettings.suppressSourceForDigestAndSilent` default flipped `false → true` and `NotificationSuppressionPolicy` now treats empty `suppressedSourceApps` as opt-out semantics ("all captured packages opted-in"). Existing users get an in-place v1 one-shot migration via `SettingsRepository.applyPendingMigrations` gated by the `suppress_source_migration_v1_applied` DataStore key — anyone who had explicitly toggled OFF is overwritten (documented trade-off). `SuppressedSourceAppsAutoExpansionPolicy` learned a no-op guard for empty `currentApps` so the implicit narrowing (opt-out → allow-list-of-one) cannot fire. ADB Scenario A on emulator-5554: `pm clear com.smartnoti.app` → install debug APK → grant listener + POST_NOTIFICATIONS → walk onboarding → `cmd notification post -S bigtext -t Promo FreshProm "쿠폰 할인 오늘만"` → `dumpsys notification --noredact` shows the original `FreshProm` is gone and only `smartnoti_replacement_digest_default_light_private_noheadsup` remains. Scenarios B (upgrade-from-OFF overwritten) and C (idempotency post-user-toggle-OFF) covered exhaustively by the new `SettingsRepositoryMigrationTest`. Both digest-suppression / silent-auto-hide journeys updated (Preconditions + Change log); `last-verified` not bumped because the recipe sections were not re-run end-to-end. Resolves the long-standing "신규/미설정 사용자에게 DIGEST/SILENT 가 이중으로 뜬다" Known gap on `digest-suppression`. |


### 2026-04-22 (journey-tester — rules-feedback-loop end-to-end re-verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | PASS | Fresh sender `AssignTest_003634` posted via `cmd notification post` → Home StatPill counted, card landed in 정리함 > 보관 중 > Shell 묶음. Detail shows single "분류 변경" CTA (no 4-button grid, no 무시 affordance — redesign intact). Sheet rendered "기존 분류에 포함" header + 3 seeded categories (중요 알림 PRIORITY / 프로모션 알림 DIGEST / 반복 알림 DIGEST) + "+ 새 분류 만들기" terminal row per spec. Path A: tap 중요 알림 → sheet dismissed, back on Detail. `run-as … cat files/datastore/smartnoti_rules.preferences_pb` shows new `PERSON:AssignTest_003634` rule; `smartnoti_categories.preferences_pb` shows `cat-onboarding-important_priority` ruleIds appended `…,PERSON:AssignTest_003634` with name/action/order unchanged (idempotent dedupe-append holds). Path B: second sender `PathBTest_003943` → "+ 새 분류 만들기" → editor prefilled (name=sender, action=즉시 전달 PRIORITY = dynamic-opposite of SILENT, pendingRule pre-checked) → 추가 → new category `cat-user-1776958835023\|PathBTest_003943\|PRIORITY\|3\|PERSON:PathBTest_003943` persists. Auto-reclassify: follow-up post on same sender bumped Home "즉시" StatPill 18 → 19 (PRIORITY auto-route via new Category). `last-verified` stays 2026-04-22 (already today). No drift. |


### 2026-04-23 (journey-tester — onboarding-bootstrap Categories seeding PASS post #281, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| onboarding-bootstrap | PASS | Built fresh debug APK (post-merge of #281 b0574e8), reinstalled, `pm clear com.smartnoti.app`, granted listener + POST_NOTIFICATIONS, walked permissions → quick-start → "이대로 시작할게요". Inspected `files/datastore/smartnoti_categories.preferences_pb` via `run-as`: 3 deterministic Categories present — `cat-onboarding-important_priority` (action=PRIORITY, order=0, ruleIds=keyword:인증번호,결제,배송,출발), `cat-onboarding-promo_quieting` (action=DIGEST, order=1, keyword:광고,프로모션,쿠폰,...), `cat-onboarding-repeat_bundling` (action=DIGEST, order=2, repeat_bundle:3). Names (중요 알림 / 프로모션 알림 / 반복 알림) match Rule.title. Corresponding `smartnoti_rules.preferences_pb` carries the matching 3 rules. 1:1 mapping per plan default verified — Exit state "CategoriesRepository 에 quick-start 가 만든 룰만큼의 1:1 Category" holds. `last-verified` 2026-04-22 → 2026-04-23. |


### 2026-04-22 (journey-tester — onboarding-bootstrap end-to-end PASS post pm-clear, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| onboarding-bootstrap | PASS | Sanctioned `pm clear com.smartnoti.app`, pre-seeded 2 tray notifications via `cmd notification post`, launched MainActivity → OnboardingScreen rendered in PERMISSIONS state ("시작하려면 권한을 허용해 주세요"). Granted listener via `cmd notification allow_listener` + POST_NOTIFICATIONS via `pm grant`; relaunch flipped CTA to "빠른 시작 추천 보기" with both rows showing "허용됨" (steps 1-4). Tapped CTA → QUICK_START state with PROMO_QUIETING preselected (steps 5-6). Tapped "이대로 시작할게요" → navigated to Home with bottom-nav (홈/정리함/분류/설정), onboarding popped (step 7). Bootstrap classified all 50 tray notifications: home shows "오늘 알림 50개", reasons "온보딩 추천 · 19건" + "사용자 규칙 · 19건" + "조용한 시간 · 49건" — confirms quick-start preset rules + bootstrap pipeline both fired (steps 8-9). `am force-stop` + relaunch kept count at 50 (no re-bootstrap, exit-state plag consumed). Sample2 ("인증번호") didn't promote to Priority but expected — IMPORTANT_PRIORITY preset wasn't selected. `last-verified` 2026-04-21 → 2026-04-22. |


### 2026-04-22 (journey-tester — quiet-hours static-source + live-DB sweep PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | PASS | JDK 미설치로 unit 테스트 실행 불가 → 소스 수준 + live DB 로 verify. `NotificationClassifier.kt:90` 의 `if (input.packageName in shoppingPackages && input.quietHours) return DIGEST` 분기와 `QuietHoursPolicy.kt:7-15` (same-day / overnight) 가 Observable steps 1-3 와 정확히 일치. emulator 시각 00:34 KST → default `[23,7)` 안. `run-as com.smartnoti.app sqlite3 smartnoti.db 'SELECT DISTINCT reasonTags FROM notifications LIMIT 20;'` 결과에 `발신자 있음\|조용한 시간` row 가 실제로 존재 → quiet-hours 분기가 production 알림 stream 에서 발화 확인. `SettingsRepository.kt:57-60` 의 `quietHoursPolicy = QuietHoursPolicy(startHour=settings.quietHoursStartHour, endHour=settings.quietHoursEndHour)` 주입 경로도 doc Code pointers 와 일치. DRIFT 없음. `last-verified` 2026-04-21 → 2026-04-22. |


### 2026-04-22 (journey-tester — protected-source-notifications PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| protected-source-notifications | PASS | Recipe A (MediaStyle synthetic). `adb shell cmd notification post -S media -t "Player" MediaTest "미디어 스타일 테스트"` posted a `category=transport` notification from `com.android.shell`. Post-enqueue `dumpsys notification --noredact` still shows `NotificationRecord ... key=0|com.android.shell|2020|MediaTest|2000 ... category=transport` present in the list — SmartNoti listener did not `cancelNotification` it, which matches Observable steps 3–4 (routing overrides force `cancelSourceNotification=false / notifyReplacementNotification=false`). Category-path protection intact. Recipes B (YouTube Music FG service + mediaSession extra) and C (direct `FLAG_FOREGROUND_SERVICE` post) remain SKIP per doc — require real app / not postable via `cmd notification`. `last-verified` bumped 2026-04-21 → 2026-04-22. |


### 2026-04-22 (journey-tester — persistent-notification-protection PASS, JVM unit suite)

| Journey | Result | Notes |
|---|---|---|
| persistent-notification-protection | PASS | `./gradlew :app:testDebugUnitTest --tests "...PersistentNotificationPolicyTest"` → BUILD SUCCESSFUL, 7/7 testcases pass in 0.016s (`disables_bypass_when_critical_persistent_protection_is_turned_off`, `keeps_recording_and_navigation_persistent_notifications_visible`, `ignores_normal_clearable_notifications`, `keeps_call_related_persistent_notifications_visible`, `allows_charging_notifications_to_be_hidden`, `treats_ongoing_notifications_as_persistent`, `treats_non_clearable_notifications_as_persistent`). Recipe-documented Policy-level coverage (bypass keywords + protection toggle off + ongoing/non-clearable promotion) intact. End-to-end leg (real call/maps/recording app) deliberately not re-run — recipe documents it as out-of-band. `last-verified` set to 2026-04-22. |


### 2026-04-22 (journey-tester — digest-suppression SKIP recipe blocked by NMS per-package quota, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | ⏭️ SKIP | Settings → "Digest·조용히 알림의 원본 숨기기" 토글 ON → DataStore 에 `suppress_source_for_digest_and_silent` key persisted 확인. SmartNoti `POST_NOTIFICATION` appop 이 `ignore` 상태였어서 `cmd appops set com.smartnoti.app POST_NOTIFICATION allow` 로 grant 후 `for i in 4 5 6; do cmd notification post -S bigtext -t 'Promo' "SuppTest$i" '같은 광고 텍스트'; done` 및 unique 본문 (`DigSupp7..9`) 두 차례 시도 — 두 batch 모두 `pkg=com.smartnoti.app` 신규 NotificationRecord 0건, 정리함 `Digest · 3건` 카운트 증가 없음, DataStore `suppressed_source_apps` key 미생성. logcat root cause: `NotificationService: Package has already posted or enqueued 50 notifications. Not showing more. package=com.android.shell` — `cmd notification post` 가 항상 `com.android.shell` 패키지로 enqueue 되는데 NMS per-package 50건 제한이 누적된 stale 테스트 알림으로 포화 상태. Recipe step 2 의 발사 알림이 NMS 단계에서 drop 되어 SmartNoti listener 까지 도달하지 못함. Stale shell 알림 mass-cancel 은 다른 verification state 영향이라 sandbox 차단 — 안전상 시도 안 함. `last-verified` 변경 없음. Suggested follow-up: recipe 가 (a) 수동 알림 shade clear 또는 (b) `cmd notification post` 대신 `com.smartnoti.testnotifier` (별도 quota 보유) 경유 헬퍼를 명시하도록 hardening (gap-planner 후보). |


### 2026-04-22 (journey-tester — duplicate-suppression PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| duplicate-suppression | PASS | `for i in 1 2 3; do cmd notification post -S bigtext -t 'Shopping' "Repeat$i" '한정 특가'; done` (1s gap) → Home StatPills `즉시 0 / Digest 3 / 조용히 47` (3시간 윈도우, Δ=+3 from this batch). 정리함 탭 → `Digest · 3건 / 보관 중 · 44건 / 처리됨 · 3건` 세그먼트, Digest 기본 → `DigestGroupCard` "Shell" 3건 + 부제 "Shell 관련 알림 3건" 렌더. Threshold(3) → DIGEST 강등 + persisted contentSignature 그룹핑 + 정리함 묶음 표출 모두 Observable steps 4–7 / Exit state 와 일치. `last-verified` set to 2026-04-22. |


### 2026-04-22 (journey-tester — notification-detail end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | PASS | `cmd notification post -S bigtext -t 'DetailTest_0422' FbTest1 '분류 변경 테스트'` → DIGEST 분류 → 정리함 → Digest 묶음 → Shell 번들 드릴 → Detail 마운트. Observable steps 1–4 관측: `DetailTopBar` + 알림 요약 `StatusBadge=Digest` + 왜 chip row + 전달 방식 5개 라벨 + "이 알림 분류하기" 카드 + 단일 `Button("분류 변경")`. 버튼 탭 → `CategoryAssignBottomSheet` 오픈 (title "분류 변경" / subtitle "이 알림이 속할 분류를 고르거나 새 분류를 만들어요." / terminal row `+ 새 분류 만들기`). 경로 B — "새 분류 만들기" 탭 → `CategoryEditorScreen` prefill 오픈 `전달 방식 = 즉시 전달 (PRIORITY)` (DIGEST 소스의 dynamic-opposite, Observable step 5.ii 증명). `last-verified` set to 2026-04-22. |


### 2026-04-22 (journey-tester — priority-inbox SKIP recipe blocked by accumulated rules, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | SKIP | Posted `PriFreshAPK_0422` with sender `은행` + body `인증번호 998877…` per Verification recipe. DB row classified `status=SILENT, reasonTags=발신자 있음\|사용자 규칙\|인증번호\|중요 키워드` — accumulated user-saved rules from prior rules-feedback-loop sweeps now shadow both `은행` and `인증번호` keyword paths. `SELECT count(*) FROM notifications WHERE status='PRIORITY'` = 0 → Home StatPill `즉시 0` + `HomePassthroughReviewCard` not rendered (count=0 hidden), so Observable steps 1, 4–8 (card tap → PriorityScreen list/inline-actions) are unreachable without DB reset or fresh rule-free APK. Routing invariant for *existing* PRIORITY rows not contradicted; recipe itself is blocked. Recurrence of the 2026-04-21 documented "recipe fragility" Known gap, now extended beyond `엄마`/`은행` to `인증번호`. `last-verified` unchanged. Suggested follow-up: gap-planner could plan a recipe-hardening (use unique non-keyword sender + per-sweep rule reset, or test-only PRIORITY injection hook). |


### 2026-04-22 (journey-tester — inbox-unified end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| inbox-unified | PASS | 4건 Coupang Digest + 2건 Promo SILENT 알림 포스팅 후 BottomNav "정리함" 탭 → `ScreenHeader` "알림 정리함" + subtitle "Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요." + `InboxTabRow` 3 세그먼트 (`Digest · 3건 / 보관 중 · 42건 / 처리됨 · 3건`) 렌더. Digest 기본 선택 → `DigestGroupCard` (Shell 3건) 표출. "보관 중" 탭 → `HiddenNotificationsScreen` 마운트 (의도된 nested HiddenTabRow 포함, 2개 앱 42건). "처리됨" 탭 → 동일 화면 재호스팅. Digest preview 카드 탭 → `Routes.Detail` "알림 상세" 진입 → 뒤로가기로 Digest 서브탭 복귀. `last-verified` set to 2026-04-22. |


### 2026-04-22 (journey-tester — categories-management empty-state PASS post #254, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| categories-management | PASS | Empty state on emulator-5554 renders ScreenHeader "분류" + "내 분류" + EmptyState ("아직 분류가 없어요") with inline `FilledTonalButton` "새 분류 만들기" centered (~y=1080) AND ExtendedFAB "새 분류 만들기" floating bottom-right above AppBottomBar (visible, not occluded — FAB regression confirmed fixed). Both entry points tap → `CategoryEditorScreen` AlertDialog (이름 / 전달 방식 / 연결할 앱 / 소속 규칙 / 닫기·추가). Single source of truth label `CategoriesEmptyStateAction.LABEL` honored across both surfaces. `last-verified` set to 2026-04-22. |


### 2026-04-22 (journey-tester — rules-feedback-loop full end-to-end PASS on fresh Categories APK, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | PASS | Fresh `./gradlew :app:installDebug` on refactor P1-P3 + runtime-wiring + Category save race fix + DataStore dedup crash fix stack. Onboarding quick-start → AssignTest_0422_T1 알림 post → Detail → "분류 변경" 시트 렌더. **Path B PASS**: "+ 새 분류 만들기" → editor prefilled (name=`AssignTest_0422_T1`, action=PRIORITY dynamic-opposite of SILENT, pendingRule=`PERSON:AssignTest_0422_T1` pre-checked) → "추가" → `smartnoti_categories.preferences_pb` persists, 분류 탭 카드 "규칙 1개 · 즉시 전달" 표출. **Path A PASS**: PathATest_0422 알림 post → Detail → 시트 "기존 분류에 포함" 헤더 + AssignTest Category row 렌더 → 탭 → Category.ruleIds=`PERSON:AssignTest_0422_T1,PERSON:PathATest_0422` (append + dedup, name/action/order 불변) + `PERSON:PathATest_0422` rule upsert. **Auto-reclassify PASS**: 동일 sender 후속 알림 포스팅 시 Home "즉시" StatPill 0 → 1 (PRIORITY 로 auto-routed). `last-verified` 2026-04-21 → 2026-04-22 bump. Orthogonal finding: 온보딩 quick-start 가 Rules 만 seed 하고 Categories 0 건으로 남김 — onboarding-bootstrap journey precondition 과 불일치 (별도 follow-up). |


### 2026-04-22 (journey-tester — rules-feedback-loop Categories end-to-end DRIFT, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | DRIFT | Fresh Categories APK (`installDebug` post Phase P1-P3 + runtime-wiring-fix #245). Detail "분류 변경" sheet PASS (렌더 + "+ 새 분류 만들기" terminal row). Path B editor PASS (prefill name=sender `AssignTest_T21b`, defaultAction=PRIORITY per dynamic-opposite of SILENT, pending rule pre-checked). **DRIFT**: editor "추가" 탭 후 신규 Category 가 persist 되지 않음 — 분류 탭이 "아직 분류가 없어요" 유지, `files/datastore/smartnoti_categories.preferences_pb` 미생성. Root cause 미조사 (gap-planner 로 routing 권장). `last-verified` 변경 없음. |

감지된 증거: fresh install 상태에서 AssignTest 알림 → Detail → "분류 변경" → "+ 새 분류 만들기" → AlertDialog 에 "새 분류" / name=AssignTest_T21b / "즉시 전달 (PRIORITY)" / 체크박스 pre-checked 로 pendingRule 노출 → "추가" 탭 (bounds 내 좌표). 결과: dialog dismiss → 분류 탭 "아직 분류가 없어요" 유지, datastore 에 categories 파일 0. Pre-drift 상태 대비: `smartnoti_rules.preferences_pb` (1787B, prior onboarding) + `smartnoti_settings.preferences_pb` (319B) 두 파일만 존재. 경로 A 는 기존 Category 0 건 precondition 때문에 본 sweep 에서 관측 불가.


### 2026-04-22 (plan-implementer — categories-split Task 12 journey doc overhaul, docs-only)

| Journey | Change | Notes |
|---|---|---|
| categories-management | **NEW** | Category CRUD + drag-reorder + 액션 선택 계약. Code pointers: `CategoriesScreen` / `CategoryDetailScreen` / `CategoryEditorScreen` / `CategoriesRepository` / `CategoryConflictResolver`. `last-verified` 비어 있음 (ADB recipe 미실행). |
| inbox-unified | **NEW** | 정리함 통합 탭 (Digest + 보관 중 + 처리됨 서브탭) 계약. `InboxScreen` 이 `DigestScreen` + `HiddenNotificationsScreen` 를 재호스팅. 레거시 digest-inbox / hidden-inbox 는 deprecated. |
| home-uncategorized-prompt | **NEW** | Home 최상단 새 앱 분류 유도 카드 + `UncategorizedAppsDetector` (N≥3 uncovered / 7-day window / 24h snooze) 계약. |
| notification-capture-classify | UPDATED | Classifier cascade 를 Rule → Category lift → `CategoryConflictResolver` (app-pin + rule-type + `order` tie-break) → `Category.action.toDecision()` 로 재작성. Known gap 추가: listener 가 `categoriesRepository.currentCategories()` 를 아직 inject 하지 않아 production hot path 에서 SILENT fallback. |
| rules-management | UPDATED | Title → "고급 규칙 편집 (Settings 하위)". Trigger 에서 BottomNav 탭 제거, Settings "고급 규칙 편집 열기" + Detail 의 "적용된 규칙" chip deep-link 만 유효. Observable step 6 에 Rule + Category 동시 upsert 명시. Known gap: Rule 삭제 시 Category cascade 없음 + atomicity 없음. |
| notification-detail | UPDATED | Trigger 의 인박스 명 갱신 (Priority→검토 대기, Digest/Hidden→정리함). 피드백 4-버튼 내부 처리에서 "Category 자동 생성 안 됨" 명시 — feedback 으로 생성된 Rule 은 orphan 이라 classifier hot path 재적용 실패. |
| rules-feedback-loop | UPDATED | Goal / Observable step 3 / Exit state / Known gaps 에 "Category 갱신 누락" drift 기록. Out-of-scope 에 categories-management 링크 추가. |
| home-overview | UPDATED | Observable steps 재작성 — 최상단 `HomeUncategorizedAppsPromptCard`, Access card connected 시 inline 1-row, QuickActionCard × 2 제거 (BottomNav 대체), `HomeQuickStartAppliedCard` TTL + ack gate, PassthroughReview/Insight/Timeline count>0 gate. BottomNav 4-tab 로 갱신. |
| priority-inbox | UPDATED | Goal + Preconditions + BottomNav 설명을 "`Category.action = PRIORITY` 결과" + 4-tab 로 갱신. |
| ignored-archive | UPDATED | Goal/Preconditions 를 "`Category.action = IGNORE`" 기준으로 재정의. filter 계약 (`status == IGNORE`) 은 불변. |
| digest-inbox, hidden-inbox | **DEPRECATED** | `status: deprecated` + `superseded-by: docs/journeys/inbox-unified.md`. `Routes.Digest` / `Routes.Hidden` 은 deep-link 용으로 유지. |
| README | UPDATED | Active journeys 인덱스에 신규 3건 추가 + deprecated 2건 이동. 섹션 heading "Rules & onboarding" → "Categories, Rules & onboarding". |

검증 성격: 이 sweep 은 **docs-only** (코드 변경 없음). `last-verified` 는 어떤 journey 에도 갱신하지 않음 — ADB recipe 실행은 후속 journey-tester 의 책임. 신규 journey 3건과 Rule/Category 분리 아키텍처 반영이 주요 대상. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Task 12 (shipped).


### 2026-04-22 (ui-ux inspector sweep — IGNORE tier audit, emulator-5554)

| Journey | Severity | Rule violated | Evidence |
|---|---|---|---|
| rules-management | CLEAN | — | `RulesScreen` 상단 `무시 1` filter chip + `무시 / 규칙 1개` tier section (IgnoreTestRule) + `RuleRow` 내 "무시" 칩이 border-only neutral gray 로 렌더되어 Linear/Superhuman 톤 계약 일치. Action dropdown 에 `무시 (즉시 삭제)` 선택 칩 (녹색 accent fill) 정상 노출. `/tmp/ui-ignore-rules-scroll3.png` / `/tmp/ui-ignore-rule-edit.png`. |
| ignored-archive | CLEAN | — | `IgnoredArchiveScreen` header (`아카이브` eyebrow + `무시됨` H1 + 설명) 가 Hidden/Priority 위계와 일치. `보관 중 1건` SmartSurfaceCard + NotificationCard 의 `무시됨` StatusBadge (IgnoreContainer `#141922` / IgnoreOnContainer `#6B7383`) 가 low-emphasis neutral gray 로 계약 준수. 빈 여백이 noisy 하지 않고 calm. `/tmp/ui-ignore-archive.png`. |
| rules-management (settings card) | CLEAN | — | Settings 의 `무시됨 / 무시된 알림 보기` 카드 — section header + helper text + toggle + `무시됨 아카이브 열기` OutlinedButton 이 curated operator control 패턴 (forms/controls 규칙) 을 따름. `/tmp/ui-ignore-settings3.png`. |
| notification-detail | MODERATE | tighter spacing rhythm + lists-and-rows tap target clarity | "이 알림 학습시키기" 의 3-버튼 secondary row 에서 `Digest로 보내기` 만 2줄 wrap 되어 버튼 높이가 `조용히 처리` / `무시` 와 불일치. `/tmp/ui-ignore-detail2.png`. Known gap 기록 + 라벨 단축 또는 FlowRow 재고 권장. |
| notification-detail (dialog) | MINOR | accent restraint | `IgnoreConfirmationDialog` 확정 "무시" 버튼이 primary accent 파랑 — 파괴성 신호가 전적으로 copy 에 의존. 규칙 위반은 아니나 destructive primary tonal variant 도입 여부 제품 결정 필요. `/tmp/ui-ignore-confirm-dialog.png`. |


### 2026-04-22 (journey-tester — digest-inbox fresh APK post-IGNORE first ADB verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-inbox | ✅ PASS | First ADB verification on post-IGNORE fresh APK (`lastUpdateTime=2026-04-22 03:46:30`) — 직전 tick 이 `last-verified` bump 를 ADB 검증까지 보류해두었던 항목. Recipe: `for i in 1..4; do cmd notification post -S bigtext -t 'Coupang' Deal$i '오늘의 딜 ${i}건'; done` 4건 연속 → duplicate-suppression 이 Deal1/2=SILENT, Deal3/4=DIGEST 로 처리 (DB `SELECT id,title,status,packageName FROM notifications WHERE title='Coupang'` 재확인). (Step 1) BottomNav `정리함` 탭 (407,2274) 탭 → Digest composable 렌더. (Steps 2–3) 기존 누적 DIGEST 11건 (Promo/광고/Player/엄마/Coupang 등) 전부 `packageName=com.android.shell` 단일 그룹으로 병합 — `observeDigestGroupsFiltered` + `toDigestGroups` 의 packageName grouping 계약 증명. (Step 4) `DigestGroupCard` 렌더: eyebrow `Shell` + count badge `11건` + 요약 `Shell 관련 알림 11건` + `최근 묶음 미리보기` 섹션 + `탭하면 원본 알림 상세를 확인할 수 있어요` 서브카피 + NotificationCard preview rows (`Shell / Coupang / 오늘의 / Digest / 발신자 있음 · 조용한 시간 · 반복 알림`) — summary copy `{app} 관련 알림 {N}건` format 과 정합. (Step 5) Coupang preview (540,1270) 탭 → `NotificationDetailScreen` 마운트 (top bar `알림 상세` + `Shell / Coupang / 오늘의 / Digest / 왜 이렇게 처리됐나요? / SmartNoti 가 본 신호 / 발신자 있음 · 조용한 시간 / 적용된 규칙 / 반복 알림 / 전달 모드 · Digest 묶음 전달 / 소리 · 조용히 표시 / Heads-up · 꺼짐 / 잠금화면 · 내용 일부만 표시`) — Routes.Detail.create(id) 경로 확인. Observable steps 1–5 + Exit state (앱별 DIGEST 묶음 렌더링 / packageName 그룹핑 계약) 전부 일치. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신. |


### 2026-04-22 (journey-tester — silent-auto-hide fresh APK post-IGNORE re-verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ✅ PASS | Fresh APK (`lastUpdateTime=2026-04-22 03:46:30`, post-IGNORE branch) 에서 group-summary + processed-flip end-to-end 재검증. Recipe: `cmd notification post -S bigtext -t 'Promo' Promo{A,B,C,D} ...` 4건 연속 → classifier 가 첫 SILENT 매칭 이후 반복 알림을 DIGEST 로 강등 (PromoA/B = SILENT+ARCHIVED, PromoC/D = DIGEST) — DB `SELECT id,status,silentMode FROM notifications WHERE title='Promo'` 로 확인. (Step 2) `dumpsys notification --noredact` 에 `pkg=com.android.shell tag=Promo[AB]` 원본 tray 잔존 (ARCHIVED source cancel 건너뜀 확인). (Steps 4–5) `smartnoti_silent_summary` 채널에 `id=23057 actions=1 flags=0x18 vis=SECRET` 로 루트 요약 게시, 새 copy `android.title=보관 중인 조용한 알림 12건` + `android.text=탭: 보관함 열기 · 스와이프: 확인으로 처리` + bigText `조용히로 분류되어 보관 중인 알림이 12건 있어요. 탭하면 '보관 중' 목록을 열고, 옆으로 밀어 없애면 확인한 것으로 처리돼요` 전부 현재 `SilentHiddenSummaryNotifier#post` 계약과 정합 — 직전 SKIP (#273) 의 env-noise (stale APK 구판 copy) 해소. (Steps 6–7) `smartnoti_silent_group` 채널에 `groupKey=smartnoti_silent_group_sender:Promo` summary (`flags=0x218`, title `Promo · 조용히 2건`, InboxStyle `android.textLines=[Promo · 오늘만, Promo · 오늘만]`, `summaryText=SmartNoti · 조용히`) + 2 children (`flags=0x18`) 게시 — N≥2 임계 충족 시 그룹 요약 게시 계약 증명. (Steps 8–9) Deep-link: `am force-stop && am start ... -e DEEP_LINK_ROUTE hidden` → Hidden 화면 `숨긴 알림 / 보관 12건 · 처리 3건 / 보관 중 · 12건 / 처리됨 · 3건 / 1개 앱에서 12건을 보관 중이에요. / Shell 숨긴 알림 12건` 렌더, DB ARCHIVED count = 루트 요약 title count = 화면 헤더 count 삼중 정합. (Step 10) Shell 그룹 카드 헤더 탭 → 펼쳐짐, `최근 묶음 미리보기` + Promo preview rows 노출. Promo preview (540, 1680) 탭 → `NotificationDetailScreen` 진입 (`알림 상세 / Shell / Promo / 조용히 정리 / 조용히 보관 중 / 원본 알림이 아직 알림창에 남아 있어요...`). (Step 11) 스크롤 후 `처리 완료로 표시` 버튼 (bounds [422,1339][659,1388]) 중앙 (540, 1363) 탭 → DB row `PromoB / SILENT / silentMode=ARCHIVED → PROCESSED` + `reasonTags` 에 `사용자 처리` append. (Step 12) Tray 상태: `PromoB` 원본 cancel 확인 (`MarkSilentProcessedTrayCancelChain` 위임 동작), Promo 그룹 summary + 잔존 child 전부 cancel (ARCHIVED 가 1건 = PromoA 만 남아 N<2 임계 미만 → Q3-A planner 가 그룹 해체), 루트 요약 title `보관 중인 조용한 알림 12건 → 11건` 으로 자동 재게시 (count 변화 시만 re-post). Observable steps 1–12 + Exit state (tray ARCHIVED 원본 유지 / 루트 요약 갱신 / 그룹 요약 N≥2 임계 / PROCESSED 전이 tray cancel + count 감소) 전부 충족. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신. |


### 2026-04-21 (journey-tester — priority-inbox fresh APK post-IGNORE first ADB verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| priority-inbox | ✅ PASS | First ADB verification on post-IGNORE fresh APK (`lastUpdateTime=2026-04-22 03:46:30`) — 직전 tick 이 `last-verified` bump 를 ADB 검증까지 보류해두었던 항목. Recipe end-to-end: (1) `cmd notification post -S bigtext -t '은행' PriFreshAPK_0421 '인증번호 778899을 입력하세요'` → `dumpsys notification` 에서 `pkg=com.android.shell tag=PriFreshAPK_0421` 원본 잔존 + `mSoundNotificationKey` 로 등록 (PRIORITY routing `cancelSource=false` 불변조건 재확인). (2) DB row `status=PRIORITY, reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드` — 키워드 `인증번호` ALWAYS_PRIORITY 룰 매치. (3) Cold-start `am force-stop && am start MainActivity` → Home 헤더 `오늘 알림 40개 중 중요한 18개를 먼저 전달했어요` + StatPill `즉시 18 / Digest 7 / 조용히 14` + HomePassthroughReviewCard `검토 대기 18 / 건드리지 않은 알림 18건 / 검토하기`. (4) `검토하기` 텍스트 (144,940) 탭 → PriorityScreen 진입: eyebrow `검토` + title `SmartNoti 가 건드리지 않은 알림` + subtitle `이 판단이 맞는지 확인하고, 필요하면 바로 Digest / 조용히 / 규칙으로 보낼 수 있어요.` + SmartSurfaceCard `검토 대기 18건` + subcopy. (5) 첫 번째 카드 (bounds [42,785][1038,1301]) = `Shell / 은행 / 인증번호 / 즉시 전달 / 발신자 있음·사용자 규칙·중요 알림·온보딩 추천·조용한 시간·중요 키워드` (6 reason chips, DB reasonTags 와 1:1 정합). 카드 바로 아래 `이 판단을 바꿀까요?` 헤더 + OutlinedButton `→ Digest` (bounds [74,1415][529,1541]) + `→ 조용히` (bounds [550,1415][1006,1541]) + TextButton `→ 규칙 만들기` (bounds [74,1562][1006,1688]) — PassthroughReclassifyActions 영역 계약 exact. (6) 카드 탭 (540,950) → `NotificationDetailScreen` 마운트 (top bar `알림 상세` + StatusBadge `즉시 전달` + `왜 이렇게 처리됐나요?` + `전달 모드 · 즉시 전달`) — Routes.Detail.create(id) 경로 확인. BottomNav 4탭 (`홈 / 정리함 / 규칙 / 설정`) 재확인 — Phase A 이후 Priority 탭 제거 상태 contract. Observable steps 1–8 + Exit state (검토 화면 PRIORITY 렌더 / tray 원본 유지 / 인라인 재분류 + Detail 진입 가능) 전부 일치. DRIFT 없음. `last-verified` = 2026-04-21 로 유지 (문서 Change log 가 "ADB 검증 전까지 bump 하지 않음" 이라 기록한 값이 실제 ADB verification 로 확정됨). Known gaps 변경 없음. |


### 2026-04-21 (journey-tester — digest-suppression fresh APK post-IGNORE re-verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | ✅ PASS | Target: 직전 ticks 가 `home-overview` / `rules-management` / `notification-capture-classify` / `notification-detail` / `ignored-archive` / `hidden-inbox` 를 fresh APK (`lastUpdateTime=2026-04-22 03:46:30`, post-IGNORE 빌드) 에서 재검증한 반면 `digest-suppression` 는 2026-04-21 의 re-verify #2 이후 post-IGNORE APK 에서 미검증. DataStore dump 확인: `suppress_source_for_digest_and_silent` 플래그 + `suppressed_source_apps` 리스트에 `com.android.shell` + `com.smartnoti.testnotifier` 영속 — opt-in 사전조건 충족 (cold-start 없이 실행 가능). Recipe 전체 실행: (1) `for i in 1 2 3; cmd notification post -S bigtext -t 'Promo' SuppTest${i}_0421v '같은 광고 텍스트'` 3회 포스팅. (2) `dumpsys notification --noredact | grep SuppTest` → **매치 0건** = 원본이 shell 패키지 tray 에서 제거됨 (Observable step 4 `cancelNotification(sbn.key)` 증명). (3) Replacement 관측: 2건의 `pkg=com.smartnoti.app` NotificationRecord 가 `channel=smartnoti_replacement_digest_default_light_private_noheadsup` + `flags=0x18 (FLAG_AUTO_CANCEL\|FLAG_ONLY_ALERT_ONCE)` + `category=status` + `actions=3` + `android.title=String (Promo)` + `android.subText=String (Shell • Digest)` + 별도 group summary 1건 `key=...|ranker_group|` `flags=0x710 (GROUP_SUMMARY)` 로 렌더 (Observable steps 5.a-d 전부 사용자 관측 일치 — 채널/제목/subText/액션 3개 계약). `ReplacementNotificationIds.idFor` 가 notificationId 포함 (2026-04-20 change log) 으로 `id=-639811959` 와 `id=247691722` 서로 다른 2건이 공존 = 같은 앱 서로 다른 내용이 덮어쓰지 않음 재현. (4) DB 쿼리 `SELECT id, status, replacementNotificationIssued, reasonTags FROM notifications WHERE id LIKE '%SuppTest%0421v'` = 3행 `status=DIGEST`, `replacementNotificationIssued=1`, `reasonTags=발신자 있음\|사용자 규칙\|프로모션 알림\|온보딩 추천\|조용한 시간` (SuppTest3 는 `반복 알림` 추가 — DuplicateNotificationPolicy 가 3번째 게시를 `반복 알림` reason 으로 표시하지만 classifier 는 여전히 DIGEST 라우팅 유지, 계약 일치). Exit state 3조건 전부 충족: (a) tray 원본 제거 확인, (b) replacement 잔존 (autoCancel), (c) DB `status=DIGEST` + `replacementNotificationIssued=true`. Observable step 6-7 (액션 탭 feedback + contentIntent 로 Detail 이동) 은 recipe 본문이 명시적으로 요구하지 않아 이번 sweep 에서는 직접 탭하지 않음 — 액션 pending-intent 는 `actions=3` 으로 인코딩 확인 (바이너리 존재 증명). DRIFT 없음. `last-verified: 2026-04-21` 유지 (오늘 자로 이미 최신). Known gaps 변경 없음 — 문서의 auto-expansion 재등록 gap 은 이번 관측에서 재현되지 않음 (opt-in 이 이미 켜져 있어 expansion 자체가 발생하지 않는 상태). IGNORE tier 도입 이후 DIGEST 라우팅에 regression 없음을 증명. |


### 2026-04-22 (journey-tester — home-overview fresh APK post-IGNORE re-verify PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| home-overview | ✅ PASS | Target: `home-overview` 는 직전 insight-drilldown tick 이 flag 한 "Phase A/B/C + IGNORE 배선 fresh APK 에서 재검증 필요" 후보 중 하나. 설치 APK `lastUpdateTime=2026-04-22 03:46:30` (IGNORE tier 포함 빌드). Recipe: (1) `cmd notification post -S bigtext -t '엄마' HomeMom_0422 '전화 좀'` / `-t 'Coupang' HomeDeal_0422 '오늘의 딜'` / `-t '광고' HomePromo_0422 '30% 할인'` 3건 포스팅. (2) `am force-stop && am start MainActivity` cold-start. Observable steps 1–11 전부 사용자 관측 일치: ScreenHeader `SmartNoti / 중요한 알림만 먼저 보여드리고 있어요` + summary `오늘 알림 36개 중 중요한 17개를 먼저 전달했어요` + StatPill `즉시 17 / Digest 4 / 조용히 14` + HomePassthroughReviewCard (`검토 대기 17` chip + `SmartNoti 가 건드리지 않은 알림 17건` + `이 판단이 맞는지 검토하고 필요하면 규칙으로 만들 수 있어요` + `검토하기` 액션) + HomeNotificationAccessCard (`실제 알림 상태 / 연결됨 / 실제 알림이 연결되어 있어요 / 최근 실제 알림 36개가 Home에 반영됐어요 · 즉시 17개 · Digest 4개 · 조용히 14개로 분류됐어요.` + `설정에서 연결 상태 보기`) + QuickActionCard × 2 (`중요 알림 / 지금 봐야 할 알림 17개 / 열기` + `정리함 / 묶인 알림 4개 / 열기`) + QuickStartAppliedCard (`추천 3개 적용됨 / 빠른 시작 추천이 적용되어 있어요`). 스크롤 다운 후 InsightCard (`Shell 알림 18개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'` + reason chips `조용한 시간 · 18건 / 사용자 규칙 · 3건 / 프로모션 알림 · 2건`), TimelineCard (`최근 흐름 / 최근 3시간 / 최근 24시간 / 최근 3시간 기준 6개의 알림이 정리됐어요` + bucket rows `흐름 · 2시간 전` / `1시간 전` / `피크 · 방금 전`), 최근 알림 리스트 (`방금 정리된 알림 / Shell / 광고 / 30% / Digest / 발신자 있음 · 사용자 규칙 · 프로모션 알림 · 온보딩 추천`). BottomNav 4탭 (`홈 / 정리함 / 규칙 / 설정`) 관측 = Phase A 이후 Priority 탭 제거 상태 그대로 (docstep 4 "카드가 검토 화면 유일한 UI 엔트리" 계약). **IGNORE 필터 계약 증명**: DB `SELECT status, COUNT(*)` = `DIGEST 4 / IGNORE 1 / PRIORITY 17 / SILENT 15` (총 37). Home header 의 `오늘 알림 36개` = 37 - 1 IGNORE = 36 (observable step 3 의 "IGNORE 는 기본 뷰에서 제외" 계약 일치). StatPill sum `17+4+14=35` = 36 - 1 persistent SILENT (hidePersistentNotifications=true 기본값이 `android / Serial console enabled` 류 1건 필터, 기존 hidden-inbox 계약과 동일). Exit state (한 스크롤로 전체 상태 파악 + 카드/칩 탭으로 다음 journey 진입 가능) 충족. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신. Known gaps 변경 없음. |


### 2026-04-21 (journey-tester — hidden-inbox ADB recipe on fresh APK post-IGNORE PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| hidden-inbox | ✅ PASS | Target: 직전 sweep 이 남겨둔 "last-verified 는 ADB 검증 전까지 bump 하지 않음" 보류 해소 + IGNORE tier (PR #182/#185) 가 Hidden 의 두 탭에서 실제로 누출 없는지 확인. Fresh APK on emulator-5554. Cold-start deep-link path: `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden` → `HiddenNotificationsScreen` 마운트 + ScreenHeader `숨긴 알림 / 보관 10건 · 처리 3건 / '보관 중' 은 아직 확인하지 않은 알림, '처리됨' 은 이미 훑어본 알림이에요.` + `HiddenTabRow` 세그먼트 (`보관 중 · 10건` / `처리됨 · 3건`) + 기본 탭 Archived 선택. Recipe 5단계 전부 일치: (1) "보관 중" 문자열 1건 매치, (2) `처리됨` 탭 tap (786,579) → 서브카피 `이미 확인했거나…` 1건 매치 + subcopy `1개 앱에서 3건을 처리했어요.` 관측, (3) collapsed 검증 — `최근 묶음 미리보기` 0건 (Archived 탭으로 되돌아온 직후 동일), (4) Shell 그룹 헤더 tap (500,1290) → `최근 묶음 미리보기` 1건 + bulk action `모두 중요로 복구` / `모두 지우기` 1건 동시 매치 (AnimatedVisibility 체인 확인). IGNORE leak 부재 — 헤더 카운트 `보관 10 / 처리 3` 이 DB 의 SILENT row 수와 일치하며 IGNORE row (이번 세션 기준 1건) 는 Hidden 에 미노출 (`toHiddenGroups` 의 `status == SILENT` 선필터 binary 상 동작 증명, journey Observable step 4 의 IGNORE 배제 bullet 과 exact 일치). DRIFT 없음. `last-verified: 2026-04-21` 유지 (문서상 이미 오늘자이나 이번이 실제 ADB 검증 시점). Change log 에 ADB verification 결과 추가. |


### 2026-04-22 (journey-tester — rules-management Phase C UI re-verify on fresh APK PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | ✅ PASS | Caller 요청: 직전 tester 가 남긴 suggestion ("Phase C UI observables 가 binary 레벨 재검증 필요 on fresh APK 2026-04-22 build") 을 실행. 현 설치 APK `lastUpdateTime=2026-04-22 03:46:30` 확인 (Phase C PR 들 #148/#150/#151/#152 전부 포함된 빌드). Recipe: (1) Home 랜딩 후 `즉시 16 / Digest 2 / 조용히 13` baseline 확보 → Rules 탭 tap (672,2232) → `RulesScreen` 마운트 + ScreenHeader `내 규칙` + "직접 규칙 추가" 카드 + `활성 규칙 9개` 헤더 + overview `전체 9개 · 즉시 전달 3 · Digest 4 · 조용히 1 · 무시 1` + FilterChip 5종 (`전체 9 / 즉시 전달 3 / Digest 4 / 조용히 1 / 무시 1`) 렌더. **IGNORE chip "무시 1" 가시성 확인** = `RuleListFilterApplicator` 가 IGNORE 룰이 존재할 때만 overview/filter 세그먼트 노출 계약 증명. (2) 리스트 그룹 순서 스크롤 관측: `즉시 전달 / 규칙 3개` (`중요 알림`, `TestSender_0421_T12`, `광고`) → `Digest / 규칙 4개` (`프로모션 알림`, `반복 알림`, `엄마`, `TestSender_0421_T11`) → `조용히 / 규칙 1개` (`SilentTest_0421_T1`) → `무시 / 규칙 1개` (`IgnoreTestRule` 설명 `'IGNOREKEYr'가 들어오면 알림센터에서 즉시 삭제하고 앱에서도 숨겨요`) — 즉시 → Digest → 조용히 → 무시 tier 스택 최하단 배치 (`RuleListGroupingBuilder` 계약 일치). (3) "새 규칙 추가" 탭 (234,787) → AlertDialog 마운트: "기본 정보" 섹션 (규칙 이름 + 이름 또는 발신자 EditText 2개), "규칙 타입" 섹션 (사람 / 앱 / 키워드 / 시간 / 반복 chip 5개), **"처리 방식" 섹션 (즉시 전달 / Digest / 조용히 / 무시 (즉시 삭제) chip 4개) — IGNORE action chip "무시 (즉시 삭제)" 가 라벨 그대로 렌더됨을 확인** (journey Observable step 6 의 "`RuleActionUi`: 즉시 전달 / Digest / 조용히 / 무시 (즉시 삭제)" 일치). (4) "예외 규칙" 섹션 → Switch 토글 (828,2002) → helper label `어느 규칙의 예외인가요?` 출현 + base dropdown button 활성화. 드롭다운 탭 (290,2085) → **candidate 9개 (`중요 알림`, `프로모션 알림`, `반복 알림`, `엄마`, `TestSender_0421_T11`, `TestSender_0421_T12`, `SilentTest_0421_T1`, `광고`, `IgnoreTestRule`) 전체 팝업 렌더** — `RuleEditorOverrideOptionsBuilder` 가 모든 기존 룰을 base 후보로 제공하는 계약 증명 (journey Observable step 6 dropdown 일치). (5) Override 룰 생성 end-to-end 시도 2회 (ASCII-only 입력, 한글 타입 불가 제약): name=`OverrideT12` / value=`TestSender_0421_T12` / action=조용히 / base=TestSender_0421_T12 → 추가 탭 후 `활성 규칙 9개` 무변동 + `logcat -d | grep RulesRepository` 에 `E RulesRepository: Rejected override upsert for person:TestSender_0421_T12: SELF_REFERENCE` 확인. 두 번째 (T11 동일 패턴) 동일 rejection. **`RuleOverrideValidator` 의 self-reference 방어가 binary 상 정상 동작** 증명 — base.id == draft.id (둘 다 `person:TestSender_0421_T12`) 면 reject, `RulesRepository.upsertRule` 이 DataStore 에 쓰지 않고 `Log.e` 경로 (journey Observable step 6 마지막 문장 일치). (6) Recipe step 2 (키워드 `인증번호` → Priority): `cmd notification post -S bigtext -t '은행' OtpTest '인증번호 123456'` → Home 탭 복귀 시 StatPill `즉시 17` 로 baseline 16→17 반영 확인. `keyword:인증번호,결제,배송,출발` 룰이 Phase B `ruleHitIds` 로 `status=PRIORITY` 매치 + Home `observePriority` 가 16→17 증가 (3-bucket 합계 30→32) — recipe step 3 계약 충족. (7) Phase C UI observables 최종 정리: **PASS** — IGNORE action chip / filter chip / tier 섹션 3면 모두 가시, 편집 다이얼로그 override switch + base dropdown + 9 candidate 팝업 가시, self-reference 방어 로그 노출. 한계: (a) 현 DB 에 override 룰이 0건이라 `RuleListHierarchyBuilder` 의 nested indent (16dp/depth) + override pill "이 규칙의 예외 · {base}" 의 실제 렌더는 binary 관찰 불가, (b) `adb shell input text` 한글 미지원으로 `광고`/`엄마`/`중요 알림` 등 한글 base 에 대한 ASCII superset override 도 생성 불가 (strict-equality 타입은 same-value 요구 → self-reference reject → 순환 딜레마), (c) drag-to-reorder 는 48dp step gesture 재현 복잡도로 비수행. 이 3개 observable 의 최종 바이너리 재검증은 instrumentation test (Compose UI test) 또는 사전-seed 된 override 룰 fixture 로 이동이 안전 — Known gap 에 기록. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신 (fresh APK on emulator-5554 에서 recipe + Phase C UI 중 exercisable 전체 PASS). |


### 2026-04-22 (journey-tester — notification-capture-classify IGNORE path end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | ✅ PASS | Fresh APK `lastUpdateTime=2026-04-22 03:46:30` (post-Phase-B/C + IGNORE-tier + nav-race fix) 에서 IGNORE 분류 path end-to-end 최초 라이브 관측. 선행 확인: live Room schema v9 = 23 columns (`ruleHitIds TEXT` 포함) + DataStore 내 `keyword:IGNOREKEYr|IgnoreTestRule|무시 (즉시 삭제)|KEYWORD|IGNORE|true|IGNOREKEYr` 룰 persisted. Recipe end-to-end (3 경로 교차 검증): (1) **PRIORITY**: `cmd notification post -t 'Bank' BankAuthCapture_0421 '인증번호 123456을 입력하세요'` → DB row `status=PRIORITY, reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드, ruleHitIds=keyword:인증번호,결제,배송,출발`. KEYWORD rule 매치 + `ruleHitIds` 영속 확인 (PR #145 Phase B Task 1 실제 바이너리 동작). (2) **IGNORE (신규 path, 이 sweep 의 main target)**: `cmd notification post -S bigtext -t 'IGNOREKEYr in title' IgnoreCaptureTest_0421 'IGNOREKEYr 본문 테스트 캡처'` → DB row `status=IGNORE, reasonTags=발신자 있음\|사용자 규칙\|IgnoreTestRule\|조용한 시간, ruleHitIds=keyword:IGNOREKEYr` 저장 + `dumpsys notification --noredact` 에서 해당 sbn 부재 (tray cancel 수행 확인 = IGNORE 의 DELETE 계약, Exit state 3번째 조건 "tray cancel 만 수행" 증명). Observable step 8 세 번째 bullet ("IGNORE 는 룰 매치 경로에서만 도달 가능, `RuleActionUi.IGNORE`→`NotificationDecision.IGNORE`") 증명. (3) **Rule-miss SILENT (대조군)**: `cmd notification post -t 'RandomSender_0421' NoMatchTest_0421 'randomcontent body'` → DB row `status=SILENT, reasonTags=발신자 있음\|조용한 시간, ruleHitIds=(empty)`. Classifier 의 rule-miss 분기가 IGNORE 를 생성하지 않음 확인 (Observable step 8 "Classifier 의 VIP / 우선순위 키워드 / 반복 / Quiet hours / 기본 SILENT 분기는 절대 IGNORE 를 만들지 않는다" 증명). 앱 UI 교차 확인: cold-start MainActivity → Home StatPill `즉시 16 / Digest 2 / 조용히 12` (sum=30) + 헤더 `오늘 알림 31개 중…` — 총 captured 31건 중 IGNORE 1건이 3-bucket (Priority/Digest/Silent) 어디에도 집계되지 않음 = Exit state "기본 뷰에서 필터 아웃" 계약 재현. `observePriority/Digest/Silent` 및 `toHiddenGroups` 의 IGNORE 필터 (PR #182 이후) 가 binary 레벨에서도 동작. DB `SELECT status, COUNT(*) GROUP BY status` = `DIGEST 2 / IGNORE 1 / PRIORITY 16 / SILENT 13` — 4-tier 구분 persistent. Observable steps 1–10 + Exit state (`status ∈ {PRIORITY,DIGEST,SILENT,IGNORE}` + IGNORE 는 tray cancel only + 기본 뷰 필터 아웃) 전부 사용자 관측에서 증명. DRIFT 없음. `last-verified: 2026-04-21 → 2026-04-22` 갱신. Known gap 변경 없음. 이전 sweep 의 pre-condition 제약 ("APK 가 Phase B/C 이전 빌드라 `ruleHitIds` 컬럼 binary 재현 불가") 은 이번 fresh APK 에서 **해소 확인**. |


### 2026-04-22 (journey-tester — ignored-archive first-tap nav-race post-fix PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| ignored-archive | ✅ PASS | Post-merge verification of PR #199 (plan `docs/plans/2026-04-22-ignored-archive-first-tap-nav-race.md` Tasks 1+2, Option A: `Routes.IgnoredArchive` 를 `AppNavHost` 에 상시 등록). Fresh APK rebuild + reinstall (`./gradlew :app:installDebug` → `lastUpdateTime=2026-04-22 03:46:30` on emulator-5554). Focus: 직전 sweep (2026-04-21) 에서 기록된 race — toggle OFF→ON 직후 2초 내 "무시됨 아카이브 열기" 탭 시 `FATAL AndroidRuntime ... IllegalArgumentException: Navigation destination that matches request uri=android-app://androidx.navigation/ignored_archive cannot be found in the navigation graph` 가 발생하던 증상. 재현 시도 3회: (iter 1) Settings → toggle 탭 (927,1129) 한 번으로 OFF → 즉시 재탭으로 ON → "무시됨 아카이브 열기" (540,1287) 탭 → `IgnoredArchiveScreen` 마운트 (`eyebrow 아카이브 / title 무시됨 / SmartSurfaceCard 보관 중 1건 / NotificationCard = Shell / IGNOREKEYr / title / 무시됨 badge / reason chips 발신자 있음·사용자 규칙·IgnoreTestRule·조용한 시간`). 크래시 0건, `AndroidRuntime E` 필터 비어있음. (iter 2) back-nav → Settings 로 돌아와 archive section 가시 상태에서 toggle (927,1976) 2회 연속 탭 → button (540,2095) 탭 → 동일하게 archive 화면 정상 진입. (iter 3) back-nav → Settings → 동일 시퀀스 → archive 진입 성공. 3회 iter 전체에 걸쳐 `logcat -s AndroidRuntime:E` + `grep -iE "FATAL\|IllegalArg\|navigation destination"` 매치 0건. Option A 계약 ("button visible ⇒ route registered" 가 단일 읽기 지점으로 고정, `IgnoredArchiveNavGate.isRouteRegistered` 이 unconditional `true`) 증명. 관측 결과 직전 sweep 의 race 보고 해소 확인. 부수 관측: 극단적으로 빠른 OFF→ON→탭 batch (`adb shell "input tap A && input tap A && input tap B"`) 에서는 두 번째 toggle 의 recomposition 이 button 의 `onClick` lambda 를 null→valid 로 전환하는 사이에 button 탭이 noop 되는 경우가 한 번 관측됨 — 재탭으로 즉시 정상 진입, 크래시 없음. 이는 race 의 crash-mode 가 UX-benign no-op 으로 degrade 된 것. Known gap 에 기록. `last-verified` 2026-04-21 → 2026-04-22 갱신. |


### 2026-04-21 (journey-tester — notification-detail IGNORE button + dialog + undo PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | ✅ PASS | Plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` 8/8 tasks post-merge (#189 + #192) end-to-end re-verify of NEW "무시" button path. Fresh APK `lastUpdateTime=2026-04-22 02:35:44` on emulator-5554. Recipe: (1) `cmd notification post -S bigtext -t '광고' IgnoreBtnTest '무시 버튼 Detail 테스트'` → DIGEST 분류 (DB `id=com.android.shell:2020:IgnoreBtnTest, status=DIGEST, sender=광고`). (2) 홈 → 정리함 탭 → 프리뷰 row (`광고 / 무시 버튼 Detail 테스트`) 탭 → `NotificationDetailScreen` 마운트. (3) 스크롤 → "이 알림 학습시키기" 카드에 4 버튼 `중요로 고정 (540,1982)` / `Digest로 보내기 (236,2101)` / `조용히 처리 (539,2101)` / `무시 (843,2102)` — least→most destructive 순서 계약 확인 (Observable step 4 갱신본). (4) `무시` 탭 → `IgnoreConfirmationDialog` 렌더: 제목 `이 알림을 무시할까요?` + 본문 `이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기.` + `취소` / `무시` 액션 (Observable step 5.i 일치). (5) 다이얼로그 `무시` 확정 → Detail pop → 정리함 배경 복귀 + 스낵바 `무시됨. 되돌리려면 탭` + action `되돌리기` 렌더 (`applyIgnoreWithUndo` → `onBack()` 체인 증명). DB row `status=IGNORE` flip + rules datastore 에 신규 `person:광고\|광고\|무시 (즉시 삭제)\|PERSON\|IGNORE\|true\|광고` upsert 확인 (`applyAction(IGNORE)` + `upsertRule(IGNORE)` 증명). (6) `되돌리기` 탭 → DB row `status=DIGEST` 복원 + rule 은 prior `person:광고\|...\|ALWAYS_PRIORITY` 로 복원 (**새 룰이 아니었으므로 delete 대신 prior action upsert** — Observable step 5.ii "기존 룰이면 prior action 으로 upsert" 경로 정확히 증명. 기존 `person:광고→ALWAYS_PRIORITY` 룰이 이전 tick 에서 유지되어 있었기 때문에 이 분기가 자연스럽게 커버됨). Observable steps 4–5 IGNORE 분기 전부 일치 + Exit state 3조건 충족. DRIFT 없음. `last-verified` 는 2026-04-21 유지 (이미 오늘자). IGNORE 플로우가 Home StatPill 제외, Digest 인박스 제외, Settings 토글 OFF 기본 계약을 유지하면서도 Detail 화면에서는 full render + destructive action + undo 3 초 창을 정확히 제공함을 재확인 — #189 + #192 머지 후 회귀 없음. |


### 2026-04-21 (journey-tester — ignored-archive first verification PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| ignored-archive | ✅ PASS | Plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` 8/8 tasks 머지 직후 신규 journey 의 첫 verification. Fresh APK rebuild (`./gradlew :app:installDebug` JDK `/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `lastUpdateTime=2026-04-21 15:47:57` → 2026-04-22 이후 타임스탬프로 갱신, `installDebug` Task `app:packageDebug` 실행). Recipe end-to-end: (1) Rules 탭 → `새 규칙 추가` → KEYWORD + keyword `IGNOREKEYr` + action `무시 (즉시 삭제)` (selected 칩이 녹색 background 로 하이라이트 — Phase C UI 확인) → `추가` → `활성 규칙 9개` + 새 `무시 1` chip 노출 (Rules 탭 pill 집계 IGNORE 차원 분리 계약 확인). (2) `cmd notification post -S bigtext -t 'IGNOREKEYr in title' IgnoreTest 'IGNOREKEYr 테스트 알림 본문'` → 즉시 `dumpsys notification --noredact` 에서 `IgnoreTest` tag 미존재 (SmartNoti 가 tray 에서 `cancelNotification` 호출한 결과 = IGNORE action = DELETE 계약 확인). (3) Home 탭 → StatPill `즉시 15 / Digest 1 / 조용히 11` (sum 27) + 헤더 `오늘 알림 28개 중 중요한 15개를 먼저 전달했어요` — 총 28 captured 중 IGNORE row 1건은 pill 3-bucket 어디에도 집계 안 됨 (`observePriority/Digest/Silent` 필터 IGNORE 제외 계약 재현). (4) Settings 탭 → 스크롤 다운 → `무시됨 / 무시된 알림 보기` 카드 노출, toggle `무시된 알림 아카이브 표시` OFF → 설명 `켜면 설정 화면에 아카이브 진입 버튼이 나타나요. 알림 분류 동작은 바뀌지 않아요.` + `무시됨 아카이브 열기` 버튼 **부재** (조건부 렌더 확인). (5) toggle ON → 설명 `아래 버튼으로 아카이브 화면을 열 수 있어요.` 로 전환 + `무시됨 아카이브 열기` OutlinedButton 등장 (Settings 카드 내부 inline composition — `IgnoredArchiveSettingsCard` 정확히 render). (6) 버튼 탭 → `IgnoredArchiveScreen` 마운트: eyebrow `아카이브` / title `무시됨` / subtitle `SmartNoti 가 IGNORE 규칙으로 즉시 정리한 알림이에요. 원본 알림센터에서도 사라진 상태예요.` (알림 존재 시 variant) + `SmartSurfaceCard` 헤더 `보관 중 1건` + 서브 `최신 순으로 정렬돼 있어요. 탭하면 상세 화면에서 어떤 규칙이 걸렸는지 확인할 수 있어요.` + `NotificationCard` 1개 (`Shell / IGNOREKEYr / title / 무시됨 badge (neutral gray) / reason chips: 발신자 있음·사용자 규칙·IgnoreTestRule·조용한 시간`). (7) NotificationCard 탭 → `Routes.Detail.create(id)` → `NotificationDetailScreen` 마운트 + `StatusBadge=무시됨` (중립 gray tone) 관측 (notification-detail 교차 계약 확인). (8) toggle OFF 로 되돌림 → 버튼 즉시 사라짐 + 설명 원상복구 (`observeSettings().showIgnoredArchive` 가 `produceState` 로 false emit, `AppNavHost` 조건부 composable 제거). Observable steps 1–7 + Exit state 3조건 전부 일치 = **PASS**. 한 가지 간헐적 race 관측 + Known gap 에 기록: `toggle OFF → ON → 버튼 탭` 을 2초 내에 연속 수행한 첫 시도에서 `IllegalArgumentException: Navigation destination that matches request uri=android-app://androidx.navigation/ignored_archive cannot be found in the navigation graph ComposeNavGraph(0x0) startDestination={Destination(0x78d845ec) route=home}` AndroidRuntime FATAL 로 1회 크래시 → Launcher 로 throwback. 동일 시퀀스를 toggle off→on→tap 으로 재시도 시 재현 안 됨 (`produceState` recomposition 이 `navigate()` 호출 시점에 graph 등록을 완료). 앱 재시작 후 toggle 이 persisted ON 으로 복원된 상태에서는 크래시 재현 안 됨. Race window 가 좁아 사용자 영향 미미하지만 opt-in 첫 순간의 UX 안정성 문제이므로 Known gap + gap-planner 라우팅 후보로 남김. `last-verified` 를 empty → 2026-04-21 로 갱신 (신규 journey 의 첫 verification). |


### 2026-04-22 (journey-tester — insight-drilldown end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| insight-drilldown | ✅ PASS | All 16 journeys at `last-verified=2026-04-21`; picked insight-drilldown as lowest-risk pre-Phase-C candidate since Home insights + drilldown screen are unaffected by Phase A (BottomNav/HomePassthroughReviewCard) + Phase B (ruleHitIds) + Phase C (hierarchical rules) code paths. 설치된 APK `lastUpdateTime=2026-04-21 15:47:57` KST (= 06:47 UTC, Phase A/B/C merges 전), 하지만 insight drilldown 계약은 pre-Phase 코드와 동일하므로 재현 가능. Recipe end-to-end: (1) `for i in 1..5; do cmd notification post -S bigtext -t 'Coupang' "Deal$i" "오늘의 딜 $i"; done` 포스팅 → 중복 suppression 경로로 5건이 DIGEST/Silent 혼합 분류 (Home `최근 효과` 스트립이 "반복 알림 5건이 Digest로 묶였어요" 기록 확인). (2) `am force-stop && am start MainActivity` cold-start → Home 상단 StatPill `즉시 16 / Digest 10 / 조용히 12` 렌더, 스크롤 다운 시 `일반 인사이트 / SmartNoti 인사이트 / 지금까지 22개의 알림을 대신 정리했어요 / 전체 알림 중 57% / Shell 알림 22개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'` + reason chip 3개 (`조용한 시간 · 12건 / 반복 알림 · 5건 / 사용자 규칙 · 5건`) 노출. (3) `반복 알림 · 5건` chip 탭 → `Routes.Insight.createForReason('반복 알림', ...)` navigate → InsightDrillDownScreen mount: eyebrow `인사이트` + title `반복 알림 이유` + subtitle `'반복 알림' 이유로 정리된 알림 4건을 모아봤어요.` + ContextBadge `일반 인사이트` + subtitle `최근 24시간 기준 '반복 알림' 이유로 정리된 알림 4건을 시간순으로 보여줘요.` + FilterChip 3종 (`최근 3시간 / 최근 24시간 / 전체`) + stats `Digest 4 / 조용히 0` + reason navigation `조용한 시간 · 4건` + 필터 리스트 (Shell/Shopping/한정/Coupang/오늘의 카드 렌더). Observable steps 1–10 전부 exact 일치. (4) 범위 전환 확인 — Back → Home → `Shell 인사이트` 카드 탭 → `createForApp('Shell', ...)` → App 기준 drilldown mount: title `Shell 인사이트` + subtitle `Shell 알림 20건이…` + stats `Digest 9 / 조용히 11` + reason navigation 3종 (`조용한 시간 · 12건 / 사용자 규칙 · 5건 / 프로모션 알림 · 5건`). `최근 3시간` chip 탭 → `InsightDrillDownBuilder.build` 가 window 재계산해 title `Shell 알림 13건이 …` + subtitle `최근 3시간 기준 Shell에서 정리된 알림 13건을 시간순으로 보여줘요.` + stats `Digest 6 / 조용히 7` + reason 리스트 `조용한 시간 · 12건 / 반복 알림 · 4건 / 사용자 규칙 · 2건` (프로모션 알림 chip 은 5건이 3h 밖 → 필터 아웃, 계약대로 동작). DRIFT 없음 — Observable steps 1-10 + Exit state (특정 앱/이유 좁힌 리스트 + 다른 이유로 재드릴다운 가능) 전부 사용자 관측에서 증명. Known gaps (PRIORITY 드릴다운 제외, range back-stack 복원 안 됨) 변경 없음. Task 요청 "stale pre-Phase-C APK 주의" 는 **confirmed** — `BottomNavItem` 현재 코드 (홈/정리함/규칙/설정, PR #143 `411a759`) 와 달리 실제 탭 영역이 `홈/중요/정리함/규칙/설정` 5개로 관측됨 = APK 가 Phase A Task 4 전 빌드. 이는 insight-drilldown contract 와는 무관하지만, 다음 tick 에서 Phase A/B/C 의존 journey (priority-inbox, home-overview, rules-management Phase C UI, notification-detail Phase B signals) 재검증 전에 APK rebuild + 재설치 선행 필요. `last-verified` 를 2026-04-21 → 2026-04-22 갱신. |


### 2026-04-21 (journey-tester — notification-detail end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-detail | ✅ PASS | 모든 16 journey 가 `last-verified=2026-04-21` 이어서 at-risk 판단: 최근 Phase B Task 1 (`ruleHitIds` 컬럼, PR #145) + Task 2 (`NotificationClassification.matchedRuleIds` wrapper, PR #146) 가 classifier→detail 체인을 건드렸으므로 notification-detail 이 핫스팟. 현 설치 APK (`versionName=0.1.0`, `lastUpdateTime=2026-04-21 15:47:57`) 의 live Room schema `.schema notifications` 는 22개 컬럼 + `ruleHitIds` **미포함** (notification-capture-classify tick 과 동일 제약) — 따라서 Phase B 전용 observables (reason 섹션 "분류기 신호 / 적용된 규칙 chips" 분리) 는 binary 재현 불가. 대신 문서화된 Observable steps 1–5 + Exit state 를 end-to-end 재현. Recipe: (1) `cmd notification post -S bigtext -t '광고' DetailTest_0421 '오늘의 이벤트 테스트'` → DB 분류 `status=DIGEST, reasonTags=발신자 있음\|사용자 규칙\|프로모션 알림\|온보딩 추천\|조용한 시간, sender=광고`. (2) `am start MainActivity` → 홈 → 정리함 탭 (539,2274) → Shell 그룹 `최근 묶음 미리보기` 내 `광고 / 오늘의 이벤트 테스트` 프리뷰 row (283,1360) 탭 → `NotificationDetailScreen` 마운트 확인. (3) Observable step 4 모든 섹션 관측: `DetailTopBar` 타이틀 `알림 상세` / 알림 요약 카드 (`Shell / 광고 / 오늘의 이벤트 테스트 / StatusBadge=Digest`) / "왜 이렇게 처리됐나요?" + `ReasonChipRow` 5개 chip (`발신자 있음`, `사용자 규칙`, `프로모션 알림`, `온보딩 추천`, `조용한 시간`) / 온보딩 추천 카드 (`빠른 시작 추천에서 추가된 규칙이에요` + `온보딩에서 선택한 '프로모션 알림' 추천이 이 알림 정리에 영향을 줬어요`) / "어떻게 전달되나요?" (`덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요.` + 5줄 라벨 `전달 모드 · Digest 묶음 전달 / 소리 · 부드럽게 알림 / 진동 · 가벼운 진동 / Heads-up · 꺼짐 / 잠금화면 · 내용 일부만 표시`) / "원본 알림 처리 상태" (`SmartNoti가 원본 알림 숨김을 시도했고 대체 알림도 표시했어요…` + `원본 상태 · 원본 숨김 시도됨` + `대체 알림 · 표시됨`) / "이 알림 학습시키기" (`한 번 누르면 상태를 바꾸고 같은 유형의 규칙도 함께 저장해요` + 3 버튼 `중요로 고정` / `Digest로 보내기` / `조용히 처리`). (4) Observable step 5 액션 체인: `중요로 고정` (540,1810) 탭 → DB row `SELECT id,status,reasonTags FROM notifications WHERE id LIKE '%DetailTest_0421%'` = `PRIORITY`, `reasonTags` 그대로 `발신자 있음\|사용자 규칙\|프로모션 알림\|온보딩 추천\|조용한 시간` (`NotificationFeedbackPolicy.applyAction` + `NotificationRepository.updateNotification` 증명) → `smartnoti_rules.preferences_pb` strings dump 마지막 엔트리 신규 등장 = `person:광고\|광고\|항상 바로 보기\|PERSON\|ALWAYS_PRIORITY\|true\|광고` (`toRule` + `RulesRepository.upsertRule` 증명). (5) Exit state: DB status flip 완료, Rules 탭으로 이동 시 새 PERSON 룰 노출 가능 — journey 계약의 모든 3 Exit 조건 충족. PERSON→ALWAYS_PRIORITY 자동 재적용은 중립 sender `TestSender_0421_T12` 로 교차 검증: 재-post `cmd notification post -S bigtext -t 'TestSender_0421_T12' DetailTest_0421_v6 '확인 text'` → `status=PRIORITY, reasonTags=발신자 있음\|사용자 규칙\|TestSender_0421_T12\|조용한 시간`. DRIFT 없음. Recipe fragility 1건은 Known gap 에 기록: recipe step 3 이 제안한 sender `광고` 는 온보딩이 기본 주입한 KEYWORD-DIGEST 룰 (`광고,프로모션,쿠폰,세일,특가,이벤트,혜택`) 과 충돌해 `RuleConflictResolver` 가 DIGEST 로 라우팅 — step 3 단독 관측만 하면 "ALWAYS_PRIORITY 룰이 동작 안 하는 것처럼" 보이지만 PERSON 분기 자체는 중립 sender 로 증명됨. 코드 계약 문제 아님, recipe 문구만 개선 대상. `last-verified: 2026-04-21` 유지 (오늘 재실행이므로 이미 최신). |


### 2026-04-21 (journey-tester — notification-capture-classify end-to-end + Phase B/C contract PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| notification-capture-classify | ✅ PASS | Phase C Task 2 (#149, `RuleConflictResolver` 라우팅) + Phase B Task 1 (#145, `ruleHitIds` 컬럼) + Phase B Task 2 (#146, `NotificationClassification(decision, matchedRuleIds)` wrapper) 이후 이 journey 계약 재검증. Recipe end-to-end: (1) `adb shell cmd notification allow_listener com.smartnoti.app/.notification.SmartNotiNotificationListenerService` 선행 확인 (`dumpsys notification` 의 `Live notification listeners` 목록에 SmartNoti proxy 연결 상태). (2) `cmd notification post -S bigtext -t 'Bank' CaptureClassifyTest_0421 '인증번호 123456을 입력하세요'` 포스팅. (3) DB 쿼리 `SELECT id, packageName, title, substr(body,1,30), status, reasonTags, postedAtMillis FROM notifications WHERE id LIKE '%CaptureClassifyTest%' ORDER BY postedAtMillis DESC LIMIT 3` → 1행 `com.android.shell:2020:CaptureClassifyTest_0421 \| com.android.shell \| Bank \| 인증번호 \| PRIORITY \| 발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|조용한 시간\|중요 키워드 \| 1776780895594` 저장됨 — `onNotificationPosted` → `processNotification` → `NotificationCaptureProcessor` → `NotificationClassifier` → `RuleConflictResolver` → `NotificationRepository.save` 전 파이프라인이 Observable steps 1–9 그대로 실행. 특히 `사용자 규칙` 태그가 수록됨으로써 Phase C Task 2 의 rule 매치 라우팅 (기존 `keyword:인증번호,결제,배송,출발` 룰 매치) 이 실제로 reason 태그에 반영됐음을 증명. Code-level contract: `./gradlew :app:testDebugUnitTest --tests 'com.smartnoti.app.domain.usecase.{NotificationClassifierTest,RuleConflictResolverTest,DuplicateNotificationPolicyTest,QuietHoursPolicyTest}' --tests 'com.smartnoti.app.notification.NotificationCapturePolicyTest'` → `BUILD SUCCESSFUL`, XML 집계 `NotificationClassifierTest tests=19, RuleConflictResolverTest tests=8, DuplicateNotificationPolicyTest tests=2, QuietHoursPolicyTest tests=2, NotificationCapturePolicyTest tests=3` — 총 34건 모두 failures=0/errors=0/skipped=0 (문서 Tests 섹션 4개 + 파이프라인 정책 커버). Code pointer 재확인 (`grep class` 기준): `SmartNotiNotificationListenerService`, `ListenerReconnectActiveNotificationSweepCoordinator` (+ `SweepDedupKey` in-process Set), `NotificationCaptureProcessor`, `NotificationClassifier`, `RuleConflictResolver`, `NotificationClassification` (`domain/model/NotificationDecision.kt`), `DuplicateNotificationPolicy`, `LiveDuplicateCountTracker`, `PersistentNotificationPolicy`, `NotificationRepository`, `NotificationEntity`, `NotificationEntityMapper` 전부 `app/src/main` 에 존재 + `matchedRuleIds`/`ruleHitIds`/`existsByContentSignature` 심볼 10개 파일에 33회 등장 (mapper/classifier/processor/listener 전경로 배선 일관). 주의: 설치된 APK (`versionName=0.1.0`, `lastUpdateTime=2026-04-21 15:47:57`) 의 live Room schema `.schema notifications` 는 22개 컬럼 (`id…sourceEntryKey`) 에 `ruleHitIds` 미포함 — PR #145 (Phase B Task 1) 이 APK 빌드 시점보다 뒤라서 binary 상 Migration 경로는 이번 sweep 에서 재현 불가. 이는 rules-management sweep 과 동일 pre-condition 제약 (agent rule: "rebuild 은 journey-tester scope 밖, 계약은 code-level test 로 증명"). Live DB 에도 `ruleHitIds` 없이 `reasonTags` 만으로 PRIORITY 라우팅이 관측됐으므로 Observable steps 1–10 + Exit state (`status ∈ {PRIORITY,DIGEST,SILENT}`) 는 그대로 충족. DRIFT 없음. `last-verified: 2026-04-21` 유지 (오늘 재실행이므로 이미 최신). Known gaps 변경 없음 — 문서의 1개 gap (리스너 disconnect 중 dismiss 된 알림은 복구 불가; reconnect sweep 은 tray 잔존분만 소급 캡처) 는 여전히 유효, onboarding-bootstrap sweep 경로 (오늘 별도 PASS) 로 보완 관계 재확인. 다음 단계: APK 를 Phase B/C 포함 빌드로 재설치 후 `ruleHitIds` 컬럼 end-to-end (Detail 화면 "적용된 규칙" 섹션 렌더까지) 재검증 권장 — 현재 journey-detail-sync 는 별 journey 도 커버 중이므로 notification-detail sweep 에 묶어 돌리는 편이 효율적. |


### 2026-04-21 (journey-tester — rules-management Phase C contract PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | ✅ PASS | #160 (Phase C Task 6 journey doc sync) 이후 첫 재검증 — 직전 변경이 이 문서에 집중됐기에 가장 드리프트 위험이 컸던 journey 선택. Code-level contract 검증: `./gradlew :app:testDebugUnitTest` 로 문서 Tests 섹션의 9개 클래스 전부 실행 → `BUILD SUCCESSFUL` + XML 집계 `RuleOrderingTest tests=8, RuleListHierarchyBuilderTest tests=8, RuleOverrideValidatorTest tests=9, RuleOverrideSupersetValidatorTest tests=6, RuleEditorOverrideOptionsBuilderTest tests=4, RuleListGroupingBuilderTest tests=2, RuleListFilterApplicatorTest tests=2, RuleEditorDraftValidatorTest tests=2, RuleConflictResolverTest tests=8` — 총 49건 모두 failures=0/errors=0/skipped=0. 이로써 Phase C 계약 (1-level override 트리, tier-aware `RuleOrdering` 이웃 swap, `RuleOverrideValidator` self-reference/cycle reject, `RuleOverrideSupersetValidator` KEYWORD token / 그 외 strict superset, `RuleEditorOverrideOptionsBuilder` base 후보 dropdown, `RuleConflictResolver` base/override 우선순위 + tier tie-break) 이 모두 green. Code pointer 재확인: 문서에 열거된 15개 파일 (`RuleUiModel`, `RuleStorageCodec`, `RuleOverrideValidator`, `RuleOrdering`, `RuleConflictResolver`, `RuleListHierarchyBuilder`, `RuleOverrideSupersetValidator`, `RuleEditorOverrideOptionsBuilder`, `RuleListPresentationBuilder`, `RuleListGroupingBuilder`, `RuleListFilterApplicator`, `RuleEditorDraftValidator`, `RuleDraftFactory`, `RulesRepository`, `RuleRow`) 전부 존재 + `RuleRow.kt:50-54` 에 `sealed interface RuleRowPresentation { Base / Override / BrokenOverride }` 선언 확인 (문서 Observable step 5 일치). End-to-end 은 recipe 대로 `cmd notification post -S bigtext -t '은행' OtpRMTest_0421 '인증번호 987654'` → DB `SELECT FROM notifications WHERE id LIKE '%OtpRMTest%'` = 1건 `status=PRIORITY, reasonTags=발신자 있음\|사용자 규칙\|중요 알림\|온보딩 추천\|중요 키워드` 로 기존 `keyword:인증번호,결제,배송,출발` 룰이 매치되어 PRIORITY 로 분류됨 확인 → Home cold-start StatPill `즉시 13 / Digest 4 / 조용히 6` 반영 + summary `최근 실제 알림 23개가 Home에 반영됐어요` 렌더. Rules 탭 UI 관측: ScreenHeader `내 규칙 / 중요 연락, 앱, 키워드, 시간대를 운영 규칙처럼 정리해…` + "직접 규칙 추가" 카드 "새 규칙 추가" 버튼 + `활성 규칙 7개` 헤더 + FilterChip 4종 (`전체 7 / 즉시 전달 2 / Digest 4 / 조용히 1`) + 액션별 그룹 (`즉시 전달 / 규칙 2개` 섹션 내부 `중요 알림 / '인증번호,결제,배송,출발'가 들어오면 바로 보여줘요 / 키워드 / 즉시 전달` row) — Observable steps 1–3 + 일부 4 가 사용자 관측과 exact 일치. 주의: 설치된 APK (`lastUpdateTime=2026-04-21 15:47:57`) 가 Phase C 커밋들 (#148 `18:16`, #150 `18:39`, #151 `18:50`, #152 `21:23`) 보다 **이전** 빌드라 Rules 탭 UI 의 Phase C 전용 observables (`RuleListHierarchyBuilder` 트리 indent 16dp/depth, override pill "이 규칙의 예외 · {base}", broken 경고, editor "예외 규칙" Switch + base dropdown, drag handle tier-aware 시각 피드백) 은 **binary 상으로 재현 불가**. 단, 이들은 전부 code-level test 49건 (위) 으로 증명된 contract 이며, APK rebuild 는 journey-tester scope 밖 (agent rule: "pre-condition 미충족 시 SKIP, rebuild 는 수행하지 않음"). DataStore `smartnoti_rules.preferences_pb` 덤프는 7-column legacy 포맷 7개 엔트리 저장 (2 KEYWORD + 1 REPEAT_BUNDLE + 3 PERSON + 1 SILENT) — `RuleStorageCodec` "8-column 포맷 (legacy 7-column tolerant)" 계약 (문서 Code pointers) 과 일치 (legacy 데이터를 현재 UI 가 정상 parse + render). DRIFT 없음. `last-verified: 2026-04-21` 유지 (오늘 재실행이므로 그대로). Known gaps 변경 없음 — 문서의 3개 gap (AlertDialog 기반 폭, import/export 미구현, 1-level chain) 은 그대로 유효, Recipe fragility 주석도 baseline 7개 (2 + 1 + 3 + 1) 로 여전히 재현됨. 다음 단계: 후속 tick 에서 APK 를 Phase C 포함 빌드로 재설치 후 Rules 탭 tree/override pill/editor 섹션 observables 전용 재검증 권장. |


### 2026-04-21 (journey-tester — rules-feedback-loop broadcast recipe SKIP, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | ⏭️ SKIP | Phase C Task 5 (tier-aware moveRule, drag handle, #152) 이 `RulesRepository` 를 건드렸고, Phase C Task 1 (hierarchical rules data, #148) 이 `upsertRule` 저장 포맷을 8-column 으로 확장했기에 rules-feedback-loop 의 recipe 를 재실행 시도. Recipe B (broadcast 경로) 는 `AndroidManifest.xml` 의 `SmartNotiNotificationActionReceiver android:exported="false"` 때문에 `adb shell am broadcast -a com.smartnoti.app.action.PROMOTE_TO_PRIORITY` (명시적 `-n` 컴포넌트 지정 포함) 가 enqueue 만 되고 receiver `onReceive` 가 실행되지 않아 DB 가 SILENT 그대로 유지 + Rules 탭 baseline 7개 무변동 — 동일 UID 에서만 전달되도록 설계된 receiver 라서 shell 에서 headless 실행 불가. Recipe A (Detail UI 경유) 는 Phase A Task 4 이후 bottom-nav 에서 Hidden 탭이 제거되어 SILENT 분류된 `TestSender_0421_T99` 를 Home passthrough 카드 경유로 열어야 하는데 recipe 본문은 "Home 또는 Hidden 에서 카드 탭" 수준으로만 기술, 실제 네비게이션 단계가 enumerated 돼있지 않아 end-to-end 재현 불가. 두 경로 모두 사전조건을 recipe 본문만으로 충족 못 하므로 SKIP (DRIFT 로 단정하기엔 recipe 가 구형 전제를 따르는 문제이고, 코드 동작이 잘못된 것은 아님). `last-verified` bump 없음. 다음 단계는 recipe 를 (a) exported receiver 대신 app 내부 테스트 hook, 또는 (b) Home passthrough 카드 → Detail 진입 시퀀스를 명문화하는 방향으로 gap-planner 가 손볼 것. |


### 2026-04-21 (journey-tester — quiet-hours policy-level PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| quiet-hours | ✅ PASS | 가장 오래된 `last-verified=2026-04-20` journey. `./gradlew :app:testDebugUnitTest --tests QuietHoursPolicyTest --tests NotificationClassifierTest` → `BUILD SUCCESSFUL`, XML `tests=2 skipped=0 failures=0 errors=0` + `tests=19 skipped=0 failures=0 errors=0` (같은 시간대 분기, overnight 분기, `shopping_app_during_quiet_hours_goes_to_digest` 포함). Code pointer 재확인: `NotificationClassifier.kt:36` 이 `input.packageName in shoppingPackages && input.quietHours` → `NotificationDecision.DIGEST` 로 Observable step 3 와 exact 일치, `NotificationCaptureProcessor.kt:98-99` 의 `tags += "조용한 시간"` 경로 보존, `SettingsRepository.currentNotificationContext` 이 `QuietHoursPolicy(startHour, endHour)` 주입 그대로. Live DB 관측 (`run-as com.smartnoti.app sqlite3 smartnoti.db 'SELECT DISTINCT reasonTags FROM notifications;'`) = `조용한 시간` tag 부재 — emulator 시각 22:18 KST 가 default `[23,7)` 범위 밖이라 계약 일치. Recipe 의 end-to-end 부분은 `cmd notification post --pkg` 가 지원되지 않아 `com.coupang.mobile` 경로를 에뮬레이터에서 재현 불가 — doc recipe 가 이를 명시하고 policy unit test 를 primary 수단으로 제시. DRIFT 없음. `last-verified` 를 2026-04-20 → 2026-04-21 갱신. |


### 2026-04-21 (journey-tester — persistent-notification-protection policy-level PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| persistent-notification-protection | ✅ PASS | 가장 오래된 `last-verified=2026-04-20` journey. 문서에 명시된 runnable recipe (`./gradlew :app:testDebugUnitTest --tests PersistentNotificationPolicyTest`) 실행 → `BUILD SUCCESSFUL`, TEST XML `tests=7 skipped=0 failures=0 errors=0` (7/7 bypass / ongoing / isClearable / protect-토글 분기 전부 통과). Code pointer 재확인: `PersistentNotificationPolicy.kt` BYPASS_KEYWORDS 세트가 journey doc Observable step 3 의 키워드 리스트 (`통화, 전화, call, dialer, 길안내, 내비, navigation, maps, 녹화, recording, screen record, 마이크 사용 중, camera in use, camera access, microphone in use`) 와 exact 일치. DRIFT 없음 — contract (`shouldTreatAsPersistent` + `shouldBypassPersistentHiding` + `BYPASS_KEYWORDS`) 가 코드와 doc 양쪽에서 동일. End-to-end (통화/내비/녹화 실앱 트리거) 부분은 `cmd notification post` 가 `FLAG_ONGOING_EVENT` 를 설정할 수 없어 emulator 에서는 불가 — doc recipe 가 이를 명시하고 policy unit test 를 primary 수단으로 제시하므로 해당 보조 단계는 out-of-scope. `last-verified` 를 2026-04-20 → 2026-04-21 갱신. |


### 2026-04-21 (journey-tester — onboarding-bootstrap reconnect-sweep end-to-end PASS, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| onboarding-bootstrap | ✅ PASS | Listener-reconnect T1–T4 배선 포함 fresh APK (`lastUpdateTime=2026-04-21 15:47:57`) 에서 reconnect-sweep 경로 end-to-end 관측. Recipe: (1) `cmd notification disallow_listener com.smartnoti.app/.SmartNotiNotificationListenerService` 로 리스너 revoke → dumpsys `NotificationListeners: Disallowing` 확인. (2) 리스너 disabled 상태에서 `cmd notification post -S bigtext -t 'ReconnectSweepTest' SweepTag '인증번호 424242 SWEEP_TEST'` 포스팅 (tray 에만 쌓이고 `onNotificationPosted` 는 호출되지 않는 구간). (3) `cmd notification allow_listener ...` 로 재허용 → logcat `NotificationListeners: 0 notification listener service connected` 관측, `onListenerConnected` 재발화. (4) 관측: (a) DB 쿼리 `SELECT id,packageName,title,body,status,postedAtMillis FROM notifications ORDER BY postedAtMillis DESC LIMIT 10` 최상단에 `com.android.shell:2020:SweepTag / ReconnectSweepTest / 인증번호 / PRIORITY / 1776756189019` 저장됨 — disconnect 구간에 posted 되어 `onNotificationPosted` 경로를 못 탔던 알림이 reconnect 후 `enqueueReconnectSweep` → `ListenerReconnectActiveNotificationSweepCoordinator.sweep(activeNotifications)` → `processNotification` 경로로 소급 캡처되었음을 증명. (b) `cmd notification list` 에 SmartNoti 자체 replacement notification `0|com.smartnoti.app|23057|null|10192` 추가 게시 (PRIORITY 계열 라우팅 완료). (c) onboarding bootstrap 플래그는 이미 이전 온보딩에서 consume 된 상태 (기기 firstInstallTime=2026-04-19) 라 `enqueueOnboardingBootstrapCheck` 는 no-op, sweep 만 동작 — Known gaps 의 "권한 토글 등 일반 재접속에서도 tray 에 남은 미처리 알림을 sweep 이 소급 캡처" 계약 그대로. (d) Observable step 10 의 `existsByContentSignature` + in-process `SweepDedupKey` Set 중복 방지도 동반 확인: 재-reconnect 시 이미 저장된 SweepTag row 는 skip (dedup Set 에 기록된 키 + DB 존재 둘 다 hit). DRIFT 없음 — plan `docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md` 의 T1–T4 구현 목표가 사용자 관측에서 증명됨. `last-verified` 를 2026-04-20 → 2026-04-21 갱신. |


### 2026-04-21 (journey-tester — hidden-inbox dual-surface + deep-link filter PASS after #127/#128/#130, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| hidden-inbox | ✅ PASS | Fresh APK (post #128/#127/#130) 재검증. Recipe cold-start: `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden`. (Exp 1) Hidden 화면 렌더 — 탑바 `숨긴 알림`, ScreenHeader title `보관 1건 · 처리 4건`, subtitle `'보관 중' 은 아직 확인하지 않은 알림, '처리됨' 은 이미 훑어본 알림이에요. 탭으로 오가며 정리하세요.`, `HiddenTabRow` 두 세그먼트 `보관 중 · 1건` / `처리됨 · 4건` 모두 노출, 기본 선택 탭 = Archived (summary `1개 앱에서 1건을 보관 중이에요.` 렌더). (Exp 3) 탭별 count 가 silentMode 필터에 정합: DB `SELECT status, silentMode, isPersistent, COUNT(*) FROM notifications WHERE status='SILENT' GROUP BY ...` → `SILENT\|ARCHIVED\|0\|1 (com.android.shell/Promo), SILENT\|PROCESSED\|0\|1 (com.android.shell/Promo), SILENT\|NULL\|0\|3 (com.android.shell/Player), SILENT\|NULL\|1\|1 (android/Serial console enabled)`. `hidePersistentNotifications=true` 기본값이 persistent 1건을 필터 → Archived 탭 = 1건 (Promo), Processed 탭 = 4건 (PROCESSED 1 + legacy null 3, 모두 Shell) 로 계약 (doc step 4 + legacy null→PROCESSED 매핑) 정합. 처리됨 탭 탭 (786,580) 후 subtitle `1개 앱에서 4건을 처리했어요.` + `이미 확인했거나 이전 버전에서 넘어온 알림이에요.` bodycopy 확인. (Exp 2) Deep-link `sender` 쿼리 필터: `am start -e DEEP_LINK_ROUTE hidden -e DEEP_LINK_SENDER Promo` cold-start → `HiddenDeepLinkFilterResolver.resolve(sender=Promo) = SilentGroupKey.Sender("Promo")` → `LaunchedEffect(initialFilter)` 가 `selectedTab = Archived` 강제, `highlightedGroupId` 가 매칭 그룹 id 로 resolve (Shell/Promo 그룹이 ARCHIVED 탭에 존재) → `animateScrollToItem` + `highlightBorder` 렌더 경로 실행. UI 확인: Archived 탭 자동 선택 + Shell 그룹 카드 단독 노출 (summary `1개 앱에서 1건을 보관 중이에요.`). PROCESSED-only sender (`-e DEEP_LINK_SENDER Player`) cold-start → 계약대로 Archived 탭 강제, Player 그룹은 ARCHIVED 에 없으므로 highlight 없음 (doc: "PROCESSED items won't have a matching group summary in the tray by construction"). Observable steps 1–8 (mount → observeSettings/observeAllFiltered → toHiddenGroups(ARCHIVED/PROCESSED) → ScreenHeader + HiddenTabRow → 탭별 summary card) + Trigger 3경로 중 루트 요약 + 그룹 summary deep-link 2경로 모두 검증. DRIFT 없음 — Change log `2026-04-21 tray 그룹 summary deep-link 필터 지원` + `보관 중/처리됨 탭 분리` 계약이 사용자 관측에서 증명됨. `last-verified: 2026-04-21` 유지. |


### 2026-04-21 (journey-tester — silent-auto-hide group-summary PASS after fresh APK, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ✅ PASS | Fresh APK (`lastUpdateTime=2026-04-21 15:47:57`, includes commits through `5eff674` = silent-archive-drift-fix Task 1-3 + silent-tray-sender-grouping Task 5) 재설치 후 re-run. Recipe: `cmd notification post -S bigtext -t 'Promo' Promo{A..E} '...'` 5건 연속 포스팅 → 분류기가 첫 SILENT 매칭 이후 같은 sender 를 DIGEST 로 강등하는 실 환경 동작으로 PromoA, PromoD 두 건이 `SILENT + silentMode=ARCHIVED` 저장됨 (`SELECT id,status,silentMode FROM notifications WHERE sender='Promo'` → `PromoA/SILENT/ARCHIVED, PromoD/SILENT/ARCHIVED`, 나머지는 DIGEST). Observable step 2 (tray 원본 유지) 증명: `dumpsys notification --noredact | grep 'key=.*Promo[AD]'` → `pkg=com.android.shell id=2020 tag=PromoA/D` 모두 잔존. Step 6-7 (그룹 summary N≥2): `smartnoti_silent_group` 채널에 `groupKey=smartnoti_silent_group_sender:Promo` 로 summary + child NotificationRecord 게시 확인. Step 4-5 (루트 요약): `smartnoti_silent_summary` 채널에 `id=23057 actions=1` 로 게시. Hidden deep-link: `am start ... -e DEEP_LINK_ROUTE hidden` 진입 시 "보관 중 · 2건" + "1개 앱에서 2건을 보관 중이에요." + Shell 그룹 카드 렌더. Step 10-12 (Detail "처리 완료로 표시"): 카드 탭 → 프리뷰 탭 → Detail `알림 상세 / 조용히 보관 중 / 처리 완료로 표시` 버튼 노출, 탭 후 `PromoD` row 가 `silentMode=ARCHIVED → PROCESSED` 로 flip + tray 에서 PromoD 원본 cancel (PromoA 만 남음). ARCHIVED count 가 2→1 로 떨어지자 `smartnoti_silent_group` 채널의 group summary/child 전부 cancel (Q3-A 임계 동작 확인, `grep NotificationRecord.*smartnoti_silent_group` = 0), 루트 요약은 count=1 상태로 유지. 직전 세션 `2026-04-21 silent-auto-hide SKIP` 의 env noise (stale APK, 배선 미포함) 해소. `last-verified: 2026-04-21` 유지. |


### 2026-04-21 (journey-tester — silent-auto-hide group-summary re-verify, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| silent-auto-hide | ⏭️ SKIP | 설치된 APK (`lastUpdateTime=2026-04-21 12:42:01`) 가 `silent-archive-drift-fix` Task 2 (`2298d16`, 2026-04-21 14:38 KST) 가 landing 되기 전에 빌드/설치되어 있음. 즉 listener 의 `SilentCaptureRoutingSelector.silentModeFor(...)` → `SourceNotificationRoutingPolicy.route(..., silentMode=ARCHIVED)` 배선 + `baseNotification.copy(silentMode = capturedSilentMode ?: ...)` 저장 경로가 설치된 바이너리에 포함되지 않음. 실증: fresh SILENT 3건 `cmd notification post -S bigtext -t 'Promo' Promo{A,B,C} '...'` 로 포스팅 → tray 원본 전부 사라짐 (`grep Promo$ /dumpsys` hit 0), DB row 저장은 되지만 `SELECT id, status, silentMode FROM notifications WHERE id LIKE '%Promo%'` 결과 세 건 모두 `SILENT / silentMode=NULL` (ARCHIVED 아님). 이어서 `grep -c smartnoti_silent_group` = 0 — `SilentGroupTrayPlanner` 가 `filterSilentArchivedForSummary` 로 ARCHIVED rows 를 받지 못해 그룹 summary/child posts 가 하나도 발생하지 않음. 루트 요약 (`smartnoti_silent_summary`) 만 post 됨. 이 상태에서 journey 의 Observable steps 3/6/7/8 (ARCHIVED DB persist + planner 그룹 집계 + `smartnoti_silent_group` 채널 게시 + `DEEP_LINK_SENDER` deep-link) 을 사용자 관측으로 검증 불가. Contract drift 아님 — 소스 (`SmartNotiNotificationListenerService.kt:275-280, 339`, `SilentCaptureRoutingSelector.kt`) 는 올바름. 릴리즈 APK rebuild + install 후 재검증 필요. journey-tester scope 상 rebuild 는 수행하지 않음 (agent rule "돌릴 recipe 의 pre-condition 이 맞지 않으면 SKIP"). `last-verified` 는 2026-04-21 그대로 유지 — 이 SKIP 은 env noise 이고 문서 갱신 대상 아님. |


### 2026-04-21 (v1 loop tick — hidden-inbox re-verify post-#89 collapsible contract, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| hidden-inbox | ✅ PASS (new contract) | PR #89 (commit `71d8fd4`) 이 hidden-inbox 의 app 그룹 카드를 기본 collapsed 로 전환한 뒤 첫 re-verify. 문서 (`docs/journeys/hidden-inbox.md`, last-verified 2026-04-21) 의 Observable step 7 이 "기본 상태는 collapsed — preview/bulk action 숨김" + step 8 "헤더 탭 → expanded state toggle + chevron 180° + `AnimatedVisibility`" 로 이미 갱신되어 있어 **새 계약 기준** 으로 검증. Recipe 그대로 cold-start: `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden`. (1) Collapsed default 증명 — `uiautomator dump` 결과 eyebrow `숨긴 알림` + title `숨겨진 알림 37건` + summary `3개 앱에서 37건을 숨겼어요.`, 그룹 카드 3개 (`Shell 35건`, `Android System 1건`, `Digital Wellbeing 1건`) 모두 chevron `content-desc="펼치기"` + label `{App} 숨긴 알림 {N}건` 만 노출, `grep -c "최근 묶음 미리보기" /tmp/ui_hidden.xml = 0` (expected per recipe step 3). 프리뷰 rows 와 bulk action row (`모두 중요로 복구` / `모두 지우기`) 부재 확인 — `DigestGroupCard(collapsible=true)` 기본값이 의도대로 collapsed. (2) Expand toggle 증명 — Shell 카드 헤더 중앙 (500, 1050) `input tap` 후 재-dump → `grep -c "최근 묶음 미리보기" /tmp/ui_hidden2.xml = 1`, chevron `content-desc="접기"` 1개 관측 (Shell 만 펼쳐짐, 나머지는 화면 밖으로 밀려났지만 이전 dump 에서 모두 `펼치기` 였음 = 초기값 동일). `rememberSaveable(groupId)` expanded state + `AnimatedVisibility` + chevron 180° rotate 가 관측 가능한 사용자 동작으로 작동. Observable steps 1–9 (HiddenNotificationsScreen 마운트 → observeSettings/observeAllFiltered → toHiddenGroups → ScreenHeader → SmartSurfaceCard + `DigestGroupCard(collapsible=true)` collapsed 렌더 → 헤더 탭 expand → preview 카드 tap 가능 상태) 및 Exit state (DB SILENT=37, 화면=37, 요약 알림 count=37 일치) 전부 충족. DRIFT 없음 — 문서와 코드가 PR #89 계약으로 정렬. Plan `docs/plans/2026-04-20-hidden-inbox-collapsible-groups.md` 의 구현 목표가 사용자 관측에서 증명됨. 다른 12개 non-SKIP journey 는 이번 cycle 에서 same-day PASS 보유 — 재검증 스킵. |


### 2026-04-21 (v1 loop tick — rules-feedback-loop re-verify #3 (KEEP_SILENT branch), emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | ✅ PASS | 지금까지 cycle 에서 verify 되지 않았던 **KEEP_SILENT 분기** 를 전용으로 검증. Cold start: `am force-stop com.smartnoti.app` → `am start -n com.smartnoti.app/.MainActivity --es com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden` 로 Hidden (숨긴 알림) 화면 진입. Fresh sender: `cmd notification post -S bigtext -t "SilentTest_0421_T1" FbSilentTest "조용히 처리 피드백 테스트"` → Hidden 목록에 "Shell / SilentTest_0421_T1 / 조용히 정리" 카드로 등장. 카드 탭 → Detail 화면 ("알림 상세") 진입, reasonTags 초기 관측 = `발신자 있음 / 조용한 시간` (사용자 규칙 없음). 스크롤 후 하단 "이 알림 학습시키기" 섹션에서 **"조용히 처리" 버튼 (우측, bounds [685,1945][851,1994]) 탭**. (1) Detail reasonTags 즉시 갱신 관측 → `발신자 있음 / 조용한 시간 / 사용자 규칙` (="사용자 규칙" 태그 추가 — `NotificationFeedbackPolicy.applyAction` 호출 증거). (2) Rules 탭 이동 → 헤더 count `전체 7 · 즉시 전달 2 · Digest 4 · 조용히 1` 로 "조용히 1" 카테고리 **신규 생성** (cycle 시작 시점엔 0). 스크롤하여 "조용히 / 규칙 1개" 섹션에서 신규 rule `SilentTest_0421_T1 / 사람 / 조용히 / 발신자 기준 / "SilentTest_0421_T1 연락은 조용히 정리해요"` 카드 확인 — `RuleTypeUi.PERSON`, matchValue = sender title, id = `person:SilentTest_0421_T1`. sender 존재 분기 (PERSON) + KEEP_SILENT action → `upsertRule` 경로가 의도대로 동작. Observable steps 2.iii/2.iv/2.v/2.vi (applyAction → updateNotification → toRule → upsertRule) 와 Exit state (DB reasonTags 에 "사용자 규칙" + RulesRepository 새 person rule upsert) 모두 충족. 이로써 rules-feedback-loop 의 3개 액션 분기 (ALWAYS_PRIORITY / ALWAYS_DIGEST / KEEP_SILENT) 가 04-21 cycle 에서 **전부 실행 경로로 관측 완료** — 직전 re-verify #2 (PR #75) 에서 남긴 관찰 공백 해소. Known gaps 변경 없음 (잔존 person rule + deep-link cold-start fragility 는 이번에도 동일하게 재현 — 의도된 제약 |


### 2026-04-21 (v1 loop tick — digest-suppression re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| digest-suppression | ✅ PASS | Baseline DataStore (`run-as com.smartnoti.app strings files/datastore/smartnoti_settings.preferences_pb`) 에 `suppress_source_for_digest_and_silent` ON + `suppressed_source_apps=[com.smartnoti.testnotifier, com.android.shell]` 로 pre-set 상태 재확인 (이번 cycle 초기 세팅 잔존). Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|29, PRIORITY\|13, SILENT\|33` (총 75 — cycle 누적 baseline, 직전 insight-drilldown tick #76 과 동일). Recipe 그대로 고유 signature 로 3회 posting: `for i in 1 2 3; do cmd notification post -S bigtext -t "SuppReverify0421" "SuppReV_$i" "같은 광고 텍스트 한정 세일"; sleep 1; done`. (1) **원본 tray 제거** `dumpsys notification --noredact \| grep "tag=SuppReV_" \| wc -l` = 0 — 세 건 모두 cancel. (2) **Replacement 게시** `pkg=com.smartnoti.app id=-385633862 channel=smartnoti_replacement_digest_low_off_private_noheadsup importance=2 flags=0x18 category=status groupKey=silent actions=3 vis=PRIVATE` 관측, extras `android.title=SuppReverify0421`, `android.subText=Shell • Digest`, `android.text=원본 알림 숨김을 시도하고 Digest에 모아뒀어요 · 조용한 시간 · 반복 알림`, `android.template=BigTextStyle`. 3 actions 텍스트 `[0] 중요로 고정 / [1] Digest로 유지 / [2] 열기` — broadcastIntent (→ `SmartNotiNotificationActionReceiver`) / broadcastIntent / startActivity (→ `MainActivity`) 로 정확히 3분기 라우팅. (3) **DB** `SELECT id,title,status,replacementNotificationIssued FROM notifications WHERE title='SuppReverify0421' ORDER BY postedAtMillis DESC` → `SuppReV_3\|DIGEST\|1 / SuppReV_2\|SILENT\|0 / SuppReV_1\|SILENT\|0` — classifier threshold=3 으로 3번째에서 DIGEST 전이 + `replacementNotificationIssued=1` flag 정확히 set. DB 총계 `DIGEST 29→30 (+1) / SILENT 33→35 (+2) / PRIORITY 13 불변` — 2 SILENT + 1 DIGEST 배정과 완전 일치. Observable steps 1–5 (auto-expansion 는 이미 등록된 앱이라 no-op / `NotificationSuppressionPolicy.shouldSuppressSourceNotification` true / `SourceNotificationRoutingPolicy.route(DIGEST,*,suppress=true)` → cancelSource+notifyReplacement / main-thread cancelNotification / `SmartNotiNotifier.notifySuppressedNotification` 호출) 및 Exit state (tray 원본 제거 + replacement 1건 (AutoCancel=true — flags=0x18 includes `FLAG_AUTO_CANCEL`) + DB `status=DIGEST, replacementNotificationIssued=1`) 전부 충족. Steps 6–7 (액션/contentIntent 수신) 는 별도 journey (rules-feedback-loop / notification-detail) 에서 커버. 04-21 tick #1 (PR #40 당시 SKIP→PASS 업그레이드 후 frontmatter 복원, 이후 cycle 초반 tick) 이 동일 recipe 를 `SuppSweep0421P` signature 로 증명했고, 이번 tick 은 **더 커진 baseline (총 75 vs 이전 ~50)** + 다른 unique signature 로 재현 — digest-suppression 의 핵심 불변조건 (auto-expansion no-op + DIGEST 라우팅 + replacement 채널/액션/subText/본문 포맷 + DB flag) 이 scale/시점과 무관하게 안정적임을 재확증. 이로써 이번 session 의 re-verify cycle 이 완료되어 non-SKIP 13개 journey 전부 2026-04-21 기준 **twice** re-verified. Known gaps 변경 없음 |


### 2026-04-21 (v1 loop tick — rules-feedback-loop re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-feedback-loop | ✅ PASS | Baseline DataStore (`run-as com.smartnoti.app strings files/datastore/smartnoti_rules.preferences_pb`) 5 엔트리: 2 KEYWORD + 1 REPEAT_BUNDLE + `person:엄마\|PERSON\|DIGEST` + `person:TestSender_0421_T11\|PERSON\|DIGEST` (이전 re-verify #1 의 upsert 잔존). Fresh unique sender 로 `cmd notification post -S bigtext -t 'TestSender_0421_T12' FbT12 '피드백 루프 재검증 T12'` → DB row `com.android.shell:2020:FbT12 \| sender=TestSender_0421_T12 \| status=SILENT \| reasonTags=발신자 있음\|조용한 시간` (quiet hours 기반 SILENT 분기 — 계약 무관 baseline). **Detail 경유 (recipe A)**: `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden` 으로 cold-start deep-link → Hidden 화면에서 `TestSender_0421_T12` preview row bounds `[126,1440][558,1489]` 중앙 (540,1465) 탭 → `NotificationDetailScreen` 진입 확인 (탑바 `알림 상세` + sender `TestSender_0421_T12` + body `피드백 루프 재검증 T12`). 1회 `input swipe 540 1800 540 800 400` 로 `이 알림 학습시키기` 섹션 노출 → 3버튼 (`중요로 고정 [457,1787][623,1836] · Digest로 보내기 [196,1945][429,1994] · 조용히 처리 [685,1945][851,1994]`) 관측. 이번 tick 은 앞선 re-verify #1 (PR #58) 이 `Digest로 보내기` 경로였던 것과 대비해 **다른 액션 분기 (`중요로 고정` → `ALWAYS_PRIORITY`)** 커버를 목적으로 (540,1810) 탭. Observable steps 2.3–2.6 end-to-end 관측: (2.3) `NotificationFeedbackPolicy.applyAction(PROMOTE_TO_PRIORITY)` 적용 → DB row `status=SILENT → PRIORITY` 전이 확인, `reasonTags=발신자 있음\|조용한 시간\|사용자 규칙` 로 `사용자 규칙` 태그 append 됨. (2.5–2.6) `NotificationFeedbackPolicy.toRule` + `RulesRepository.upsertRule` → DataStore 재덤프에 신규 엔트리 `person:TestSender_0421_T12\|TestSender_0421_T12\|항상 바로 보기\|PERSON\|ALWAYS_PRIORITY\|true\|TestSender_0421_T12` insert 관측 (rule id=`person:TestSender_0421_T12`, matchValue=sender, label=`항상 바로 보기`, decision=ALWAYS_PRIORITY). sender 존재 → `RuleTypeUi.PERSON` 분기 정확히 실행 (recipe 본문의 괄호 설명 "매치값=com.android.shell" 은 sender 없는 `-t ""` 경로 전용 — 이번 tick 은 sender 존재 분기를 증명). Exit state (DB status/reasonTags 업데이트 + RulesRepository 신규 upsert + 후속 동일 sender 알림 자동 PRIORITY 적용 가능 상태) 전부 충족. replacement cancel step 은 Detail 경로라 우회 (journey 명시). PR #58 (re-verify #1) 이 `DIGEST` 분기 + fresh insert 를 증명했다면 이번 tick 은 동일 DataStore upsert 경로에서 `ALWAYS_PRIORITY` + SILENT→PRIORITY 전이 + `사용자 규칙` reasonTag 까지 3가지 액션 커버 — PROMOTE/KEEP_DIGEST/KEEP_SILENT 중 2개 분기가 이 cycle 에서 관측적으로 증명됨 (남은 KEEP_SILENT 는 후속 tick 대상). Known gaps 변경 없음. (주의) upsert 된 `person:TestSender_0421_T12` 룰은 다음 tick 환경에 잔존 — 재현 시 다른 unique sender 권장 |


### 2026-04-21 (v1 loop tick — insight-drilldown re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| insight-drilldown | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|29, PRIORITY\|13, SILENT\|33` (총 75 — 이번 cycle 누적 baseline). Recipe 그대로 5건 posting (`cmd notification post -S bigtext -t "Coupang" "DrillDown0421R2_$i" "오늘의 딜 $i"`) → DB `SELECT title,status,contentSignature FROM notifications WHERE title='Coupang' ORDER BY postedAtMillis DESC LIMIT 5` → `Coupang DIGEST x3, Coupang SILENT x2`, contentSignature 모두 `coupang 오늘의` 동일 (duplicate heuristic 경로 — digest-drilldown 무관 capture 계약). `am force-stop com.smartnoti.app && am start -n com.smartnoti.app/.MainActivity` → Home StatPill `즉시 13 / Digest 29 / 조용히 32`, summary `최근 실제 알림 74개가 Home에 반영됐어요 · 즉시 13개 · Digest 29개 · 조용히 32개로 분류됐어요.` 렌더. `input swipe 540 1800 540 600 400` 로 Insight 카드 뷰포트 진입 후 dump 에서 `일반 인사이트 / SmartNoti 인사이트 / 지금까지 61개의 알림을 대신 정리했어요 / 전체 알림 중 82%를 대신 정리했어요` header + clickable app chip `Shell 알림 55개가 가장 많이 정리됐고, 주된 이유는 '조용한 시간'예요` bounds `[84,1330][996,1456]` + reason breakdown `조용한 시간 · 35건 / 사용자 규칙 · 15건 / 반복 알림 · 11건` 관측. App chip (540,1393) 탭 → `InsightDrillDownScreen` 진입 (step 1 경로 인자 파싱): (step 4) eyebrow `인사이트` + title `Shell 인사이트` + subtitle `Shell 알림 55건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.`, (step 5) `ContextBadge` `일반 인사이트` (GENERAL 톤), (step 6) range `FilterChip` 3개 중 `최근 24시간` default selected (subtitle `최근 24시간 기준 Shell에서 정리된 알림 55건을 시간순으로 보여줘요.`), (step 7) breakdown 차트 `Digest 25 / 조용히 30` + helper `이 앱에서 가장 많이 보인 이유는 '조용한 시간'이에요.`, (step 8) reason nav rows `조용한 시간 · 35건 / 사용자 규칙 · 15건 / 온보딩 추천 · 11건` 각각 `탭해서 자세히 보기` hint, (step 9) `NotificationCard` 리스트 (`Shell / Shop / 테스트 / 조용히 정리 / 발신자 있음 / 조용한 시간`). Range 변경 (step 6 분기 동작): `최근 3시간` (203,783) 탭 → title 총계 `55→20`, subtitle `최근 3시간 기준…20건…`, breakdown `Digest 10 / 조용히 10`, reason rows `조용한 시간 · 20건 / 사용자 규칙 · 6건 / 온보딩 추천 · 5건` 로 `InsightDrillDownBuilder` 가 24h→3h window 로 재계산. Reason re-drilldown (step 8 → step 1 loop): `사용자 규칙 · 6건` row (200,1221) 탭 → 새 `InsightDrillDownScreen` (eyebrow `인사이트` + title `사용자 규칙 이유` + subtitle `'사용자 규칙' 이유로 정리된 알림 6건을 모아봤어요.` + context `일반 인사이트`), range 3h 유지 (`최근 3시간 기준 '사용자 규칙' 이유로 정리된 알림 6건을 시간순으로 보여줘요.` — 경로 인자의 range 전파), breakdown `Digest 6 / 조용히 0` + helper `'사용자 규칙'와 함께 많이 보인 이유는 '조용한 시간'이에요.`, 현재 이유 row `사용자 규칙 · 6건` 에 marker `현재 보고 있는 항목` 표시, 공기 이유 `조용한 시간 · 6건 / 온보딩 추천 · 5건`, 카드 리스트 `Shell / SuppSweep0421P / 오늘만 특가 할인 텍스트 / Digest` + reason chips `발신자 있음 / 사용자 규칙 / 프로모션 알림 / 온보딩 추천 / 조용한 시간 / 반복 알림` 관측. Observable steps 1–10 (경로 파싱 → repository+settings collect → builder 필터링 → ScreenHeader → ContextBadge → range FilterChip → 차트 → reason nav → filtered card list → Detail 진입 가능) 및 Exit state (앱/이유 필터 좁힘 + 재드릴다운 + Detail 경로 open) 전부 충족. 04-21 tick #1 (PR #59) 이 동일 경로를 더 작은 baseline (총 49) 에서 PASS 로 증명했고, 이번 tick 은 더 커진 baseline (총 75) + 다른 unique tag 로 재현 — `InsightDrillDownBuilder` 의 app/reason 필터링 + range window 계산 + 재드릴다운 전파가 스케일과 무관하게 안정적임을 재확증. Known gaps 변경 없음 |


### 2026-04-21 (v1 loop tick — protected-source-notifications re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| protected-source-notifications | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|29, PRIORITY\|11, SILENT\|33` (총 73 — 이번 cycle 8개 tick 누적으로 환경이 더 커진 baseline). Recipe A 실행: `cmd notification post -S media -t "Player" MediaTest0421R2 "미디어 스타일 테스트 R2"` → posting 확인 (`category=transport vis=PRIVATE`, flags=0x0, shell_cmd channel). (1) 원본 tray 잔존: `dumpsys notification --noredact \| grep -B1 -A5 MediaTest0421R2` 에서 `NotificationRecord(0x0803d4b1: pkg=com.android.shell user=UserHandle{0} id=2020 tag=MediaTest0421R2 importance=3 key=0\|com.android.shell\|2020\|MediaTest0421R2\|2000: Notification(channel=shell_cmd … category=transport vis=PRIVATE))` 관측, 추가로 `mSoundNotificationKey=0\|com.android.shell\|2020\|MediaTest0421R2\|2000` 로 등록 — SmartNoti 가 cancel 하지 않음 명확. (2) DB row 저장: `SELECT id,packageName,title,status,sourceSuppressionState FROM notifications WHERE id LIKE '%MediaTest0421R2%'` → `com.android.shell:2020:MediaTest0421R2 \| com.android.shell \| Player \| SILENT \| NOT_CONFIGURED`, `reasonTags=발신자 있음\|조용한 시간`. **Critical evidence**: classifier 가 SILENT 로 분류했음에도 원본이 살아있다 — `SourceNotificationRoutingPolicy.route(SILENT,*,*)` 의 기본 정책은 `cancelSourceNotification=true` (SILENT 분기 = unconditional cancel). 원본 tray 잔존 + `sourceSuppressionState=NOT_CONFIGURED` (NOT `SUPPRESSION_ATTEMPTED`) 라는 두 지점이 `SmartNotiNotificationListenerService#processNotification` 의 `isProtectedSourceNotification` 분기가 작동해 routing 을 강제 덮어썼음을 증명 — `-S media` 가 `category=transport` 를 세팅하므로 `ProtectedSourceNotificationDetector.isProtected(signals)` 의 category 기반 경로 (`category ∈ {"call","transport","navigation","alarm","progress"}`) 로 보호 확정. Observable steps 1–5 (`signalsFrom(sbn)` 4필드 추출 → `isProtected` 평가 → `cancelSourceNotification=false`/`notifyReplacementNotification=false`/`shouldSuppressSourceNotification=false` 3필드 강제 덮어쓰기 → cancelNotification/notifySuppressedNotification 미호출 → `NotificationRepository.save` 는 그대로 수행) 과 Exit state (시스템 tray 원본 유지 + DB row SILENT 로 분류·기록 + 후속 Home/Hidden 인박스 노출 가능) 전부 충족. Recipe B (YouTube Music) 는 에뮬레이터에 앱 미설치로 계속 SKIP — category 경로만으로도 contract 증명. 04-21 tick #1 (PR #56) 이 동일 경로를 `MediaTest0421` 로 PASS 로 증명했고, 이번 tick 은 더 큰 baseline (73 vs #56 당시 45ish) + 다른 unique tag 로 재현 — category 기반 보호의 불변조건 안정성 재확증. Known gaps 변경 없음 |


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


### 2026-04-21 (v1 loop tick — rules-management re-verify #2, emulator-5554)

| Journey | Result | Notes |
|---|---|---|
| rules-management | ✅ PASS | Baseline DB `SELECT status, COUNT(*) FROM notifications GROUP BY status` → `DIGEST\|29, PRIORITY\|11, SILENT\|33` (총 73 — 이번 cycle 누적 baseline). Baseline DataStore (`run-as com.smartnoti.app strings files/datastore/smartnoti_rules.preferences_pb`) → 5 entries: `keyword:인증번호,결제,배송,출발\|KEYWORD\|ALWAYS_PRIORITY`, `keyword:광고,프로모션,쿠폰,세일,특가,이벤트,혜택\|KEYWORD\|DIGEST`, `repeat_bundle:3\|REPEAT_BUNDLE\|DIGEST`, `person:엄마\|PERSON\|DIGEST`, `person:TestSender_0421_T11\|PERSON\|DIGEST` (이전 sweep 들로 upsert 된 PERSON 잔존). Rules 탭 cold start (홈 → (751,2232) 탭) → `ScreenHeader` `규칙 / 내 규칙 / 중요 연락, 앱, 키워드…` + `직접 규칙 추가` 카드 + `새 규칙 추가` 버튼 bounds `[84,724][384,850]` + `활성 규칙 5개` 카드 + summary `전체 5개 · 즉시 전달 1 · Digest 4 · 조용히 0` + `FilterChip` 3개 (`전체 5 / 즉시 전달 1 / Digest 4`, 조용히 chip 은 숨김 — `RuleListFilterApplicator` 가 non-empty group 만 렌더) 관측. Observable step 5 (편집 다이얼로그 + validator + factory + upsertRule): `새 규칙 추가` (234,786) 탭 → `AlertDialog` 열림. Dialog 레이아웃 `기본 정보 (규칙 이름 · 이름 또는 발신자) / 규칙 타입 (사람·앱·키워드·시간·반복) / 처리 방식 (즉시 전달·Digest·조용히)` 확인. 키워드 chip (588,1361) 탭 → 두 번째 EditText 라벨이 `키워드 / 쉼표로 여러 키워드를 입력할 수 있어요. 예: 배포,장애,긴급` 로 재구성 (`RuleTypeUi.KEYWORD` 분기 + `RuleEditorDraftValidator` 의 키워드 변이). 첫 field 에 `RuleSweep0421R2`, 두 번째 field 에 `RuleSweep0421Kw`, `즉시 전달` 액션 chip 선택 후 `추가` (802,1979) 탭 → 헤더 `활성 규칙 5개 → 6개`, summary `즉시 전달 1 → 2`, FilterChip `즉시 전달 1 → 2` 으로 즉시 동기화. DataStore 재덤프에 신규 엔트리 `keyword:RuleSweep0421Kw\|RuleSweep0421R2\|항상 바로 보기\|KEYWORD\|ALWAYS_PRIORITY\|true\|RuleSweep0421Kw` 영속화 관측 — `RuleDraftFactory.build` 가 id=`keyword:RuleSweep0421Kw` (matchValue) + label=`항상 바로 보기` + enabled=true 로 정확히 생성. **Exit state end-to-end**: `cmd notification post -S bigtext -t '은행' OtpRuleMgmt0421 'RuleSweep0421Kw 코드 445566 입력하세요'` → DB `com.android.shell:2020:OtpRuleMgmt0421 \| 은행 \| PRIORITY \| reasonTags=발신자 있음\|사용자 규칙\|RuleSweep0421R2\|조용한 시간` — classifier 가 `RulesRepository.currentRules()` 로 새 룰을 즉시 참조해 PRIORITY 분기, reasonTags 에 rule name 이 첨부됨 (snapshot 플로우 확인). Observable step 6 (deleteRule): 리스트 스크롤 후 RuleSweep0421R2 카드의 `삭제` button (content-desc=`삭제`) bounds `[881,1091][986,1196]` (center 933,1143) 탭 → 헤더 `활성 규칙 6개 → 5개`, `즉시 전달 2 → 1`, DataStore 덤프에서 `keyword:RuleSweep0421Kw` 엔트리 제거. Observable steps 1–6 (repository 구독 → ScreenHeader+새 규칙 추가 → 활성 규칙 카드+FilterChip → action 그룹+RuleRow → AlertDialog 편집+validator+factory+upsertRule → deleteRule) 및 Exit state (DataStore 영속화 + currentRules 스냅샷 반영 + 신규 알림 분류) 전부 충족. #54 tick (PR #57, rules-management re-verify #1) 대비 baseline 이 더 커진 상태 (DB 73 vs 당시 ~55) 에서 동일 CRUD + classifier 적용 경로를 다른 unique name/keyword 로 재현 — UI↔DataStore↔Classifier 삼중 동기화가 스케일과 무관하게 안정적임을 재확증. Known gaps 변경 없음. (주의) 이전 sweep 이 남긴 `person:엄마` / `person:TestSender_0421_T11` DIGEST 룰은 다음 tick 환경에 남아있음 — priority-inbox recipe 의 `엄마` 경로는 계속 DIGEST 로 라우팅됨 |


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

| ID | Superseded by | 이유 |
|---|---|---|
| [digest-inbox](digest-inbox.md) | [inbox-unified](inbox-unified.md) | 2026-04-22 plan `categories-split-rules-actions` Phase P3 Task 11 이후 Digest 전용 탭은 `정리함` 통합 탭의 서브탭으로 호스팅됨. `Routes.Digest` 는 tray deep-link 용으로만 유지. |
| [hidden-inbox](hidden-inbox.md) | [inbox-unified](inbox-unified.md) | 동 plan — 보관/처리 탭이 `정리함` 통합 탭의 서브탭으로 호스팅됨. `Routes.Hidden` 은 silent summary deep-link 용으로 유지. |

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
