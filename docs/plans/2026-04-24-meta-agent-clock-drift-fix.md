---
status: planned
type: meta
related-agents:
  - .claude/lib/audit-log-append.sh
  - .claude/agents/journey-tester.md
  - .claude/agents/gap-planner.md
  - .claude/agents/plan-implementer.md
  - .claude/agents/project-manager.md
  - .claude/agents/loop-monitor.md
---

# Meta — Eliminate agent clock drift in audit timestamps and journey `last-verified` bumps

> **For Hermes:** This plan modifies one shell helper (`.claude/lib/audit-log-append.sh`) and adds a one-line "read the system clock first" instruction to five agent specs. Carve-out per `agent-loop.md` ("system architecture itself evolves through the same plan/implement/review pipeline it governs"). All changes go through one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** After this PR ships, audit-log row timestamps and journey `last-verified` dates always reflect actual UTC at the moment of action, never the LLM's training-context wall clock. Concretely: (a) every row appended via `audit-log-append.sh` carries a timestamp from `date -u`, regardless of what the calling agent thinks "now" is; (b) every agent that writes a date into a doc invokes `date -u` once at task start and uses that value, eliminating the recurring +/- 1–2 day drift that loop-monitor has flagged 30+ times.

**Architecture:**
- **F1 (helper-side fix):** `audit-log-append.sh` already calls `date -u` for the *commit subject* (`ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"`, line 173) and for the ops-branch suffix (line 132), but the **row content itself** is constructed by the caller (project-manager, loop-monitor) and pasted in via `--row`. So the helper cannot fix the timestamp inside the row without parsing/rewriting caller-supplied markdown. Instead, F1 introduces an opt-in `--stamp-now` flag: when set, the helper prepends/replaces the leading timestamp column (first `|`-delimited field) with the freshly read `date -u` value. Default OFF for backward compat; PM and loop-monitor opt in.
- **F2 (agent-side fix):** Add an identical bullet to the "Before you start" / "Inputs" section of five agent specs:
  > **Read the wall clock first.** At task start, run `date -u +"%Y-%m-%d"` (and `date -u +"%Y-%m-%dT%H:%M:%SZ"` if you will write a timestamp). Use that value for every date you write into docs/PRs/audit rows. Do NOT trust your model context for "today's date" — it lags real UTC by hours to days.
- **F3 (combined):** F1 + F2 land in the same PR. F1 protects audit-log rows (mechanical, reliable). F2 protects journey `last-verified`, plan filenames, retrospective dates, and any other date the agent writes (where helper-level enforcement is impossible without invasive parsing).

**Tech Stack:** Bash (`.claude/lib/audit-log-append.sh`), Markdown agent specs, Bats tests for the helper.

---

## Product intent / assumptions

