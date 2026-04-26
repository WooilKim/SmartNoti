---
status: planned
---

# Detail "이 알림도 지금 재분류" Inline CTA Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 `NotificationDetailScreen` 의 "분류 변경" 시트로 새 Category 에 할당하거나 신규 Category 를 만들면, **눈앞의 그 알림 row 자체** 가 새로 매핑된 Category 의 action 으로 즉시 갱신된다 (`status` + `reasonTags` 모두). 현재는 후속 동일 sender 알림에만 효과가 발현되어 사용자가 "방금 한 게 적용된 건지" 가 불확실하다 (snackbar 카피는 2026-04-25 plan 으로 추가됐지만 row 상태 자체는 안 바뀜). 본 plan 은 그 silent-success UX 후퇴를 명시적 "이 알림도 지금 재분류" CTA + repository write 로 보완한다.

**Architecture:**
- `AssignNotificationToCategoryUseCase.assignToExisting` 가 현재는 `upsertRule + appendRuleIdToCategory` 두 write 만 수행. 그 뒤에 "지금 보고 있는 알림 row 도 갱신" 옵션을 추가해 `NotificationRepository.updateNotification` 으로 status/reasonTags 를 덮어쓴다 — 단, write 는 use case 의 새 entry point (`assignToExistingAndApplyToCurrent`) 또는 별도 helper (`ApplyCategoryActionToNotificationUseCase`) 로 분리해 기존 단일 write 시맨틱을 유지하는 호출자 (예: 후속 일괄 reassign 등 hypothetical) 와 분리한다.
- 경로 B (신규 Category) 는 editor 저장 후 `CategoryEditorScreen.onSaved(Category)` 콜백 (2026-04-25 plan 에서 시그니처 확장됨) 에서 동일 helper 를 호출.
- Detail 화면 측은 sheet/editor 가 close 된 시점에 "이 알림도 함께 옮기기" 토글 또는 명시적 secondary CTA 를 보고 helper 호출 여부 결정. MVP 는 **always-on** (자동 적용) — toggle 없이 row 도 같이 옮김. 사용자가 원치 않으면 후속 plan 에서 opt-out toggle 추가 가능 (Risks 참조).

**Tech Stack:** Kotlin, Jetpack Compose, Room (NotificationRepository), Gradle unit tests, ADB visual verification.

---

## Product intent / assumptions

- **gap 원문 (notification-detail Known gaps)**: "**대상 알림 row 즉시 상태 변경 부재** (redesign 2026-04-22): legacy 4-버튼은 `NotificationRepository.updateNotification` 으로 해당 row 의 status/reasonTags 를 즉시 덮어써 Home/리스트에 즉시 반영했으나, redesign 이후 이 경로는 Rule+Category 만 persist 하고 row 자체는 건드리지 않는다 … 이 UX 후퇴를 후속 plan 이 명시적 '이 알림도 지금 재분류' CTA 로 보완할지 결정 필요." rules-feedback-loop Known gaps 에도 동일 의미 ("Detail '즉시 상태 변경' 부재") 가 등록.
- **결정 가정 (MVP — implementer 가 PR 본문에 명시)**: 경로 A/B 모두 row 자동 적용 (toggle 없음). 이유: legacy 4-버튼이 그랬고, 사용자가 시트에서 "이 분류로 옮긴다" 행위를 한 직후이므로 row 도 같이 옮기는 것이 멘탈모델에 부합. snackbar 카피도 "옮겼어요" / "만들었어요" 로 이미 row 변화를 시사한다.
- **결정 가정**: row 갱신 후 새 `status` 는 **새로 매핑된 Category 의 action** 을 그대로 따른다 — `CategoryAction.PRIORITY → NotificationStatusUi.PRIORITY`, `DIGEST → DIGEST`, `SILENT → SILENT`, `IGNORE → IGNORE`. 매핑 함수는 새 helper 의 단위 테스트로 고정. 이미 코드 베이스 어딘가에 `categoryAction → notificationStatus` 매퍼가 있으면 재사용 (implementer 확인 — 없으면 신규 생성).
- **결정 가정**: row 의 `reasonTags` 에 `"사용자 분류"` 또는 동등한 reason tag 를 추가해 사용자가 후일 Detail 에 다시 들어왔을 때 "왜 이렇게 처리됐나요?" chip 에서 manual 재분류 임을 식별할 수 있게 한다. 정확한 라벨은 implementer 가 기존 reason tag 컨벤션 (`사용자 규칙` / `사용자 복구` 등) 와 맞춰 한 단어로 결정 — `"사용자 분류"` 권장.
- **결정 필요 (Risks 에 open question 으로 적힘)**: IGNORE Category 로 옮긴 경우 row 가 즉시 IGNORE 상태가 되면 기본 뷰 (Home/Inbox) 에서 사라진다. 사용자가 Detail 에 머무르고 있을 때 row 가 사라지는 것의 UX (back 시 어디로 복귀? Detail 자체는 그대로 살아 있음) 는 implementer 가 검증.
- **결정 필요 (Risks)**: 같은 sender 의 **다른 row** 들도 같이 옮길 것인가? MVP scope 는 "지금 보고 있는 single row 만". 다른 row bulk reassign 은 후속 plan.
- **out of intent**: opt-out toggle ("이 분류로 옮기되 알림 row 는 그대로 두기") — 후속 plan 후보. tray 알림 재게시 (시스템 notification center 에 다시 띄우기) — 본 plan scope 외. Snackbar 카피 변경 — 이미 적합.

