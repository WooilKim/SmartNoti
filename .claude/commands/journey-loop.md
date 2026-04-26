---
name: journey-loop
description: One tick of the SmartNoti improvement loop. Inlines the per-tick decision tree (Phase A → Phase B → monitor) directly in the wrapper because nested Agent tool calls are blocked in sub-agents. Self-schedules via ScheduleWakeup.
---

Execute one iteration of the improvement Ralph loop.

## Tick

### Step 1 — Stop-first check

```bash
gh pr list --state open --json number,title,headRefName \
  --jq '[.[] | select(.headRefName | startswith("docs/journey-verify-"))] | length'
```

If count ≥ 2: report
> `journey-loop paused — N verify PRs awaiting review. Merge or close them, then run /journey-loop to resume.`

Then stop. Do NOT proceed to Step 2. Do NOT call `ScheduleWakeup`.

### Step 2 — Snapshot state

Run these in parallel (independent):

```bash
git fetch origin main --quiet
git rev-parse --short=7 origin/main             # HEAD
gh pr list --state open --json number,title,headRefName,createdAt
grep -l "^status: planned" docs/plans/*.md       # planned plan list
git log origin/main --oneline -10                # recent merges
grep -H "^last-verified:" docs/journeys/*.md | sort -t: -k3   # rotation pool
```

Parse open-PR results into branch-pattern counts:
- `docs/journey-verify-*` → tester PRs
- `docs/plan-*` → planner PRs (gap-planner-adjacent)
- `feat/*` / `fix/*` → implementer PRs
- `ops/*` → infra/audit/meta PRs

For each `status: planned` plan, check if an open implementation PR already exists by slug match (`gh pr list --search "<slug> in:title"`).

### Step 3 — Phase A decision tree (one productive subagent at most)

Apply in order; first matching condition wins; others skip this tick.

**Phase A.1 — `journey-tester`**: spawn (via Agent tool, `subagent_type: journey-tester`) if EITHER:
- A merge to `main` since the previous tick touched any file under `app/src/main/**` OR `docs/journeys/*.md` (heuristic: scan recent commits for non-`docs/plans` / non-`docs/auto-merge-log` / non-`docs/pr-review-log` paths).
- The current oldest journey by `last-verified` is ≥ 1 day older than the next-oldest (rotation through journeys).

Spawn prompt template:
> `Run one verification cycle. Pick the journey most likely to need re-verification given <recent-merge-summary | rotation-pool>. Open a PR only if you updated a journey doc; self-merge per your 4 gates. Reply ≤ 80 words.`

If tester returned PASS / DRIFT / SKIP — record and skip A.2/A.3.
If tester returned NO-OP — fall through.

**Phase A.2 — `gap-planner`**: spawn (via Agent tool, `subagent_type: gap-planner`) if A.1 NO-OP'd AND there are Known gaps across `docs/journeys/*.md` not covered by any current `status: planned` plan.

Spawn prompt template:
> `The loop fell through to you. Scan Known gaps across docs/journeys/*.md. If a leverage-9 gap exists that is NOT already covered by a queued plan (status: planned files: <list>), draft ONE new plan, open a PR. Otherwise return NO-OP. Reply ≤ 100 words.`

If planner drafted a plan — record and skip A.3.
If planner NO-OP'd — fall through.

**Phase A.3 — `plan-implementer`**: spawn (via Agent tool, `subagent_type: plan-implementer`) if A.2 NO-OP'd AND there's a `status: planned` plan on main with NO open implementation PR.

Pre-compute eligible plan: sort `status: planned` plans by date (oldest first), pick the first one with no existing implementation PR AND whose dependencies (if any) are merged.

Spawn prompt template:
> `Implement <plan-path> per its Tasks section. Bundle Task 1 alone if it's >= 30 lines, otherwise bundle Tasks 1-2. Annotate completed Task headings with [IN PROGRESS via PR #N]. Open code PR — do NOT self-merge; PM handles. Reply ≤ 120 words.`

If implementer opened a PR — record.
If no eligible plan — record NO-OP for Phase A.

### Step 4 — Phase B drainage (always runs)

Spawn `project-manager` (via Agent tool, `subagent_type: project-manager`) with target `all`.

Pre-compute: re-check open-PR count. If 0, prompt PM to confirm and report NO-OP. Otherwise let it sweep normally.

Spawn prompt template:
> `Sweep all open PRs per your judgment-based escalation rubric. Apply: APPROVE → merge; REQUEST_CHANGES → comment + log; ESCALATE → comment + leave for human; DEFER (CI pending only) → comment. Open audit PR for any merges. Pre-sweep snapshot showed <N> open PRs — confirm independently with 'gh pr list --state open' and report NO-OP if empty. Reply ≤ 150 words.`

Capture PM verdicts.

### Step 4.5 — Feature report (conditional)

If Phase B's PM merged any PR this tick, scan its diff for plan-frontmatter flips of the form `status: planned` → `status: shipped`:

```bash
for sha in <merged-shas>; do
  git show "$sha" -- 'docs/plans/*.md' | grep -E '^\+status: shipped' && echo "$sha"
done
```

