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

4. **Spawn `loop-monitor`** subagent with `subagent_type: loop-monitor`, passing the orchestrator's summary text as the prompt. Monitor does an end-of-tick health check: detects anomalies (PM_SKIPPED, AUDIT_DRIFT, STUCK_PR, SUB_AGENT_ERROR, REPEAT_NOOP_STORM, SELF_MOD_PENDING, CI_RED_WITHOUT_VERDICT), auto-fixes enumerated safe cases, escalates everything else. Appends a row to `docs/loop-monitor-log.md`. Returns ≤ 150-word health report.

5. **Capture both reports** (orchestrator + monitor) and pass them through to the user verbatim. Do not re-summarize.

6. **Self-schedule** unless EITHER (a) the orchestrator's `Next-tick hint` was `pause — *` or it errored, OR (b) the monitor reported `Health: FAULT`:
   ```
   ScheduleWakeup(
     delaySeconds: 21600,            # 6h default; user may override per session
     prompt: "/journey-loop",
     reason: "Next loop tick; previous tick: <orchestrator hint>"
   )
   ```

## Why this wrapper is so short

Per-tick decisions (which agent to spawn, how to interpret state, when to escalate) all live in `loop-orchestrator`'s spec. End-of-tick health checks live in `loop-monitor`'s spec. The wrapper just stitches the two together. Keeping it minimal:
- Main session burns ~70 tokens of wrapper context per tick instead of ~1500
- Orchestrator / monitor spec changes don't require updating this file
- Long unattended runs don't accumulate per-tick deliberation in the main context

If you ever need to see the full per-tick logic, read `.claude/agents/loop-orchestrator.md` (tick decisions) and `.claude/agents/loop-monitor.md` (end-of-tick health).

## Safety rules

- Never bypass the stop-first check.
- Never call orchestrator or monitor with alternate targets — orchestrator operates on `all`; monitor receives the orchestrator summary.
- Never push to main, never merge PRs from this wrapper. Merges happen via the orchestrator's spawned subagents (journey-tester docs-only carve-out, PM judgment-based merges) or via monitor's audit-row backfill PRs (never self-merged).
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
- If the orchestrator returns an error block, surface it verbatim + skip ScheduleWakeup (user must see failure before loop resumes).
- If the monitor returns `Health: FAULT`, surface its report + skip ScheduleWakeup (unrecoverable anomaly needs user disposition).
