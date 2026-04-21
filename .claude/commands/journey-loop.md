---
name: journey-loop
description: One tick of the improvement loop. Phase A chains tester → gap-planner → plan-implementer (each fall-through on NO-OP). Phase B always runs project-manager to drain the open-PR queue (review + merge approved code PRs). Self-schedules via ScheduleWakeup.
---

Execute one iteration of the improvement Ralph loop.

The loop has two phases per tick:

**Phase A — Production** (fall-through chain, one productive subagent per tick):
1. `journey-tester` — verifies journey docs against current behavior. Self-merges docs-only updates under its own 4-gate rule.
2. On tester NO-OP → `gap-planner` — drafts ONE plan file from the highest-leverage Known gap. Opens plan PR; no self-merge.
3. On planner NO-OP → `plan-implementer` — implements oldest `status: planned` file. Opens code PR; no self-merge.

**Phase B — Drainage** (always runs, regardless of Phase A outcome, even on full NO-OP):
4. `project-manager` — sweeps every open agent-origin PR, posts comment-verdict, merges PRs that pass its checklist. Defers carve-outs (Room schema, `.claude/`, `.github/`, Gradle wrapper) to human.

Rationale: prior version had no merger. Observed: after Phase A opens a PR (e.g. plan-implementer opens a code PR), it sat for hours until the human invoked `/pr-review` manually. Phase B closes the loop — agent PRs flow tester → planner → implementer → PM merge → next tick consumes the new state. Single-account env (all agents = same gh user) means PM uses comment-verdict + direct `gh pr merge` instead of `gh pr review --approve` (GitHub blocks self-author approve); see `.claude/agents/project-manager.md`.

## Stop-first check (always before spawning the tester)

1. List open agent-origin PRs:
   ```bash
   gh pr list --state open --json number,title,headRefName,createdAt \
     --jq '.[] | select(.headRefName | startswith("docs/journey-verify-"))'
   ```
2. Count the result.
3. **If count ≥ 2**: the loop is paused. Do NOT spawn the tester. Do NOT call `ScheduleWakeup`. Report:
   > `journey-loop paused — N PRs awaiting review (branches: ...). Merge or close them, then run /journey-loop to resume.`
   Then stop.

Rationale: agent-origin PRs accumulating means human review is the bottleneck. Piling on more PRs would only make the backlog worse. The loop must yield back to the human.

## Tick

If the stop-first check passed (< 2 pending PRs):

1. Spawn the `journey-tester` subagent via the Agent tool with `subagent_type: journey-tester` and prompt:
   > `Run one verification cycle. Pick the journey with the oldest last-verified date (ties broken by id). Follow your agent definition exactly — open a PR only if you updated a journey doc, no code changes. Report back PASS / DRIFT / SKIP.`
2. Wait for the subagent's report.
3. Capture the report for the final message — do not modify it.

If the subagent itself produced an error (permission denied, emulator not reachable, etc.), treat this tick as a failed tick: do NOT self-schedule, and report the error to the user with the exact message from the subagent.

## Fall-through chain (on NO-OP)

A subagent returns **NO-OP** when it honestly has nothing to do — not an error, just empty work. Treat the tick as "continue down the chain" instead of ending early.

### Step 2 — `gap-planner` (when tester NO-OPs)

Spawn with `subagent_type: gap-planner` and prompt:

> `The loop fell through to you because journey-tester had nothing to verify. Scan Known gaps in docs/journeys/*.md and docs/coverage-log.md and docs/pr-review-log.md for the single highest-leverage gap. Draft ONE plan file under docs/plans/YYYY-MM-DD-<slug>.md with status: planned, following your agent spec. Open a PR — do NOT self-merge. If no gap warrants a new plan (all already covered by queued plans), return NO-OP with the rationale.`

Safety:
- Gap-planner opens at most one plan PR per tick.
- Never self-merges.
- If it hits the "no worthwhile gap" condition, return NO-OP to trigger step 3.

### Step 3 — `plan-implementer` (when planner NO-OPs)

