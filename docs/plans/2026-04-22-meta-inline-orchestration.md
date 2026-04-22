---
status: in-progress
type: meta
---

# Meta — Inline loop-orchestrator into the `/journey-loop` wrapper

> **For Hermes:** This plan modifies the loop architecture itself. Carve-out per `agent-loop.md` ("system architecture itself evolves through the same plan/implement/review pipeline it governs"). All changes go through one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** Permanently inline the per-tick decision tree into the `/journey-loop` wrapper command and deprecate the `loop-orchestrator` subagent. Eliminate the structural "orchestrator cannot spawn nested sub-agents" failure observed on 2026-04-22T08:50Z, where the orchestrator agent surfaced `Without a Task tool exposed` and produced zero work.

**Diagnosis:** This Claude Code harness blocks the `Agent`/`Task` tool from being loaded into sub-agent tool palettes, even when the agent's frontmatter lists it. The loop-orchestrator's spec (`tools: Read, Grep, Glob, Bash, Agent`) is honored at frontmatter-load time but the tool is stripped at runtime. The orchestrator then attempts a Bash fallback (`claude --dangerously-skip-permissions`) which is correctly blocked by the security gate. Net result: every productive tick faults at Phase A.

**Decision (user-approved 2026-04-22):** Move the orchestrator's decision tree into the wrapper. The main session, which DOES have the Agent tool, will spawn Phase A + Phase B + monitor directly.

**Trade-off accepted:** Main session burns ~1500 tokens per tick on the inlined decision tree (vs. ~70 tokens on the previous "spawn orchestrator + relay" wrapper). Across 50 unattended ticks that's ~75K extra tokens — significant but tolerable given autocompaction. The user explicitly preferred this over the alternative ("decider-only orchestrator that returns spawn-requests for the wrapper to execute") because the indirection added complexity for marginal context savings.

**Tech stack:** Markdown only (`.claude/commands/journey-loop.md` + `.claude/agents/loop-orchestrator.md` + `.claude/rules/agent-loop.md`).

---

## Product intent / assumptions

