---
status: planned
type: meta
kind: meta-plan
related-rule: ../../.claude/rules/agent-loop.md
related-command: ../../.claude/commands/journey-loop.md
related-agent: ../../.claude/agents/coverage-guardian.md
---

# Meta — Decide whether `coverage-guardian` gets decommissioned or auto-triggered (4-retro QUIET streak)

> **For Hermes:** This plan resolves a standing leverage-9 retrospective finding: `coverage-guardian` has logged zero invocations across a 30-day window despite ~50 prod-touching PRs merging. The plan is **decision-first** (user picks A/B/C); only after that choice does an implementation PR follow. No code, no agent definition edits in this plan's PR — this PR is purely the meta-plan markdown. Apply `kind: meta-plan` carve-out at PM review.

**Goal:** Force a binary decision (with a "C: something else" escape hatch) on the coverage-guardian agent's role in the loop. Either (A) decommission the agent and remove it from `agent-loop.md`, (B) wire the agent into `/journey-loop` Step 4 so it auto-fires on every implementer-origin PR with prod-code changes (in parallel with PM), or (C) restructure its contract in some third way the user defines. After this plan ships and a follow-up implementation PR lands, `loop-retrospective` should stop surfacing coverage-guardian as a standing leverage-9 target — either because the agent is gone or because it's actually producing rows in `docs/coverage-log.md`.

**Architecture:** Touches `.claude/agents/coverage-guardian.md` (decommission or contract update), `.claude/rules/agent-loop.md` (eleven-agent table row removed or amended), `.claude/commands/journey-loop.md` (Step 4 auto-spawn wiring under Option B), and `docs/coverage-log.md` (left as-is under A; continues to receive rows under B). No production code touched in either branch. The decision itself routes via this meta-plan's PR; the resulting agent / command / rule edits route via a separate implementation PR after the user picks A / B / C.

**Tech stack:** Markdown only.

---

## Product intent / assumptions

- `coverage-guardian` was added to the loop as the test-per-prod ratio specialist. Its contract (per its own spec): scan PRs, classify OK / ADEQUATE / THIN / MISSING, comment on the PR, append rows to `docs/coverage-log.md`, and signal `project-manager` for the merge gate. It does not cast a review vote.
- Empirical state as of 2026-04-27T17:27Z:
  - `docs/coverage-log.md` contains zero data rows since file creation 2026-04-20 (header only).
  - Last four `loop-retrospective` rows (2026-04-26T06:56Z, 2026-04-26T09:29Z, 2026-04-27T08:55Z, 2026-04-27T16:53Z) all classify coverage-guardian as QUIET.
  - 2026-04-27T08:55Z retro explicitly flagged `MISSING: coverage-guardian zero rows in 30-day window despite ~50 prod-touching PRs — agent never invoked; PM substitutes ad-hoc judgment.`
  - 2026-04-27T16:53Z retro logged a HEAVY-PRODUCTIVITY-DAY (9 prod merges across 4 plans) with **zero coverage-guardian invocations**. PM merged everything via its own gate.
- Three possible explanations, mutually exclusive at the system level but combinable at the diagnosis level:
  1. **Invocation contract is broken.** Nothing in `/journey-loop` spawns the agent; humans don't run `/coverage-check`; PM proceeds without it. Signal would be valuable but never reaches the gate.
  2. **Signal is redundant.** PM's existing scope/traceability/CI gate already enforces tests-first via the implementer's RED-by-design pattern. Coverage-guardian's classification adds no new information PM lacks.
  3. **Agent has rotted.** The original use case (v3 auto-merge guardrail) hasn't materialized; without v3, the agent's design contract no longer maps to real workflow.
- These map to remediation options:
  - **Option A — decommission:** Move `coverage-guardian.md` to `.claude/agents-archive/` (or delete with git history). Remove its row from `agent-loop.md` table. Remove its mention from `/journey-loop` if any. Leave `docs/coverage-log.md` as a historical artifact. Update `loop-retrospective`'s rubric so it no longer expects coverage-guardian rows.
  - **Option B — auto-trigger:** Modify `/journey-loop` Step 4 (or wherever PM is spawned) to spawn `coverage-guardian` in parallel with PM whenever the open PR set contains an implementer-origin PR with `app/src/main/**` changes. PM then reads coverage-guardian's PR comment as input to its merge decision. Coverage-guardian still does not vote; PM still owns the gate. Each productive tick gains ~10s overhead per qualifying PR.
  - **Option C — user-defined third path.** E.g. fold coverage-guardian's logic directly into PM's checklist (eliminating the standalone agent), or restrict its scope to v3 auto-merge only and explicitly mark it dormant until v3 ships. Implementer PR shape would follow user's wording.
- **User-decision needed (see Risks):** This plan does NOT pre-pick A vs. B vs. C. The user must decide on the basis of the audit findings in Tasks 1–2 below. Recommendation language is reserved for the implementer PR's body, not this meta-plan.

---

## Task 1: Audit coverage-guardian's actual invocation history

**Objective:** Establish the ground truth of how often (if ever) coverage-guardian has fired and what it produced when it did.

