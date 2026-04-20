---
name: journey-loop
description: One tick of the journey-verification Ralph loop — checks stop conditions, runs journey-tester if safe, falls back to plan-implementer on a queued plan when verification is saturated, self-schedules the next tick via ScheduleWakeup. Start the loop by calling this once; it self-perpetuates.
---

Execute one iteration of the journey-verification Ralph loop.

This command spawns `journey-tester` each tick. When the tester returns NO-OP because verification is saturated (all journeys share the same `last-verified` date and no drift surfaced), the loop **falls back** to `plan-implementer` on the oldest `status: planned` file under `docs/plans/` — see the "Plan-implementer fallback" section below.

Rationale: prior design kept `plan-implementer` fully manual because it writes code. Observed consequence: when all journeys share the same `last-verified` date, `journey-tester` correctly no-ops and the loop idles for hours with queued plan work sitting untouched. Auto-merge authorization + per-PR `coverage-guardian` + `project-manager` gates let us safely let the loop attempt code work after the verification queue saturates, without removing human review at merge time.

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

## Plan-implementer fallback (when tester no-ops)

If the tester returns a **NO-OP** outcome (no drift, no stale journey, nothing to verify), do **not** stop the tick. Instead:

1. List planned work:
   ```bash
   ls -t docs/plans/*.md | head -20
   ```
2. Pick the oldest file (by filename date) whose frontmatter has `status: planned` AND has not already been merged as implemented (search `git log --all --grep=<slug>` for existing implementation PRs).
3. If such a file exists, spawn the `plan-implementer` subagent via the Agent tool with `subagent_type: plan-implementer` and prompt:
   > `Implement the plan at <path>. Follow its Tasks section in order, tests-first. Open a normal PR for human review (don't self-merge). If the plan has open questions you can't resolve from the code, stop and report — don't guess.`
4. Wait for the subagent's report. Include its PR URL (if any) in the final message.

Fallback safety:
- Still one subagent per tick. If the tester ran + the implementer ran, that's still one *coverage* unit — the tester was just brief.
- Implementer PRs go through normal human review (plus `coverage-guardian` signal + `project-manager` gate). The loop does NOT self-merge implementer PRs, ever.
- If no `status: planned` file exists, truly no-op — report "verification saturated, no queued plans" and continue to self-schedule (the user may add a plan later).
- If the implementer errors (unresolved open question, build failure outside its scope, etc.), treat like tester error: do NOT self-schedule, report the error.

## Self-schedule

After a successful tick (subagent reported PASS / DRIFT / SKIP without error), call `ScheduleWakeup` with:
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
 ├─ Tester result: <PASS | DRIFT | SKIP | NO-OP | error>
 ├─ Journey: <id or "n/a">
 ├─ Tester PR: <url or "n/a">
 ├─ Fallback: <plan path + implementer PR url, or "not triggered">
 └─ Next tick: <scheduled for +6h | PAUSED — reason>
```

Keep the message under 150 words.

## Safety rules

- Never bypass the stop-first check, even "just this once." The check is the entire purpose of wrapping the tester in a loop.
- `plan-implementer` may run ONLY as the fallback path described above (tester NO-OP + at least one `status: planned` plan). Never call `gap-planner` from this command — planning requires deliberate user framing.
- Never self-merge `plan-implementer` PRs. Those changes touch production code and must go through human review plus `project-manager` verdict, regardless of CI status.
- Never push to `main`, never merge PRs directly from this wrapper. Auto-merge only happens through each subagent's own self-merge rules (tester's `docs/journeys/**` carve-out).
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
