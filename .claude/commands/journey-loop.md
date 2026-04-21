---
name: journey-loop
description: One tick of the SmartNoti improvement loop. Runs stop-first check, then delegates to the loop-orchestrator subagent which makes all per-tick decisions and returns one summary. Self-schedules via ScheduleWakeup.
---

Execute one iteration of the improvement Ralph loop.

## Tick

1. **Stop-first check** (cheap, in-wrapper):
   ```bash
   gh pr list --state open --json number,title,headRefName \
     --jq '[.[] | select(.headRefName | startswith("docs/journey-verify-"))] | length'
   ```
   If count ≥ 2: report
   > `journey-loop paused — N verify PRs awaiting review. Merge or close them, then run /journey-loop to resume.`
   Then stop. Do NOT spawn the orchestrator. Do NOT call `ScheduleWakeup`.

2. **Spawn `loop-orchestrator`** subagent with `subagent_type: loop-orchestrator` and an empty prompt (it operates on `target=all` by default).

3. **Wait for the orchestrator's report.** The orchestrator does the entire tick — Phase A (tester→gap-planner→plan-implementer fall-through) and Phase B (PM drainage). It returns one tight summary in a fixed contract format.

4. **Capture the summary** and pass it through to the user verbatim. Do not re-summarize.

5. **Self-schedule** unless the orchestrator's `Next-tick hint` was `pause — *` or it errored:
   ```
   ScheduleWakeup(
     delaySeconds: 21600,            # 6h default; user may override per session
     prompt: "/journey-loop",
     reason: "Next loop tick; previous tick: <orchestrator hint>"
   )
   ```

## Why this wrapper is so short

Per-tick decisions (which agent to spawn, how to interpret state, when to escalate) all live in `loop-orchestrator`'s spec. Keeping the wrapper minimal:
- Main session burns ~50 tokens of wrapper context per tick instead of ~1500
- Orchestrator's decision tree changes don't require updating this file
- Long unattended runs don't accumulate per-tick deliberation in the main context

If you ever need to see the full per-tick logic, read `.claude/agents/loop-orchestrator.md`.

## Safety rules

- Never bypass the stop-first check.
- Never call orchestrator with non-empty prompt or alternate target — the agent always operates on `all`.
- Never push to main, never merge PRs from this wrapper. Merges happen via the orchestrator's spawned subagents (journey-tester docs-only carve-out, PM judgment-based merges).
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
- If the orchestrator returns an error block instead of the expected summary contract, surface it verbatim and skip the ScheduleWakeup — the user needs to see the failure mode before the loop resumes.
