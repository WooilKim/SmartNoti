---
status: planned
---

# Detail "분류 변경" Confirmation Snackbar Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 `NotificationDetailScreen` 의 "분류 변경" 시트에서 기존 Category 에 할당하거나 새 Category 를 만들기 위해 editor 로 진입한 직후, 화면 하단 Snackbar 로 짧은 확인 메시지가 한 번 표시된다 (예: 경로 A `"'<카테고리명>'에 추가됐어요"`, 경로 B 는 editor 가 저장 후 onSaved → `"'<카테고리명>'을 만들었어요"`). 사용자는 더 이상 "시트가 닫혔는데 변경이 적용된 건지 모르겠다" 는 silent-success 불안을 겪지 않는다. 행동/persistence 는 일체 변경되지 않는다 — 시각적 confirmation 만 추가.

**Architecture:**
- `NotificationDetailScreen` 의 outermost `Box` 를 `Scaffold(snackbarHost = ...)` 로 감싸거나, 동일 효과의 `SnackbarHost` overlay 를 `Box` 안에 추가. `SnackbarHostState` 를 `remember` 하고 두 호출 사이트 (`onAssignToExisting`, `CategoryEditorScreen.onSaved`) 가 `scope.launch { snackbarHostState.showSnackbar(...) }` 트리거.
- 경로 A: 기존 `assignUseCase.assignToExisting` 호출 후 동일 코루틴 내부에서 await → snackbar. Category 이름은 시트가 dismiss 되기 직전 상태에서 `categories.firstOrNull { it.id == categoryId }?.name` 로 lookup.
- 경로 B: `CategoryEditorScreen` 의 `onSaved: () -> Unit` 콜백은 현재 인자 없음. 가장 작은 변경은 callback signature 를 `onSaved: (Category) -> Unit` 로 보강하거나 별도 콜백 (`onCategoryCreated: (Category) -> Unit`) 을 추가해 신규 Category 정보를 노출. Detail 은 그 콜백에서 snackbar 표시. 시그니처 변경 시 다른 호출 사이트 (`CategoriesScreen`) 도 함께 갱신해 빌드를 깨지 않게 유지.
- Snackbar 는 단일 라인 텍스트 + 짧은 duration. action button 없음 (undo 는 본 PR scope 외 — 별도 plan 가능).

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`SnackbarHost`, `SnackbarHostState`, `rememberCoroutineScope`).

---

## Product intent / assumptions

- **gap 원문 (notification-detail Known gaps)**: "재분류 시 토스트/확인 UI 없음 (시트만 닫혀 UX 가 조용함)." rules-feedback-loop Known gaps 에도 "시트 확정 후 사용자에게 토스트나 확인 UI 없음 (조용히 성공)." 동일 의미로 등록.
- **결정 가정 (implementer 가 PR 본문에서 한 줄 명시)**: 경로 A 메시지 카피는 `"'${categoryName}'에 추가됐어요"`. 경로 B (editor 저장 후) 는 `"'${categoryName}'을(를) 만들었어요"`. 카피는 implementer 가 한국어 자연성 위주로 다듬어도 무방 — 핵심은 `categoryName` 이 포함되어 사용자가 어떤 분류에 들어갔는지 즉시 알 수 있는 것.
- **결정 가정**: Snackbar duration 은 `SnackbarDuration.Short` (≈ 4초). action button 없음. 사용자가 빠르게 다음 동작으로 이동해도 거슬리지 않게.
- **결정 필요 (Risks 에 open question 으로 적힘)**: 경로 B 에서 사용자가 editor 를 cancel (저장 안 함) 하면 snackbar 는 표시되지 않음 — `onDismiss` 와 `onSaved` 분기 명확. confirmed 동작.
- **결정 필요 (Risks)**: Detail 화면을 떠나기 직전 (`onBack`) 에 snackbar 가 떠 있는 상태이면 그대로 사라짐. snackbar host 가 Detail 의 lifecycle scope 에 묶이므로 자연스러운 동작 — 별도 처리 없음.
- **out of intent**: undo 액션 추가, toast 대신 in-place inline confirmation, snackbar 위치/색상 커스텀, 경로 A 의 "분류에 추가됐지만 이미 같은 rule 이 들어 있던 idempotent 케이스" 와 신규 추가 케이스의 카피 분리 — 모두 후속 plan 의 잠재 작업.

---

## Task 1: Add failing tests for snackbar trigger callbacks