---

## Task 1: Add failing tests for categoryAction → notificationStatus mapper [IN PROGRESS via PR #377]

**Objective:** Category action 을 notification status 로 변환하는 helper 의 매핑 규칙을 unit test 로 고정. 기존 매퍼가 있는지 implementer 가 먼저 확인하고, 있으면 그 매퍼 사용 + 누락된 분기 테스트만 보강. 없으면 신규 작성.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/CategoryActionToNotificationStatusMapperTest.kt`
- 신규 (Task 2 에서 생성, 매퍼가 없는 경우): `app/src/main/java/com/smartnoti/app/domain/usecase/CategoryActionToNotificationStatusMapper.kt`

**Steps:**
1. 매핑 시나리오:
   - `PRIORITY → NotificationStatusUi.PRIORITY`
   - `DIGEST → NotificationStatusUi.DIGEST`
   - `SILENT → NotificationStatusUi.SILENT`
   - `IGNORE → NotificationStatusUi.IGNORE`
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.CategoryActionToNotificationStatusMapperTest"` → RED 확인.

## Task 2: Add failing tests for new "apply category action to current row" use case [SHIPPED via PR (Tasks 2-3 bundle)]

**Objective:** Use case 가 (a) status 를 mapper 에 따라 갱신, (b) reasonTags 에 `"사용자 분류"` (또는 implementer 가 결정한 라벨) 를 dedup 추가, (c) 다른 모든 필드 (title/body/sender 등) 는 보존 한다는 계약을 unit test 로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/ApplyCategoryActionToNotificationUseCaseTest.kt`
- 신규 (Task 3 에서 생성): `app/src/main/java/com/smartnoti/app/domain/usecase/ApplyCategoryActionToNotificationUseCase.kt`

**Steps:**
1. 시나리오:
   - 기존 `status=DIGEST` row + new `CategoryAction.PRIORITY` → 호출 후 stored row 의 status 가 `PRIORITY` 로 변경, `reasonTags` 에 `"사용자 분류"` 추가, 나머지 필드 동일.
   - 이미 `reasonTags` 에 `"사용자 분류"` 가 있으면 dedup (한 번만).
   - 기존 `reasonTags` 가 비어 있으면 `"사용자 분류"` 단독으로 set.
   - mapper 가 `IGNORE` 를 반환하는 케이스 → row status 가 `IGNORE` 로 변경 (Home/Inbox 에서 사라지는 것은 caller 책임 — use case 는 단순 write).
   - port 가 in-memory fake 라 `updateNotification(notification)` 호출이 expected entity 로 한 번 발생했는지 verify.
2. Use case 는 `Ports` 인터페이스 (Test 가 fake 주입) 를 가지며 production 은 `NotificationRepository.updateNotification` 에 위임.
3. RED 확인.

## Task 3: Implement mapper + use case [SHIPPED via PR (Tasks 2-3 bundle); mapper part already shipped via PR #377]

**Objective:** Tasks 1, 2 를 GREEN 으로.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/CategoryActionToNotificationStatusMapper.kt` (없는 경우)
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/ApplyCategoryActionToNotificationUseCase.kt`

**Steps:**
1. Mapper — `fun map(action: CategoryAction): NotificationStatusUi` 한 함수 + `when` exhaustive.
2. Use case — `suspend fun apply(notification: NotificationUiModel, action: CategoryAction)` 가 mapper 로 새 status 결정, `reasonTags` 에 `"사용자 분류"` dedup append (NotificationRepository 의 기존 `appendReasonTag` 패턴 참고), `notification.copy(status = newStatus, reasonTags = newTags)` → `ports.updateNotification(...)` 호출.
3. `Ports` 는 `suspend fun updateNotification(notification: NotificationUiModel)` 단일 메서드만 노출.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.*"` GREEN.

