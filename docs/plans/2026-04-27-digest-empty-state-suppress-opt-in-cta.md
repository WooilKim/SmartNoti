---
status: shipped
shipped: 2026-04-27
superseded-by: docs/journeys/inbox-unified.md
---

# Digest 인박스 Empty State — Suppress Opt-in 유도 카피 + Inline CTA

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Digest 서브탭 (정리함의 기본 진입 화면이자 `Routes.Digest` deep-link 의 standalone 호스트) 에 진입했는데 아직 DIGEST 로 분류된 알림이 한 건도 없는 사용자가, "왜 이 화면이 비어있는가 / 어떻게 채울 수 있는가" 에 대한 길 안내를 받지 못해 SmartNoti 의 핵심 가치 (반복 알림을 묶어서 조용히 정리) 를 놓치는 문제를 해결한다. Empty state 의 subtitle 을 **suppress opt-in 의 의미를 짧게 설명**하는 카피로 바꾸고, 그 아래에 **"숨길 앱 선택하기"** inline CTA 를 노출해 한 탭으로 Settings 의 `suppressedSourceApps` 선택 화면에 진입할 수 있게 한다. 사용자는 카피 → 버튼의 자연스러운 시선 흐름으로 어떤 앱을 정리함으로 보낼지 결정할 수 있다.

**Architecture:** `ui/screens/digest/DigestScreen` 의 `groups.isEmpty()` 분기가 현재 `EmptyState(title="아직 정리된 알림이 없어요", subtitle="반복되거나 덜 급한 알림을 여기에 모아둘게요")` 만 렌더한다. `EmptyState` 컴포넌트는 plan `2026-04-22-categories-empty-state-inline-cta` 에서 이미 옵셔널 `action: (@Composable () -> Unit)?` 슬롯을 가졌으므로 이번 plan 은 컴포넌트 시그니처를 건드리지 않는다 — `DigestScreen` 의 호출부만 (1) subtitle 카피를 suppress opt-in 의미를 담는 문구로 교체하고, (2) `action` 슬롯에 `FilledTonalButton` + `"숨길 앱 선택하기"` 를 주입하며, (3) 버튼 onClick 으로 `Routes.Settings` 의 suppressed apps 선택 sub-route 로 navigate 한다. Settings 진입 경로는 navigation graph 의 deep-link 를 재사용 (별도 라우트 신설 없음). 카피 / label 은 단일 상수 객체 `DigestEmptyStateAction` 으로 추출해 headless 계약 테스트로 회귀 방지. 로직/그룹 렌더 경로는 무변경 — 빈 분기 (`groups.isEmpty()`) 만 폴리시.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit (headless 계약 테스트), 기존 navigation graph (`Routes.Settings` 의 suppressed apps 진입점 재사용).

---

## Product intent / assumptions

- Digest 인박스 empty state 는 두 가지 의미가 동시에 가능: (a) 사용자가 SmartNoti 를 막 깔아 아직 어떤 알림도 DIGEST 로 분류된 적이 없음, (b) 분류는 됐지만 시간이 지나 모두 처리됨. 두 케이스 모두에서 "어떻게 정리함이 채워지는가" 를 안내하는 카피는 가치가 있다. (b) 의 사용자는 이미 사용법을 알지만 한 번 더 보는 것이 비용이 아님.
- subtitle 카피는 **suppress opt-in 의 의미** (= "옵트인된 앱의 반복 알림을 트레이에서 빼고 여기에 모아드려요") 를 짧게 풀고, CTA 는 **Settings 의 숨길 앱 선택** 으로 직접 보낸다 — 이는 [digest-suppression](../journeys/digest-suppression.md) 의 트리거 조건 (`suppressedSourceApps` 화이트리스트) 과 정확히 일치.
- CTA label 은 **"숨길 앱 선택하기"** — Settings 안의 동일 섹션 카피 ("숨길 앱 선택") 와 어휘를 맞춘 action-oriented 표현. `FilledTonalButton` 사용 (`.claude/rules/ui-improvement.md` 의 "accent sparingly" 원칙 준수, 이 화면에는 다른 accent surface 가 없음).
- **Empty 분기 외 normal state 는 무변경** — 분류된 알림이 있으면 기존 그룹 카드 + bulk action 이 그대로 보인다.
- 본 화면은 inbox-unified 의 Digest 서브탭이 reuse 하는 동일 composable (`DigestScreen`) 이므로, 변경은 두 진입 경로 (legacy `Routes.Digest` deep-link + 통합 정리함 탭) 에서 동시에 발효된다 — 별도 wiring 없음.

### 사용자 판단이 필요한 결정 (Risks / open questions 으로 옮김)

- **Settings 의 정확한 sub-route 이름** — `Routes.Settings` 의 suppressed-apps 진입점이 별도 sub-route 인지, Settings 화면 안의 anchor 인지 plan 작성 시점에 미확인. 구현 시 navigation graph 를 읽어 가장 자연스러운 경로 (별도 sub-route 가 있으면 그걸로, 없으면 `Routes.Settings` 로 보내고 사용자가 한 번 스크롤) 선택. 두 경우 모두 본 plan 의 가치 (카피로 의미 전달 + 버튼으로 한 탭 진입) 는 유지.

