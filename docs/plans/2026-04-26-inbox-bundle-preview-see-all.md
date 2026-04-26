---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/inbox-unified.md
---

# Inbox Bundle Preview "전체 보기" 진입점 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 정리함 (`Routes.Inbox`) 의 Digest / 보관 중 / 처리됨 서브탭에서 앱별 그룹 카드 (`DigestGroupCard`) 가 묶음 안의 알림을 모두 노출하지 않고 가장 최근 3건 (`model.items.take(3)`) 만 preview 로 렌더한다. 그래서 4번째 이후 행은 Detail 로 진입할 UI 경로가 없다 — quiet-hours 같이 "원본 tray 가 이미 cancel 된 historical row" 의 Detail (`reasonTags`, `QuietHoursExplainerBuilder`, `RuleHitChipRow` 등) 검증이 사용자/journey-tester 모두 막힌다. 본 plan 은 카드 안 preview 끝에 "전체 보기 · N건 더" inline CTA 를 추가해 expanded state 에서 모든 `model.items` 가 렌더되도록 한다 (LazyColumn 컨텍스트 안 inline expansion). CTA 탭 시 카드 자체가 모든 row 를 펼치고, 각 row 는 기존 `NotificationCard` + `onNotificationClick(item.id)` wiring 을 그대로 재사용해 `Routes.Detail.create(id)` 로 이동한다. Detail 진입 후 뒤로가기는 동일 서브탭 + 동일 그룹의 expanded-all 상태로 복귀한다 (rememberSaveable).

**Architecture:**
- `DigestGroupCard` 의 inner expanded `Column` 안 preview 영역을 두 단계 게이트로 나눈다: 기본은 `model.items.take(PREVIEW_LIMIT)` (PREVIEW_LIMIT = 3, 기존 동작과 동일), `showAll == true` 면 `model.items` 전체. `showAll` 은 `rememberSaveable("$model.id-showAll")` 로 그룹별 영속.
- `model.items.size > PREVIEW_LIMIT` 일 때만 preview 마지막 줄에 `TextButton` (또는 비슷한 텍스트 CTA) "전체 보기 · ${model.items.size - PREVIEW_LIMIT}건 더" 렌더. 탭 시 `showAll = true`. 펼친 뒤에는 같은 자리에 "최근만 보기" CTA 로 토글 (UX 의 일관성 유지 + 잘못 펼쳤을 때 회수 경로).
- `model.items.size <= PREVIEW_LIMIT` 면 CTA 자체가 안 뜨고 기존과 동일 (회귀 없음).
- `bulkActions` slot 은 preview 다음에 그대로 렌더 (CTA 가 bulk action 위치를 가리지 않도록 순서 보존).
- 별도의 새 화면 / 새 route 는 만들지 않는다. inline 확장이 (a) 기존 InboxScreen 의 LazyColumn 안에서 자연스럽고 (b) Detail 진입/복귀 wiring 을 재발명하지 않으므로 가장 risk 낮음.
- `DigestGroupCard` 는 [digest-inbox](../journeys/digest-inbox.md) (deprecated), [hidden-inbox](../journeys/hidden-inbox.md) (deprecated), [inbox-unified](../journeys/inbox-unified.md) (active contract) 모두에서 같은 컴포넌트가 재사용되므로 본 변경은 세 journey 의 Observable steps 에 동시에 영향. 활성 계약은 `inbox-unified` 한 군데만 갱신하고 deprecated 두 곳은 Change log 한 줄 cross-link.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`TextButton`, `rememberSaveable`, `AnimatedVisibility`), JUnit / ComposeRule UI test.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `quiet-hours` 의 Known gap blocker (a) — "정리함 inbox 의 Digest/보관 bundle preview 가 가장 최근 rows 만 노출 → 과거 quiet-hours rows 의 Detail UI 로 직접 navigation 경로 부재. 우회 후보: bundle preview 에 '전체 보기' 진입점 추가." 사용자가 직접 본문에 "우회 후보" 로 등록한 항목이라 본 plan 의 scope ("inline 전체 보기 CTA") 는 사용자 의도와 일치.
- **결정 필요**: inline expansion vs 별도 list 화면 — 본 plan 은 inline expansion 채택. 이유: (a) 별도 화면은 새 route + 새 ViewModel + 새 deep-link contract 가 발생하고, (b) historical row 라도 한 그룹의 절대 다수는 < 30건이라 inline LazyColumn 안에서 충분히 스크롤 가능, (c) Detail 복귀 시 어느 화면으로 돌아갈지 (group-list vs InboxScreen) 라는 새로운 navigation 결정이 사라짐. 만약 그룹 안 row 가 100건 이상으로 자주 뜨는 사용자 시나리오가 발견되면 별도 화면을 후속 plan 으로 분리 — 본 plan 은 그 데이터를 모으기 위한 최소 진입점만.
- **결정 필요**: CTA 카피 — 본 plan 은 `"전체 보기 · ${remaining}건 더"` (펼친 뒤에는 `"최근만 보기"`) 로 채택. 이유: 기존 Inbox 의 카운트 카피 (`"${count}건"`, `"Digest · N건"`) 와 일관. 다국어는 본 plan scope 외 (앱 전체가 한국어 single-locale).
- **결정 필요**: Detail 진입 후 뒤로가기 시 expanded-all 상태 보존 여부 — 본 plan 은 보존 채택 (`rememberSaveable`). 사용자가 4번째 row 의 Detail 을 보고 돌아온 뒤 5번째 row 를 탭할 수 있어야 자연스러움. 만약 사용자가 "한 번 펼친 그룹은 다음 세션에도 펼쳐있길" 까지 원하면 SettingsRepository 까지 들고가야 하므로 본 plan 에서는 process-level rememberSaveable 까지만 (앱 종료 후 재진입 시 다시 collapsed/preview 만 — 기존 패턴 그대로).
- **결정 필요**: PREVIEW_LIMIT 상수의 위치 — 본 plan 은 `DigestGroupCard.kt` 의 file-private `const` 로 둔다 (`private const val PREVIEW_LIMIT = 3`). 다른 컴포넌트가 같은 상수를 참조하지 않으므로 글로벌 location 으로 끌어올릴 이유 없음.
- **결정 필요**: 접근성 — `TextButton` 은 기본적으로 TalkBack 에 announced 됨. 추가 `contentDescription` 불필요. CTA 의 동적 카운트 substring (`"${remaining}건 더"`) 도 string 으로 그대로 읽힘.
- **결정 필요 (OPEN QUESTION)**: 그룹 안 row 가 200~300건 이상인 극단 시나리오에서 inline expansion 의 LazyColumn 성능. 본 plan 은 현재 prod 환경에서 이런 케이스를 발견하지 못했다는 가정 하에 inline 으로 진행한다. journey-tester 의 ADB 검증 단계에서 사용자 device DB 의 그룹별 max items 를 SQL 로 측정해 보고서에 한 줄 첨부 — 만약 100건 이상 그룹이 일상적이라면 별도 화면 plan 으로 escalate.