## Task 4: Wire use case into Detail "분류 변경" Path A (existing Category) [SHIPPED via PR #379]

**Objective:** 사용자가 시트에서 기존 Category 한 row 를 탭하면 Rule/Category write 직후 use case 가 호출되어 row 도 같이 옮겨진다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`
- (DI 또는 직접 instantiation 패턴에 따라) `app/src/main/java/com/smartnoti/app/SmartNotiApp.kt` 또는 해당 화면의 ViewModel/state holder 가 use case 인스턴스를 받도록 갱신.

**Steps:**
1. Detail 화면이 이미 `AssignNotificationToCategoryUseCase` 를 어디서 받고 있는지 grep — 동일 경로로 새 use case 도 주입.
2. `onAssignToExisting(categoryId)` 콜백 (또는 동등 핸들러) 에서:
   - 기존 `assignUseCase.assignToExisting(notification, categoryId)` 완료 대기.
   - 동일 코루틴에서 `categories.firstOrNull { it.id == categoryId }?.action` 으로 새 action 조회. null (race) 이면 row 갱신 skip + 기존 snackbar 만 진행.
   - non-null 이면 `applyUseCase.apply(notification, action)` 호출.
   - 그 다음 기존 `snackbarHostState.showSnackbar(...)` 진행.
3. snackbar 카피는 변경하지 않음 — `"<카테고리명> 분류로 옮겼어요"` 가 row 변경까지 자연스럽게 시사.

## Task 5: Wire use case into Detail "분류 변경" Path B (new Category) [SHIPPED via PR #379]

**Objective:** 사용자가 "+ 새 분류 만들기" 로 editor 에 들어가 저장 (`onSaved(Category)`) 한 시점에 Detail 이 동일 use case 를 호출.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`

**Steps:**
1. `CategoryEditorScreen.onSaved` (시그니처: `(Category) -> Unit`, 2026-04-25 plan 에서 확장됨) 콜백에서:
   - 신규 Category 의 `action` 으로 `applyUseCase.apply(notification, savedCategory.action)` 호출.
   - 그 다음 기존 snackbar `"새 분류 '<카테고리명>' 만들었어요"` 진행.
2. 사용자가 editor 에서 cancel 하면 `onSaved` 가 호출되지 않으므로 row 도 그대로 — 기존 snackbar 미등장 동작과 일관.

## Task 6: ADB visual verification — row 가 즉시 옮겨지는지

**Objective:** Path A / Path B / Path B-cancel / IGNORE Category Path A 4 케이스에서 row 의 즉시 상태 변경 (또는 미변경) 을 시각 확인.

**Files:** 코드 변경 없음 — 검증만.

**Steps:**
1. Build/install 후 baseline 알림 게시:
   ```
   adb shell cmd notification post -S bigtext -t 'DetailRowApply_0426' RowApply1 '이 알림도 지금 재분류 테스트'
   ```
2. **Path A (PRIORITY Category)**: Home/정리함 진입 → 카드 status badge 가 (예: `Digest`) 인지 확인 → 카드 탭 → Detail → "분류 변경" → 기존 PRIORITY Category (없으면 사전 생성) 한 row 탭 → 시트 dismiss + snackbar.
   - 뒤로가기로 list 복귀 → 같은 row 의 badge 가 `즉시 전달` (PRIORITY) 로 바뀌어 있고, Home Priority 섹션에 등장하는지 확인. uiautomator dump 인용.
3. **Path B (PRIORITY action 신규 Category)**: Detail 재진입 → "새 분류 만들기" → editor 에서 name 입력 + action=PRIORITY 저장 → snackbar.
   - 뒤로가기로 list 복귀 → 같은 row 의 badge 가 `즉시 전달` 로 변경 확인.
