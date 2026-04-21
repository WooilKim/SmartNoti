---
title: "IGNORE (무시) — 4th decision tier: delete-level classification"
status: planned
created: 2026-04-21
---

# IGNORE (무시) — 네 번째 분류 티어

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** PRIORITY / DIGEST / SILENT 3-티어 체계에 네 번째 티어 **IGNORE (무시)** 를 추가한다. 사용자가 어떤 알림을 "소음 — 보지도, 기록에도 남기지 말고 버려라" 고 명시했을 때, SmartNoti 는 그 알림을 원본 tray 에서 cancel 하고, replacement alert 도 게시하지 않으며, Home/Hidden/Digest 기본 뷰에서 제외한다. DB row 는 남긴다 (감사/복구/power-user 리뷰용) — "안 보이는" 것과 "없었던 것" 은 다르다. Option A (SILENT 기본 동작 바꾸기) 대신 Option B (새 티어 명시) 를 선택한 이유: "숨김"/"조용히" 의 기존 의미를 깨지 않고, "삭제" 의 의도를 언어로 분리하기 위함.

**Architecture:**
- 4-티어 enum 확장 — `NotificationDecision` / `NotificationStatusUi` / `RuleActionUi` 에 `IGNORE` 항목을 추가.
- IGNORE 는 **룰 드리븐 only** — `NotificationClassifier` 는 스스로 IGNORE 로 승격하지 않는다 (너무 파괴적). `RuleActionUi.IGNORE` 가 걸린 rule 이 매치될 때만 `NotificationDecision.IGNORE` 가 반환된다.
- 룰 계층: IGNORE 는 `ALWAYS_PRIORITY` 에 의해 오버라이드될 수 있다 (기존 `RuleUiModel.overrideOf` 체계 재사용) — "모든 광고 앱 IGNORE, 단 COMPANY_APP 은 always-priority" 패턴.
- Notifier 는 IGNORE 결정 시 early-return — tray cancel 은 여전히 수행, replacement 알림/채널 posting 은 건너뜀.
- Inbox 쿼리 (`NotificationRepository.observePriority/Digest/Silent/Hidden`) 는 IGNORE 를 기본으로 제외. Settings 에 "무시된 알림 보기" 토글 (default OFF) 과 "무시됨" 전용 아카이브 뷰를 추가.

**Tech Stack:** Kotlin, Room (v8→v9 migration), Jetpack Compose, DataStore, Gradle unit tests.

---

## Product intent / assumptions

- **Rule-driven only.** Classifier 의 cascade (rule → VIP → persistence → default) 는 기본을 여전히 SILENT 로 유지한다. IGNORE 는 사용자가 "이건 쓰레기다" 라고 명시 선언한 것에만 적용되어야 한다. 자동 승격은 파괴적 — 복구 비용이 너무 크다.
- **Persistence 는 유지.** IGNORE row 도 DB 에 남겨 감사/복구/주간 인사이트에 쓸 수 있게 한다. "보이지 않음" 은 UI 쿼리 필터로 처리하고, 물리 삭제는 하지 않는다.
- **User-facing view 기본 제외.** Home / Hidden / Digest / Priority / Detail 의 기본 쿼리는 IGNORE 를 filter out. Settings 의 opt-in 토글이 켜졌을 때만 "무시됨" 아카이브가 별도 화면에 노출된다.
- **Weekly insights 포함 — 확정.** 주간/일간 noise-reduction 카운트에 IGNORE row 를 포함한다 ("SmartNoti 가 N개를 조용히 삭제해줬다" 포함). `InsightDrillDownSummaryBuilder` / `SuppressionInsightsBuilder` 의 집계에 IGNORE 케이스를 추가 (Task 6 에 포함). DIGEST/SILENT/IGNORE 세 카운트를 별도로 분리해 노출하면 투명성이 가장 좋다.
- **Detail "무시" 피드백 버튼 — 확정 scope-in.** `rules-feedback-loop` 의 promote/keep-digest/keep-silent 패턴에 네 번째 버튼 추가. 파괴적이므로 **확인 다이얼로그 + undo snackbar** 필수. 자동 룰 upsert 는 수행 (기존 feedback 패턴 유지).
- **Migration 는 아이덴티티.** 기존 DB row 의 `status` 값은 `PRIORITY/DIGEST/SILENT` 셋 중 하나 — v8→v9 는 새 enum 값을 허용하는 것 외에 데이터 재작성이 필요 없다 (string column, enum 파싱이 ingest 시 일어남). 그래도 no-op SQL migration 을 명시적으로 기록해 향후 schema sweep 도구가 당황하지 않게 한다.
- **Color token 은 neutral gray 제안** — IGNORE 는 "존재감이 가장 낮음" 이 의도. 현재 SILENT 가 low-emphasis gray 계열이라면 IGNORE 는 그보다 더 낮은 opacity 혹은 border-only chip 으로. 구현 시 `ui-improvement.md` 톤에 맞춰 최종 결정.

