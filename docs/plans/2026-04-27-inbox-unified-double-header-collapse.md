---
status: planned
---

# 정리함 통합 탭 — DigestScreen 임베드 시 ScreenHeader 중복 제거

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `Routes.Inbox` (정리함 통합 탭) 진입 시 화면 상단에 동일한 `ScreenHeader` (eyebrow `정리함` + title `알림 정리함`) 가 두 번 연속으로 렌더되는 시각 회귀를 제거한다. 외부 `InboxScreen` 의 헤더 + sort dropdown + tab row 바로 아래에 임베드된 `DigestScreen` 이 자기 자신의 `ScreenHeader` 를 또 그리고, 그 아래 SmartSurfaceCard 요약 카드까지 노출되어 첫 화면 절반 가까이를 중복된 chrome 으로 채우고 있다 (2026-04-27 ui-ux sweep, `/tmp/ui-inbox-sort-dropdown-closed.png`). PR #418 (sort dropdown) 자체의 결함은 아니지만 dropdown row 가 두 헤더 사이에 끼면서 중복이 가시화됐다 — `inbox-denest-and-home-recent-truncate` (2026-04-22) 가 Hidden 두 서브탭에는 `HiddenScreenMode.Embedded` 분기로 헤더를 끈 반면 Digest 서브탭에는 같은 처리를 하지 않은 상태로 ship 됐기 때문.

**Architecture:** 두 가지 옵션 후보. 결정은 구현 PR 에서 product judgment 로 — 어느 쪽이든 표면 회귀가 없도록 `Routes.Digest` deep-link standalone 진입과 `Routes.Inbox` 의 Digest 서브탭 임베드 진입을 모두 커버해야 함.

- **Option A (권장)** — `HiddenNotificationsScreen` 이 가진 `HiddenScreenMode.Standalone | Embedded` sealed class 패턴을 `DigestScreen` 에도 동일하게 도입. `DigestScreenMode.Standalone` 은 기존 `ScreenHeader` + 요약 SmartSurfaceCard 를 그대로 렌더. `DigestScreenMode.Embedded` 는 두 chrome 을 모두 끄고 그룹 LazyColumn 만 렌더 (외부 InboxScreen 의 헤더/탭이 이미 컨텍스트를 전달하므로). `InboxScreen` 의 호출부는 `DigestScreen(mode = Embedded, ...)` 로 한 줄 바뀜. 영향 범위가 좁고 Hidden 측 패턴과 대칭이라 신규 회귀 가능성이 낮음. Plan `2026-04-22-inbox-denest-and-home-recent-truncate` 의 결정과 일관됨.
- **Option B** — `DigestScreen` 에서 `ScreenHeader` 를 영구 제거하고, standalone deep-link (`Routes.Digest`) 진입자가 헤더를 필요로 하면 NavHost 단에서 별도 wrapper composable 로 감싼다. 코드 path 는 단순해지지만 deep-link UX 가 달라질 수 있어 (현재 standalone 에서는 헤더가 화면 컨텍스트를 제공) 추가 verification 필요.

> 결정 권장: Option A. 패턴이 이미 codebase 에 있고, standalone deep-link 의 시각 컨텍스트를 보존한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), 기존 `HiddenScreenMode` sealed class 패턴 mirror, JUnit (mode-aware contract test).

---

## Product intent / assumptions

