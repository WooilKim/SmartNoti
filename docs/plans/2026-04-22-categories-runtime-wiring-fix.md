---
status: shipped
created: 2026-04-22
updated: 2026-04-22
superseded-by: ../journeys/rules-feedback-loop.md
---

# Categories Runtime Wiring Fix — Detail "분류 변경" Redesign + Listener Injection + Rule Delete Cascade

> **For Hermes:** Use subagent-driven-development skill. This plan replaces the four Detail action buttons with a single "분류 변경" bottom-sheet flow (assign-to-existing OR create-new Category). Write tests first for each task; the UX change is the spine, and two remaining drifts (listener injection, rule-delete cascade) piggy-back.

**Goal:** A notification's tier is always determined by a Category the user owns. In Detail, users no longer pick an "action" (중요/Digest/조용/무시). Instead they tap a single "분류 변경" button, which opens a bottom sheet offering (a) "기존 분류에 포함" — pick from their existing Categories, or (b) "새 분류 만들기" — open CategoryEditor pre-filled with a rule matching this notification. Future notifications from the same sender/app flow through that Category. The live listener really uses the user's Categories (not just Rules) and deleting a Rule no longer leaves orphan ids in Categories.

**Architecture:**
- **UX redesign (new spine):** `NotificationDetailScreen` drops its four action buttons + their reducers. A new single-button CTA opens a `CategoryAssignBottomSheet` composable. Choosing an existing Category triggers a domain "assign to Category" use case that derives an auto-rule from the notification (sender → PERSON rule; otherwise APP rule), upserts the Rule, and appends the new Rule id to that Category's `ruleIds`. Choosing "새 분류 만들기" opens the existing `CategoryEditor` with pre-filled `name`, `appPackageName`, one pre-added rule, and an `action` picker (PRIORITY / DIGEST / SILENT / IGNORE).
- **Drift 1 (Listener → Classifier injection):** `SmartNotiNotificationListenerService.processNotification(...)` reads `rulesRepository.currentRules()` but does not pass `categoriesRepository.currentCategories()` into `processor.process(...)`. Post-P1, Rules are pure matchers, so the Classifier without Categories always falls back to the SILENT default. Fix: inject Categories alongside Rules at the same call site.
- **Drift 3 (Rule deletion cascade):** `RulesRepository.deleteRule(ruleId)` only removes the Rule from its own DataStore key. Every Category listing that id in `ruleIds` now holds a dangling reference. Fix: `CategoriesRepository.onRuleDeleted(ruleId)` that rewrites each affected Category with the id stripped, hooked into `RulesRepository.deleteRule`.
- **Drift 2 — now resolved by deletion:** The previous "feedback upserts Rule not Category" drift disappears with the UX redesign. `NotificationFeedbackPolicy.applyAction`, `toRule`, and `CategoryFeedbackResult` stubs are removed entirely along with their call sites.

**Tech Stack:** Kotlin, DataStore, Jetpack Compose (ModalBottomSheet), Gradle unit tests.

---

## Product intent / assumptions

- **One operator verb, not four:** The four-button grid ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시") conflates "which bucket" with "which Category". Replacing with a single "분류 변경" CTA frames the operation as *classification management* — the user is always telling SmartNoti *which Category this belongs to*, never emitting a bare action.
- **Rule derivation is deterministic:** For "기존 분류에 포함", the auto-rule is `PERSON` matching `sender` if sender is non-blank; otherwise `APP` matching `packageName`. Id is deterministic (`"${type}:$matchValue"`) so repeat assignments to the same Category are idempotent.
- **Existing Category unchanged otherwise:** Assigning to an existing Category only appends to `ruleIds` (deduped). `action`, `name`, `order`, `appPackageName` stay as the user set them. The chosen Category's action is what governs future classification.
- **New-Category wizard pre-fill:** `name` defaults to sender-or-appName; `appPackageName` to notification packageName; one pre-added rule (same derivation as above); `action` picker opens on PRIORITY but user can change. User can edit anything before saving.
- **Replacement notifications lose action buttons:** Today's replacement alert (silent-suppression replacement) currently carries the same four `addAction(...)` buttons. With the buttons deleted, the replacement alert becomes tap-only — tapping still opens Detail, which is where classification management now lives.
- **IgnoreConfirmationDialog + undo snackbar are collateral:** Both were introduced for the "무시" button's destructive-confirm path (Task 6a of the IGNORE plan already merged to main). With the button gone, both UI affordances are removed. The IGNORE action itself survives as an action the user can pick inside the new-Category wizard or an existing IGNORE-actioned Category.
- **α/β/γ multi-rule Category flip question retired:** That trilemma only existed because feedback was mutating an existing Category's action. The new flow never mutates an existing Category's action via feedback — it only appends rule ids. Question is moot.
- **Listener injection is a pure bug fix** — no product question attached.