---

## Task 1: Failing tests for 4-tier enum + round-trip [shipped via PR #178]

**Objective:** IGNORE 를 도입하기 전에 enum 확장과 Room string round-trip 을 테스트로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/model/NotificationDecisionTierTest.kt`
- 보강: `app/src/test/java/com/smartnoti/app/data/local/NotificationEntityMapperTest.kt` (존재 시, 없으면 신규)

**Steps:**
1. `NotificationDecision.IGNORE` 가 enum 에 존재하는지, `valueOf("IGNORE")` round-trip 이 되는지.
2. `NotificationStatusUi.IGNORE` 도 동일.
3. `RuleActionUi.IGNORE` 가 존재하는지.
4. `NotificationUiModel(status = IGNORE).toEntity().toUiModel().status == IGNORE` round-trip.
5. 현 상태에서는 모두 컴파일 실패 — 의도된 RED.

## Task 2: Extend enums and persistence layer [IN PROGRESS via PR #178]

**Objective:** Task 1 의 테스트를 green 으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/model/NotificationDecision.kt`
- `app/src/main/java/com/smartnoti/app/domain/model/NotificationUiModel.kt` (NotificationStatusUi)
- `app/src/main/java/com/smartnoti/app/domain/model/RuleUiModel.kt` (RuleActionUi)
- `app/src/main/java/com/smartnoti/app/data/local/SmartNotiDatabase.kt` — `SMART_NOTI_DATABASE_VERSION` 8 → 9
- Migration 등록 지점 (현재 destructive fallback 이면 no-op Migration(8, 9) 을 선언해 history 에 남김)
- 필요 시 `NotificationEntityMapper.kt` — `valueOf` 실패 시 SILENT fallback 가드 추가

**Steps:**
1. Enum 3개에 `IGNORE` 추가.
2. Room version bump + no-op Migration (status column 은 free-form string 이므로 데이터 재작성 불필요, 단 스키마 diff 는 없음을 명시).
3. Task 1 테스트 통과 확인.
4. `./gradlew :app:testDebugUnitTest` 전체 빌드 확인 — when 분기 누락 컴파일 오류가 있는 곳을 전부 TODO 로 수집 (Task 3~5 에서 해결).

## Task 3: Classifier routes `RuleActionUi.IGNORE` → `NotificationDecision.IGNORE` [IN PROGRESS via PR #179]

**Objective:** 룰 기반으로만 IGNORE 에 도달 가능하게 한다.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationClassifier.kt`
- `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierTest.kt` (보강)

**Steps:**
1. `RuleActionUi.IGNORE -> NotificationDecision.IGNORE` 매핑 추가.
2. Classifier 의 기본 fallback 경로 (VIP / 반복 / 기본 SILENT) 는 **건드리지 않는다** — 테스트로 "IGNORE 룰 없이 IGNORE 가 나오지 않는다" 를 고정.
3. Override 테스트 — base IGNORE 룰 + overrideOf = ALWAYS_PRIORITY 룰 동시 매치 시 최종 decision 이 PRIORITY 인지 검증 (기존 `RuleConflictResolver` 와 합동으로).

## Task 4: Notifier early-return for IGNORE + tray cancel 유지 [IN PROGRESS via PR #181]

**Objective:** replacement alert / heads-up / 채널 posting 을 IGNORE 에서 skip 하되, 원본 tray cancel 은 유지 (사용자가 "삭제" 를 원했으므로).

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotifier.kt` (존재 시) 또는 해당 replacement posting 경로
- `app/src/main/java/com/smartnoti/app/notification/NotificationSuppressionPolicy.kt` — IGNORE 분기 추가
- 테스트 보강: `NotificationSuppressionPolicyTest.kt`

