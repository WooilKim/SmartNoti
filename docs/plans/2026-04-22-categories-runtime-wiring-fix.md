---
status: planned
created: 2026-04-22
---

# Categories Runtime Wiring Fix — Listener Injection + Feedback Upsert + Rule Delete Cascade

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Tests first — all three drifts break production classification today, so each fix starts with a failing test that pins the contract.

**Goal:** Categories-based classification actually runs in production. After this plan ships, (a) live notifications flowing through `SmartNotiNotificationListenerService` are classified using the user's real Category list, (b) Detail screen feedback buttons ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시") persist a Category whose `action` drives future classifications for the same sender/app, and (c) deleting a Rule removes its id from every Category's `ruleIds`, preventing orphan accumulation.

**Architecture:**
- **Drift 1 (Listener → Classifier injection):** `SmartNotiNotificationListenerService.processNotification(...)` reads `rulesRepository.currentRules()` but does not pass `categoriesRepository.currentCategories()` into `processor.process(...)`. After the P1 refactor, Rules are pure matchers (action field removed) so Classifier without Categories always falls back to the SILENT default. Fix: inject Categories alongside Rules at the same call site. Unit tests that stub `CategoriesRepository` pass today because the pathway being tested is synthetic — the real listener call site is not covered.
- **Drift 2 (Feedback → Category upsert):** `NotificationFeedbackPolicy.applyAction(...)` + `toRule(...)` produces a `RuleUiModel`, which `NotificationDetailViewModel` (or equivalent) upserts through `RulesRepository`. Post-P1, a new Rule with no Category pointing at it has no effect on classification. Fix: feedback must either (i) find a Category whose `ruleIds` already contain the matching rule id and update that Category's `action`, or (ii) create a new Category with `ruleIds = [newRule.id]`, `action = feedbackAction`, and sensible defaults for `name` / `appPackageName` / `order`.
- **Drift 3 (Rule deletion cascade):** `RulesRepository.deleteRule(ruleId)` only removes the Rule from its own DataStore key. Every Category that listed that rule id in `ruleIds` now holds a dangling reference. Fix: introduce `CategoriesRepository.onRuleDeleted(ruleId)` that rewrites each affected Category with the id stripped from `ruleIds`, and hook it into `RulesRepository.deleteRule` (either through a cross-repo coordinator or direct injection).

**Tech Stack:** Kotlin, DataStore, Jetpack Compose, Gradle unit tests, Claude Code implementation.

---

## Product intent / assumptions

- **Feedback upsert target — Category, not Rule alone:** Post-P1, Rules are pure matchers. A feedback tap must result in a Category mutation (create or update) so classification actually changes. The existing `toRule(...)` helper is still useful for generating the matcher; the new logic wraps it.
- **Category default shape from feedback:** `name` = sender-or-appName (same derivation as `toRule.title`), `appPackageName` = notification's package name when the matcher type is APP, `ruleIds` = `[generatedRule.id]`, `action` = mapped from `RuleActionUi`, `order` = `max(existing.order) + 1` (append to bottom so explicit user-ordered Categories still win specificity ties).
- **Update vs. create — prefer update:** If a Category already owns a Rule with the same `id` that `toRule(...)` would produce (deterministic by `"${type}:$matchValue"`), update that Category's `action` in place rather than creating a duplicate. This preserves user-chosen `order` and `name`.
- **Deletion cascade atomicity:** Rule deletion and Category cleanup don't need to be transactional across DataStore keys (there are two). The user-visible invariant is eventual — orphan ids never survive past the next classifier read. Cross-repo ordering: delete Rule first, then cascade, so an in-flight classifier read never sees a Category pointing at a non-existent Rule that still affects tier lookup.
- **Listener injection is not a behavior change, it's a bug fix:** The classifier pipeline already accepts Categories; the listener just forgets to pass them. No product question attached.
- **Out of scope — wizard / detail UI:** The feedback buttons' visual affordance ("중요로 고정" label, Category-picker sheet) is unchanged by this plan. Only the underlying persistence swap from Rule-only to Category-inclusive.

---

## Task 1: Failing tests for all three drifts [IN PROGRESS via PR #243]

**Objective:** Pin each drift as a red test before touching production code.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/notification/SmartNotiNotificationListenerServiceCategoryInjectionTest.kt` (or extend an existing listener test harness)
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationFeedbackPolicyCategoryUpsertTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/data/rules/RulesRepositoryDeleteCascadeTest.kt`