- **Root cause is the same for both symptoms.** Agents read "today" from model context (training cutoff + heuristic), not from `date -u`. When that context is several hours/days behind real UTC, every date written by the agent inherits that drift. F1 alone fixes audit logs but leaves journey-tester still bumping `last-verified` to a stale date (the symptom that prompted today's `2026-04-24` rules-feedback-loop date-correction commit). F2 alone fixes agent-written dates but leaves the helper's caller-supplied row timestamps unguarded if a future caller forgets the F2 instruction. F3 closes both.
- **Backward compatibility for the helper.** Existing callers (and historical row formats) must keep working. `--stamp-now` is opt-in; without it, behavior is identical to today. PM and loop-monitor opt in by default after this plan; other future callers can opt out if they have a legitimate reason to keep a caller-supplied timestamp.
- **Five agents need the F2 bullet:** journey-tester (writes `last-verified`), gap-planner (writes plan filename date), plan-implementer (flips plan frontmatter `shipped:` date, updates journey Change log entries), project-manager (writes audit row timestamps), loop-monitor (writes audit-backfill row timestamps + monitor-log row timestamps). Other agents (code-health-scout, ui-ux-inspector, coverage-guardian, loop-manager, loop-retrospective, loop-orchestrator) also write dates but at lower frequency and lower drift-impact; they get the bullet too for consistency, applied via a single shared snippet that the implementer pastes into each spec's "Before you start" section. Total: 11 agent files touched (one-line addition each).
- **Open product question:** should the F2 instruction be promoted into a top-level project rule (`.claude/rules/clock-discipline.md`) that every agent's frontmatter pulls in by reference, instead of duplicating one bullet across 11 specs? See Risks / open questions — this is the load-bearing decision the user should weigh in on before implementation.

---

## Task 1: Add failing Bats tests for `--stamp-now` flag

**Objective:** Lock the new helper behavior with tests before touching the implementation.

**Files:**
- New: `.claude/lib/test/audit-log-append-stamp-now.bats` (or extend an existing bats file if one exists in `.claude/lib/test/`).

**Steps:**
1. Test 1 (default behavior unchanged): call `audit-log-append.sh --log pr-review --row '| 2026-01-01T00:00:00Z | foo | bar |'` without `--stamp-now`; assert appended row contains the literal `2026-01-01T00:00:00Z` (caller-supplied stamp preserved).
2. Test 2 (stamp-now overrides leading column): call with `--stamp-now --row '| OLD-TS | foo | bar |'`; assert appended row's first column matches `^\| 2[0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z \|` and `OLD-TS` no longer appears.
3. Test 3 (stamp-now with no leading timestamp column): call with `--stamp-now --row '| foo | bar |'`; document expected behavior — either reject (exit 1 with clear error) or prepend a new timestamp column. Plan recommends reject with error "row missing timestamp column; --stamp-now requires column 1 to be a timestamp placeholder". Implementer to confirm.
4. Test 4 (dedupe + stamp-now interaction): if the row would normally dedupe-skip but `--stamp-now` produces a different timestamp, the dedupe check must run on the *post-stamp* row (otherwise we'd append duplicates whenever the timestamp differs). Assert two consecutive `--stamp-now` calls with the same row body skip the second on dedupe.
5. Run `bats .claude/lib/test/audit-log-append-stamp-now.bats` — confirm all four fail.

## Task 2: Implement `--stamp-now` in the helper

**Objective:** Make Task 1's tests pass.

**Files:**
- `.claude/lib/audit-log-append.sh`

**Steps:**
1. Add `--stamp-now` to the arg parser (boolean, default 0).
2. After arg parsing, if `STAMP_NOW=1`: split `$ROW` on the first `|` separator pair, replace column 1 (between first and second `|`) with ` $(date -u +"%Y-%m-%dT%H:%M:%SZ") `, reassemble. If column 1 doesn't look like a timestamp placeholder per Test 3, exit 1 with the documented error.
3. Move dedupe pre-check (line 124) to AFTER the stamp-now rewrite so the deduped row is the post-stamp row.
4. Update the helper's top-of-file usage block to document `--stamp-now`.
5. Re-run Task 1 tests — all green.

## Task 3: Wire `--stamp-now` into project-manager and loop-monitor invocations

**Objective:** Eliminate audit-row timestamp drift at the two callers that produce 99% of rows.

**Files:**
- `.claude/agents/project-manager.md` — section that documents the `audit-log-append.sh` invocation pattern.
- `.claude/agents/loop-monitor.md` — section that documents the audit-backfill invocation pattern.

**Steps:**
1. In each agent spec, locate the example/template `audit-log-append.sh` command. Add `--stamp-now` to the standard invocation. Update any inline row-construction guidance to use a placeholder like `PENDING_STAMP` for column 1 (the helper will overwrite it).
2. Add a one-sentence note: "Use `--stamp-now` so the row's timestamp reflects actual UTC at append time, not your context wall clock."

## Task 4: Add the F2 "wall clock first" bullet to all eleven agent specs

**Objective:** Eliminate date drift in every place an agent writes a date that the helper cannot enforce (journey `last-verified`, plan filenames, frontmatter `shipped:`, retrospective dates, monitor-log row content beyond column 1, etc.).

**Files (eleven, one-line addition each):**
- `.claude/agents/journey-tester.md`
- `.claude/agents/ui-ux-inspector.md`
- `.claude/agents/code-health-scout.md`
- `.claude/agents/gap-planner.md`
- `.claude/agents/plan-implementer.md`
- `.claude/agents/coverage-guardian.md`
- `.claude/agents/project-manager.md`
- `.claude/agents/loop-manager.md`
- `.claude/agents/loop-retrospective.md`
- `.claude/agents/loop-orchestrator.md`
- `.claude/agents/loop-monitor.md`

**Steps:**
1. Locate each spec's "Before you start" / "Inputs" / equivalent first-action section.
2. Add the standardized bullet at the top of that section:
   > **Read the wall clock first.** Run `date -u +"%Y-%m-%d"` (and `date -u +"%Y-%m-%dT%H:%M:%SZ"` for any timestamp you will write). Use that value everywhere — journey `last-verified`, plan filenames, frontmatter `shipped:`, audit rows, log entries. Do NOT use your model context for "today's date" — it can lag real UTC by hours to days.
3. Implementer confirms each spec's first-action section is the right anchor; in agents where there is no "Before you start" section (e.g. loop-orchestrator's spec uses different headings), pick the equivalent and document the choice in the PR body.

## Task 5: Verify no agent docs were missed

**Objective:** Catch any agent spec that introduces a date-writing pattern after Task 4 but before this plan ships.

**Steps:**
1. `grep -l 'last-verified\|YYYY-MM-DD\|date' .claude/agents/*.md` — confirm every match either contains the new bullet or has documented why not.
2. PR body lists the eleven files touched + grep output as evidence.

## Task 6: Update meta-plan and journey docs

**Objective:** Reflect the new contract in the docs the loop reads on every tick.

**Files:**
- `.claude/rules/agent-loop.md` — if the F2 bullet is promoted to a shared rule (see Risks), reference it from the agent table; otherwise add a one-line note under "각 agent 의 공통 안전 규칙" reminding readers that every agent reads `date -u` first.
- `docs/loop-monitor-log.md` — add a row marking AGENT_CLOCK_DRIFT and AUDIT_HELPER_TIMESTAMP_DRIFT as resolved (with link to this plan).
- This plan's frontmatter — flip `status: planned` → `status: shipped` (per `2026-04-22-meta-implementer-frontmatter-flip.md`).

**Steps:**
1. Make the rule edit only if Task 0 (Risks resolution) chose the shared-rule approach.
2. Append the loop-monitor-log resolution row.
3. Flip plan frontmatter as the last commit of the PR.

## Task 7: Self-review + PR

**Steps:**
1. Bats suite green.
2. Manual sanity: run `audit-log-append.sh --log pr-review --row '| PENDING_STAMP | test | row |' --stamp-now` against a scratch branch; confirm appended row's column 1 is current UTC.
3. PR title: `meta: eliminate agent clock drift in audit timestamps and last-verified bumps`.
4. PR body lists: ranking rationale, scope (1 helper + 11 agent specs + 2 doc updates), and a manual UTC sanity check screenshot.

---

## Scope

**In:**
- `--stamp-now` flag in `audit-log-append.sh` + Bats tests.
- One-line "read wall clock first" bullet in eleven agent specs.
- PM + loop-monitor invocation snippets updated to use `--stamp-now`.
- Loop-monitor log row marking the two anomalies as resolved.
- This plan's frontmatter flip to `shipped` per the implementer-flip carve-out.

**Out:**
- Backfilling historical audit rows that have drifted timestamps. Past rows stay as-is (immutable history).
- Promoting the wall-clock bullet to a shared rule file (`.claude/rules/clock-discipline.md`) — kept as Risks open question; default plan path repeats the bullet across eleven specs.
- Auto-correcting journey `last-verified` dates that are already on `main` with drifted values. Today's correction commit (rules-feedback-loop 04-22 → 04-24) is the precedent; future drifts are addressed by re-verification, not bulk-rewrite.
- Any change to `loop-orchestrator` Phase A/B decision logic — the orchestrator's only "date" surface is the tick summary, which is ephemeral and not persisted.

---

## Risks / open questions

- **Open question for user (load-bearing):** Should the F2 wall-clock bullet be promoted into a single shared rule file (`.claude/rules/clock-discipline.md`) that every agent's "Before you start" section references by link, instead of duplicating the same bullet across eleven specs? Pros of shared rule: one source of truth, future agents inherit automatically, easier to evolve. Cons: agents must follow the link (one extra read), and rule files don't enforce — the agent still has to internalize the instruction. **This plan defaults to the duplicated-bullet approach** (matches the existing pattern in `agent-loop.md` and other meta-plans), but the user should confirm before implementer runs Task 4.
- **Helper backward compat:** existing callers without `--stamp-now` continue to work unchanged. If a future caller produces malformed rows (no leading `|` separator), `--stamp-now` will exit 1 — Task 1 Test 3 documents this. Acceptable trade-off; no current caller is affected.
- **What if `date -u` itself drifts?** macOS/Linux system clock drift is bounded by NTP and well within tolerances we care about (seconds). Out of scope.
- **Agent compliance is best-effort, not enforced.** The F2 bullet is an instruction, not a runtime check. If a future agent ignores it, drift returns. The helper-side F1 fix is the only enforced layer; F2 is the educational layer. The duplicate-bullet-vs-shared-rule decision above directly affects how easy this is to maintain.
- **Loop-monitor log row format:** if the AGENT_CLOCK_DRIFT and AUDIT_HELPER_TIMESTAMP_DRIFT signals are still firing during this PR's review window (because old PM/monitor invocations on in-flight PRs predate `--stamp-now`), the resolution row should be appended only after the PR merges — not preemptively. Implementer to sequence accordingly.

---

## Related journey

This plan does not modify any user-facing journey. It is a meta-plan touching agent infrastructure (`.claude/lib/` + `.claude/agents/`). On ship, no journey doc is updated; only `docs/loop-monitor-log.md` gets the resolution row, and this plan's frontmatter flips to `shipped` (per `2026-04-22-meta-implementer-frontmatter-flip.md`'s carve-out for meta-plans).
