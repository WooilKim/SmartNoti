---
status: shipped
shipped: 2026-04-26
kind: meta-plan
focus: loop-monitor log commit/push race & cross-session loss
related-files:
  - .claude/agents/loop-monitor.md
  - .claude/lib/audit-log-append.sh
  - docs/loop-monitor-log.md
related-plans:
  - 2026-04-21-meta-audit-log-direct-append.md
  - 2026-04-22-meta-audit-append-race-helper-hardening.md
  - 2026-04-26-meta-frontmatter-on-merge-enforcement.md
---

> **For Hermes:** This meta-plan adds a `monitor-log-append.sh` helper analogous to `.claude/lib/audit-log-append.sh` so `loop-monitor` can append-and-land its tick row to `docs/loop-monitor-log.md` on `origin/main` in a single inline call (with race retry + clock-discipline `--stamp-now`). It eliminates the recurring `MONITOR_LOG_COMMIT_RECURRENCE` failure mode where monitor edits the log but never commits, polluting the next session's working tree (blocked `journey-tester` 2026-04-26) and forcing per-session ad-hoc ops PRs (#350, #351 in one session). Carve-out: this is a `.claude/` change → `project-manager` will DEFER, human review required.

# Meta — Loop-monitor log commit/push helper (close MONITOR_LOG_COMMIT_RECURRENCE)

**Goal:** After this ships, every `loop-monitor` invocation that writes a row to `docs/loop-monitor-log.md` either lands it on `origin/main` inside the same call (via short-lived ops branch + ff-push, retry on race) or fails gracefully and reports `MONITOR_LOG_APPEND_DEFERRED` so the next tick / PM sweep cleans up. No row stays uncommitted across the wrapper boundary, so the next session inherits a clean working tree and `journey-tester`'s "refuse to run on dirty tree" precondition holds without manual ops PRs.

**Architecture:** Mirrors the MP-1 / MP-1.1 architecture for `audit-log-append.sh`. A new `.claude/lib/monitor-log-append.sh` does one job: append a single row to `docs/loop-monitor-log.md` and ff-push to `main`, retrying on non-ff per the same broad-race classification used by the audit helper. `loop-monitor.md`'s "Log format" section is rewritten to invoke the helper inline instead of bare-editing the file. Helper accepts `--stamp-now` so column 1 is real UTC at append time (clock-discipline rule; see MP-3). Existing audit-helper test harness layout (`.claude/lib/tests/audit-log-append-*.sh`) is the model — implementer adds `.claude/lib/tests/monitor-log-append-*.sh` covering happy-path, stamp-now, and race scenarios. Trust boundary: monitor was already PM-blessed to direct-write `main` for AUDIT_DRIFT backfill via the audit helper (MP-1.1); writing its own log via a sibling helper is the same trust level.

**Tech Stack:** bash + `git` + `gh` + `.claude/agents/loop-monitor.md` markdown.

---

## Product intent / assumptions

- `docs/loop-monitor-log.md` is append-only and idempotent; no caller appends two rows for the same tick. The helper still defaults dedupe ON (matching audit helper MP-1.1 default) so a re-invoked monitor on the same tick with the same row body short-circuits.
- Cross-session row preservation matters: each tick row is the only place `REPEAT_NOOP_STORM` history, anomaly recurrence, and tick cadence are stored. Losing rows because they sat in an uncommitted working tree at session end (the current failure mode) silently degrades every subsequent monitor decision that consults history (`agent-loop.md` "Treat duplicate anomalies across ticks as escalating severity" depends on this row history).
- Monitor's "never modify `.claude/**`" rule is unchanged. Monitor invokes the helper; helper is added in this plan and reviewed by a human before ship.
- **Reversibility:** revert the commit and monitor returns to bare-editing the file (current broken behavior). No data migration. Existing rows stay.
- **Race rarity:** monitor runs once per tick under `/journey-loop`; concurrent monitor instances are not currently possible. The retry loop exists to handle PM appending an audit row in the same window (different file, but same `git push origin HEAD:main` contention).
- **Why not extend `audit-log-append.sh` with a `--log monitor` mode instead?** Considered. Rejected to keep blast radius small: audit helper is hardened (MP-1.1) with 13 tests and is invoked by PM on every sweep. Adding a third file kind risks regressions in PM's hot path. A sibling helper that copies the same architecture is reviewable in isolation and can be deleted independently if monitor changes shape later.
- **Pre-existing local rows** (the working-tree pollution this plan is designed to prevent) are NOT part of helper scope — they predate the helper. The retroactive cleanup path is described in Task 5 below.

