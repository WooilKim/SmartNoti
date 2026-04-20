---
name: journey-loop
description: One tick of the improvement loop. Runs journey-tester first; on NO-OP falls through to gap-planner (draft new plan from Known gaps), then plan-implementer (implement oldest status:planned). Self-schedules via ScheduleWakeup. Start with one call; it self-perpetuates.
---

Execute one iteration of the improvement Ralph loop.

The loop is a **fall-through chain**, one subagent per tick:

1. `journey-tester` — verifies journey docs against current behavior. Self-merges docs-only updates under its own 4-gate rule.
2. On tester NO-OP → `gap-planner` — drafts ONE plan file from the highest-leverage Known gap across `docs/journeys/*.md`. Opens a PR for the new plan; does NOT self-merge (plans are human-gated until `project-manager` approves).
3. On planner NO-OP (no Known gap leverage-worthy OR a planned file already covers it) → `plan-implementer` — picks the oldest `status: planned` file under `docs/plans/` that hasn't been implemented yet (no matching shipped journey change). Opens a code PR; does NOT self-merge (code goes through `project-manager` review + merge, per project-manager spec).
4. If all three no-op, the loop is truly idle — still self-schedule so drift surfacing kicks in when code lands.

Rationale: prior version ran only `journey-tester`. Observed consequence: after the verification queue saturated (all journeys same-day `last-verified`), the loop no-op'd indefinitely while Known gaps accumulated and `status: planned` plans sat untouched. The fall-through keeps plan generation and implementation moving without removing human review at merge time.

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

### Step 4 — truly idle

If all three return NO-OP, the final report says "all three agents idle; nothing queued." Still self-schedule (state may change externally).

## Self-schedule

After a successful tick (any of: tester PASS/DRIFT/SKIP; gap-planner drafted a plan; plan-implementer opened a code PR; or all three returned NO-OP), call `ScheduleWakeup` with:
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
 ├─ Step 1 — tester: <PASS | DRIFT | SKIP | NO-OP | error>
 │    └─ Journey: <id or "n/a"> · PR: <url or "n/a">
 ├─ Step 2 — gap-planner: <drafted | NO-OP | skipped | error>
 │    └─ Plan: <path or "n/a"> · PR: <url or "n/a">
 ├─ Step 3 — plan-implementer: <opened PR | NO-OP | skipped | error>
 │    └─ Plan: <path or "n/a"> · PR: <url or "n/a">
 └─ Next tick: <scheduled for +<interval> | PAUSED — reason>
```

Keep the message under 150 words.

## Safety rules

- Never bypass the stop-first check, even "just this once." The check is the entire purpose of wrapping the loop.
- Fall-through order is fixed: tester → gap-planner → plan-implementer. Never call them in a different order or invoke any two in the same tick.
- Never self-merge `plan-implementer` or `gap-planner` PRs from this command. Those go to `project-manager` (per `.claude/agents/project-manager.md`). Only `journey-tester` has its own narrow self-merge carve-out (docs-only journey changes).
- Never push to `main`, never merge PRs directly from this wrapper.
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