4. **Path B cancel**: Detail 재진입 → "새 분류 만들기" → editor 에서 cancel → snackbar 미등장 + row 상태 변경 없음 확인.
5. **Path A → IGNORE Category**: IGNORE action Category 생성 후 Path A 로 같은 row 할당 → row 가 기본 뷰에서 사라지는지 (Settings 의 무시된 알림 아카이브 토글 ON 시 archive 화면에 등장하는지) 확인.
6. 모든 결과를 PR 본문에 dump 인용 또는 캡처로 첨부.

## Task 7: Update notification-detail + rules-feedback-loop journeys

**Objective:** 두 journey 의 Known gaps annotate + Observable / Exit / Code pointers / Tests / Change log 동기화.

**Files:**
- `docs/journeys/notification-detail.md`
- `docs/journeys/rules-feedback-loop.md`

**Steps:**
1. `notification-detail.md` Known gaps:
   - "**대상 알림 row 즉시 상태 변경 부재** (redesign 2026-04-22) …" bullet 앞에 `(resolved <ship-date>, plan 2026-04-26-detail-reclassify-this-row-now)` prefix 추가. 본문 보존.
2. `rules-feedback-loop.md` Known gaps:
   - "Detail '즉시 상태 변경' 부재 …" bullet 앞에 동일 resolved prefix 추가. 본문 보존.
3. `notification-detail.md` Observable steps 6 ("재분류 효과는 **다음 tick** 에 관측됨 — 이 경로는 대상 알림 row 자체의 status / reasonTags 를 즉시 업데이트하지 않는다 …") 수정:
   - 새 동작 ("이 row 도 새 Category action 으로 즉시 갱신") 으로 본문 갱신. "후속 동일 sender 알림" 효과 부분은 Category-driven classifier 동작이라 그대로 유지.
4. Exit state 에서 `NotificationRepository: **대상 알림 row 는 이 경로에서 갱신되지 않는다**` 줄을 갱신: "대상 알림 row 의 `status` 가 매핑된 Category action 으로, `reasonTags` 에 `사용자 분류` 가 dedup 추가" 같은 의미로.
5. Code pointers 에 `domain/usecase/ApplyCategoryActionToNotificationUseCase` + `domain/usecase/CategoryActionToNotificationStatusMapper` 추가.
6. Tests 에 신규 테스트 두 건 추가.
7. Change log 에 본 PR 라인 append (날짜는 implementer 가 작업한 실제 UTC 날짜, 요약 + plan link + PR link).
8. `last-verified` 는 implementer 가 직접 ADB recipe 를 처음부터 끝까지 돌려 모든 Observable steps 를 재검증한 게 아니라면 그대로 둔다.

## Task 8: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 항목 포함:
  1. 채택한 reason tag 라벨 (`"사용자 분류"` 권장 — 다른 라벨로 결정한 경우 이유).
  2. 기존 `categoryAction → notificationStatus` 매퍼 재사용 여부 (있으면 경로, 없으면 신규 추가 사실).
  3. ADB 검증 결과 — Path A / Path B / Path B-cancel / IGNORE Path A 4 케이스 dump 인용.
  4. IGNORE 케이스에서 사용자가 Detail 에 머무를 때 row 사라짐의 UX 관찰 (back 동작 / archive 진입 가능 여부).
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/notification-detail.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- 신규 `CategoryActionToNotificationStatusMapper` (없는 경우) + `ApplyCategoryActionToNotificationUseCase` (둘 다 pure Kotlin) + 단위 테스트.
- `NotificationDetailScreen` 의 Path A / Path B 콜백 두 곳에 use case 호출 추가.
- 신규 reason tag (`"사용자 분류"`) dedup append (기존 `appendReasonTag` 패턴 따름).
- Journey 문서 두 건 동기화 (Observable / Exit / Code pointers / Tests / Known gaps annotate / Change log).

**Out:**
- "이 분류로 옮기되 알림 row 는 그대로 두기" opt-out toggle — 후속 plan 후보.
- 같은 sender 의 다른 row bulk reassign — 본 plan 은 single row 만.
- tray 알림 재게시 (시스템 notification center 에 다시 띄우기) — `hidden-inbox` Known gaps 의 "복구 = 시스템 알림 재게시" 기대와 별개 issue.
- Snackbar 카피 변경 — 이미 적합.
- `CategoryEditorScreen.onSaved` 시그니처 추가 변경 — 2026-04-25 plan 에서 이미 `(Category) -> Unit` 으로 확장됨, 본 plan 은 그대로 사용.
- IGNORE Category 로 옮긴 row 의 archive UX (이미 `ignored-archive.md` journey 에서 처리).
- legacy 4-버튼 path / `NotificationFeedbackPolicy.applyAction` 부활 — redesign 의도 유지, 본 plan 은 단일 CTA + use case 로만 보완.

