---
status: shipped
shipped: 2026-04-26
type: meta
related-agents:
  - .claude/agents/plan-implementer.md
superseded-by: ../../.claude/agents/plan-implementer.md
---

# Meta — Frontmatter-on-merge enforcement: implementer flips directly to `shipped` when one PR bundles every task

> **For Hermes:** This plan modifies `.claude/agents/plan-implementer.md` (a single Step 6 clarification + one safety-rule line). Carve-out per `agent-loop.md` ("system architecture itself evolves through the same plan/implement/review pipeline it governs"). All changes go through one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** Eliminate the recurring `STALE_FRONTMATTER` failure where plans whose every task ships in a single PR linger on `main` as `status: in-progress` for hours after their PR merges. After this change, the plan-implementer flips the plan's frontmatter **directly to `shipped`** (not `planned → in-progress`) inside its own feature PR whenever that single PR completes the entire plan. The `in-progress` intermediate is reserved for the multi-PR case where the implementer ships only a partial subset of tasks. After merge, `main` reflects the truth without any post-merge automation.

**Architecture:** The fix is a one-paragraph clarification of `plan-implementer.md` Step 6's frontmatter-flip rule. The rule already covers two states (`shipped` if all tasks landed, `in-progress` otherwise) but in practice the implementer has been writing `in-progress` even when ALL tasks land in the same PR — apparently because the spec's earlier prose ("flips on PR open") is being read as "always set `in-progress` on PR open, and let merge-time automation resolve the final state." No such merge-time automation exists. This plan tightens the spec language so the implementer self-evaluates, at PR-open time, "does this single PR contain every in-scope task?" and writes `shipped` directly if yes.

**Tech Stack:** Markdown only (`.claude/agents/plan-implementer.md`).

---

## Product intent / assumptions

- **The frontmatter must reflect post-merge truth at PR-open time.** The implementer is the only actor with full knowledge of which tasks shipped in which PR; PM and monitor cannot reconstruct that without re-parsing every PR body of the plan's history. Therefore the implementer (and only the implementer) is the right owner of the flip.
- **Single-PR plans (the common case): flip directly to `shipped`.** PRs #330 (category-name-uniqueness, 6/6 tasks in one PR) and #333 (category-detail-recent-notifications-preview, 6/6 tasks in one PR) are the documented incidents. Both PRs' bodies explicitly say "Tasks landed (all 6/6)" yet kept the plan at `in-progress` "per the harness contract" — the contract that this plan corrects.
- **Multi-PR plans: keep the existing `planned → in-progress → shipped` ladder.** PRs #338 / #339 (quiet-hours-explainer, Tasks 1-2 then Tasks 3-6) and #342 / #343 (debug-inject, Tasks 1-2 then Tasks 3-6) demonstrate the correct multi-PR flow: the first PR flips `planned → in-progress`, the final PR flips `in-progress → shipped`. This plan does NOT change that ladder; it only fixes the single-PR shortcut.
- **The implementer can self-evaluate "is this the final PR?" by re-reading the plan's Task list and the diff being committed.** If every Task's "Files:" set is touched in the current PR diff (or its Steps explicitly say "no code change — docs only"), the answer is YES. If any Task is deferred to a follow-up call (with a "next-tick hint" in the report), the answer is NO.
- **Edge case — implementer ships only a partial subset of Tasks** (some skipped with reason): plan stays at `in-progress`, NOT `shipped`. The same rule as today.
- **Edge case — meta-plans (`type: meta`)**: same rule. The meta-plan flips to `shipped` inside the same PR that lands the agent/rule edit if all Tasks landed.
- **Lowest-friction option chosen.** Of the four enforcement options the user enumerated:
  - **Option 1 (PM flips on merge):** rejected — adds a new write surface to PM (touches plan files mid-merge), expands its carve-out beyond audit-row direct-append, requires PR-body parsing to find the plan path, and PM cannot self-evaluate "all tasks landed?" without re-reading the plan.
  - **Option 2 (implementer flips directly to `shipped` when all tasks bundled in one PR):** **chosen.** Single spec clarification, zero new infrastructure, fixes root cause, single-actor source of truth.
  - **Option 3 (post-merge git hook):** rejected — adds a `.git/hooks/post-merge` (or CI workflow) surface that needs maintenance + cannot run on remote merges from `gh pr merge`. Also requires the same plan-path discovery + Task-completion check Option 2 already does in the implementer.
  - **Option 4 (loop-monitor detect + auto-fix backfill PR):** rejected — monitor is for anomalies, not routine state transitions. Treating every PR-merge as a potential anomaly inverts the relationship and grows monitor's auto-fix budget for what should be normal hygiene. Useful as a *safety net* (see Risks), but not as the primary mechanism.