**Steps:**
1. `processNotification` 에서 decision == IGNORE 분기를 DIGEST/SILENT 처리 앞에 둔다.
2. DB persist 는 **수행** (row 를 남겨야 audit 가능).
3. `shouldSuppressSourceNotification(decision=IGNORE, ...) = true` — tray 원본 즉시 cancel.
4. Replacement notification 게시 경로 (`postReplacementNotification` 등) 는 IGNORE 에서 호출되지 않음을 보장 (if-guard 또는 when 분기).
5. 테스트: IGNORE → (persist=true, replacement=false, sourceCancel=true).

## Task 5: Rule editor UI — add IGNORE action

**Objective:** 사용자가 IGNORE 를 룰 액션으로 선택할 수 있게.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/components/RuleRow.kt`
- `app/src/main/java/com/smartnoti/app/ui/components/RuleRowDescriptionBuilder.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RuleListGroupingBuilder.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RuleListPresentationBuilder.kt`
- `app/src/main/java/com/smartnoti/app/data/rules/RuleStorageCodec.kt` — enum 직렬화에 IGNORE 추가
- `app/src/main/java/com/smartnoti/app/domain/usecase/RuleDraftFactory.kt`

**Steps:**
1. `RuleActionUi.IGNORE` 를 action dropdown 에 추가 — 카피는 "무시 (삭제)".
2. Rules 탭의 티어 섹션에 "무시" 그룹 추가 (`RuleListGroupingBuilder`).
3. `RuleStorageCodec` 직렬화 enum 값이 hash/order sensitive 면 backward-compat 확인.
4. Description builder — IGNORE 룰의 설명 문구 ("이 조건에 맞는 알림은 알림센터에서 즉시 삭제합니다. SmartNoti 에도 보이지 않습니다.").
5. UI smoke 테스트 / preview 렌더 확인. `ui-improvement.md` 톤에 맞춰 chip 색은 neutral gray 로.

## Task 6: Inbox filtering + Settings archive toggle + Insights 집계

**Objective:** 기본 뷰에서 IGNORE 를 숨기고, opt-in 아카이브 뷰를 제공. Insights 집계에 IGNORE 추가.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt` — `observePriority/Digest/Silent/Hidden` 에 IGNORE 제외 필터 추가
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — `showIgnoredArchive: Boolean` (default false) 추가
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/` — 토글 UI 추가
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveScreen.kt` (아카이브 뷰, 토글이 on 일 때만 nav 에 노출)
- `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` — 조건부 route
- `app/src/main/java/com/smartnoti/app/ui/components/StatusBadge.kt` / `NotificationCard.kt` — IGNORE 케이스 방어적 추가 (기본 뷰에 나타나면 dev assertion or neutral gray badge)
- `app/src/main/java/com/smartnoti/app/domain/usecase/InsightDrillDownSummaryBuilder.kt` — `ignoredCount` 필드 추가, 세 카운트 (digest/silent/ignored) 를 별도 집계
- `app/src/main/java/com/smartnoti/app/domain/usecase/SuppressionInsightsBuilder.kt` — IGNORE 를 "조용히 처리" 묶음이 아닌 별도 스트림으로 집계

**Steps:**
1. Repository 필터 단위 테스트 — IGNORE row 가 존재해도 observePriority 등에서 반환되지 않는지.
2. 새 `observeIgnoredArchive()` flow 추가.
3. Settings 토글과 연결된 나브 entry (조건부 노출).
4. 아카이브 화면은 plain list — 오래된 순/최신 순 정렬, 탭하면 Detail (IGNORE 상태로 표시).
5. StatusBadge — IGNORE 브랜치 추가 시 style 은 neutral gray (low opacity, border-only 고려).
6. Insights builder 테스트 보강 — DIGEST/SILENT/IGNORE 카운트 분리 집계 검증.