- 통합 정리함 탭 한 화면에 같은 헤더 (`정리함` eyebrow + `알림 정리함` title + 동일 의미 subtitle 두 개) 가 두 번 보이는 것은 정보 중복일 뿐 아니라 사용자가 "두 화면을 잘못 쌓은 것 아닌가" 의심하게 만드는 시각 노이즈. `.claude/rules/ui-improvement.md` 의 "scaffold coherence" / "spacing rhythm" / "favor clarity over decoration" 원칙 위반.
- 정리함 첫 진입 시 그룹 카드가 fold 아래로 밀려 사용자가 한 번 스크롤해야 본문에 닿음 — 정렬 dropdown 은 헤더 영역에 있어야 하지만 (쉽게 도달 가능) 그 헤더가 두 번이면 dropdown 의 가치도 희석됨.
- standalone `Routes.Digest` deep-link 진입 (현재는 사용 빈도 낮음, replacement notification contentIntent 만 사용) 에서는 헤더가 사용자에게 "여긴 정리함 화면" 컨텍스트를 제공하므로 보존 가치가 있음 — Option A 가 두 진입 경로의 의도를 동시에 만족.
- 정리함 임베드 시 본문 위 중복된 `현재 N개의 묶음이 준비되어 있어요` SmartSurfaceCard 도 함께 사라져야 함 — 외부 InboxTabRow 의 `Digest · N건` count 가 이미 같은 정보를 더 컴팩트하게 전달함.

### 사용자 판단이 필요한 결정 (Risks / open questions 으로 옮김)

- 임베드 모드에서 SmartSurfaceCard 요약 카드를 완전히 숨길지, 아니면 `현재 N개의 묶음이 준비되어 있어요` 만 살리고 helper subtitle 만 제거할지. 권장: **완전 숨김** — InboxTabRow 의 카운트 chip 이 같은 정보를 더 빠르게 전달.
- standalone 진입의 `last-verified` 가 부재 — Option A 채택 후 standalone 회귀 없는지 ADB 로 확인 필요 (replacement notification 시뮬레이션 환경 부재 시 수동 nav).

---

## Tasks

### Task 1 — `DigestScreenMode` sealed class 도입

- [ ] (test-first) `DigestScreenMode` sealed class 신설: `Standalone` (data object) + `Embedded` (data object). `HiddenScreenMode` 의 시그니처 mirror — 미래에 mode 별 파라미터가 생기면 같은 자리에 추가.
- [ ] `DigestScreenModeContractTest` — `Standalone.shouldRenderHeader()` / `Embedded.shouldRenderHeader()` 의 boolean 계약 (Hidden 측 패턴 mirror).

**파일:** `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestScreenMode.kt` (신규), `app/src/test/java/com/smartnoti/app/ui/screens/digest/DigestScreenModeContractTest.kt` (신규).

**검증:** `./gradlew test --tests "*DigestScreenModeContractTest"` 통과.

### Task 2 — `DigestScreen` 시그니처에 `mode: DigestScreenMode` 추가 (default = `Standalone`)

- [ ] `DigestScreen(...)` 의 시그니처에 `mode: DigestScreenMode = DigestScreenMode.Standalone` 추가. `Standalone` 분기는 기존 `ScreenHeader` + SmartSurfaceCard 요약을 그대로 렌더, `Embedded` 분기는 두 item 을 LazyColumn 에서 빼고 그룹 카드부터 시작.
- [ ] empty branch (`groups.isEmpty()`) 도 동일하게 mode 분기 — Embedded 에서는 EmptyState 만 렌더 (InboxScreen 헤더가 이미 컨텍스트 전달).
- [ ] `DigestScreen` 호출부 모두 default 그대로 빌드 통과 확인 (Standalone 진입은 무영향).

**파일:** `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestScreen.kt`.

**검증:** `./gradlew assembleDebug` 통과 + 기존 `DigestScreenBulkActionsWiringTest` / `DigestEmptyStateContractTest` 회귀 없음.

### Task 3 — `InboxScreen` 호출부를 `Embedded` 로 전환

- [ ] `InboxScreen` 의 `DigestScreen(...)` 호출부에 `mode = DigestScreenMode.Embedded` 추가. 다른 prop 무변경.
- [ ] 빌드 후 ADB smoke: `정리함` 탭 진입 → 헤더 1개 (`정리함 / 알림 정리함 / Digest 묶음과 숨긴 알림을…`) + sort dropdown + InboxTabRow 직후 그룹 카드 LazyColumn — 중복 헤더 + SmartSurfaceCard 요약 카드 부재 확인. uiautomator dump 의 `정리함` 텍스트 노드 1회만 등장.