---

## Task 1: Add failing tests for monitor-log-append helper

**Objective:** Codify the contract before writing the helper. Tests should fail because the helper does not exist yet.

**Files:**
- New: `.claude/lib/tests/monitor-log-append-test.sh` (happy path).
- New: `.claude/lib/tests/monitor-log-append-stamp-now-test.sh` (column-1 rewrite).
- New: `.claude/lib/tests/monitor-log-append-race-test.sh` (retry-on-non-ff via `MONITOR_LOG_PRE_PUSH_HOOK`).

**Steps:**

1. Read existing audit-helper tests under `.claude/lib/tests/` and mirror their layout (bare origin + clone, env hooks, cleanup trap, exit-code assertions).
2. Happy-path test: seed `docs/loop-monitor-log.md` with header + one row on bare origin; invoke `monitor-log-append.sh --row '| 2026-04-26T05:00:00Z | PASS | 0 | none | seed |'`; assert exit 0, row appears on `origin/main`, no duplicate, working tree clean post-call.
3. `--stamp-now` test: invoke with row prefixed `| PENDING_STAMP | PASS | 0 | none | stamp-test |`; assert column 1 is rewritten to a real UTC timestamp (regex `^\| 20[0-9]{2}-`), row body (columns 2..N) preserved verbatim, dedupe by body works (re-invoking with identical body+different placeholder short-circuits exit 0).
4. Race test: install `MONITOR_LOG_PRE_PUSH_HOOK` that, on first call only, appends an unrelated row directly to bare origin's `docs/loop-monitor-log.md`. Invoke helper. Assert: exit 0, ≥1 retry logged, both the racer's row and helper's row present on `origin/main` HEAD, no duplicates, helper's row is appended AFTER the racer's row (proves rebase semantics, not stale-base overwrite).
5. Run all three tests; expect all FAIL with "command not found" or similar (helper not yet written).
6. Commit with message `test(monitor-helper): characterize commit-and-push contract before implementation`.

**Definition of done:** Three test files exist, run, and fail on current `main`.

## Task 2: Implement `.claude/lib/monitor-log-append.sh`

**Objective:** Make Task 1 pass by porting `audit-log-append.sh`'s architecture (initial cut → append → ff-push with broad race classification → fallback PR on exhaustion).

**Files:**
- New: `.claude/lib/monitor-log-append.sh` (executable; `chmod +x`).

**Steps:**

1. Copy `.claude/lib/audit-log-append.sh` as a starting point. Strip `--log` flag (target file is fixed: `docs/loop-monitor-log.md`).
2. Required flags: `--row '<markdown row>'`. Optional: `--source <agent-name>` (defaults `loop-monitor`), `--stamp-now`, `--no-dedupe`, `--dedupe` (no-op alias).
3. Validation: row must match `^\|[[:space:]]*[^|[:space:]][^|]*\|.*\|[[:space:]]*$` (same regex audit helper uses). With `--stamp-now`, column 1 placeholder is rewritten with `date -u +%Y-%m-%dT%H:%M:%SZ` BEFORE dedupe + push (clock-discipline rule).
4. Dedupe semantics: identical to audit helper. Default ON. With `--stamp-now`, dedupe compares row BODY (columns 2+) since column 1 always differs by second.
5. Push loop: `MAX_RETRIES` from env `MONITOR_LOG_MAX_RETRIES` (default 3). Pre-push hook env `MONITOR_LOG_PRE_PUSH_HOOK` for tests. On any non-zero `git push`, refetch + dedupe re-check + freshness re-assert + re-cut + retry. Same broad classification as audit helper MP-1.1.
6. Fallback path on exhaustion (exit 2): push ops branch `ops/monitor-log-<TS>-<sha>` and open a small PR titled `docs(monitor): loop-monitor-log append (fallback)` via `gh pr create`. Body cites this meta-plan. PR is human-merged (monitor never self-merges, MP-1.1 invariant).
7. Commit subject pattern: `docs(monitor): loop-monitor-log append 1 row (<NOW_TS>[<retry-tag>]) [source: loop-monitor]`. Sources other than `loop-monitor` are accepted (future-proofs for a hypothetical second writer).
8. Run Task 1 tests; expect PASS.
9. Commit with message `feat(monitor-helper): add monitor-log-append.sh with race-retry`.