**Steps:**
1. **Listener injection test:** spin up the listener (or a thin testable seam around `processNotification`) with a fake `RulesRepository` returning one matching Rule and a fake `CategoriesRepository` returning one Category that wraps that Rule with `action = PRIORITY`. Capture the argument passed to `processor.process(...)`. Assert the Categories list is non-empty and contains the expected Category. Currently fails because the listener never reads `categoriesRepository.currentCategories()` at this call site.
2. **Feedback → Category upsert test:** feed `NotificationFeedbackPolicy` (or its wrapping use case) a notification + `RuleActionUi.ALWAYS_PRIORITY`. Assert that after the action, `CategoriesRepository.currentCategories()` contains a Category with `action == PRIORITY` and `ruleIds` containing the rule id returned by `toRule(...)`. Second sub-case: pre-seed a Category already owning that rule id with `action = DIGEST`; assert the Category's `action` is updated in place (no duplicate Category created).
3. **Rule delete cascade test:** pre-seed Rules `[r1, r2]` and Categories `[catA(ruleIds=[r1]), catB(ruleIds=[r1, r2])]`. Call `RulesRepository.deleteRule("r1")`. Assert Rules = `[r2]`, `catA.ruleIds == []`, `catB.ruleIds == [r2]`, no Category deleted (empty `ruleIds` is preserved — user sees a "rule-less" Category to edit, not silent disappearance).
4. `./gradlew :app:testDebugUnitTest` — all three tests red.

## Task 2: Wire listener to pass Categories + feedback policy upserts Category

**Objective:** Turn tests 1 and 2 green.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — read `categoriesRepository.currentCategories()` next to `rulesRepository.currentRules()` and pass both into `processor.process(...)`.
- 변경: `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationFeedbackPolicy.kt` — add new method (e.g. `applyActionToCategory(notification, action, existingCategories, existingRules) → FeedbackResult(rule, category)`) that returns both a Rule to upsert and a Category to upsert. Keep `applyAction(...)` and `toRule(...)` intact for call sites still using them.
- 변경: Detail screen viewmodel(s) that currently call `rulesRepository.upsertRule(...)` — route through the new path so Category write happens alongside Rule write. Locate via `Grep` for `NotificationFeedbackPolicy` usage.

**Steps:**
1. In the listener, add `val categories = categoriesRepository.currentCategories()` immediately after the existing `currentRules()` read. Pass both into the `processor.process(input, rules, categories, settings)` call signature. If the `process(...)` overload does not yet accept Categories, add the parameter (Classifier machinery was built to consume them in Phase P2).
2. In `NotificationFeedbackPolicy`, add `applyActionToCategory(...)`. Algorithm:
   - Derive the Rule using existing `toRule(...)`.
   - Look for a Category in `existingCategories` whose `ruleIds` contains `rule.id`. If found, return a copy with `action = action.toCategoryAction()` and keep `name` / `order` / `appPackageName` unchanged.
   - If not found, create a new Category: `id = "cat-from-rule-${rule.id}"`, `name = rule.title`, `appPackageName = notification.packageName.takeIf { rule.type == RuleTypeUi.APP }`, `ruleIds = listOf(rule.id)`, `action = action.toCategoryAction()`, `order = (existingCategories.maxOfOrNull { it.order } ?: -1) + 1`.
3. Update feedback call sites (Detail screen) to persist both Rule and Category through their repositories in the same coroutine scope — Rule first, then Category.
4. Rerun Task 1 tests → green.

## Task 3: Rule deletion cascade into Categories