---

## Task 1: Add failing UI test for "전체 보기" CTA visibility [SHIPPED via PR #349]

**Objective:** CTA 가 `model.items.size > 3` 일 때만 렌더되고, 탭 시 `showAll` 상태가 변하며 모든 row 가 노출됨을 ComposeRule 로 고정.

**Files:**
- 신규 `app/src/androidTest/java/com/smartnoti/app/ui/components/DigestGroupCardSeeAllTest.kt` (또는 unit-style ComposeTest 가 가능하면 `app/src/test/...` 에 두되, 현재 코드베이스에 ComposeRule unit test 디렉터리 패턴이 어디 있는지 implementer 가 확인 후 선택)

**Steps:**
1. 다음 시나리오를 assertion 으로 작성:
   - `items.size = 2` → CTA "전체 보기" 부재. 두 row 모두 렌더.
   - `items.size = 3` → CTA "전체 보기" 부재 (PREVIEW_LIMIT 와 같으면 더 노출할 게 없음). 세 row 모두 렌더.
   - `items.size = 5` + collapsible header 펼친 상태 → CTA "전체 보기 · 2건 더" 렌더, 4번째/5번째 row 는 미렌더.
   - 위 상태에서 CTA 탭 → CTA 카피가 "최근만 보기" 로 토글, 4번째/5번째 row 도 렌더.
   - "최근만 보기" 탭 → CTA 카피 다시 "전체 보기 · 2건 더", 4번째/5번째 row 미렌더.
   - 헤더 collapsible=true 에서 카드 collapse → CTA + 모든 row 가 보이지 않음 (기존 collapse 동작 보존). expand → 직전 `showAll` 상태로 복원.