- The loop must be able to make forward progress under autonomous `ScheduleWakeup` cycles. Today it cannot — every tick faults at the orchestrator spawn step.
- We have already proven the inlined approach works: 3 consecutive successful ticks on 2026-04-22T09:23 / 10:35 / 11:47 verified `categories-management` (#255), `inbox-unified` (#256), and `priority-inbox` (#257) using the same decision tree the orchestrator spec defined, executed by the main session directly.
- Keeping `loop-orchestrator.md` around as an executable agent invites future regressions (someone re-invokes it; same fault recurs). It needs to be either deleted or clearly marked deprecated with a pointer to the wrapper.
- The `loop-monitor` agent is unaffected — its `Agent` tool listing is for the auto-fix path (re-invoking PM on PM_SKIPPED) which has not yet been observed firing in practice. If it ever does fire and hits the same nested-Agent block, that's a separate follow-up plan.

---

## Task 1: Inline the decision tree into `/journey-loop.md`

**Objective:** Replace the wrapper's "spawn orchestrator + relay" steps with the full Phase A decision tree + Phase B PM drainage + monitor handoff.

**Files:**
- `.claude/commands/journey-loop.md`

**Steps:**

1. Keep the `Stop-first check` step (verify-PR pause gate) as the first action — unchanged.
2. Add a `Snapshot state` step that runs:
   ```bash
   git fetch origin main --quiet
   HEAD=$(git rev-parse --short=7 origin/main)
   gh pr list --state open --json number,title,headRefName,createdAt
   grep -l "^status: planned" docs/plans/*.md
   git log origin/main --oneline -8
   ```
3. Add the `Phase A decision tree` (copied from the orchestrator's spec, slightly tightened):
   - **A.1 — `journey-tester`**: spawn if EITHER (a) any merge to main since the previous tick touched `app/src/main/**` or `docs/journeys/*.md`, OR (b) the oldest journey by `last-verified` is ≥ 1 day older than the next-oldest. Use the spawn prompt template from the orchestrator spec.
   - **A.2 — `gap-planner`**: spawn if A.1 returned NO-OP AND there are Known gaps not covered by any `status: planned` plan.
   - **A.3 — `plan-implementer`**: spawn if A.2 returned NO-OP AND there's a `status: planned` plan on main with no open implementation PR.
4. Add the `Phase B drainage (PM always)` step that spawns `project-manager` with target `all`. Pre-compute open-PR count and pass it in the prompt so PM can early-exit on empty queue.
5. Add the `Loop monitor` step that spawns `loop-monitor` with the synthesized tick summary as its prompt.
6. Keep the `Self-schedule` step using the same gate (skip if Phase A errored OR monitor returned `Health: FAULT`).
7. Update the `Why this wrapper is so short` section — remove that framing since it's no longer short. Replace with a short note: "The decision tree is inlined here because the harness blocks nested Agent tool calls in sub-agents; see `docs/plans/2026-04-22-meta-inline-orchestration.md` for diagnosis."

**Definition of done:** The wrapper, when invoked as `/journey-loop`, runs the full tick without ever spawning `loop-orchestrator`.

## Task 2: Mark `loop-orchestrator.md` deprecated

**Objective:** Prevent future invocations of the broken orchestrator agent.

**Files:**
- `.claude/agents/loop-orchestrator.md`

**Steps:**

1. Update the frontmatter `description` field to start with `[DEPRECATED 2026-04-22]` and link to this plan.
2. Replace the body with a short "Deprecation notice" block that:
   - States the diagnosis verbatim ("nested Agent tool calls blocked by harness").
   - Links to `.claude/commands/journey-loop.md` as the new home for tick decisions.
   - Links to this meta-plan for full context.
3. Do NOT delete the file outright — keeping it (with a deprecation notice) preserves the historical reference, and Claude Code's agent-discovery scan won't accidentally invoke a deprecated agent if the description is clear.
4. Optional polish: add `disabled: true` if the Claude Code agent frontmatter supports it (verify by checking other agents — if none use it, skip this step).

**Definition of done:** `cat .claude/agents/loop-orchestrator.md` clearly shows the agent is no longer the active orchestrator.

## Task 3: Update `agent-loop.md` rule to reflect new architecture

**Objective:** Keep the loop-architecture documentation in sync so future sessions don't re-learn the deprecation by hitting the fault.

**Files:**
- `.claude/rules/agent-loop.md`

**Steps:**

1. In the agent table, mark `loop-orchestrator` as deprecated (strike-through or `[DEPRECATED]` prefix in the Agent column) with a one-line note pointing to `/journey-loop`.
2. In the "오케스트레이션 옵션" section, update the v1 description to state: "메인 세션이 `/journey-loop` skill 안에서 직접 Phase A → Phase B → monitor 를 spawn. 이전에는 loop-orchestrator subagent 가 결정을 담당했지만 nested Agent tool 호출이 하니스에 의해 차단되어 2026-04-22 에 인라인으로 전환." Link to this plan.
3. Do not change v2 / v3 descriptions — those are forward-looking and not affected by this restructure.
4. Do not touch `loop-monitor` references — monitor is unaffected.

**Definition of done:** A reader of `agent-loop.md` understands the current loop architecture without needing to read the orchestrator agent or this plan.

## Task 4: Verify on a live tick

**Objective:** Confirm the new wrapper works end-to-end without regression.

**Steps:**

1. Open the PR with all three file changes.
2. After human merge, the next `/journey-loop` invocation should:
   - Pass the stop-first check (or report verify-PR pause if applicable).
   - Snapshot state via inline commands.
   - Walk Phase A decision tree, spawn the appropriate sub-agent, capture result.
   - Spawn PM, capture result.
   - Spawn monitor, capture result.
   - Self-schedule unless blocked.
3. The first tick after merge IS the verification — if it works the same way as the 3 inlined ticks already executed, the change is good.

**Definition of done:** One green tick on `main` post-merge with the inlined wrapper.

---

## Scope

**In:**
- `.claude/commands/journey-loop.md` — full inline rewrite.
- `.claude/agents/loop-orchestrator.md` — deprecation notice (preserve file).
- `.claude/rules/agent-loop.md` — agent-table + v1 architecture description update.

**Out:**
- `loop-monitor` changes — unaffected; its potential nested-Agent issue is theoretical and tracked separately.
- New permission rules to allow `claude --agent ... -p ...` Bash invocation — explicitly rejected by user as a security concession.
- Decider-only orchestrator architecture — explicitly rejected by user as adding indirection without proportional benefit.
- Any code changes — this is a docs-and-config-only plan.

---

## Risks / open questions

- **Risk: per-tick context burn** — main session sees ~1500 extra tokens per tick. Mitigated by autocompaction; user accepted the trade-off.
- **Risk: future readers re-invoke deprecated orchestrator** — mitigated by deprecation notice in the agent file + rule update. If Claude Code adds a `disabled: true` frontmatter flag in future, switch to that.
- **Risk: nested Agent calls become supported in a future Claude Code release** — at that point we can revert (un-deprecate the orchestrator + restore the original 70-token wrapper). Until then, inlining is the only working architecture.

---

## Change log

- 2026-04-22: Drafted in response to 4 consecutive successful inlined ticks (#255, #256, #257) confirming the inlined approach works. User approved Option A (inline permanently) over Option B (decider-only orchestrator).