## Task 6a: Detail "무시" feedback button + undo

**Objective:** Detail 화면에 네 번째 피드백 버튼 "무시" 를 추가. 파괴적 액션이므로 확인 다이얼로그 + undo snackbar 필수.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` — 액션 버튼 줄에 "무시" 추가
- `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationFeedbackPolicy.kt` — `applyAction` 에 IGNORE 분기 (target status = IGNORE, reasonTag "사용자 규칙"), `toRule` 에 IGNORE action 매핑
- 신규: `app/src/main/java/com/smartnoti/app/ui/components/IgnoreConfirmationDialog.kt` — "이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기." 확인 문구
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationActionReceiver.kt` — broadcast 경로에도 `ACTION_IGNORE` 추가 (replacement alert 에는 IGNORE 버튼을 노출하지 않음 — replacement 가 뜨는 것 자체가 non-IGNORE 결정이라서)
- 테스트: `SmartNotiNotificationActionReceiverTest.kt`, `NotificationFeedbackPolicyTest.kt` 에 IGNORE 케이스 추가

**Steps:**
1. Detail 버튼 레이아웃에 "무시" 추가 (위치: "조용히 유지" 우측). 탭 시 확인 다이얼로그.
2. 다이얼로그 확정 시 `NotificationFeedbackPolicy.applyAction(notification, IGNORE)` → DB `status=IGNORE` + `reasonTags += "사용자 규칙"`. `toRule` 로 `RuleActionUi.IGNORE` 룰 upsert (sender → PERSON, packageName → APP).
3. 3초 undo snackbar 표시 — 탭 시 이전 status/reasonTags 복원 + 룰 upsert rollback (기존 룰이 있었으면 이전 action 으로 되돌림, 새 룰이면 delete).
4. Detail 화면은 즉시 뒤로 navigate (pop) — IGNORE 된 알림이 현재 화면에 남아있지 않게.
5. Undo 경로는 SmartNoti 의 임시 in-memory undo stack 으로 충분 (프로세스 재시작 시 undo 불가 — snackbar 창이 닫히면 영구화).

## Task 7: Journey doc updates + Change log

**Objective:** 관측 동작 변화를 문서로 동기화.

**Files:**
- `docs/journeys/notification-capture-classify.md` — Decision cascade 에 IGNORE 분기 (rule-driven only) 추가. Change log 항목.
- `docs/journeys/rules-management.md` — Rule action 목록에 IGNORE 추가, 편집 UX 한 문장, Change log.
- `docs/journeys/notification-detail.md` — IGNORE row 는 아카이브 토글이 켜진 경우에만 Detail 도달 가능. Change log.
- `docs/journeys/home-overview.md` / `docs/journeys/hidden-inbox.md` / `docs/journeys/digest-inbox.md` / `docs/journeys/priority-inbox.md` — "IGNORE 는 기본 뷰에서 제외됨" 한 줄 및 Change log.
- `docs/journeys/rules-feedback-loop.md` — (open question 에 따라) Detail 의 "무시" 피드백 버튼이 룰 upsert 를 만드는지 여부를 문서화.
- 신규 journey 고려: `docs/journeys/ignored-archive.md` — Settings 토글 기반 아카이브 뷰가 별도 journey 로 분리될만한 복잡도면 분리, 아니면 각 inbox 문서의 out-of-scope 로.
- `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` frontmatter — `status: shipped` + `superseded-by:` 로 갱신 (Task 8 의 PR 이 머지되는 순간).

**Steps:**
1. 모든 Change log 항목에 PR 번호 + commit hash + 날짜 명시.
2. Known gaps 에 weekly insights 포함 결정 (open question) 의 잠정 결론을 기록.

## Task 8: Self-review + PR

**Steps:**
1. `./gradlew :app:testDebugUnitTest` 전체 통과.
2. ADB 검증 시나리오:
   - 광고 키워드 IGNORE 룰 생성 → 해당 키워드 포함 알림 게시 → tray 에서 즉시 사라지고 SmartNoti Home/Hidden 어디에도 안 보이는지 확인.
   - Settings 에서 "무시된 알림 보기" 토글 on → 아카이브 화면에 방금 그 row 가 보이는지.
   - IGNORE 룰 + `ALWAYS_PRIORITY` override 룰 동시 매치 시 PRIORITY 로 처리되는지.