Spawn with `subagent_type: plan-implementer` and prompt:

> `The loop fell through to you because tester and gap-planner both had nothing. Pick the oldest docs/plans/*.md with status: planned. Before starting: git log --all --grep=<slug> to confirm no implementation PR already exists. If one does, bump the plan frontmatter to status: shipped (trivial docs PR) and return NO-OP. Otherwise implement per the plan's Tasks section, tests-first. Open a normal PR — do NOT self-merge; project-manager handles code-PR merges per its spec.`

Safety:
- Implementer opens one PR per tick (code PR, not plan PR).
- Never self-merges. Project-manager (per `.claude/agents/project-manager.md`) is the merger for code PRs.
- If it can't resolve a plan's open question from the code, stop and report — don't guess.

### Step 4 — truly idle (Phase A)

If all three Phase A agents return NO-OP, Phase A produced nothing this tick. **Still proceed to Phase B** — there may be open PRs from prior ticks waiting on PM review/merge.

## Phase B — `project-manager` (always runs)

After Phase A finishes (productive or all-NO-OP), spawn `project-manager` with `subagent_type: project-manager` and prompt:

> `Sweep target=all. Review every open agent-origin PR per your spec. For PRs you would approve and that pass your full checklist (no carve-out), proceed to merge per the single-account env path (comment-verdict + gh pr merge). Defer carve-outs to human. Append docs/pr-review-log.md rows for every verdict. Report which PRs you merged, which you deferred, which you requested changes on.`

Phase B safety:
- PM never merges its own audit-row PRs (it opens them as `ops/pr-review-log-*` for human review).
- PM never merges carve-out PRs even if all other gates pass.
- PM never force-pushes, never merges with `--admin`, never bypasses CI.
- If PM errors out (gh auth, etc.), report and continue to self-schedule — Phase B failure does NOT block self-scheduling (Phase A may have produced useful work; missing a single drain isn't fatal).

## Self-schedule

After a successful tick (Phase A any outcome including NO-OP, plus Phase B drainage attempted — even if PM errored or had nothing to do), call `ScheduleWakeup` with:
- `delaySeconds: 21600` — 6 hours. Crosses the prompt-cache window so long-running state resets, but still fires multiple times per day.
- `prompt: "/journey-loop"` — re-enters this same command on wake.
- `reason: "Next journey-verify tick; previous run was <PASS|DRIFT|SKIP>."` — be specific so the user can read the wakeup log.

Skip the `ScheduleWakeup` call entirely if any of these is true:
- The stop-first check paused the loop.
- The subagent errored out.
- The user explicitly said "stop the journey loop" or "don't continue" in the current turn.

## Final report format

Return exactly this shape to the user:

```
journey-loop tick
 ├─ Open agent PRs before tick: <N>
 ├─ Phase A — production
 │    ├─ Step 1 tester: <PASS | DRIFT | SKIP | NO-OP | error> · PR: <url>
 │    ├─ Step 2 gap-planner: <drafted | NO-OP | skipped | error> · PR: <url>
 │    └─ Step 3 plan-implementer: <opened PR | NO-OP | skipped | error> · PR: <url>
 ├─ Phase B — drainage (PM)
 │    ├─ Reviewed: <N PRs>
 │    ├─ Merged: <list of #nums> · audit row PR: <url>
 │    └─ Deferred (carve-out / changes-requested): <list>
 └─ Next tick: <scheduled for +<interval> | PAUSED — reason>
```

Keep the message under 150 words.

## Safety rules

- Never bypass the stop-first check, even "just this once."
- Phase A order is fixed: tester → gap-planner → plan-implementer. Never call them in a different order or invoke any two in the same tick.
- Phase B (PM) ALWAYS runs after Phase A — never skip, even on full Phase A NO-OP. PM-skipped ticks let PR queue grow.
- This wrapper never pushes to `main` and never merges PRs directly. Merges happen via subagents' own self-merge rules: tester for docs-only journey changes, PM for approved code/plan PRs (per its spec).
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