**Definition of done:** Helper exists, executable, all Task 1 tests pass, test count parity with audit-helper suite (3 files mirror 3 audit-helper test files; race test covers the same MP-1.1 hardening surface).

## Task 3: Update loop-monitor spec to invoke the helper inline

**Objective:** Eliminate the bare-edit code path that produced MONITOR_LOG_COMMIT_RECURRENCE.

**Files:**
- `.claude/agents/loop-monitor.md` — `## Log format` section.

**Steps:**

1. Locate the `## Log format` section (currently ~lines 159-167).
2. Replace the "Append one row per run to `docs/loop-monitor-log.md` (create with header if missing)" instruction with a procedure that invokes the helper inline:

   ```bash
   .claude/lib/monitor-log-append.sh \
     --row '| PENDING_STAMP | <Health> | <Anomalies> | <Auto-fix> | <Notes> |' \
     --stamp-now \
     --source loop-monitor
   ```

   - The helper handles append + commit + ff-push + retry + fallback. Monitor does NOT bare-edit the file or run `git commit`/`git push` itself.
   - On helper exit 0: report row landed; proceed.
   - On helper exit 2 (fallback PR opened): include `MONITOR_LOG_APPEND_DEFERRED, fallback PR opened by helper` in the monitor report; do NOT open a duplicate PR.
   - On helper exit 1 (validation / git failure): include `MONITOR_LOG_APPEND_FAILED, <stderr summary>` in the report and continue; next tick will append two rows in one go (acceptable since rows are independent).
3. Update `## Meta-safety` to add: "Never bare-edit `docs/loop-monitor-log.md`. Always go through `.claude/lib/monitor-log-append.sh`. Bare edits are the root cause of MONITOR_LOG_COMMIT_RECURRENCE (this plan)."
4. Update the `## Why you exist` list with one bullet: "Tick rows that never landed on main (working-tree pollution → next-session blockage → ad-hoc ops PRs)" so future readers understand the failure mode the helper closes.
5. Commit with message `ops(loop-monitor): route tick-row append through monitor-log-append helper`.

**Definition of done:** A new agent reading `loop-monitor.md` would never bare-edit the log file.

## Task 4: Document the new write path in agent-loop.md

**Objective:** Make the "all monitor-log writes go through one helper" invariant discoverable from the loop overview.

**Files:**
- `.claude/rules/agent-loop.md`

**Steps:**