If at least one such flip is detected AND the affected plan is NOT a `kind: meta-plan` (check the plan's frontmatter — meta plans are loop infrastructure, not user-facing features):

Spawn `feature-reporter` (via Agent tool, `subagent_type: feature-reporter`) with the prompt:

> `update — plan(s) flipped to status: shipped this tick: <plan-slug-list>. Fold them into docs/feature-report.md per your spec. Open one PR. Reply ≤ 100 words.`

If feature-reporter opens a PR, the next tick's Phase B PM will sweep it (docs-only carve-out — auto-merge expected). If feature-reporter returns NOOP, that's logged but no PR opens.

If the wrapper detects `FEATURE_REPORTER_NOT_LOADED` (see agent-availability gate below), it logs the skip and proceeds to Step 5 unchanged.

If no plan flipped this tick, skip Step 4.5 entirely. Do NOT spawn feature-reporter speculatively — its trigger is a real shipped feature, not idle ticks.

#### Agent-availability gate (registry-vs-merge race)

Claude Code loads its agent registry **at session start**, not per-tool-invocation. If `feature-reporter`'s spec was merged on `main` *after* the current loop session began, the Agent tool will fail to spawn it even though `.claude/agents/feature-reporter.md` exists on disk. Treat this as a known graceful-skip outcome — NOT an error. See `.claude/rules/agent-loop.md` ("Agent 추가/변경 시 세션 재시작 필요성") and `docs/plans/2026-04-26-meta-feature-reporter-not-loaded-skip.md`.

Match the failure narrowly. If the Agent tool returns an error whose message contains any of these literal substrings (observed runtime behavior — not documented API; update list if Claude Code changes its error format):

- `not loaded`
- `unknown subagent_type`
- `Without a Task tool exposed`
- `agent not found`

then record the skip with the literal marker `FEATURE_REPORTER_NOT_LOADED` so loop-monitor's rubric (Task 2 of the meta-plan) can match it, and append exactly one line to the tick summary block:

```
Step 4.5: SKIPPED — feature-reporter agent not in current session registry (merged 2026-04-26, current session predates merge). Will resume on next session restart. [FEATURE_REPORTER_NOT_LOADED]
```

Do NOT retry the spawn. Do NOT fall back to a Bash invocation (`claude --dangerously-skip-permissions ...`). Do NOT substitute another agent. Just record + continue to Step 5.

Any other error shape (genuine sub-agent crash, prompt validation failure, etc.) still surfaces as `SUB_AGENT_ERROR` per the existing path.

### Step 5 — Loop monitor

Re-fetch HEAD post Phase A/B (since tester / PM may have merged PRs).

Spawn `loop-monitor` (via Agent tool, `subagent_type: loop-monitor`) with the synthesized tick summary as the prompt:

```
inlined-orchestrator tick — <ISO8601>
 ├─ State snapshot (pre-tick)
 │    ├─ HEAD: <sha7>
 │    ├─ Open PRs: <count>
 │    └─ Plans planned on main: <count>
 ├─ Phase A — production
 │    └─ <agent>: <verdict> · <details> · PR <#nums>
 ├─ Phase B — drainage (PM)
 │    └─ project-manager: <verdict> · <details>
 └─ Next-tick hint: <continue | pause — reason | blocked on human — #nums>
Post-tick HEAD: <sha7>
```

Append to monitor prompt: `Run your end-of-tick health check. Re-derive state independently. Append the tick row to docs/loop-monitor-log.md.`

Capture monitor's report.

### Step 6 — Surface both reports verbatim

Pass the tick summary block + monitor report through to the user without re-summarizing. The user reads them directly.

### Step 7 — Self-schedule

Skip `ScheduleWakeup` if EITHER:
- The synthesized Phase A result was an `error` (not NO-OP — actual error) or any spawned agent errored.
- Monitor reported `Health: FAULT`.

Otherwise:

```
ScheduleWakeup(
  delaySeconds: 60,               # 60s tick — runtime floor; user-tuned 2026-04-22
  prompt: "/journey-loop",
  reason: "Next loop tick; previous tick: <Phase A hint>"
)
```

## Why this wrapper inlines orchestration

The `loop-orchestrator` subagent (formerly invoked here) cannot spawn nested sub-agents because the Claude Code harness blocks `Agent`/`Task` tool invocations from inside sub-agents, even when the agent's `tools:` frontmatter lists them. Surfaced 2026-04-22T08:50Z as `Without a Task tool exposed`. See `docs/plans/2026-04-22-meta-inline-orchestration.md` for the full diagnosis. The deprecated orchestrator agent is kept at `.claude/agents/loop-orchestrator.md` with a deprecation notice so future sessions don't re-invoke it.

Trade-off: the main session burns ~1500 tokens of decision-tree context per tick (vs. ~70 in the pre-deprecation wrapper). Acceptable because (a) autocompaction handles it across long runs, (b) the indirection alternative — orchestrator returns spawn-requests for the wrapper to execute — added complexity for marginal savings.

## Safety rules

- Never bypass the stop-first check.
- Never spawn agents speculatively — every Phase A spawn must match a triggered condition.
- Never push to main, never merge PRs from this wrapper. Merges happen via spawned subagents (journey-tester docs-only carve-out, PM judgment-based merges).
- If `ScheduleWakeup` fails, report it plainly and stop; do not retry with a shorter delay.
- If any Phase A subagent returns an `error` status (not NO-OP — actual error), surface it verbatim + skip ScheduleWakeup.
- If monitor returns `Health: FAULT`, surface its report + skip ScheduleWakeup.
- The wrapper itself never modifies files. State changes happen only through spawned subagents.