**Objective:** Turn test 3 green.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/data/categories/CategoriesRepository.kt` — add `suspend fun onRuleDeleted(ruleId: String)` that reads `currentCategories()`, rewrites each Category with `ruleIds = ruleIds - ruleId`, and persists.
- 변경: `app/src/main/java/com/smartnoti/app/data/rules/RulesRepository.kt` — inject `CategoriesRepository` (or a narrow callback interface to avoid a direct cycle) and call `categoriesRepository.onRuleDeleted(ruleId)` after the existing `persist(...)` in `deleteRule(...)`.
- 변경: DI / construction sites (`SmartNotiApplication` or equivalent) — wire `CategoriesRepository` into `RulesRepository` constructor.

**Steps:**
1. Implement `CategoriesRepository.onRuleDeleted(ruleId)` — idempotent (calling twice with the same ruleId is a no-op after the first), preserves Category even when `ruleIds` becomes empty.
2. Modify `RulesRepository.deleteRule(ruleId)` to await the cascade after persist. Order is Rule-persist → Category-cascade so an observer reading during the window sees at worst a Category pointing at a deleted Rule (harmless — classifier resolves by id and skips misses) rather than a live Rule with no Category.
3. If injecting `CategoriesRepository` into `RulesRepository` creates a dependency cycle, introduce a minimal `RuleDeletionObserver` interface that `CategoriesRepository` implements; `RulesRepository` holds a list of observers wired at construction.
4. Rerun Task 1 cascade test → green. Run full suite.

## Task 4: Journey doc sync — remove the three Known-gap bullets

**Objective:** Close the loop — the journey docs that currently flag these drifts should reflect the fixed state. No Observable steps / Exit state changes; only Known gaps and Change log.

**Files:**
- 변경: `docs/journeys/notification-capture-classify.md` — remove Known-gap bullet about listener not injecting Categories; add Change log row with commit hash + summary.
- 변경: `docs/journeys/notification-detail.md` — remove Known-gap bullet about feedback creating Rule without Category; Change log row.
- 변경: `docs/journeys/rules-management.md` (or wherever the cascade gap was logged) — remove Known-gap bullet about orphan rule ids in Categories; Change log row.
- 변경: 이 plan `status: shipped` + `superseded-by:` 로 실제 영향받은 journey 중 대표 링크.

**Steps:**
1. For each journey file, locate the exact bullet added in PR #241. Delete it (keep surrounding gap bullets untouched).
2. Append a Change log entry: `YYYY-MM-DD — Categories runtime wiring fix shipped (commit <sha>): listener injects Categories, feedback upserts Category, Rule deletion cascades.`
3. Do not touch `last-verified` — that only moves when a real verification recipe runs.

---

## Scope

**In:**
- Listener call site injects `categoriesRepository.currentCategories()` into `processor.process(...)`.
- Feedback policy creates-or-updates a Category alongside the Rule, with deterministic id / default `order` / name.
- Rule deletion cascades into Categories to strip orphan ids.
- Unit tests for all three paths, failing first.
- Journey doc Known-gap removal + Change log.

**Out:**
- Category picker UI in Detail (user picking an existing Category instead of auto-create) — future plan.
- Category auto-merge when feedback would create a near-duplicate — future plan.
- Migration of pre-existing orphan ids in currently-deployed Categories — cascade only applies to future deletions. If existing users already have orphans, one-time cleanup at app start is a separate plan if verification shows it matters.
- Moving Rule/Category coordination into a single use case (architectural refactor) — this plan stays at the repository seam.

---

## Risks / open questions

- **Dependency direction RulesRepository ↔ CategoriesRepository:** Injecting `CategoriesRepository` into `RulesRepository` can induce a cycle if the reverse edge ever grows. The observer pattern (Task 3 step 3) is the fallback. Decision at implementation time — prefer direct injection unless a cycle appears.
- **Feedback upsert when sender is null:** `toRule(...)` uses `packageName` as `matchValue` when sender is blank. New Category's `appPackageName` is already set from notification. What if the user's existing Category list already has a DIFFERENT Category covering the same app with a different action? Current algorithm matches by `rule.id` (deterministic), so it won't collide — it will create a new Category. Acceptable for MVP; user can manually merge. Document in plan.
- **"Update in place" vs. "stack action":** If a Category exists with `action = DIGEST` and user taps "중요로 고정", the plan updates to PRIORITY. But what if that Category wraps 5 other Rules? The user might only mean "for this sender" — flipping all 5 is a side effect. Open question: should update-in-place be gated on `ruleIds.size == 1`, and create a new Category otherwise? Default in Task 2: always update-in-place (simpler, matches user's mental model of "I'm telling SmartNoti how to treat this sender"). Flag for verification after ship.
- **Orphan cleanup for existing deployments:** Users who already deleted Rules before this plan landed have orphan ids in Categories. Classifier ignores misses gracefully, but `CategoryDetailScreen` may show phantom rows. Verification recipe should check and, if needed, a follow-up plan adds one-time cleanup.
- **Listener test seam:** `SmartNotiNotificationListenerService` is an Android service — hard to unit-test directly. Task 1 step 1 may need a thin extract (e.g. `NotificationProcessingCoordinator`) to make the call site testable. If the extract is larger than ~20 lines, split into its own task.

---

## Related journeys

- [notification-capture-classify](../journeys/notification-capture-classify.md) — Drift 1 (listener injection) is the Classifier entry point described here.
- [notification-detail](../journeys/notification-detail.md) — Drift 2 (feedback buttons) is the Observable-step tail of this journey.
- [rules-management](../journeys/rules-management.md) — Drift 3 (deletion cascade) affects the Rule lifecycle contract recorded here.

Shipping this plan replaces the three Known-gap bullets added in PR #241 with Change log entries linking back to this plan.