**파일:** `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt`.

**검증:** ADB end-to-end smoke + 헤더 텍스트 노드 카운트 (`grep -c '정리함' uiautomator-dump`).

### Task 4 — Standalone deep-link 회귀 가드

- [ ] `Routes.Digest` deep-link 의 NavHost composable 등록에서 `DigestScreen` 호출이 mode 를 명시적으로 `Standalone` 으로 (혹은 default 이용) 호출하는지 확인. 호출부 변경 없음.
- [ ] ADB 검증: `am start -a android.intent.action.VIEW -d "smartnoti://digest" com.smartnoti.app` (현 deep-link scheme 이 있다면 사용, 없으면 Routes.Digest 직접 navigate 확인) → standalone 진입에서 헤더 + 요약 카드 정상 렌더.

**파일:** `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` (확인만).

**검증:** standalone 진입에서 헤더 1회, embedded 진입에서 헤더 1회 — DRIFT 없음.

### Task 5 — `inbox-unified` journey 갱신

- [ ] Observable steps 5 (지금 `groups.isEmpty()` 분기 + 헤더 카피 단일 source 명시) 와 7 (Digest 탭 본문) 에 `DigestScreen(mode = Embedded)` 위임 사실 추가. Code pointers 에 `DigestScreenMode` 신규 항목 추가.
- [ ] Known gaps 의 dual-header bullet 제거 + Change log 에 본 plan 의 ship 결과 한 줄 (날짜 + "DigestScreen embed mode 도입으로 정리함 통합 탭 dual-header 회귀 해소" + commit hash).
- [ ] `last-verified` 는 verification recipe 전체 재실행 시점에만 갱신 (per `.claude/rules/docs-sync.md`).

**파일:** `docs/journeys/inbox-unified.md`.

**검증:** Change log + Known gaps + Code pointers 동기화.

---

## Risks / open questions

- standalone deep-link 의 trigger 가 현재 production 에서 거의 안 쓰임 (replacement notification contentIntent 가 직접 Detail 로 점프하는 경우가 더 흔함). standalone 회귀 검증 환경이 부족하면 Option B (영구 제거 + standalone wrapper) 가 더 깔끔할 수 있음 — 구현 PR 에서 product 판단 필요.
- `DigestScreenMode` 가 미래에 sub-state (예: `Embedded(showSortHint = true)`) 를 받을 가능성. 현 plan 은 두 data object 만 — 필요시 sealed class 분화 비용은 mod 작음.
- `InboxScreen` 의 SmartSurfaceCard 요약 (`현재 N개의 묶음이 준비되어 있어요`) 이 일부 사용자에게 시작 컨텍스트로 가치가 있을 수 있음. 본 plan 은 임베드 모드에서 InboxTabRow `Digest · N건` 칩으로 충분하다고 가정 — 의견 분기 시 product judgment 필요.

---

## Definition of done

- 정리함 탭 진입 시 `정리함` eyebrow + `알림 정리함` title 이 화면당 1회만 등장 (uiautomator dump verifiable).
- Standalone `Routes.Digest` deep-link 진입에서 헤더 + 요약 카드 보존 (회귀 없음).
- `DigestScreenModeContractTest` 통과.
- `DigestScreenBulkActionsWiringTest` / `DigestEmptyStateContractTest` 회귀 없음.
- `inbox-unified` journey 의 Known gaps 에서 dual-header bullet 제거, Change log 항목 추가.

---

## Change log

- 2026-04-27: planned — ui-ux sweep `/tmp/ui-inbox-sort-dropdown-closed.png` 에서 정리함 진입 시 `ScreenHeader` 가 두 번 렌더되는 회귀 발견. Hidden 측 패턴 (`HiddenScreenMode.Standalone | Embedded`) 의 mirror 로 `DigestScreenMode` 를 도입해 표면 회귀 없이 임베드 헤더를 끄는 작업.
