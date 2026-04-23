---
status: planned
type: meta
related-agents:
  - .claude/agents/plan-implementer.md
---

# Meta — `plan-implementer` flips plan frontmatter as part of its own feature PR

> **For Hermes:** This plan modifies `plan-implementer.md` agent spec (one new Task + one allowed-to-change line + one safety-rule line). Carve-out per `agent-loop.md` ("system architecture itself evolves through the same plan/implement/review pipeline it governs"). All changes go through one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** Eliminate the recurring "plan frontmatter drift" failure where shipped plans linger as `status: planned` (or `status: in-progress`) on `main` until a downstream agent has to bulk-triage them. After this change, the plan-implementer flips the plan's frontmatter to `status: shipped` (with `superseded-by:` set to the related journey, when one exists) **inside its own feature PR** — so the moment that PR merges, `main` reflects the truth.

**Diagnosis (recurring 2026-04-22, 6 occurrences in one day):**
- The `plan-implementer` agent spec lists journey doc updates under "How to work the tasks" (Step 5) but never mentions touching the plan's own frontmatter. Plans therefore ship with their pre-implementation status intact.
- Two explicit triage commits hit `main` recently to clean this up:
  - `505babc` — "docs(plans): triage & flip shipped plans to status:shipped" (#276)
  - `18dd901` — "docs(plans): triage round 2 — flip in-progress drifts to shipped" (#283)
- Today (2026-04-22 tick batch): 6th recurrence. The pattern: implementer ships → PM merges feature PR → frontmatter still says `planned` → next-tick gap-planner / orchestrator sees stale "open plan" signal → either picks the stale plan again (wasted spawn) or has to triage before doing real work.
- The user-evaluated fix-path matrix (this tick):
  - **A — implementer flips its own plan**: smallest scope (one spec edit), zero new infrastructure, fixes root cause, no race risk (single PR atomically updates code + plan + journey).
  - B — PM merge-time helper auto-flips plan: requires new helper (parse PR body for plan path, edit file, append to merge commit), introduces a new failure mode (PM commits to `main` directly outside its current carve-out).
  - C — scheduled gap-planner triage sweep: ongoing maintenance burden + always lagging the actual ship moment.
- **Path A chosen** (user-recommended default this tick). It is the smallest scope, addresses the most-recurrent issue, and changes nothing about how PM, monitor, or gap-planner behave.

**Trade-off accepted:** Implementer's PR diff grows by one frontmatter edit (typically ~3 lines: `status:` flip + new `superseded-by:` line, occasionally a `shipped:` date if we choose to include it). Reviewers see one extra hunk in the plan file. This is strictly clarifying — the PR's truth surface now includes "this plan is done."

**Tech stack:** Markdown only (`.claude/agents/plan-implementer.md`).

---

## Product intent / assumptions

- **The plan file is part of the PR's contract.** Today the implementer touches code + journey but leaves the plan in a state that contradicts what the PR just did. Path A makes the plan a first-class output of the implementer, same as the journey.
- **`status: shipped` on a plan means "the journey it points at has been updated to reflect this plan's outcome."** That moment is exactly when the implementer finishes its journey-doc update task — there's no temporal gap to bridge.
- **`superseded-by:` is set to the journey path the implementer just updated**, mirroring the pattern in `2026-04-22-meta-inline-orchestration.md` (`superseded-by: ../../.claude/commands/journey-loop.md`) and `2026-04-22-meta-monitor-tester-self-merge-rubric-exclusion.md` (`superseded-by: ../../.claude/agents/loop-monitor.md`). Use the relative path that resolves from the plan file's directory.
- **Edge case — plan has no Related journey** (rare; mostly meta-plans whose `related-agents:` already points elsewhere): implementer still flips `status:` but leaves `superseded-by:` to whatever the plan author already set, or omits if absent. The frontmatter flip is the load-bearing change; `superseded-by:` is best-effort.
- **Edge case — implementer ships only a partial subset of Tasks** (some skipped with reason): plan stays at `status: in-progress`, NOT `shipped`. The flip only happens when ALL in-scope tasks landed AND the journey was updated. This matches today's `status: in-progress` semantic.
- **Edge case — meta-plans modifying `.claude/`**: same rule. The meta-plan itself flips to `shipped` inside the same PR that lands the agent/rule edit. The two existing meta-plans (#meta-inline-orchestration, #meta-monitor-rubric) were flipped this way manually after merge — Path A makes that automatic.
- The `coverage-guardian` and `project-manager` agents see the frontmatter flip as part of the PR diff. Coverage scoring is unaffected (the plan file is a doc, not prod code; never counted toward prod ratio). PM scope check is unaffected (the plan file is in scope of any PR that implements it).

---

## Task 1: Update `.claude/agents/plan-implementer.md` — add explicit frontmatter-flip step

**Objective:** Codify the frontmatter flip as a mandatory step in the implementer's task loop, alongside the existing journey doc update.

**Files:**
- `.claude/agents/plan-implementer.md`

**Steps:**

1. Locate the `## How to work the tasks` section (line 26 as of 2026-04-22).
2. After Step 5 (the "update journey docs" step), insert a new Step 6:
   > **Step 6 — Flip the plan's own frontmatter.** Before the final commit, edit the plan file you just implemented:
   > - Set `status:` to `shipped` if ALL in-scope tasks landed AND the related journey was updated; otherwise leave at `in-progress` (or whatever pre-existing partial state).
   > - Set `superseded-by:` to a relative path pointing at the related journey doc (e.g. `superseded-by: ../journeys/<journey-id>.md`). If the plan has no Related journey AND a `related-agents:` entry already exists, leave `superseded-by:` to whatever the author set or omit if absent.
   > - Bundle this edit into the same commit as the journey-doc update (Step 5), or as a small separate commit immediately after if Step 5 was already committed. The PR that implements a plan MUST land the plan's frontmatter flip — never punt to a follow-up triage.
3. Renumber the existing Step 6 ("Commit after every task...") to Step 7. Adjust any cross-references in the file.
4. In the `## What you're allowed to change` section, change the bullet about "Other plan files" — currently it reads "Other plan files." under the "must not touch" list. Add a clarifier:
   > - Other plan files (you may edit ONLY the plan you are implementing — specifically its frontmatter `status:` and `superseded-by:` fields per Step 6).
5. In the `## Safety rules` section, add a new bullet at the end:
   > - Never ship a plan PR without flipping the plan's `status:` to reflect the actual outcome (`shipped` if all tasks landed; `in-progress` if partially shipped). Stale `status: planned` after merge is a documented audit failure that triggers downstream cleanup work — not acceptable.

**Definition of done:** Reading `plan-implementer.md` makes it unambiguous that frontmatter flip is a mandatory step inside the same PR as the implementation. A new contributor reading the spec for the first time would not omit it.

## Task 2: Sanity-check by walking the most-recent shipped plan

**Objective:** Confirm the new spec, applied retroactively to a real recent PR, would have produced the right result.

**Files:** none (read-only verification).

**Steps:**

1. Pick a recent shipped plan whose status was triaged afterward — e.g. one of the entries from #276 or #283.
2. Identify the feature PR that shipped it. Read that PR's diff.
3. Confirm: under the new spec, the implementer would have included a `status: planned → shipped` + `superseded-by:` edit in that same PR. No ambiguity, no extra spawn needed.
4. Note any cases where the retroactive walk reveals an edge case the new spec doesn't handle. If found, surface as an open question instead of guessing.

**Definition of done:** The retroactive walk shows zero ambiguity for the sample plan. Any newly-discovered edge case is surfaced in the PR description or this plan's Risks section, not silently patched.

## Task 3: Verify on the next live tick (post-merge)

**Objective:** Confirm the new spec's first real run produces a frontmatter-flipped plan inside the implementer's own PR.

**Steps:**

1. After the human merges this meta-PR, the next `/journey-loop` tick that spawns `plan-implementer` (when there's a `status: planned` plan and no open implementation PR) should:
   - Implement the plan as before.
   - Update the related journey doc as before.
   - **NEW:** Edit the plan's frontmatter to `status: shipped` + `superseded-by:` in the same PR diff.
2. After PM merges that feature PR, confirm on `main`:
   - The plan file now reads `status: shipped`.
   - No follow-up triage commit is required.
3. If the implementer's PR lands without the frontmatter flip, that's a spec-compliance regression — surface to the user, do not silently triage.

**Definition of done:** The first post-merge plan PR includes the frontmatter flip in its own diff, and `main` shows zero stale `status: planned` for that plan.

---

## Scope

**In:**
- One Task addition + one renumber + two clarifying lines in `.claude/agents/plan-implementer.md`.

**Out:**
- Path B (PM merge-time helper that auto-flips). Rejected — adds new failure mode + violates PM's current "no direct main commits outside MP-1 audit append" carve-out.
- Path C (scheduled gap-planner triage sweep). Rejected — ongoing maintenance, lags the ship moment, doesn't fix root cause.
- Retroactive backfill of frontmatter on existing stale plans. The two recent triage commits (#276 + #283) already cleaned `main`; future implementer runs handle new plans correctly.
- Any change to `gap-planner.md` (gap-planner already handles `status: shipped` plans correctly — it filters them out when scanning for "active" plans).
- Any change to `project-manager.md` PR review rubric (PM doesn't currently check frontmatter freshness; it doesn't need to once implementer is the source of truth).
- A `shipped:` date field in frontmatter (could be added in a follow-up if useful for reporting, but not load-bearing for the drift fix).

---

## Risks / open questions

- **Risk: implementer mistakenly flips to `shipped` when partial tasks remain.** Mitigation: spec language explicitly says "ALL in-scope tasks landed AND the related journey was updated." If implementer misreads, PM's scope check catches the mismatch (journey diff says X, plan file says shipped, but Tasks weren't fully done — visible in PR diff).
- **Risk: merge conflict if a concurrent triage PR (like a hypothetical future #283 round 3) and an implementer PR both edit the same plan file.** Mitigation: this fix removes the need for triage PRs going forward, so the conflict surface shrinks to zero. Existing triage queue is empty (per this tick's report: "Plan queue is FULLY CLEAN").
- **Risk: `superseded-by:` path computed incorrectly** (relative-path arithmetic). Mitigation: the spec gives a concrete example and points at the two existing meta-plans for reference. Worst case: link is broken but `status: shipped` is still correct — the load-bearing field still flips.
- **Open question: should the spec also require the implementer to add a `Change log:` line at the bottom of the plan file** (e.g. "2026-04-22: Shipped via PR #NNN.")? Useful for traceability but adds yet another touch point. Recommendation: out of scope for this PR; revisit if traceability gaps surface in a future retrospective.
- **Open question: meta-plans (`type: meta`) carve-out.** This meta-plan itself is `type: meta`. Should the implementer treat meta-plans differently — e.g. `superseded-by:` points at the agent file, not a journey? The two existing precedents (`2026-04-22-meta-inline-orchestration.md` superseded-by `journey-loop.md`, `2026-04-22-meta-monitor-tester-self-merge-rubric-exclusion.md` superseded-by `loop-monitor.md`) confirm the pattern is "point at whatever the meta-plan modified." The Step 6 spec language says "related journey" but should be widened to "related journey OR related agent/rule file (use the existing `superseded-by:` author set if any, else the file the plan modified)." Captured in Task 1 Step 2 above.

---

## Related agent

`.claude/agents/plan-implementer.md` — the spec being modified.

(No journey doc — this is a structural fix to the loop's authoring discipline, not a user-observable behavior change.)

---

## Change log

- 2026-04-22: Drafted in response to 6th-recurrence FRONTMATTER_DRIFT_RECURRING per `loop-orchestrator` Phase A report. User pre-recommended path A (smallest scope, fixes most-recurrent issue) and gap-planner concurred after reviewing the three candidate fix-paths. Two prior triage rounds (#276, #283) demonstrated the bulk-cleanup cost being externalized to gap-planner.
