---
status: planned
type: meta
kind: meta-plan
related-rule: ../../.claude/rules/agent-loop.md
related-command: ../../.claude/commands/journey-loop.md
related-agent: ../../.claude/agents/feature-reporter.md
---

# Meta — Stop spawning `feature-reporter` until the agent registry catches up to the merged spec

> **For Hermes:** This plan modifies loop wrapper behavior + monitor anomaly classification. It is a `kind: meta-plan` so feature-reporter (when it eventually does load) will correctly skip its own flip per the existing meta-plan rule. All edits land via one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** Make `/journey-loop` Step 4.5 a graceful no-op instead of a per-tick `Agent tool not loaded` abort, so that loop-monitor stops surfacing `FEATURE_REPORTER_NOT_LOADED` as repeated noise on every plan-close tick. After this plan ships, the wrapper either (a) skips Step 4.5 outright, or (b) detects agent-availability before spawning, in both cases recording one informational log line instead of an aborted Agent tool invocation.

**Architecture:** Touches three loop-infrastructure files only — `.claude/commands/journey-loop.md` (Step 4.5 logic), `.claude/agents/loop-monitor.md` (anomaly rubric: `FEATURE_REPORTER_NOT_LOADED` becomes a known suppressed informational class, not a recurring monitor warning), and `.claude/rules/agent-loop.md` (one-paragraph note documenting the agent-registry-vs-merge race so the next session does not re-discover it the hard way). No code touched. No journey docs touched.

**Tech stack:** Markdown only.

---

## Product intent / assumptions