---

## Task 1: Failing tests for the new flow + two retained drifts [IN PROGRESS via PR #245]

**Objective:** Pin the new assign-flow contracts and the two retained drifts as red tests before touching production code.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/AssignNotificationToCategoryUseCaseTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/ui/category/CreateCategoryFromNotificationPrefillTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/notification/SmartNotiNotificationListenerServiceCategoryInjectionTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/data/rules/RulesRepositoryDeleteCascadeTest.kt`

**Steps:**
1. **Assign-to-existing test (with sender):** given a notification with non-blank sender "Alice" from package `com.kakao.talk`, and an existing Category `catWork` with `action=PRIORITY`, calling `AssignNotificationToCategoryUseCase.assignToExisting(notification, catWork.id)` must: (a) upsert a Rule `{type=PERSON, matchValue="Alice", id="PERSON:Alice"}`, (b) update `catWork.ruleIds` to include `"PERSON:Alice"` (deduped), (c) leave `catWork.action`/`name`/`order` untouched. Then reclassify a second notification from the same sender through the pipeline and assert it resolves to `catWork`'s action.
2. **Assign-to-existing test (sender blank):** same as above but sender is blank → expect an `APP` rule with `matchValue=packageName` instead.
3. **Create-new prefill test:** given notification with sender "Bob" / package "com.foo", calling the "create new Category" entry point must produce a `CategoryEditor` initial state with `name="Bob"`, `appPackageName="com.foo"`, `ruleIds` containing one pre-added rule `PERSON:Bob`, and `action=PRIORITY` (default). After the user saves, assert Categories list includes the new Category and future notifications from "Bob" classify via it.
4. **Listener injection test:** spin up the listener (or a testable seam) with a fake `RulesRepository` and a fake `CategoriesRepository` holding one Category wrapping a matching Rule with `action=PRIORITY`. Capture the argument into `processor.process(...)`. Assert the Categories list is non-empty and contains the expected Category. Fails today because the listener doesn't read `categoriesRepository.currentCategories()` at this call site.
5. **Rule-delete cascade test:** pre-seed Rules `[r1, r2]` and Categories `[catA(ruleIds=[r1]), catB(ruleIds=[r1, r2])]`. Call `RulesRepository.deleteRule("r1")`. Assert Rules = `[r2]`, `catA.ruleIds == []`, `catB.ruleIds == [r2]`, and no Category is deleted (empty `ruleIds` preserved).
6. `./gradlew :app:testDebugUnitTest` — all five tests red.

## Task 2: Detail UI — remove 4 buttons, add "분류 변경" + bottom sheet [IN PROGRESS via PR #245]

**Objective:** Delete the 4-button grid and its reducers; introduce a single CTA that opens `CategoryAssignBottomSheet`.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/ui/notification/NotificationDetailScreen.kt` — remove the four action button composables + their click callbacks + state-handling plumbing.
- 신규: `app/src/main/java/com/smartnoti/app/ui/notification/CategoryAssignBottomSheet.kt` — ModalBottomSheet containing: section "기존 분류에 포함" (scrollable list of user's Categories with name + action chip), and CTA row "새 분류 만들기".
- 변경: `NotificationDetailViewModel` — replace action-apply state with `onOpenAssignSheet`, `onAssignToExisting(categoryId)`, `onCreateNewCategory()`. Remove state for action results / snackbar-undo.
- 변경: `app/src/main/java/com/smartnoti/app/ui/category/CategoryEditor*.kt` (locate via Grep) — accept an optional `prefill: CategoryEditorPrefill?` carrying `name`, `appPackageName`, `pendingRule`, `defaultAction`. When present, seed editor state on first composition.
- 변경: `AppNavHost.kt` — add a navigation path from Detail's "새 분류 만들기" into CategoryEditor with prefill args (serialize via saved-state or nav args), and a return path back to Detail after save.
- 삭제: `IgnoreConfirmationDialog` composable + the undo snackbar wiring added in Task 6a of the IGNORE plan. Grep for `IgnoreConfirmationDialog` and remove all references.

**Steps:**
1. Delete the four `Button`/`FilledTonalButton` composables and their `onClick` bindings from `NotificationDetailScreen`. Replace with one `Button(onClick = viewModel::onOpenAssignSheet) { Text("분류 변경") }`.
2. Build `CategoryAssignBottomSheet` — collect Categories list from viewmodel, render each row (name + action-color chip + current `ruleIds.size` small label). Tapping a row calls `onAssignToExisting(categoryId)` and dismisses. "새 분류 만들기" row navigates to editor with prefill.
3. Extend CategoryEditor to accept prefill. First-composition effect: if editor state is empty (new-Category flow) and prefill is non-null, seed `name`, `appPackageName`, insert pre-added rule into pending rule list, set `action` to `defaultAction`.
4. Delete `IgnoreConfirmationDialog.kt` + references. Delete undo-snackbar logic tied to the removed "무시" action.
5. Turn Task 1 prefill test green.

## Task 3: Domain — assign-to-Category use case + auto-rule derivation [IN PROGRESS via PR #245]

**Objective:** Turn Task 1 steps 1–3 green.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/AssignNotificationToCategoryUseCase.kt` — exposes `assignToExisting(notification, categoryId)` and `buildPrefillForNewCategory(notification): CategoryEditorPrefill`.
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationRuleDerivation.kt` (or inline helper) — `deriveAutoRule(notification) → RuleUiModel`: PERSON if sender non-blank, else APP; id = `"${type}:$matchValue"`; title = sender-or-appName.
- 변경: `CategoriesRepository.kt` — add `suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String)` that reads, dedupes, persists. Leaves other fields untouched.
- 변경: `NotificationDetailViewModel` — call the use case from `onAssignToExisting` and `onCreateNewCategory`.

**Steps:**
1. Implement `NotificationRuleDerivation.deriveAutoRule(...)`.
2. Implement `AssignNotificationToCategoryUseCase.assignToExisting`: derive rule, `rulesRepository.upsertRule(rule)`, `categoriesRepository.appendRuleIdToCategory(categoryId, rule.id)`. Dedupe via set conversion.
3. Implement `buildPrefillForNewCategory`: derive rule; return `CategoryEditorPrefill(name=rule.title, appPackageName=if(rule.type==APP) notification.packageName else null, pendingRule=rule, defaultAction=PRIORITY)`.
4. Wire viewmodel callbacks. Turn Task 1 tests 1–3 green.

## Task 4: Listener injection (drift #1) [IN PROGRESS via PR #245]

**Objective:** Turn Task 1 step 4 green.

**Files:**
- 변경: `SmartNotiNotificationListenerService.kt` — read `categoriesRepository.currentCategories()` next to `rulesRepository.currentRules()`; pass both into `processor.process(...)`.
- 변경: `processor.process(...)` signature if it does not yet accept Categories (Classifier machinery was built to consume them in P2).

**Steps:**
1. Add the categories read at the call site; pass into processor.
2. If the service is hard to unit-test directly, extract a thin `NotificationProcessingCoordinator` seam (≤ 20 LOC) so the test can target plain Kotlin. If larger, split into its own task.
3. Turn Task 1 step 4 green.

## Task 5: Rule-delete cascade (drift #3) [IN PROGRESS via PR #245]

**Objective:** Turn Task 1 step 5 green.

**Files:**
- 변경: `CategoriesRepository.kt` — add `suspend fun onRuleDeleted(ruleId: String)` that strips `ruleId` from every Category's `ruleIds` and persists. Empty `ruleIds` is preserved (user sees a "rule-less" Category to edit).
- 변경: `RulesRepository.kt` — after persisting the delete, invoke `categoriesRepository.onRuleDeleted(ruleId)`. Wire via direct injection; if that creates a cycle, fall back to a narrow `RuleDeletionObserver` interface.
- 변경: DI / construction sites — wire CategoriesRepository into RulesRepository.

**Steps:**
1. Implement `onRuleDeleted` — idempotent, preserves empty-ruleIds Categories.
2. Call it from `deleteRule` after persist; order = Rule-persist → Category-cascade.
3. If a cycle appears, add `RuleDeletionObserver`.
4. Turn Task 1 step 5 green.

## Task 6: Cleanup — delete dead code [IN PROGRESS via PR #245]

> **Collateral carve-out, 2026-04-22:** `NotificationFeedbackPolicy.applyAction`
> / `toRule` are still used by the live `PassthroughReviewReclassifyDispatcher`
> (Priority screen inline reclassify from `rules-ux-v2-inbox-restructure`
> Phase A Task 3). Deleting them was out of scope for this plan (not in the
> Out list, not in any other plan's Task list, and would have broken that
> feature). The methods stay; every Detail-side caller is removed. The
> `CategoryFeedbackResult` stub referenced in the plan never materialized in
> code, so there was nothing to delete there. `markSilentProcessed` +
> `PROCESSED_REASON_TAG` are preserved (used by `silent-archive-drift-fix`).


**Objective:** Remove every piece the redesign made obsolete. No behavior change; pure deletion.

**Files (delete or prune):**
- `NotificationFeedbackPolicy.applyAction(...)` + `toRule(...)` + any `CategoryFeedbackResult` stubs. If `NotificationFeedbackPolicy` has no remaining members, delete the whole file.
- `SmartNotiNotifier` — `ACTION_KEEP_PRIORITY` / `ACTION_KEEP_DIGEST` / `ACTION_KEEP_SILENT` / `ACTION_IGNORE` constants + every `NotificationCompat.Builder.addAction(...)` call that wires them. Keep notification construction otherwise intact.
- `SmartNotiNotificationActionReceiver` — delete the entire broadcast handler for those four actions. If the receiver has no remaining handlers, remove its `<receiver>` from `AndroidManifest.xml` and delete the class.
- `IgnoreConfirmationDialog` composable (Task 6a of IGNORE plan) + all call sites + the undo snackbar tied to it.

**Steps:**
1. Grep for each symbol; delete definitions and references atomically per symbol.
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — green.
3. Manual smoke: replacement alert from silent-suppression has no action buttons, only tap-to-open Detail.

## Task 7: Journey doc overhaul [IN PROGRESS via PR #TBD]

**Objective:** Sync journeys to the new flow and close the three Known-gap bullets.

**Files:**
- 변경: `docs/journeys/rules-feedback-loop.md` — rewrite the loop: Detail no longer emits actions; user picks a Category (existing) or creates one (editor). Observable steps, Exit state, Code pointers all updated.
- 변경: `docs/journeys/notification-detail.md` — button list updated to the single "분류 변경" CTA; bottom-sheet flow referenced; replacement-alert behavior documented (no action buttons).
- 변경: `docs/journeys/notification-capture-classify.md` — Known-gap bullet about listener not injecting Categories replaced with Change log row.
- 변경: `docs/journeys/rules-management.md` — Known-gap bullet about orphan rule ids replaced with Change log row.
- 변경: 이 plan `status: shipped` + `superseded-by:` 로 `rules-feedback-loop.md` 링크.

**Steps:**
1. Rewrite `rules-feedback-loop.md` Observable steps around the new "분류 변경" flow. Keep Goal/Exit state stable where the user-observable outcome (future notifications classify correctly) is unchanged.
2. In `notification-detail.md`, update the button enumeration; add a short section on `CategoryAssignBottomSheet`.
3. Append Change log rows (`YYYY-MM-DD — <summary> (commit <sha>)`) to all four journey docs.
4. Do not touch `last-verified` on any journey — that only moves when a real verification recipe runs.

---

## Scope

**In:**
- Delete the 4 Detail action buttons and every downstream code path (policy, notifier actions, broadcast receiver, ignore dialog, undo snackbar).
- New single "분류 변경" CTA → `CategoryAssignBottomSheet` with "기존 분류에 포함" + "새 분류 만들기".
- `AssignNotificationToCategoryUseCase` + auto-rule derivation.
- CategoryEditor prefill path from notification.
- Listener injects `currentCategories()` into classifier pipeline.
- Rule deletion cascades into Categories.
- Unit tests first, red before each implementation task.
- Journey doc overhaul (`rules-feedback-loop.md` rewritten; three Known-gap bullets resolved).

**Out:**
- Category auto-merge / smart suggestion ("this sender looks like your Work category").
- One-time migration cleanup of existing deployments' orphan rule ids (classifier ignores misses gracefully; defer to a follow-up plan if verification shows it matters).
- Multi-rule Category "flip action" semantics (α/β/γ trilemma — now moot).
- Moving Rule/Category coordination into a single domain aggregate — stays at the repository seam.

---

## Risks / open questions

- **Dependency direction RulesRepository ↔ CategoriesRepository:** direct injection is preferred; the `RuleDeletionObserver` interface is the fallback if a cycle appears.
- **(a) Replacement-alert UX after action buttons are gone — RESOLVED 2026-04-22:** action buttons on the replacement alert are **removed entirely**. Tap on the alert opens Detail, and Detail's "분류 변경" sheet is where the user reclassifies. No quick-action buttons on the replacement alert at all. Re-verify `notification-replacement-alert` journey recipe post-ship; the extra tap is accepted as an intentional trade.
- **Prefill serialization across navigation:** `CategoryEditorPrefill` must survive process death. Use saved-state handle or nav arguments keyed by a short-lived id in a one-shot `PrefillStore`. Implementation task decides which is cleaner for the current nav graph.
- **(b) CategoryEditor prefill default action — RESOLVED 2026-04-22:** the "새 분류 만들기" default action is **dynamic-opposite** relative to the current notification's Category action (if it has one). Mapping: current action DIGEST → default PRIORITY, SILENT → default PRIORITY, IGNORE → default PRIORITY, and current PRIORITY → default DIGEST. When the notification has no owning Category (classifier fell back to a default decision), use PRIORITY. The user can still change it in the editor.
- **Deterministic rule id collisions across users of the same Category:** two different notifications sharing the same sender/package will produce the same rule id. Assigning either to the same Category is idempotent (good). Assigning each to different Categories creates two Categories both listing the same rule id — classifier resolves by `order`, so user-set ordering governs. Documented expectation, not a bug.
- **Listener test seam:** `SmartNotiNotificationListenerService` is an Android service — hard to unit-test directly. A ≤ 20-LOC extract is fine; anything larger splits into its own task.
- **CategoryEditor prefill invariants:** if the user cancels the editor, no Rule should have been written. Use case must only persist on editor-save, not on open.

---

## Related journeys

- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — rewritten by this plan; the "분류 변경" flow is its new spine.
- [notification-detail](../journeys/notification-detail.md) — buttons section and sheet flow updated.
- [notification-capture-classify](../journeys/notification-capture-classify.md) — Drift 1 (listener injection) resolved.
- [rules-management](../journeys/rules-management.md) — Drift 3 (rule-delete cascade) resolved.

Shipping this plan replaces the two retained Known-gap bullets (drifts #1 and #3) with Change log entries, and retires drift #2 by deleting its code path entirely.

---

## Change log

- 2026-04-22: Plan drafted with 4-button detail + Categories auto-generation approach. Superseded same-day by #244 rewrite.
- 2026-04-22: Plan rewritten around "분류 변경" single-CTA + `CategoryAssignBottomSheet` redesign. Detail 4-button grid + IgnoreConfirmationDialog + undo snackbar removed; Tasks 1-6 scope set (#244, `a5325f7`).
- 2026-04-22: Tasks 1-6 shipped via PR #245 — Task 1 RED tests, Task 2 Detail UI redesign + bottom sheet + editor prefill, Task 3 `AssignNotificationToCategoryUseCase` + `NotificationRuleDerivation`, Task 4 Listener injection via `NotificationProcessingCoordinator` seam, Task 5 Rule-delete cascade via `CategoriesRepository.onRuleDeleted` + `RuleDeletedCascade` pure helper, Task 6 dead-code cleanup (`SmartNotiNotificationActionReceiver` + manifest `<receiver>` entry + `ACTION_*` constants + `IgnoreConfirmationDialog` composable + undo snackbar).
- 2026-04-22: Task 7 — journey doc overhaul (`rules-feedback-loop.md` rewritten around "분류 변경" spine, `notification-detail.md` CTA section updated + replacement alert note, `notification-capture-classify.md` listener injection drift resolved in Change log, `rules-management.md` rule-delete cascade Known-gap resolved in Change log, `ignored-archive.md` Detail "무시" 버튼 제거 반영). Plan frontmatter → `status: shipped` + `superseded-by: rules-feedback-loop.md`.