**Files to read (no edits):**
- `docs/coverage-log.md`
- `.claude/agents/coverage-guardian.md`
- `docs/loop-retrospective-log.md`
- `docs/pr-review-log.md`
- `docs/auto-merge-log.md`

**Steps:**

1. Count the data rows in `docs/coverage-log.md`. Expected: 0 (header-only). If non-zero, list each row's PR number and classification.
2. Grep `docs/loop-retrospective-log.md` for every mention of `coverage-guardian` and quote the verdict ("QUIET", "HEALTHY", etc.) plus the date.
3. Grep `docs/pr-review-log.md` and `docs/auto-merge-log.md` for any PM verdict that references a coverage-guardian comment, signal, or classification. Expected: zero.
4. Use `gh pr list --state all --limit 100 --json number,headRefName,author,comments` (or equivalent) to spot-check whether any PR comment thread contains the literal string `coverage-guardian signal:`. Expected: zero.
5. Record findings as plain bullet list in the meta-plan's implementation PR description (or in a follow-up audit comment on this plan's PR if user prefers).

**Definition of done:** A 5-line evidence summary (rows / retros / PM-references / PR-comments) that the user can read in 30 seconds and use to inform the A/B/C choice.

---

## Task 2: Survey PM's review behavior for implicit coverage signal

**Objective:** Determine whether PM's existing checklist already covers what coverage-guardian was designed to flag — i.e. whether the signal is redundant.

**Files to read:**
- `.claude/agents/project-manager.md`
- Last 10 PM verdicts in `docs/pr-review-log.md` (focus: implementer-origin PRs touching `app/src/main/**`)

**Steps:**

1. In `project-manager.md`, identify whether any review checklist item explicitly references test coverage, test-to-prod ratio, or "MISSING tests" detection. Quote the literal text or report its absence.
2. Sample the last 10 implementer-origin PR reviews in `docs/pr-review-log.md`. For each, note whether PM's verdict body mentions test files, coverage, or RED-by-design verification. Tally how many implicitly enforce the same constraint coverage-guardian would.
3. Cross-check with the implementer agent's `tests-first` discipline: does the implementer's own PR template + agent contract guarantee that any prod-line change is accompanied by tests? Quote the relevant sentence from `.claude/agents/plan-implementer.md` if so.
4. Conclude: does PM + implementer together already enforce the THIN / MISSING gate that coverage-guardian was meant to specialize in? Yes / No / Partially.

**Definition of done:** A one-paragraph verdict on signal redundancy that the user can read alongside Task 1's evidence to choose A / B / C.

---

## Task 3: User decision point — A / B / C

**Objective:** The user picks one of the three options based on Tasks 1–2 evidence. This task has no implementation steps — it is the explicit gate.