**Objective:** "기존 Category 에 할당 직후 + editor 저장 후" 두 경로가 callback 으로 신호를 노출하는지 (혹은 사이드이펙트로 snackbar host 에 enqueue 하는지) 를 unit-testable 한 layer 로 고정. 화면 단위 Compose UI 테스트가 부재한 상태이므로 본 task 는 두 가지 옵션 중 implementer 가 하나를 선택:

- **옵션 A (선호)**: `AssignNotificationToCategoryUseCase` 호출 결과를 wrapping 하는 작은 helper (예: `DetailReclassifyConfirmationMessageBuilder`) 를 신규 작성 — `(notification, categories, assignedCategoryId, mode: AssignedExisting | CreatedNew) → String?` 반환. Unit test 가 메시지 합성 규칙을 고정. Detail 쪽은 단순히 빌더가 만든 문자열을 snackbar 에 흘림.
- **옵션 B**: 신규 Compose UI test (`NotificationDetailScreenSnackbarTest`) 로 두 경로 후 snackbar 텍스트 등장을 검증. 단, 현재 Detail 화면은 단독 Compose test 가 부재 (notification-detail Known gaps 의 "단독 Compose UI 테스트 부재" 와 무관하지 않음) 라서 setup 비용이 더 큼. 먼저 옵션 A 로 가는 편을 권장.

**Files (옵션 A 기준):**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/detail/DetailReclassifyConfirmationMessageBuilderTest.kt`
- 신규 (Task 2 에서 생성) `app/src/main/java/com/smartnoti/app/ui/screens/detail/DetailReclassifyConfirmationMessageBuilder.kt`

**Steps:**
1. 테스트 시나리오:
   - 경로 A — 기존 Category id `"cat-promo"` (name=`"프로모션"`) 에 할당 → 메시지 `"'프로모션'에 추가됐어요"`.
   - 경로 A — categoryId 가 categories 리스트에 없음 (race / stale) → 메시지 `null` 또는 fallback `"분류에 추가됐어요"` (둘 중 하나로 명시 결정 — implementer 판단, PR 본문에 표기).
   - 경로 B — `CreatedNew(categoryName="새 분류")` → 메시지 `"'새 분류'을(를) 만들었어요"` (혹은 implementer 가 다듬은 카피).
   - 경로 B — categoryName 이 blank → 메시지 `null` (snackbar 표시 안 함).
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.detail.DetailReclassifyConfirmationMessageBuilderTest"` → RED 확인.

## Task 2: Implement message builder + Detail SnackbarHost wiring

**Objective:** Task 1 RED → GREEN. 그리고 Detail 화면에 `SnackbarHostState` + `SnackbarHost` 를 추가, 두 호출 사이트가 builder 로 메시지를 만들어 snackbar 를 띄운다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/detail/DetailReclassifyConfirmationMessageBuilder.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`
- 필요 시: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt` — `onSaved` 시그니처에 신규 Category 노출 (혹은 별도 `onCategoryCreated` 콜백 추가). 다른 호출 사이트 (`CategoriesScreen`) 도 새 시그니처에 맞춰 갱신.

**Steps:**
1. `DetailReclassifyConfirmationMessageBuilder.kt` 작성 — pure Kotlin object/class, Task 1 의 시그니처에 맞춰 메시지 합성. `AssignedExisting(categoryId)` 와 `CreatedNew(categoryName)` sealed class 사용.
2. `NotificationDetailScreen` 안에서:
   - `val snackbarHostState = remember { SnackbarHostState() }` 추가.
   - 기존 `Box(modifier = Modifier.fillMaxSize())` 안에 `SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))` 를 추가하거나, outermost 를 `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })` 로 감싸기. `Scaffold` 를 도입하면 기존 `Box` + `LazyColumn` padding 계산이 살짝 바뀌므로, 가능하면 `SnackbarHost` overlay (옵션 1) 가 더 적은 변경.
   - `onAssignToExisting` 콜백 코루틴 내부에서 `assignUseCase.assignToExisting(...)` 완료 후 `builder.build(AssignedExisting(categoryId), categories)` → non-null 시 `snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)`.
   - 경로 B: editor `onSaved` 콜백 (시그니처 변경 후) 에서 `builder.build(CreatedNew(category.name), categories)` → 동일 패턴으로 snackbar.