2. 테스트 실행 → 모두 RED 확인.

## Task 2: Implement "전체 보기" CTA in `DigestGroupCard` [SHIPPED via PR #349]

**Objective:** Task 1 의 테스트가 모두 GREEN 이 되게.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/components/DigestGroupCard.kt`

**Steps:**
1. 파일 상단에 `private const val PREVIEW_LIMIT = 3` 추가. 기존 매직넘버 제거.
2. `Composable` 본문 안 expanded `Column` 직전에 `var showAll by rememberSaveable("${model.id}-showAll") { mutableStateOf(false) }` 추가.
3. expanded `Column` 안 preview 부분을 다음 구조로 교체:
   ```
   val visibleItems = if (showAll) model.items else model.items.take(PREVIEW_LIMIT)
   visibleItems.forEach { item -> NotificationCard(model = item, onClick = onNotificationClick) }
   val remaining = model.items.size - PREVIEW_LIMIT
   if (remaining > 0) {
       TextButton(onClick = { showAll = !showAll }) {
           Text(if (showAll) "최근만 보기" else "전체 보기 · ${remaining}건 더")
       }
   }
   ```
4. `bulkActions` 호출은 CTA **아래** 그대로 둔다 (순서 보존). bulk action 이 늘어나도 CTA 가 그 사이에 끼지 않도록.
5. `./gradlew :app:testDebugUnitTest` + `:app:connectedDebugAndroidTest` (ComposeRule 위치에 따라) 의 새 테스트가 모두 GREEN.

## Task 3: Manual ADB verification on emulator-5554 (positive-case quiet-hours historical row) [SHIPPED via PR #349]

**Objective:** quiet-hours 의 historical row 가 실제로 Detail 까지 navigate 되어 `QuietHoursExplainerBuilder` 의 sub-section + `reasonTags` chip row 가 렌더됨을 사용자 액션으로 재현.

**Steps:**
1. APK 빌드 + 설치: `./gradlew :app:installDebug`. emulator-5554 켜고 SmartNoti 켜기.
2. quiet-hours window editor (PR #346) 로 시작=현재 시각, 종료=현재 시각+2 로 설정.
3. 같은 그룹에 5건 이상의 row 가 쌓이도록 `DebugInjectNotificationReceiver` 로 5번 broadcast — 각 broadcast 사이 1초 간격, 모두 `--es package_name com.coupang.mobile --es app_name "Coupang"` + 다른 `--es title` (예: `QuietBundle1` ~ `QuietBundle5`):
   ```
   for i in 1 2 3 4 5; do
     adb -s emulator-5554 shell am broadcast \
       -a com.smartnoti.debug.INJECT_NOTIFICATION \
       -p com.smartnoti.app \
       --es title "QuietBundle$i" \
       --es body "shopping body $i" \
       --es package_name com.coupang.mobile \
       --es app_name "Coupang"
     sleep 1
   done
   ```
4. SmartNoti → 정리함 → Digest 서브탭 → Coupang 그룹 헤더 탭 (펼침) → preview 가 가장 최근 3건 (`QuietBundle3` ~ `QuietBundle5`) 만 보이고 그 아래 "전체 보기 · 2건 더" CTA 가 보이는지 uiautomator dump + screenshot.
5. CTA 탭 → 4번째 row `QuietBundle2` + 5번째 row `QuietBundle1` 추가 렌더 + CTA 카피가 "최근만 보기" 로 변경 확인.
6. 4번째 row `QuietBundle2` 탭 → Detail 마운트 → "왜 이렇게 처리됐나요?" 카드 안 chip row (`발신자 있음 / 쇼핑 앱 / 조용한 시간`) + "지금 적용된 정책" sub-section ("지금이 조용한 시간(...)이라 자동으로 모아뒀어요.") 렌더 확인.
7. 뒤로가기 → 동일 그룹이 expanded + showAll 상태로 복귀, 5번째 row `QuietBundle1` 도 여전히 노출되는지 확인 (`rememberSaveable` 검증).
8. "최근만 보기" 탭 → CTA 카피 토글 + 4/5번째 row 사라짐 확인.
9. **Out-of-scope guard**: `items.size <= 3` 인 다른 그룹 (예: 일반 SILENT 그룹) 에는 CTA 가 안 뜨는지 확인 (회귀 없음).
10. **그룹 max items 측정** (open question 답):
    ```
    adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
      databases/smartnoti.db \
      "SELECT packageName, COUNT(*) c FROM notifications WHERE status='DIGEST' GROUP BY packageName ORDER BY c DESC LIMIT 5;"
    ```
    상위 5개 그룹의 row 수를 PR 본문에 1줄로 첨부 — 100건 이상이면 별도 화면 plan escalate 트리거.

## Task 4: Update `inbox-unified` journey + cross-link deprecated journeys [SHIPPED via PR #349]

**Objective:** Observable steps + Code pointers + Tests + Change log 동기화. quiet-hours / digest-inbox / hidden-inbox 의 Known gap 본문은 그대로 유지하되 plan link annotate.

**Files:**
- `docs/journeys/inbox-unified.md`
- `docs/journeys/quiet-hours.md`
- `docs/journeys/digest-inbox.md` (deprecated, Change log 한 줄만)
- `docs/journeys/hidden-inbox.md` (deprecated, Change log 한 줄만)

**Steps:**
1. `inbox-unified.md` Observable step 6 ("Digest 탭 — `DigestGroupCard` preview 카드 탭 → `Routes.Detail.create(id)` 로 이동") 에 한 문장 추가: "그룹의 `items.size > 3` 이면 preview 끝에 '전체 보기 · ${remaining}건 더' inline CTA 가 노출되어 탭 시 카드 안에서 모든 row 가 펼쳐진다 (`rememberSaveable` 로 그룹별 showAll 상태 영속, Detail 진입 후 뒤로가기 시 동일 상태 복원). 토글 후 카피는 '최근만 보기'."
2. `inbox-unified.md` Code pointers 에 "DigestGroupCard 의 PREVIEW_LIMIT (3건) + showAll 토글" 한 줄 추가.
3. `inbox-unified.md` Tests 에 "DigestGroupCard `전체 보기` CTA — visibility (`items.size > 3`) + showAll 토글 + collapse 시 숨김" 한 줄 추가.
4. `inbox-unified.md` Known gaps 에서 (필요 시) "historical row navigation 부재" 가 명시돼 있지 않으므로 별도 추가하지 않음 (해소 대상이 quiet-hours 쪽 bullet).
5. `inbox-unified.md` Change log 에 본 PR 항목 append.
6. `quiet-hours.md` Known gaps 의 blocker (a) 본문 끝에 ` → plan: ` `docs/plans/2026-04-26-inbox-bundle-preview-see-all.md` 한 줄 annotate. **본문 텍스트 변경 금지** (`.claude/rules/docs-sync.md` 규칙). PR ship 시 implementer 가 (resolved YYYY-MM-DD …) prefix 로 마킹.
7. `digest-inbox.md` Change log 에 한 줄: "본 plan ship 으로 `DigestGroupCard` preview 가 inline expansion 지원 (legacy 진입로지만 컴포넌트 공유)."
8. `hidden-inbox.md` Change log 에 한 줄: "본 plan ship 으로 `DigestGroupCard` (보관 중 / 처리됨 그룹) preview 가 inline expansion 지원."
9. `last-verified` 는 verification recipe 를 처음부터 다시 돌리지 않았으므로 갱신하지 않는다 (per `.claude/rules/docs-sync.md`).

## Task 5: Self-review + PR [SHIPPED via PR #349]

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과, 새 ComposeRule 테스트 GREEN.
- PR 본문에 다음 명시:
  1. ADB 검증 결과 (CTA 가시성 / 토글 / Detail 진입 + 뒤로가기 + 상태 복원 / 회귀 없음 그룹) — uiautomator dump 발췌 또는 screenshot 1장.
  2. Task 3 step 10 의 그룹 max items SQL 결과 (1줄, open question 답).
  3. PREVIEW_LIMIT = 3 채택 사유 (기존 동작 동일 — 회귀 risk 0).
- PR 제목 후보: `feat(inbox): add "전체 보기" CTA to bundle preview for historical row navigation`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/inbox-unified.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- `DigestGroupCard.kt` — `PREVIEW_LIMIT` 상수 + `showAll` `rememberSaveable` + 인라인 CTA `TextButton`.
- `inbox-unified` journey 의 Observable steps / Code pointers / Tests / Change log 동기화.
- `quiet-hours` Known gap (a) plan link annotate.
- 신규 ComposeRule UI 테스트 1개 (CTA 가시성 + 토글 + collapse 보존).
- `digest-inbox` / `hidden-inbox` deprecated journey 의 Change log 한 줄씩 cross-link.

**Out:**
- 별도 "그룹 전체 화면" 새 route — 본 plan 에서는 inline expansion 만. 100건 이상 그룹이 측정 데이터에서 발견되면 별도 plan.
- "한 번 펼친 그룹은 다음 세션에서도 펼쳐 있게" 영속화 (SettingsRepository) — 본 plan 은 process-level `rememberSaveable` 까지만.
- bulk action (전체 중요로 복구 / 전체 지우기) 의 `showAll` 연동 — 기존 동작 (그룹 전체 대상) 그대로 유지.
- PREVIEW_LIMIT 의 사용자 조절 가능 옵션 (예: Settings 의 "preview 최대 건수") — out.
- Digest 탭이 아닌 Categories 탭의 "최근 분류된 알림" preview 의 see-all (다른 plan: `2026-04-26-category-detail-recent-notifications-preview.md` 가 별도 5건 캡 보유) — 본 plan 의 scope 외.

---

## Risks / open questions

- **OPEN: 그룹 max items 의 실측치** — Task 3 step 10 의 SQL 결과가 일상적으로 100건 이상이면 inline LazyColumn 안에 또 100여 NotificationCard 가 한 번에 mount 되어 스크롤 jank 가능. 본 plan 은 우선 inline 으로 진행하고 측정값을 PR 본문에 첨부해 사용자/journey-tester 가 확인. 100+ 케이스가 흔하면 별도 화면 plan 으로 escalate.
- **rememberSaveable key 충돌** — `"${model.id}-showAll"` 와 기존 `model.id` 기반 `expanded` saveable 가 같은 namespace 를 쓴다. Compose 의 saveable map 은 string-key 라 충돌 없음을 단언하지만, implementer 가 명시적으로 두 key 가 다른지 grep 으로 한 번 확인.
- **CTA 가 bulk action 위 vs 아래** — 본 plan 은 "preview → CTA → bulk action" 순서. 만약 사용자가 "전체 보기 한 뒤 bulk action 도 펼쳐진 모든 row 만 대상으로 한다" 라고 가정한다면 spec 명료화 필요. 현 동작은 bulk action 이 그룹 전체 (DB 의 모든 row) 를 대상으로 하므로 showAll 상태와 무관 — 이 invariant 가 유지됨을 PR 본문에 한 줄 명시.
- **Detail 복귀 시 LazyColumn scroll position** — InboxScreen 의 LazyColumn 자체 scroll 은 NavController 의 default save/restore 로 보존. 카드 안의 inline expansion 도 `rememberSaveable` 로 보존. 두 메커니즘이 합쳐진 결과 사용자가 동일 row 위치로 복귀하는지 ADB 단계에서 확인.
- **접근성 카피** — "전체 보기 · ${remaining}건 더" 의 가운뎃점은 TalkBack 이 어떻게 읽는지 implementer 가 한 번 청취. 만약 어색하면 단순 dot/whitespace (`"전체 보기 (${remaining}건 더)"` 등) 로 변경 가능 — copy 결정은 implementer 권한.
- **테스트 fragility** — ComposeRule 테스트는 emulator 실행 환경이 필요. unit-style preview 만으로 표현이 부족하면 implementer 가 ComposeRule 위치 (`androidTest/`) 로 이동, CI 에 androidTest job 이 없으면 PR 본문에 "로컬 emulator GREEN" 만 첨부.

---

## Related journey

- [inbox-unified](../journeys/inbox-unified.md) — 본 plan 의 활성 계약 owner. Observable step 6 + Code pointers + Tests + Change log 갱신 대상.
- [quiet-hours](../journeys/quiet-hours.md) — Known gap blocker (a) ("정리함 inbox 의 Digest/보관 bundle preview 가 가장 최근 rows 만 노출 → 과거 quiet-hours rows 의 Detail UI 로 직접 navigation 경로 부재") 해소. PR ship 후 (resolved YYYY-MM-DD, plan `2026-04-26-inbox-bundle-preview-see-all`) prefix 로 마킹.
- [digest-inbox](../journeys/digest-inbox.md) (deprecated, superseded by inbox-unified) — Change log 한 줄 cross-link.
- [hidden-inbox](../journeys/hidden-inbox.md) (deprecated, superseded by inbox-unified) — Change log 한 줄 cross-link.