3. PR 제목: `feat: add IGNORE (무시) 4th classification tier`.
4. PR 본문에 스크린샷 3장 (룰 편집 드롭다운, Hidden 에 안 보임, 아카이브 뷰) 첨부.

---

## Scope

**In scope**
- `NotificationDecision` / `NotificationStatusUi` / `RuleActionUi` 에 IGNORE 항목 추가 + Room v8→v9.
- Classifier 의 rule-driven IGNORE 라우팅 (자동 승격 금지).
- Notifier 의 early-return + tray cancel 유지.
- Rule editor UI 의 IGNORE 옵션.
- Inbox 쿼리 필터링 + Settings 토글 + 조건부 아카이브 뷰.
- Journey 문서 동기화.

**Out of scope**
- IGNORE row 의 물리 삭제 / 보관 기간 정책 (현재 전체 보관, retention policy 는 후속).
- Bulk IGNORE 액션 (Hidden 화면에서 선택 → IGNORE 전환 등).
- IGNORE 룰의 bulk import / export (단일 규칙 생성만).

---

## Risks / resolved design decisions

- **Weekly insights 포함 — 결정: 포함.** DIGEST / SILENT / IGNORE 세 스트림을 별도 카운트로 노출 ("SmartNoti 가 조용히 정리 N개 + 삭제 M개"). `InsightDrillDownSummaryBuilder` + `SuppressionInsightsBuilder` 수정 (Task 6).
- **Detail "무시" 피드백 버튼 — 결정: scope-in.** 확인 다이얼로그 + undo snackbar 로 파괴성 완화. `rules-feedback-loop` 패턴 유지 (자동 룰 upsert). Task 6a 신설.
- **Color / typography tokens.** neutral gray + low opacity 로 결정 (border-only chip 도 검토). 구현 시 `ui-improvement.md` 톤 최종 체크.
- **Override 방향성.** 기존 `overrideOf` 는 base rule → override rule 방향. "IGNORE 를 ALWAYS_PRIORITY 로 덮기" 는 `RuleConflictResolver` 가 현재 지원하는 방향인지 Task 3 테스트로 검증 (미지원 시 resolver 확장 포함).
- **DB migration 의 no-op 성.** `status` 가 string 이라 실제 schema 변경은 없지만, Room 은 version bump 시 migration 객체를 요구한다. `Migration(8, 9) { /* no-op */ }` 로 명시 등록하고 주석에 "enum value set 확장, 데이터 무변경" 을 남긴다.
- **기존 SILENT 룰과의 사용자 인지 충돌.** Rule editor 의 action 라벨 카피가 중요 — "무시 (즉시 삭제)" 처럼 파괴성을 드러내는 단어를 붙인다.
- **Undo stack 수명.** Task 6a 의 undo snackbar 는 in-memory only — 프로세스 재시작 시 undo 불가. 사용자 기대와 일치하는지 ADB 검증 시 확인.

---

## Related journeys

- [notification-capture-classify](../journeys/notification-capture-classify.md) — decision cascade 에 IGNORE 분기 추가.
- [rules-management](../journeys/rules-management.md) — Rule action 에 IGNORE 추가.
- [notification-detail](../journeys/notification-detail.md) — IGNORE row 의 도달 경로 (아카이브 토글 on 에 한함).
- [silent-auto-hide](../journeys/silent-auto-hide.md) — "SILENT 와 IGNORE 의 차이" 를 Known gaps / out-of-scope 에 명시.
- [home-overview](../journeys/home-overview.md) / [hidden-inbox](../journeys/hidden-inbox.md) / [digest-inbox](../journeys/digest-inbox.md) / [priority-inbox](../journeys/priority-inbox.md) — 기본 뷰에서 IGNORE 제외 한 줄.

플랜이 shipped 되는 순간 각 journey 의 Change log 에 해당 PR 번호 + 해시를 추가하고, 이 plan 의 frontmatter 를 `status: shipped` + `superseded-by:` 로 갱신한다.