3. `CategoryEditorScreen` 의 `onSaved: () -> Unit` 을 최소 침습적으로 변경 — `onSaved: (Category) -> Unit` 또는 `onCategoryCreated: (Category) -> Unit` 추가. 다른 호출 사이트 (`CategoriesScreen`) 가 인자를 무시하도록 람다만 `_ -> {}` 또는 `it -> { /* existing */ }` 로 갱신. 빌드가 깨지지 않게 한 PR 안에서 모두 정리.
4. `./gradlew :app:testDebugUnitTest` 전체 GREEN.
5. `./gradlew :app:assembleDebug` 통과 확인.

## Task 3: ADB visual verification

**Objective:** 두 경로에서 실제로 snackbar 가 한 번씩 뜨는지, 카피가 의도한 대로 보이는지 시각 확인.

**Files:** 코드 변경 없음 — 검증만.

**Steps:**
1. 빌드/설치 후 알림 게시:
   ```
   adb shell cmd notification post -S bigtext -t 'DetailToastTest' Toast1 '확인 토스트 테스트'
   ```
2. Home/정리함에서 카드 탭 → Detail 마운트 → "분류 변경" 탭 → CategoryAssignBottomSheet 오픈.
3. 경로 A: 기존 Category 한 row 탭 → 시트 dismiss + 화면 하단 snackbar `"'<카테고리명>'에 추가됐어요"` 등장 (≈ 4초). uiautomator dump 로 텍스트 확인.
4. 경로 B: 동일 또는 새 알림에서 Detail 재진입 → "분류 변경" → "+ 새 분류 만들기" → CategoryEditorScreen 오픈 → 이름 입력 후 저장 → editor close + Detail 로 복귀 + snackbar `"'<신규명>'을(를) 만들었어요"` 등장.
5. 경로 B 취소: editor 에서 cancel/back → snackbar **표시되지 않음** 확인.
6. 결과를 PR 본문에 (가능하면) 화면 캡처 또는 dump 인용으로 첨부.

## Task 4: Update notification-detail + rules-feedback-loop journeys

**Objective:** 두 journey 의 Known gaps + Code pointers + Change log 동기화 (gap 본문은 그대로 두고 resolved annotation 만 추가).

**Files:**
- `docs/journeys/notification-detail.md`
- `docs/journeys/rules-feedback-loop.md`

**Steps:**
1. `notification-detail.md` Known gaps:
   - "재분류 시 토스트/확인 UI 없음 (시트만 닫혀 UX 가 조용함)." bullet 앞에 `(resolved <ship-date>, plan 2026-04-24-detail-reclassify-confirm-toast)` prefix 를 추가. 본문은 보존.
2. `rules-feedback-loop.md` Known gaps:
   - "시트 확정 후 사용자에게 토스트나 확인 UI 없음 (조용히 성공)." bullet 앞에 동일 resolved prefix 추가. 본문은 보존.
3. Observable steps (notification-detail) 5 의 끝에 한 문장 추가: "시트 dismiss 직후 화면 하단에 짧은 confirmation snackbar 가 한 번 표시된다 (경로 A: 추가된 분류명, 경로 B: 신규 분류명)." — Exit state / Trigger / Goal 은 건드리지 않음.
4. Code pointers 에 `ui/screens/detail/DetailReclassifyConfirmationMessageBuilder` 추가 + `NotificationDetailScreen` 의 SnackbarHost 추가 사실 한 줄 추가.
5. Tests 에 `DetailReclassifyConfirmationMessageBuilderTest` 추가.
6. Change log 에 본 PR 라인 append (날짜는 implementer 가 작업한 실제 UTC 날짜, 요약 + plan link + PR link).
7. `last-verified` 는 **갱신하지 않음** — implementer 가 직접 ADB recipe 를 처음부터 끝까지 돌려 모든 Observable steps 를 재검증한 게 아니라면 그대로 둔다.

## Task 5: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 항목 포함:
  1. 채택한 옵션 (A=메시지 builder + unit test 우선 / B=Compose UI test 우선) 과 이유.
  2. 카피 결정 (경로 A / 경로 B 의 최종 문자열, blank/race fallback 정책).
  3. `onSaved` 시그니처 변경 방식 (인자 추가 vs 별도 콜백) 과 영향받은 다른 호출 사이트 목록.
  4. ADB 검증 결과 — 두 경로 + cancel 케이스의 snackbar 등장/미등장 확인.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/notification-detail.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- 신규 `DetailReclassifyConfirmationMessageBuilder` (pure Kotlin) + 단위 테스트.