---

## Task 1: Add failing unit test for empty-state CTA contract

**Objective:** 카피 / label 단일 source-of-truth 계약을 RED-first 로 고정. Compose UI 테스트 인프라가 repo 에 없으므로 headless 계약 테스트로 대체 (precedent: `CategoriesEmptyStateContractTest`).

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/digest/DigestEmptyStateContractTest.kt`

**Steps:**
1. `DigestEmptyStateAction` (prod 상수 객체, Task 2 에서 작성) 를 참조하는 JUnit 테스트 추가.
2. 계약 assertion:
   - `assertEquals("숨길 앱 선택하기", DigestEmptyStateAction.CTA_LABEL)` — 버튼 카피.
   - `assertEquals("아직 정리된 알림이 없어요", DigestEmptyStateAction.TITLE)` — title 보존.
   - `assertEquals(...)` — subtitle 의 새 카피 (Task 2 에서 확정한 문구를 그대로 박는다; 한 줄, suppress opt-in 의미 + Settings 진입 유도). 가이드: 50자 이내, 명사형 마무리.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.digest.DigestEmptyStateContractTest"` → 컴파일 실패 (상수 부재) 로 RED.

## Task 2: Add the constants object and rewire DigestScreen empty branch

**Objective:** Task 1 의 테스트가 초록이 되게 + 실제 화면이 새 카피와 CTA 를 노출하게.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestEmptyStateAction.kt`
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestScreen.kt`

**Steps:**
1. `DigestEmptyStateAction` object 에 `TITLE` / `SUBTITLE` / `CTA_LABEL` 상수 정의. SUBTITLE 카피는 plan 작성자/리뷰어가 합의된 한 문장 (예: `"숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요"`). CTA_LABEL = `"숨길 앱 선택하기"`.
2. `DigestScreen` 의 `groups.isEmpty()` 분기 (현재 `EmptyState(title=..., subtitle=...)` 만 호출) 를 다음으로 교체:
   - `title = DigestEmptyStateAction.TITLE`, `subtitle = DigestEmptyStateAction.SUBTITLE`
   - `action = { FilledTonalButton(onClick = onOpenSuppressedAppsSettings) { Text(DigestEmptyStateAction.CTA_LABEL) } }`
3. `DigestScreen` 시그니처에 `onOpenSuppressedAppsSettings: () -> Unit` 추가. 기본값 없음 — 호출자가 명시 navigation 을 plumb 하도록 강제 (콜백 누락이 컴파일 에러로 잡히게).
4. 호출자 갱신 (두 군데):
   - `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` — `Routes.Digest` composable 진입점이 `DigestScreen(...)` 를 호출하는 지점. NavController 의 `navigate(Routes.Settings.* )` 를 lambda 에 넣는다. 정확한 sub-route 는 navigation graph 를 읽어 결정 (Risks 참조).
   - `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt` — `InboxTab.Digest` 분기에서 `DigestScreen(...)` 호출하는 지점. 동일한 lambda.
5. `./gradlew :app:testDebugUnitTest` 전체 초록 확인.
6. (선택) `DigestScreen` 안의 다른 empty-state-인접 카피 (`"반복되거나 덜 급한 알림을 여기에 모아둘게요"`) 가 normal state header subtitle 등 다른 위치에서 재사용되고 있다면 그쪽은 건드리지 않음 — empty 분기만 교체.

## Task 3: ADB smoke + journey doc sync + ship

**Objective:** 실제 디바이스에서 두 진입 경로 모두에서 새 카피 + CTA 가 보이고 탭 시 Settings 로 이동하는지 확인 + 두 journey 문서를 갱신 + PR 마감.

**Files:**
- 수정: `docs/journeys/digest-inbox.md` — Known gap (`"Digest 알림이 0건이면 empty-state 만 보여주는데, suppress opt-in 유도 같은 교육용 문구 없음"`) bullet 옆에 `(resolved YYYY-MM-DD, plan ...)` annotation 추가, Change log 에 한 줄 append. 기존 Goal/Observable/Exit state 무변경.
- 수정: `docs/journeys/inbox-unified.md` — 통합 탭 Digest 서브탭이 reuse 하는 컴포넌트 변경이므로 Code pointers 또는 Change log 에 동일 plan 링크와 한 줄 append.

