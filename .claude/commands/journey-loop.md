---
name: journey-loop
description: One tick of the journey-verification Ralph loop — checks stop conditions, runs journey-tester if safe, self-schedules the next tick via ScheduleWakeup. Start the loop by calling this once; it self-perpetuates.
---

Execute one iteration of the journey-verification Ralph loop.

This command is the safe-to-automate half of the three-agent system (`.claude/rules/agent-loop.md`). Only `journey-tester` runs here — `gap-planner` and `plan-implementer` stay manual because they write plans or code and need human review before each execution.

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
 ├─ Tester result: <PASS | DRIFT | SKIP | error>
 ├─ Journey: <id or "n/a">
 ├─ PR: <url or "n/a">
 └─ Next tick: <scheduled for +6h | PAUSED — reason>
```

Keep the message under 150 words.

## Safety rules

- Never bypass the stop-first check, even "just this once." The check is the entire purpose of wrapping the tester in a loop.
- Never call `gap-planner` or `plan-implementer` from this command. If the tester reports DRIFT and the user wants a plan, they invoke `/gap-plan` manually.
- Never push to `main`, never merge PRs — the subagent is already constrained, keep the wrapper aligned.
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