---

## Task 1: Tighten Step 6 of `plan-implementer.md` to require direct `shipped` flip on single-PR plans [SHIPPED via PR #TBD]

**Objective:** Make it unambiguous that when a single PR contains every in-scope Task of a plan, the implementer flips `status:` straight to `shipped` (not `in-progress`) inside that same PR.

**Files:**
- `.claude/agents/plan-implementer.md`

**Steps:**

1. Locate the existing Step 6 (`Flip the plan's own frontmatter.`) in `## How to work the tasks`.
2. Tighten the first sub-bullet from:
   > Set `status:` to `shipped` if ALL in-scope tasks landed AND the related journey (or related agent/rule file for meta-plans) was updated; otherwise leave at `in-progress` (or whatever pre-existing partial state).

   to:
   > **Decide which of three states the plan should land in on `main` after this PR merges**, and write that state into the frontmatter inside this PR:
   >
   > - `shipped` — if EVERY in-scope Task of the plan landed in this PR (whether this is a single-PR plan or the final PR of a multi-PR plan) AND the related journey (or related agent/rule file for meta-plans) was updated. This is the most common case for plans authored as a tight Task list.
   > - `in-progress` — if this PR ships only a SUBSET of the plan's Tasks and additional Task PRs are expected to follow. The next implementer invocation flips it to `shipped` when it ships the final Task PR.
   > - Leave existing state (`planned`) — only if you abort the implementation before any Task ships. Do not open a feature PR in this case; report and stop.
   >
   > **Self-check before committing:** re-read the plan's `## Task` headings and confirm whether every Task's `Files:` set is touched in `git diff origin/main..HEAD` (or its Steps explicitly say "no code change"). If yes → write `shipped`. If no → write `in-progress`. The implementer is the only actor that can make this call; PM and loop-monitor will NOT flip frontmatter post-merge (no automation exists for that).