1. Locate the `loop-monitor` row in the eleven-agent table; the "건드리는 범위" column currently reads `docs/loop-monitor-log.md + 선택적 ops PR (...)`. Append a footnote pointer: `(via .claude/lib/monitor-log-append.sh — MP-1.2)`.
2. Add a new MP-1.2 bullet under the existing MP-1 / MP-1.1 footnote (the paragraph starting `**MP-1.1 (2026-04-22, ...)**`):

   > **MP-1.2 (2026-04-26, `docs/plans/2026-04-26-meta-monitor-log-commit-push-helper.md`):** `loop-monitor`'s tick-row append to `docs/loop-monitor-log.md` now flows through a sibling helper `.claude/lib/monitor-log-append.sh` that mirrors MP-1.1's race-retry hardening. Bare-editing the log is forbidden. Closes MONITOR_LOG_COMMIT_RECURRENCE (rows accumulating in a session's working tree, never landing on main, blocking next session's `journey-tester` on dirty-tree precondition + forcing ad-hoc ops PRs like #350/#351).

3. Commit with message `docs(agent-loop): note MP-1.2 monitor-log-via-helper`.

**Definition of done:** Reading `agent-loop.md` end-to-end, an implementer learns there is exactly one monitor-log write path.

## Task 5: Retroactive cleanup guidance for pre-existing local rows

**Objective:** Document — but do NOT auto-execute — the safe path for local rows that exist on a developer's machine when this helper ships. The helper does not heal pre-existing dirty trees.

**Files:**
- This plan (the cleanup path lives here, not in agent specs).

**Steps:**

1. State the rule in this section: when a session inherits a dirty `docs/loop-monitor-log.md` from a prior session (any rows present locally that are NOT on `origin/main`), the wrapper / human should:
   - **Prefer ops PR** (the path #350 + #351 took): cut `ops/land-loop-monitor-log-rows-<DATE>` from current `main`, cherry-pick / re-apply only the local row diff, push branch, open PR with body `Pre-MP-1.2 backfill of accumulated monitor rows; no longer occurs after .claude/lib/monitor-log-append.sh adoption (docs/plans/2026-04-26-meta-monitor-log-commit-push-helper.md).` Human merges.
   - **Auto-land via helper is NOT acceptable** for backfill: the helper is single-row + clock-stamped at append time. Replaying multi-row historical accumulations through `--stamp-now` would rewrite their timestamps to "now," destroying tick-cadence accuracy.
2. Once MP-1.2 ships, recurrence of this dirty-tree state is the failure signal: `loop-monitor` itself should add a one-tick anomaly check (left as a follow-up plan, not in scope here) that warns if `docs/loop-monitor-log.md` shows uncommitted local diffs at the START of a tick.
3. No code in this task — purely a written rule in this plan.

**Definition of done:** This section exists in the plan and is referenced from PR body so the reviewer sees the migration path is "human-driven ops PR, one time."

## Task 6: Verification (post-ship)

**Objective:** Define what "this fix works" looks like 7 days out.

**Files:** none (verification recipe documented here, executed by `loop-retrospective` on its 30-day cadence and spot-checked by humans).

**Steps:**

1. T+7d: count of ad-hoc `ops/land-loop-monitor-log-rows-*` PRs (target: 0). PRs #350 and #351 from session 2026-04-26 are the baseline.
2. T+7d: count of `docs(monitor):` commit subjects on `main` (expected: roughly equal to tick count for the period).
3. T+7d: any session-start `git status` showing modified `docs/loop-monitor-log.md` is the failure signal — escalate to a follow-up plan that adds a monitor-side dirty-tree anomaly check.
4. If the helper fallback path (exit 2 → fallback PR) fires more than 2x per week, the race assumption is wrong; re-evaluate `MAX_RETRIES` or coordination with the audit helper (they target different files but share the same `git push origin HEAD:main` contention).

---

## Scope

**In:**
- One new helper script `.claude/lib/monitor-log-append.sh`.
- Three new test scripts under `.claude/lib/tests/`.
- `.claude/agents/loop-monitor.md` Log format + Meta-safety + Why you exist edits.
- `.claude/rules/agent-loop.md` MP-1.2 footnote + table-cell pointer.
- This plan documents the retroactive cleanup procedure (Task 5).

**Out:**
- Extending `audit-log-append.sh` to handle a third log kind (rejected — see Architecture).
- A monitor-side START-of-tick dirty-tree anomaly check (left as follow-up; current scope is the WRITE path, not the READ-side defensive check).
- Backfilling historical missing rows (the row content is gone with the prior session's working tree; not recoverable).
- Any change to other agents' write surfaces.
- Schema change to `docs/loop-monitor-log.md` (still 5 columns: Date | Health | Anomalies | Auto-fix | Notes).

---

## Risks / open questions

- **Risk: helper failure on first roll-out leaves monitor unable to log.** Mitigation: helper exit 1 path is non-fatal — monitor reports `MONITOR_LOG_APPEND_FAILED` and continues. The next tick simply appends both rows. Not silent.
- **Risk: parallel PM + monitor pushes contend on `git push origin HEAD:main`.** They write different files but the same ref. Both helpers have rebase-retry; both will succeed eventually. Worst case: one extra retry per parallel call. Already proven acceptable in MP-1.1 measurement window (no helper-vs-helper deadlock observed in 4 weeks).
- **Risk: `--stamp-now` on a historical replay.** Already addressed in Task 5 — backfill is human-driven via ops PR, NOT through the helper. Documenting this prevents a well-meaning future agent from `for row in $(cat backup); do helper --row "$row" --stamp-now; done`.
- **Open: should monitor add a `dirty-tree at tick start` anomaly to its rubric?** Strong recommendation YES, but split into a separate plan once MP-1.2 ships and we have a clean baseline. Adding it inside this plan would conflate "fix the write path" with "add a defensive read-side check."
- **Open: pattern overlap with PR #344 (frontmatter-on-merge enforcement) — is there a generic "agent must commit-and-push its own writes" abstraction?** Both failure modes (STALE_FRONTMATTER per #344, MONITOR_LOG_COMMIT_RECURRENCE per this plan) share a root cause: the agent edits a file but does not commit-and-push. Generalizing across implementer, monitor, tester, and inspector would be a larger meta-plan ("agent-write commit invariant"). Keeping them as two narrow fixes for now; if a third instance surfaces in retrospective, generalize then. For this plan: explicitly NOT in scope.
- **Open: should the helper take an optional `--no-push` flag for dry-run / local testing?** Probably yes, but defer to implementer judgment — audit helper does not have this and has not needed it. Add only if Task 1 race test would otherwise need to mock `git push` at a level that's harder than just running the real push against a bare local origin.
- **Open: should the helper validate that the row matches the 5-column schema (Date | Health | Anomalies | Auto-fix | Notes)?** Probably no — audit helper trusts its callers' row format; matching that contract keeps the helper composable. Implementer can add a soft warning (stderr only, exit 0) if drift is observed.

---

## Verification (post-ship)

See Task 6.

---

## Related journey

None — this is a meta-plan touching `.claude/` and `docs/loop-monitor-log.md` only. Owning surface is the loop-monitor invariant maintained by `.claude/agents/loop-monitor.md` and the new `.claude/lib/monitor-log-append.sh`. Verification trace lives in `docs/loop-retrospective-log.md` and `docs/loop-monitor-log.md` itself.

## Change log

- 2026-04-26: Drafted by `gap-planner` after wrapper opened TWO ad-hoc ops PRs (#350 land 26 rows, #351 land take-2 rows) in a single session AND `journey-tester` refused to run because the prior session left `docs/loop-monitor-log.md` modified locally. Pattern indicates the bare-edit rule scales poorly; helper analogous to MP-1.1's `audit-log-append.sh` is the targeted fix. Pre-selected over a more general "agent commit invariant" meta-plan because narrow fixes have shipped successfully twice (MP-1, MP-1.1) and the broader abstraction lacks evidence beyond two related failure classes (this one + STALE_FRONTMATTER per PR #344).
- 2026-04-26: Shipped via PR #372 (merge commit `9ea347e`). Tasks 1-4 landed as code/spec/rules edits — failing tests, `.claude/lib/monitor-log-append.sh` helper with race-retry + `--stamp-now` + dedupe-by-body, `loop-monitor.md` Log format / Meta-safety / Why-you-exist rewrite to invoke the helper inline, and `agent-loop.md` MP-1.2 footnote + table-cell pointer. Tasks 5-6 are written guidance only (retroactive cleanup procedure + 7-day verification recipe), so no further code action is required for ship. Frontmatter flipped via follow-up docs PR per the freshly-merged frontmatter-on-merge enforcement (PR #371).
