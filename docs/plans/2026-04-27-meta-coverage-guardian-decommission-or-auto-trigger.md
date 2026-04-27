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

## Task 3: User decision point — A / B / C [RESOLVED 2026-04-27 — user chose A; implementation IN PROGRESS via PR #501]

**Objective:** The user picks one of the three options based on Tasks 1–2 evidence. This task has no implementation steps — it is the explicit gate.

**Decision question (verbatim, to be answered by the user on this plan's PR):**

> Given Task 1 evidence (coverage-guardian has zero recorded invocations and its log file has zero rows since creation) and Task 2 evidence (whether PM's existing gate is sufficient or not), choose:
>
> - **A. Decommission.** Archive the agent spec, remove from agent-loop.md table, leave `docs/coverage-log.md` as historical artifact, update retrospective rubric.
> - **B. Auto-trigger.** Wire `/journey-loop` Step 4 to spawn coverage-guardian in parallel with PM on every implementer-origin PR with `app/src/main/**` changes. PM consumes its comment as input.
> - **C. Other.** Specify the third path in plain language; the implementer plan will follow that wording.

**Definition of done:** User comments on the PR with one letter (A / B / C) and, if C, a one-line description of the third path.

---

## Task 4: If A chosen — implementation PR (separate, not this PR) [IN PROGRESS via PR #501]

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

---

## Audit findings

Evidence gathered 2026-04-27T17:48Z by plan-implementer (Tasks 1 + 2). These findings are intended as neutral input to the user's A/B/C decision in Task 3 — recommendation noted at the end but the user still picks.

### Task 1 — Invocation history (ground truth)

- **`docs/coverage-log.md` data rows: 0.** File created 2026-04-20 (commit `6ce50fe` — initial agent landing) with header + format example only. Zero analysis rows in the 7-day window since file creation through 2026-04-27T17:48Z. The "example" row inside the Format code-fence (`#123 ADEQUATE`, `#124 MISSING`) is illustrative, not a real analysis.
- **`git log --all --grep=coverage-guardian` matches: 4 commits**, all infrastructural — none are coverage-guardian invocation outputs:
  - `6ce50fe` / `f1bb02c` — agent + log file landing (PR #21)
  - `634a864` / `11439f6` — this very meta-plan landing (PR #499)
- **`docs/loop-retrospective-log.md` mentions: 1 substantive line (row 2026-04-27T08:55:00Z)** flags `MISSING: coverage-guardian zero rows in 30-day window despite ~50 prod-touching PRs — agent never invoked; PM substitutes ad-hoc judgment.` The 2026-04-27T16:53Z retro logged 9 prod merges in one tick with **zero coverage-guardian invocations**.
- **`docs/pr-review-log.md` references to coverage-guardian signal: 0.** Across the entire log, no PM verdict ever cites a `coverage-guardian signal:` comment, classification (OK/ADEQUATE/THIN/MISSING), or coverage-log row.
- **`docs/auto-merge-log.md` references: 1 row, but it's THIS PLAN's own merge audit (#499)** — not an actual coverage-guardian invocation.
- **`gh pr list ... in:body in:comments coverage-guardian` across last 100 PRs: 0 hits.** No PR comment thread on any PR (agent or human) contains the literal `coverage-guardian signal:` marker the agent's spec mandates for THIN/MISSING flags.

**Task 1 verdict: coverage-guardian has NEVER fired in production since being created 2026-04-20. No invocations, no comments, no log rows, no PM consumption. The agent exists only as a spec.**

### Task 2 — Redundancy survey (does PM already cover this?)

- **`project-manager.md` literal references to coverage / test-per-prod / test ratio / MISSING tests / THIN tests: 0.** PM's review checklist has no explicit ratio-based gate. The closest item is Quality gate bullet: *"For code PRs (plan-implementer): the diff must include test files touched for every non-trivial logic change. If a plan task said 'tests-first' and the diff shows code with no matching test change, request changes."* — this is a binary "any-tests-present" check, not a ratio classification.
- **PM diff-size escalation (informational only):** Quality gate also says *"PRs touching > 25 files OR deleting > 50 lines of production code without proportional test changes: surface in comment for human attention but still merge if checklist passes."* — this is a heuristic comment, not a hard gate, and triggers on volume not ratio.
- **Implementer's tests-first guarantee:** `plan-implementer.md` Step 2 mandates *"If the task says 'write failing tests', write them first and run `./gradlew :app:testDebugUnitTest --tests "<class>"` to confirm they fail for the intended reason."* Safety rules add *"Never commit code with failing tests locally."* This is a per-plan-task discipline, contingent on the plan saying "tests-first".
- **PM verdicts on the last 10 implementer-origin PRs (2026-04-27T11:50Z → T15:58Z, PRs #474/#476/#477/#479/#483/#485/#489/#491/#492):** Of the 10 verdicts, **6 explicitly mention `tests-first honored`** (#477, #485, #489, #491, #492, plus #491 says `tests-first`); **2 cite `RED-by-design` failing-test gate** (#483 ESCALATE, #485 DEFER) which is itself the implementer's tests-first pattern visible in CI; **2 are pure refactor / pure docs** (#474 polish, #476 refactor + characterization tests, #479 meta-plan) where ratio gates would not apply. **0 verdicts cite a coverage-guardian signal.** PM's substitute is the literal phrase "tests-first honored" written from inspecting the diff itself.

**Task 2 verdict: Signal is ~85% redundant.** PM + implementer together enforce the equivalent of coverage-guardian's MISSING gate via (a) implementer's tests-first commit discipline + (b) PM's "must include test files touched for non-trivial logic change" Quality bullet + (c) PM verbatim-checking "tests-first honored" in every implementer-origin verdict. The marginal signal coverage-guardian would add: a numeric ratio (e.g., `0.36`) and a THIN classification for ratios in `[0.05, 0.20)`. Whether that numeric precision is worth a separate agent + auto-trigger overhead is the user's judgment call — but the existence-of-tests gate is already covered.

### Optional recommendation (informational, user still picks A/B/C)

Based on Task 1 (zero invocations, zero log rows, zero PM consumption) + Task 2 (signal ~85% redundant with PM + implementer combined), **Option A (decommission, archive at `.claude/agents-archive/`)** has the strongest evidence base. Option B's auto-trigger would close the invocation gap mechanically, but Risk-1 in this plan (`100% ADEQUATE` rows under tests-first implementer) suggests B's signal would be near-noise. Option C remains open if the user wants to fold a numeric-ratio bullet into PM's Quality gate without keeping a standalone agent — the Risk: PM's existing gate is not actually sufficient mitigation in Task 4 already plans for this.

The user retains the call. This recommendation is non-binding and the implementer PR (Task 4 / 5 / 6) follows whichever letter the user picks.