**Steps:**
1. 시스템 시계 한 번 읽고 그 값을 `last-verified` / Change log 에 사용 (`.claude/rules/clock-discipline.md`).
2. `./gradlew :app:assembleDebug` + `adb -s emulator-5554 install -r ...` → 정리함 탭 진입. 기본 emulator 상태 (DIGEST 분류 0건) 에서 새 subtitle 카피 + `"숨길 앱 선택하기"` 버튼이 노출되는지 `adb shell uiautomator dump` 로 확인.
3. 버튼 탭 → Settings 의 숨길 앱 선택 화면으로 이동했는지 확인.
4. `Routes.Digest` deep-link 진입 경로도 동일 카피/CTA 가 보이는지 확인 (예: replacement 알림 contentIntent tap 시뮬레이션, 또는 `adb shell am start` 로 deep-link 직접 호출).
5. Digest 알림 1건 이상 게시 후 탭 재진입 → empty 분기를 빠져나와 기존 그룹 카드가 정상 렌더되는지 회귀 확인 (normal state 무변경).
6. journey 문서 두 개에 Change log 한 줄 append (날짜 + 요약 + PR 번호 머지 후 채움). `last-verified` 는 ADB 로 verification recipe 전체를 돌리지 않았다면 갱신 보류.
7. PR 제목: `feat(digest): empty-state suppress opt-in copy + inline "숨길 앱 선택하기" CTA`.

---

## Scope

**In:**
- `DigestScreen` 의 `groups.isEmpty()` 분기에 한정해 subtitle 카피 교체 + `EmptyState` 의 기존 `action` 슬롯에 `FilledTonalButton` 주입.
- 카피/label 단일화를 위한 `DigestEmptyStateAction` 상수 object + headless 계약 테스트.
- `DigestScreen` 시그니처에 `onOpenSuppressedAppsSettings: () -> Unit` 추가 + 두 호출자 (legacy deep-link + 통합 inbox 탭) 에서 navigation lambda 주입.
- Journey 문서 두 개의 Known gap annotation + Change log 한 줄.
- ADB smoke (uiautomator dump + 탭 → Settings 이동 확인).

**Out:**
- 다른 탭 (Hidden / Priority / Ignored) 의 EmptyState 에 CTA 추가 — 별개 plan.
- Digest 알림 1건 이상 normal state 의 layout / copy 변경.
- onboarding 의 PROMO_QUIETING / suppress opt-in 프리셋 카피 변경.
- `EmptyState` 컴포넌트 시그니처 변경 (이미 `action` 슬롯 보유).
- Settings 의 숨길 앱 선택 화면 자체 (이미 존재) 변경.

---

## Risks / open questions

- **Settings 의 정확한 sub-route 이름:** `Routes.Settings` 가 단일 화면인지, 그 안에 suppressed-apps anchor 가 별도 deep-link 로 노출되는지 plan 작성 시점에 미확인. 구현자가 `app/src/main/java/com/smartnoti/app/navigation/` 와 `app/src/main/java/com/smartnoti/app/ui/screens/settings/` 를 읽어 결정. 별도 sub-route 가 없으면 `Routes.Settings` 로 보내고 (사용자가 스크롤) 본 plan 은 그대로 ship — 추후 sub-route 가 생기면 lambda 만 갱신.
- **Subtitle 카피의 정확한 문구 (사용자 판단):** "숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요" / "옵트인된 앱의 반복 알림을 정리해서 여기에 모아드려요" / "Settings 의 '숨길 앱 선택' 에서 옵트인하면 반복 알림이 여기로 모여요" 등 여러 안 가능. **사용자 / 리뷰어가 한 안을 picking 한 뒤** 구현. 한 문장, 50자 이내, suppress opt-in 의 의미 + Settings 진입 유도 두 의미가 모두 들어가야 함.
- **CTA 가 normal state 에서 보일 위험 없음:** `groups.isEmpty()` 분기 안에만 배치하므로 분류 1건 이상이면 자연 사라짐. headless 테스트는 카피 계약만 잡고, 분기 격리는 ADB smoke 로 확인.
- **Compose UI 테스트 인프라 부재:** repo 에 `createComposeRule` 의존성이 없음 — headless 계약 테스트 (상수 단일화) + ADB uiautomator dump 로 대체. Compose 테스트 인프라 도입 시 본 테스트를 rendered-node 검증으로 격상 가능 (별개 plan).

---

## Related journey

- [`digest-inbox`](../journeys/digest-inbox.md) — 본 plan 이 ship 되면 Known gap (`"Digest 알림이 0건이면 empty-state 만 보여주는데, suppress opt-in 유도 같은 교육용 문구 없음"`) bullet 옆에 `(resolved YYYY-MM-DD, plan ...)` 를 붙이고 Change log 에 한 줄 append. Goal/Observable/Exit state 무변경 (deprecated journey 의 standalone 진입 경로 contract 는 여전히 유효).
- [`inbox-unified`](../journeys/inbox-unified.md) — Digest 서브탭이 동일 composable 을 reuse 하므로 Change log 또는 Code pointers 에 한 줄 append. Observable steps 무변경 (empty 분기는 이미 같은 contract).
- 참조: [`digest-suppression`](../journeys/digest-suppression.md) — CTA 의 destination (`suppressedSourceApps` 선택 화면) 이 호스트하는 opt-in 토글의 의미 정의처. 본 plan 은 이 journey 를 수정하지 않는다.