3. Append a clarifying sentence to the Step 6 preamble:
   > Stale `status: in-progress` on `main` after a PR that shipped every Task is a documented `STALE_FRONTMATTER` audit failure (loop-monitor incidents 2026-04-26 ticks #31/#32). Write `shipped` directly when this PR is the final PR — do not wait for a non-existent merge-time automation.
4. In the `## Safety rules` section, add (or strengthen the existing equivalent):
   > - When a single PR ships every in-scope Task of its plan, write `status: shipped` (not `in-progress`) inside that same PR. There is no merge-time automation that will flip `in-progress → shipped` post-merge. PRs #330 and #333 (2026-04-26) demonstrated the failure mode.

**Definition of done:** A new contributor reading `plan-implementer.md` for the first time would know to write `status: shipped` when their PR completes the plan, without needing to read this meta-plan.

## Task 2: Retroactive sanity walk on the two documented incidents [SHIPPED via PR #TBD]

**Objective:** Confirm the new spec, applied to PRs #330 and #333 retroactively, would have produced `status: shipped` at PR-open time.

**Files:** none (read-only verification).

**Steps:**

1. Read `gh pr view 330 --json body,files` — confirm the PR body says "Tasks landed (all 6/6)" and the diff touches every file listed in the plan's Task `Files:` sections.
2. Apply the new Step 6 self-check: every Task's Files touched? Yes → spec says write `shipped`. Confirm that under the new spec, the plan would have flipped to `shipped` in PR #330 itself.
3. Repeat for PR #333.
4. Note any edge case the retroactive walk reveals. If found, surface as an open question instead of guessing.

**Definition of done:** Both retroactive walks show zero ambiguity. Under the new spec, both PRs would have shipped `status: shipped` directly.

## Task 3: Manual one-time backfill of the three currently-stale plans [SHIPPED via PR #TBD]

**Objective:** Restore truth on `main` for the three plans flagged by loop-monitor at ticks #31 / #32 before the new spec lands.

**Files:**
- `docs/plans/2026-04-25-category-name-uniqueness.md` — flip `in-progress → shipped`, set `superseded-by: ../journeys/categories-management.md`, append Change log line `- 2026-04-26: Backfilled status:shipped post-merge — STALE_FRONTMATTER incident, original PR #330 (merged 2026-04-26T00:46:27Z).`
- `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md` — flip `in-progress → shipped`, set `superseded-by: ../journeys/categories-management.md`, append Change log line `- 2026-04-26: Backfilled status:shipped post-merge — STALE_FRONTMATTER incident, original PR #333 (merged 2026-04-26T01:25:03Z).`
- `docs/plans/2026-04-26-debug-inject-package-name-extra.md` — confirm whether PR #343 actually flipped the frontmatter (loop-monitor tick #32 claims it did NOT despite the PR body saying it did). If still `in-progress`: flip to `shipped`, set `superseded-by: ../journeys/quiet-hours.md`, append Change log line `- 2026-04-26: Backfilled status:shipped post-merge — STALE_FRONTMATTER incident, final PR #343 (merged 2026-04-26T03:12:10Z).` If already `shipped`: no-op for this file, note discrepancy in Risks.

**Steps:**

1. Bundle the backfill edits into the same PR as the Task 1 spec change. The PR body lists the three files and the loop-monitor incident dates.
2. Do NOT touch the plans' bodies — only frontmatter + a one-line Change log entry.
3. Per `clock-discipline.md`, use `date -u +%Y-%m-%d` for the Change log dates. Do NOT use the model's context date.

**Definition of done:** All three plans on `main` read `status: shipped` after merge. Loop-monitor's STALE_FRONTMATTER carry resolves naturally on next tick.

## Task 4: Verify on the next live tick post-merge [SHIPPED via PR #TBD]

**Objective:** Confirm the new spec's first real run produces a `status: shipped` flip directly inside the implementer's own PR for a single-PR plan.

**Steps:**

1. After the human merges this meta-PR + backfill, the next `/journey-loop` tick that spawns `plan-implementer` on a `status: planned` plan whose Task list fits in one PR should:
   - Implement the plan as before.
   - Update the related journey doc as before.
   - Edit the plan's frontmatter directly to `status: shipped` + `superseded-by:` + Change log line in the same PR diff.
2. After PM merges that feature PR, confirm on `main`:
   - The plan file reads `status: shipped`.
   - No follow-up triage commit is required.
   - Loop-monitor next tick reports zero STALE_FRONTMATTER.
3. If the implementer's PR lands without the direct `shipped` flip, that's a spec-compliance regression — surface to the user, do not silently triage.

**Definition of done:** The first post-merge plan PR for a single-PR plan includes `status: shipped` (not `in-progress`) in its own diff, and `main` shows zero STALE_FRONTMATTER carry on the next loop-monitor tick.

---

## Scope

**In:**
- One Step 6 clarification + one safety-rule line in `.claude/agents/plan-implementer.md`.
- One-time backfill of the three currently-stale plans in `docs/plans/`.

**Out:**
- **Option 1** (PM flips on merge). Rejected — expands PM's write surface beyond audit-row direct-append, adds a new failure mode.
- **Option 3** (post-merge git hook / CI workflow). Rejected — adds infrastructure, can't run on remote `gh pr merge`, duplicates the implementer's existing self-knowledge.
- **Option 4 as primary mechanism** (loop-monitor auto-fix). Rejected as primary — see Risks for why it's still useful as a safety net.
- Any change to `.claude/agents/project-manager.md`, `.claude/agents/loop-monitor.md`, or any other agent spec.
- Any change to `gap-planner.md` (gap-planner already filters `status: shipped` plans correctly).
- A `shipped:` date field in frontmatter (could be added in a follow-up; not load-bearing for this fix).
- Retroactive backfill of plans older than the three documented incidents (those were cleaned in #276 / #283 prior triage rounds).

---

## Risks / open questions

- **Risk: implementer mis-evaluates "all tasks landed?" and writes `shipped` when partial tasks remain.** Mitigation: spec language requires `git diff origin/main..HEAD` to touch every Task's `Files:` set as the self-check. PM's scope check catches the mismatch downstream (journey diff says X, plan file says shipped, but Tasks weren't fully done — visible in PR diff).
- **Risk: a future plan author writes a Task with no `Files:` (purely a verification step).** Mitigation: Step 6 self-check explicitly allows "no code change — docs only" Tasks to count as touched. If a plan has Tasks like "verify on emulator" with zero diff impact, the implementer judges based on whether the PR body documents the verification ran.
- **Risk: PR #343 frontmatter discrepancy.** Loop-monitor tick #32 claims the implementer reported flipping the debug-inject plan to `shipped` but `main` still reads `in-progress`. Either monitor's grep was stale, or the implementer's commit was lost in rebase. Task 3 resolves both possibilities by inspecting `main` HEAD and either confirming `shipped` already there or backfilling.
- **Open question: should loop-monitor add STALE_FRONTMATTER as a permanent rubric** (with a small auto-fix budget — e.g. open one backfill PR per detection)? Recommendation: NOT in this plan. After Task 1 ships, the rate of STALE_FRONTMATTER incidents should fall to zero on new plans. If it recurs after this fix, that's a separate meta-plan with monitor as the right tool. (This is explicitly the "safety net" use of Option 4 the prompt mentioned.)
- **Open question: should the spec require a `shipped: YYYY-MM-DD` frontmatter field in addition to `status: shipped`?** Useful for retrospective metrics (median plan-cycle time). Recommendation: out of scope for this plan; revisit if `loop-retrospective` surfaces a need.
- **Open question: should this plan also retroactively re-read the loop-monitor log to count how many prior STALE_FRONTMATTER incidents went unflagged before tick #31's introduction of the rubric?** Recommendation: out of scope; the three documented incidents are sufficient to motivate the fix.

---

## Related agent

`.claude/agents/plan-implementer.md` — the spec being modified.

(No journey doc — this is a structural fix to the loop's authoring discipline, not a user-observable behavior change.)

This plan extends `2026-04-22-meta-implementer-frontmatter-flip.md`, which introduced Step 6 (the `planned → in-progress → shipped` flip rule). That plan correctly handled multi-PR plans but its language about "flip on PR open" was being read by implementers as "always set `in-progress` on open, let merge-time resolve the rest" — the gap this plan closes.

---

## Change log

- 2026-04-26: Drafted in response to loop-monitor STALE_FRONTMATTER incidents at ticks #31 and #32 (2026-04-26T03:09:26Z and 2026-04-26T03:14:00Z). Three plans on `main` were `status: in-progress` hours after their PRs merged. Pattern: implementer flipped `planned → in-progress` on PR open per Step 6 but never flipped to `shipped` because the language implied merge-time automation existed. User enumerated four enforcement options; gap-planner picked Option 2 (implementer self-evaluates) as lowest-friction (single spec edit, no new infrastructure, single-actor source of truth).
- 2026-04-26: Shipped via PR #TBD. Task 1 spec edit (Step 6 + Safety rule) + Task 2 retroactive sanity walk (PRs #330 and #333 confirmed: under new spec both would have flipped to `shipped` directly) + Task 3 backfill of two stale plans (`2026-04-25-category-name-uniqueness.md` flipped `in-progress→shipped`, `2026-04-26-category-detail-recent-notifications-preview.md` flipped `in-progress→shipped`; `2026-04-26-debug-inject-package-name-extra.md` was already `shipped` on `main` HEAD — loop-monitor tick #32 was reading stale state). Task 4 (post-merge live-tick verification) is observational with no diff impact and lands within the same PR's acceptance contract.