**Decision question (verbatim, to be answered by the user on this plan's PR):**

> Given Task 1 evidence (coverage-guardian has zero recorded invocations and its log file has zero rows since creation) and Task 2 evidence (whether PM's existing gate is sufficient or not), choose:
>
> - **A. Decommission.** Archive the agent spec, remove from agent-loop.md table, leave `docs/coverage-log.md` as historical artifact, update retrospective rubric.
> - **B. Auto-trigger.** Wire `/journey-loop` Step 4 to spawn coverage-guardian in parallel with PM on every implementer-origin PR with `app/src/main/**` changes. PM consumes its comment as input.
> - **C. Other.** Specify the third path in plain language; the implementer plan will follow that wording.

**Definition of done:** User comments on the PR with one letter (A / B / C) and, if C, a one-line description of the third path.

---

## Task 4: If A chosen — implementation PR (separate, not this PR)

**Objective:** Decommission coverage-guardian without losing its history.

**Files (separate implementation PR, not this meta-plan PR):**
- Move `.claude/agents/coverage-guardian.md` → `.claude/agents-archive/coverage-guardian.md` (preserve git history via `git mv`).
- `.claude/rules/agent-loop.md`: strike the coverage-guardian row from the eleven-agent table; leave a one-paragraph "Decommissioned 2026-04-DD" subsection citing this plan's findings.
- `.claude/agents/loop-retrospective.md`: remove coverage-guardian from the per-agent rubric so QUIET no longer counts.
- `docs/coverage-log.md`: prepend a one-paragraph "STATUS: archived YYYY-MM-DD per `docs/plans/2026-04-27-meta-coverage-guardian-decommission-or-auto-trigger.md`" header. Leave the table intact for historical reference.
- `.claude/commands/coverage-check.md` (if it exists): mark deprecated or delete.
- This meta-plan: flip `status: shipped` + add `superseded-by:` link to the implementation PR.

**Definition of done:** Next `loop-retrospective` run shows ten agents in the table and no coverage-guardian classification. PM's review checklist gains an explicit "test-per-prod ratio" line if it lacked one (Task 2 finding determines whether this is needed).

---

## Task 5: If B chosen — implementation PR (separate, not this PR)

**Objective:** Auto-trigger coverage-guardian whenever an implementer-origin PR with prod-code changes is in the open queue.

**Files (separate implementation PR, not this meta-plan PR):**
- `.claude/commands/journey-loop.md`: add a new sub-step inside Phase B (PM drainage) that, before invoking PM, computes the set of "implementer-origin PRs with `app/src/main/**` diffs in the current open queue". For each such PR, spawn `coverage-guardian` with the PR number as input. Wait for completion (parallel with PM is fine; spawn order matters less than completion-before-PM-verdict). Record one log line per spawn in the tick summary.
- `.claude/agents/project-manager.md`: add one bullet to the merge checklist instructing PM to fetch the latest `coverage-guardian signal:` comment on each PR (if present) and treat MISSING as a hard request-changes, THIN as a soft warning to surface in the verdict body, ADEQUATE/OK as no-op.
- `.claude/agents/coverage-guardian.md`: minor edits to the `Inputs` section confirming wrapper-spawn is now the primary invocation path; manual `/coverage-check` remains as fallback.
- `.claude/rules/agent-loop.md`: amend the coverage-guardian row to mention the new auto-trigger contract.
- This meta-plan: flip `status: shipped` + add `superseded-by:` link.

**Overhead estimate:** Wrapper gains ~10s/PR per qualifying tick. On a typical 0–2 implementer-PR tick, this is negligible. On a heavy 9-PR tick (cf. 2026-04-27T16:53Z), this is ~90s additional latency, partially parallelizable with PM's own scan time.

**Definition of done:** Next post-merge implementer-PR tick logs a coverage-guardian spawn line and `docs/coverage-log.md` gains a row. Next `loop-retrospective` (≥7 days later) reclassifies coverage-guardian as HEALTHY.

---

## Task 6: If C chosen — implementation PR (separate, scope follows user's wording)

**Objective:** Implement the user's third path. The implementer plan-implementer agent receives the user's one-line wording from Task 3 as the scope and produces a follow-up plan accordingly.

**Definition of done:** Same as A or B at a structural level — next retrospective no longer surfaces coverage-guardian as standing leverage-9.

---

## Scope

**In:**
- This single meta-plan markdown file under `docs/plans/`.
- One follow-up implementation PR after user picks A / B / C (out of scope for this PR).

**Out:**
- Any code changes (no `app/src/main/**` edits).
- Any agent definition edits (those happen in the follow-up PR).
- Any `/journey-loop` wrapper edits (same — follow-up PR).
- Backfilling coverage-log.md with retroactive analyses of the ~50 prod-touching PRs that merged unscanned. Even under Option B, the auto-trigger applies forward only; historical PRs stay unscanned.
- Adjusting `coverage-guardian`'s classification thresholds (OK / ADEQUATE / THIN / MISSING ratios). Out of scope unless user explicitly raises it.

---

## Risks / open questions

- **Open question 1 (user must decide before any implementation PR ships):** A vs. B vs. C. This is the central gate of this plan. No fallback default — the user picks. Recommendation deferred to the implementer PR after evidence is gathered.
- **Open question 2:** Is the original coverage-guardian use case (THIN/MISSING signal to PM as a v3 auto-merge guardrail) still valid given today's mostly-tests-first implementer pattern? If most implementer PRs already pass with proportional test coverage because the implementer agent enforces RED-by-design, coverage-guardian's marginal value is near zero. Task 2's audit answers this.
- **Open question 3:** If Option B is chosen, is the ~10s/PR wrapper overhead worth the signal? On a heavy 9-PR tick this becomes ~90s. Compare to PM's own scan time (which dominates) — likely negligible, but should be measured on the first post-implementation tick.
- **Risk: Option B is chosen but coverage-guardian still produces zero useful signal** because implementer's tests-first pattern means every PR is ADEQUATE. Mitigation: re-evaluate after 2 weeks; if 100% of rows are ADEQUATE, downgrade to Option A.
- **Risk: Option A is chosen but a future v3 auto-merge plan resurrects the use case.** Mitigation: archive the spec at `.claude/agents-archive/`, don't delete; resurrection is `git mv` back when needed. Document in the archive header.
- **Risk: PM's existing gate is not actually sufficient** (Task 2 concludes PM has no coverage check) but user picks Option A anyway. Mitigation: Task 4's "PM checklist gains an explicit test-per-prod ratio line if needed" sub-step covers this — decommissioning the agent does not have to mean dropping the signal.
- **Risk: meta-plan-on-meta drift.** This plan resolves a leverage-9 retrospective finding; the implementer PR resolves the meta-plan; the next retrospective verifies. If retrospective still flags coverage-guardian after implementer PR ships, that is a process bug worth its own meta-meta-plan. Don't pre-emptively scope it here.

---

## Related journey

This plan does not resolve a `docs/journeys/<id>.md` Known gap — `coverage-guardian` is loop infrastructure, not a user-facing feature. The triggering signal is the **`loop-retrospective` standing leverage-9 finding** logged at `docs/loop-retrospective-log.md` row 2026-04-27T08:55:00Z (and re-confirmed 2026-04-27T16:53:11Z). When this plan + its follow-up implementation PR ship, the next retrospective row should no longer list coverage-guardian as a standing leverage target. No journey doc changes in either PR.