- `NotificationDetailScreen` 에 `SnackbarHostState` + `SnackbarHost` 한 곳 추가, 두 호출 사이트가 메시지 트리거.
- `CategoryEditorScreen.onSaved` 시그니처 또는 신규 콜백으로 신규 Category 노출 + 기존 호출 사이트 (`CategoriesScreen`) 갱신.
- Journey 문서 두 건 동기화 (Observable / Code pointers / Known gaps annotate / Tests / Change log).

**Out:**
- Undo 액션 (snackbar action 버튼) — 별도 plan.
- 경로 A 의 "이미 동일 rule 이 ruleIds 에 있던 idempotent 재할당" 과 "신규 add" 의 카피 분리 — 본 PR 은 단일 카피.
- replacement 알림 / 정리함 / Home 등 다른 진입 경로의 confirmation UI — 본 PR 은 Detail "분류 변경" 시트만.
- Compose UI test infra (옵션 B) 의 일반 도입 — notification-detail Known gaps 의 "단독 Compose UI 테스트 부재" 는 별도 plan 으로 처리.
- Toast 도입 (Snackbar 가 Compose 친화적이라 의도적으로 선택).
- Snackbar 색상/위치 커스텀 — Material3 기본 외형 사용.

---

## Risks / open questions

- **`CategoryEditorScreen.onSaved` 시그니처 변경의 파급**: editor 는 `CategoriesScreen` 에서도 호출되므로 시그니처 변경 시 두 호출 사이트가 동시에 갱신되어야 빌드가 깨지지 않는다. 가장 안전한 옵션은 기존 `onSaved: () -> Unit` 을 유지하고 별도 `onCategoryCreated: (Category) -> Unit = {}` 를 추가하는 것 — Detail 만 신규 콜백을 채우고 Categories 는 default 무시. implementer 가 둘 중 하나를 PR 본문에 명시.
- **경로 A 의 race 케이스**: snackbar 메시지 합성 시점에 `categories` flow 가 아직 새 ruleIds 변경을 반영하지 않았을 수 있음 (`assignUseCase` 가 write 한 직후의 collect 타이밍). 다행히 메시지는 categoryId → name lookup 만이고 name 자체는 이번 write 로 바뀌지 않아 stale 위험 낮음. 그래도 categories 가 비어 있는 race 에서는 fallback (`null` 또는 generic copy) 동작을 builder unit test 가 명시.
- **경로 B 의 생명주기**: editor 가 자체 AlertDialog 로 떠 있는 동안 사용자가 시스템 back 으로 Detail 도 빠져나가면 snackbar host 가 사라진다 → 자연스럽게 snackbar 표시 안 됨. 이게 의도된 동작인지 (사용자가 화면을 떠났으니 안 보여주는 게 맞다 / 토스트라면 화면 무관하게 떠야 한다) 는 implementer 가 한 줄로 결정 후 PR 본문에 적는다. 권장: Snackbar 유지 (toast 미도입).
- **카피 자연성**: "'<신규명>'을(를) 만들었어요" 는 `(을/를)` 이중 표기로 어색할 수 있음. implementer 가 한국어 자연성 위주로 `"새 분류 '${name}'을 만들었어요"` 또는 `"'${name}' 분류를 만들었어요"` 같은 패턴으로 다듬을 권한 있음 — 본 plan 은 의미 contract 만 고정.
- **rules-feedback-loop journey 변경 범위**: 본 plan 은 두 journey 의 Known gaps 만 annotate. Observable / Trigger / Exit 는 손대지 않음 — rules-feedback-loop 의 시트 흐름 자체는 위임 관계라 notification-detail 측 갱신이 본질.
- **테스트 환경**: 경로 A 검증을 위해 기존 Category 가 최소 1개 필요. 빈 환경이면 시트 상단 리스트가 비어 경로 A 자체를 테스트할 수 없음 — implementer 가 ADB 에서 사전 Category 생성 (또는 기존 분류 재사용) 후 검증.

---

## Related journey

- [notification-detail](../journeys/notification-detail.md) — Known gaps "재분류 시 토스트/확인 UI 없음" bullet 을 (resolved …) 로 마킹.
- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — Known gaps "시트 확정 후 사용자에게 토스트나 확인 UI 없음" bullet 을 동일 resolved 로 마킹. Observable / Trigger / Exit 는 변경하지 않음.