---

## Risks / open questions

- **IGNORE Category Path A 의 UX**: row 가 즉시 IGNORE 가 되면 기본 뷰에서 사라진다. 사용자는 Detail 에 머무르고 있고 (`NotificationDetailScreen` 자체는 살아 있음), back 시 list 의 그 row 가 없어진 것을 발견. legacy 4-버튼 시절의 IGNORE 도 같은 방식이었음 (Plan `2026-04-21-ignore-tier-fourth-decision.md` 의 "되돌리기" 스낵바 + IgnoreConfirmationDialog 가 있었음). 본 plan MVP 는 IGNORE 케이스에 별도 confirmation dialog / undo snackbar 를 **추가하지 않는다** — 이미 사용자가 "이 분류 = IGNORE action" 인 Category 를 의도적으로 선택한 상황이기 때문. 단, 이 결정은 implementer 가 ADB 검증 후 PR 본문에 한 줄로 confirm. 사용자가 Detail 시트의 IGNORE Category row 를 의도치 않게 탭할 위험이 크다고 판단되면 후속 plan 에서 confirm dialog 추가 가능.
- **mapper 위치**: `CategoryActionToNotificationStatusMapper` 가 이미 어딘가에 존재할 수 있음 (`NotificationProcessingCoordinator` / classifier 가 Category action 을 status 로 풀어내는 분기에서 inline 일 가능성). implementer 가 grep 으로 먼저 확인 — 있으면 추출/재사용, 없으면 신규. 신규 시에도 분기는 4 case `when` 으로 단순.
- **race**: `assignToExisting` 직후 `categories` flow 가 아직 새 ruleIds 를 반영하지 않은 상태에서 `categories.firstOrNull { it.id == categoryId }?.action` 을 읽을 수 있음. 다행히 action 자체는 이번 write 로 바뀌지 않으므로 lookup 의 결과는 stable. categories 가 비어 있는 race (cold launch) 에서는 action=null → row 갱신 skip + snackbar 만. unit test 가 use case 의 null-action 분기를 명시적으로 다루지는 않음 (caller 책임).
- **reason tag 라벨**: `"사용자 분류"` 권장. 기존 컨벤션 (`사용자 규칙` from auto-rule, `사용자 복구` from `restoreSilentToPriorityByPackage`) 과 어휘 충돌 위험 없음. implementer 가 "사용자 재분류" 등으로 다듬어도 무방 — 핵심은 reason chip 에서 manual 임을 식별 가능하면 됨.
- **content signature**: `NotificationRepository.updateNotification(notification, contentSignature = null)` 의 두 번째 인자는 default null — repo 가 기존 row 의 signature 를 lookup 해 보존하므로 본 plan use case 는 null 그대로 호출하면 된다. (signature 재계산이 필요하지 않음 — 본문 변경이 아닌 status/reasonTags 변경.)
- **테스트 환경**: Path A 검증을 위해 기존 Category 가 최소 1개 필요. 빈 환경이면 시트 상단 리스트가 비어 Path A 자체를 테스트할 수 없음 — implementer 가 ADB 에서 사전 Category 생성 (또는 기존 분류 재사용) 후 검증.
- **legacy `NotificationFeedbackPolicy.applyAction` 재사용 여부**: 2026-04-22 redesign 에서 receiver 와 함께 정책 객체도 삭제됐는지 implementer 가 확인. 살아 있으면 본 plan 의 use case 가 같은 패턴을 흉내 (단순한 `NotificationRepository.updateNotification` write 로 충분 — policy 의 추가 로직 (per-action variant) 은 redesign 후 불필요).

---

## Related journey

- [notification-detail](../journeys/notification-detail.md) — Known gaps "**대상 알림 row 즉시 상태 변경 부재**" bullet 을 (resolved …) 로 마킹. Observable step 6 + Exit state 한 줄 갱신.
- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — Known gaps "Detail '즉시 상태 변경' 부재" bullet 을 동일 resolved 로 마킹. Observable / Trigger / Exit 는 변경하지 않음.