- The `feature-reporter` agent file (`.claude/agents/feature-reporter.md`) is fully merged on `main` (PR #384, 2026-04-26 14:14Z, commit `a0f05a8`). It is a real, complete spec — not an empty stub.
- Claude Code loads its agent registry **at session start**, not on every Agent-tool invocation. Sessions that began before #384 merged (which is true of every active loop session, since the loop has been running continuously since 13:50:47Z and #384 merged at 14:14:50Z) cannot spawn `feature-reporter` even though the spec is on disk and on `main`.
- This is the same class of issue as `STUCK_PR` for sessions that fork mid-merge — it resolves itself the moment the next session starts. The fix is to acknowledge that gap, not to chase it.
- Loop-monitor has now flagged `FEATURE_REPORTER_NOT_LOADED` on **4 consecutive plan-close ticks** in the same session (2026-04-26 16:35:21Z, 17:06:40Z, 18:30:14Z, 19:14:38Z). On the 4th occurrence, monitor explicitly escalated the pattern: *"FEATURE_REPORTER_NOT_LOADED 4th recurrence escalating as config-plan candidate for next planner pickup."* That escalation is what triggered this plan.
- The two viable end-states named by monitor are mutually exclusive:
  - **Option A (skip-until-restart, recommended):** Wrapper checks agent availability before spawning. If `feature-reporter` is not in the loaded registry, log `Step 4.5 SKIPPED — feature-reporter not in current session registry; will load on next session restart` and continue. The next fresh session (whether triggered by `ScheduleWakeup`-spawned wake or human-driven restart) loads the merged registry and Step 4.5 fires normally.
  - **Option B (remove Step 4.5 entirely):** Strike Step 4.5 from `/journey-loop.md`. Move feature-report updates to a periodic on-demand `/feature-report` invocation that the user runs manually. Less automated but eliminates the failure mode permanently.
- **User-decision needed (see Risks):** This plan codes Option A as the implementation default because (i) the agent spec is real and valuable, (ii) the monitor has already classified the abort as informational-not-fault — i.e. the loop is not actually broken, just noisy — and (iii) Option A self-heals on the next session restart without losing the feature. Implementer must confirm before executing if user disagrees.

---

## Task 1: Add agent-availability gate to Step 4.5 in `/journey-loop.md`

**Objective:** Replace unconditional spawn with a check + graceful skip + single log line.

**Files:**
- `.claude/commands/journey-loop.md`

**Steps:**

1. In Step 4.5 ("Feature report (conditional)"), keep the existing pre-condition (PM merged a PR this tick AND that PR's diff contains a `status: planned` → `status: shipped` flip on a non-`kind: meta-plan`).
2. Before invoking the Agent tool with `subagent_type: feature-reporter`, **probe agent availability**. The simplest probe: attempt the spawn but treat an immediate "agent not loaded / Without a Task tool exposed / unknown subagent_type" failure as a known graceful-skip outcome rather than an error. Mark this branch with the literal string `FEATURE_REPORTER_NOT_LOADED` in the tick summary so monitor's rubric (Task 2) can match it.
3. When the skip branch fires, append one line to the tick summary block:
   ```
   Step 4.5: SKIPPED — feature-reporter agent not in current session registry (merged 2026-04-26, current session predates merge). Will resume on next session restart.
   ```
4. Do NOT retry, do NOT try Bash fallback (`claude --dangerously-skip-permissions ...`), do NOT spawn a different agent. Just record + continue.
5. Update the surrounding prose ("If feature-reporter opens a PR, the next tick's Phase B PM will sweep it") to add: "If the wrapper detects `FEATURE_REPORTER_NOT_LOADED`, it logs the skip and proceeds to Step 5 unchanged."
6. The "skip Step 4.5 entirely" note already in the file (about idle-tick speculative spawns) stays — it's a different skip-condition (no plan flip vs. agent unavailable). Both must be preserved.

**Definition of done:** A grep for `FEATURE_REPORTER_NOT_LOADED` in `.claude/commands/journey-loop.md` returns the new branch. A live tick that follows the next plan close emits one skip line + no Agent-tool error.

---

## Task 2: Teach `loop-monitor` to suppress the recurrence noise

**Objective:** When the wrapper logs `FEATURE_REPORTER_NOT_LOADED`, monitor should recognize it as a known informational class, **not** repeat the "Nth recurrence escalating as config-plan candidate" framing on every tick.

**Files:**
- `.claude/agents/loop-monitor.md`

**Steps:**

1. In the anomaly rubric section, add a new explicit class `FEATURE_REPORTER_NOT_LOADED` next to (or merged with) the `AGENT_NOT_LOADED` candidate class that monitor self-coined on 2026-04-26 14:18:14Z.
2. Document its disposition: **always informational, never escalates, never auto-fixes, never opens a backfill PR**. The mitigation is the next session restart; monitor cannot accelerate that.
3. Specify the row format: monitor still mentions the class in its tick log row (so future retrospective queries can count occurrences) but does NOT repeat "Nth recurrence" framing past the first sighting per session. Suggested phrasing: "Step 4.5 SKIPPED (agent registry stale); known graceful skip — `.claude/rules/agent-loop.md`."
4. Verify the rubric still surfaces `SUB_AGENT_ERROR` for genuine sub-agent crashes; the new class is strictly for the agent-not-loaded path that the wrapper itself logs.

**Definition of done:** Monitor's rubric file contains the class name `FEATURE_REPORTER_NOT_LOADED` with the suppressed/informational disposition. Next tick's monitor row reads "informational" once and stops repeating the escalation.

---

## Task 3: Document the agent-registry-vs-merge race in `agent-loop.md`

**Objective:** Save the next session from re-discovering the same constraint at runtime.

**Files:**
- `.claude/rules/agent-loop.md`

**Steps:**

1. Add a short subsection (under "공통 안전 규칙" or as its own H2 block) titled "Agent 추가/변경 시 세션 재시작 필요성" or English equivalent.
2. Content (one paragraph): "Claude Code 는 agent registry 를 세션 시작 시 한 번만 로드한다. `.claude/agents/<new-agent>.md` 가 main 에 머지되어도 이미 실행 중인 세션 (loop 포함) 은 그 agent 를 spawn 할 수 없다. 새 agent 가 첫 fire 하려면 `ScheduleWakeup` 사이클이 한 번 자연스럽게 종료되거나 사용자가 세션을 재시작해야 한다. `/journey-loop` Step 4.5 가 이 race 에 부딪힌 사례: `feature-reporter` (#384, 2026-04-26)."
3. Cross-link this plan path so the rationale chain is searchable.
4. Do NOT change any existing eleven-agent table rows. The new subsection is additive context only.

**Definition of done:** A reader of `agent-loop.md` who later adds a 12th agent sees the warning before they hit the same fault.

---

## Task 4: Verify with the next plan-close tick

**Objective:** Confirm Step 4.5 SKIP fires cleanly and monitor row is quieter.

**Steps:**

1. Open the PR with all three file changes.
2. After human merge, the next session restart **must** load the updated registry. The very next plan-close tick (i.e. when PM merges any plan-flip PR) is the verification:
   - Wrapper emits one `Step 4.5: SKIPPED — ...` line if the same session is still running, OR feature-reporter actually spawns successfully if a fresh session has started.
   - Monitor row says "informational" once instead of escalating.
3. No other behavior changes. PM continues normal sweep. Phase A continues normal decision tree.
4. Optional: after one fresh session restart confirms feature-reporter spawns successfully on a plan-close tick, this plan's `status` flips to `shipped` and the relevant Change log entries land on `agent-loop.md` + `journey-loop.md` (added by plan-implementer per docs-sync convention).

**Definition of done:** One green tick post-merge whose monitor row references `FEATURE_REPORTER_NOT_LOADED` exactly zero times in escalation language (informational mention OK).

---

## Scope

**In:**
- `.claude/commands/journey-loop.md` — Step 4.5 gated by agent-availability probe + skip-line emission.
- `.claude/agents/loop-monitor.md` — anomaly rubric updated with new informational class.
- `.claude/rules/agent-loop.md` — one explanatory subsection on registry-vs-merge race.

**Out:**
- Changing Claude Code's agent-loading behavior — out of our control.
- Auto-restarting the loop session — `ScheduleWakeup` is the only restart mechanism we have, and it preserves session state, so it doesn't help here. A true fresh session requires the user to start one.
- Removing `feature-reporter` agent or `/feature-report` slash command — the spec is correct and useful; only its first-fire timing is wrong.
- Backfilling `docs/feature-report.md` for plans #376, #381, #386, #391, #394, #398, #401, #404, #408 that shipped during the registry-stale window — that's the first job feature-reporter performs once it loads, on its own initiative. No need to script the catch-up.

---

## Risks / open questions

- **Open question (user must decide before implementer ships):** Option A (skip-until-restart, this plan's default) vs. Option B (remove Step 4.5 entirely). Recommendation: A. Reason: the agent and its provenance are real, the abort is purely cosmetic monitor noise, and the catch-up backfill on first successful fire is a feature, not a bug. If user prefers B, implementer should rewrite Tasks 1–3 to delete Step 4.5 + delete the agent + delete the slash command instead — but that loses the auto-feature-report capability for marginal cleanup.
- **Risk: probe approach lets a real agent error masquerade as registry-stale skip.** Mitigation: the probe matches on a narrow set of error strings (e.g. `not loaded`, `unknown subagent_type`, `Without a Task tool exposed` for the new-agent variant). Any other failure shape still surfaces as `SUB_AGENT_ERROR`. Implementer should be explicit about the match list.
- **Risk: Claude Code changes its error-string format in a future release.** Mitigation: the probe is in one file (`/journey-loop.md`), easy to update. Add a comment so future readers know the strings come from observed runtime behavior, not documented API.
- **Risk: future agents added mid-session repeat the same gap.** Mitigation: Task 3's `agent-loop.md` note + this plan's name in the cross-reference. Not bulletproof, but searchable.
- **Out-of-band consideration:** even after the user-restarts-session resolves the immediate gap, the inverse race exists for agent **removals** (a deprecated agent stays spawnable until restart). Out of scope here, but worth noting if a similar plan is needed for the deprecated `loop-orchestrator`.

---

## Related journey

This plan does not resolve a `docs/journeys/<id>.md` Known gap — `feature-reporter` is loop infrastructure, not a user-facing feature. The triggering signal is the **loop-monitor escalation** in `docs/loop-monitor-log.md` (4 recurrences ending 2026-04-26T19:14:38Z). When this plan ships, the corresponding monitor rubric update + wrapper change should make the noise stop; no journey doc changes.
