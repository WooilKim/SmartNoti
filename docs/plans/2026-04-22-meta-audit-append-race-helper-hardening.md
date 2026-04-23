---
status: planned
kind: meta-plan
focus: audit-log race / backfill contamination
related-files:
  - .claude/lib/audit-log-append.sh
  - .claude/agents/loop-monitor.md
related-plans:
  - 2026-04-21-meta-audit-log-direct-append.md
  - 2026-04-22-meta-monitor-tester-self-merge-rubric-exclusion.md
---

> **For Hermes:** This plan hardens `.claude/lib/audit-log-append.sh` so it survives concurrent monitor backfill PR diffs and re-converges on `origin/main`. It also clarifies the contract that loop-monitor backfills MUST go through the helper (not through ad-hoc PRs that race the helper). Carve-out per `agent-loop.md`: `.claude/` change → human review required, no agent self-merge.

# Meta — Audit-append race: helper rebase-retry hardening (C1)

**Goal:** End the recurring `AUDIT_APPEND_RACE` failure mode where loop-monitor backfill PRs (#278, #280) race PM's direct-append helper and either (a) get closed by the wrapper to break a race cycle or (b) carry stale-base diffs (`BACKFILL_CONTAMINATION`). After this ships, every audit-row write — whether originated by PM after a sweep merge or by loop-monitor as an AUDIT_DRIFT auto-fix — flows through the same `audit-log-append.sh` helper, which retries on non-fast-forward push by re-fetching and re-appending until either success or `MAX_RETRIES` is exhausted. No backfill PR is opened by monitor for the < 5-row case; the helper handles it inline.

**Architecture:** The helper already has a refetch-and-reappend retry loop (lines 150-192 of `.claude/lib/audit-log-append.sh`), but two real-world incidents proved insufficient. Hardening focuses on (1) making the retry loop's refetch/reapply tighter so the contamination window collapses to the size of a single `git push`, (2) adding an explicit pre-push freshness assertion (`origin/main` HEAD has not advanced since branch cut, else re-cut), and (3) routing loop-monitor's backfill auto-fix through the helper instead of opening its own PR. C2 (monitor-via-helper) and C3 (PM holds appends while monitor PR open) were considered; C1 is chosen as the smallest-scope fix that addresses both observed incidents without introducing new coordination state.

**Tech Stack:** bash + `git` + `gh` + `.claude/agents/loop-monitor.md` markdown.

---

## Product intent / assumptions

- Audit rows are append-only and idempotent on `(PR #, verdict)` pair — `--dedupe` already prevents duplicates if the helper is invoked twice with the same row. This plan does NOT change idempotency semantics.
- The retry loop's refetch + re-append (helper lines 175-191) is correct in spirit but misses one race window: between `git fetch` and `git push`, another PM/monitor invocation can land. The hardened helper closes this by treating any push rejection identically (always refetch + re-cut + re-append + retry) regardless of the rejection reason text.
- Loop-monitor's AUDIT_DRIFT auto-fix currently opens a PR with append-only rows. After this plan, that path uses the helper directly (same trust boundary — monitor already writes audit rows, just via a slower wrapper). Monitor's "never self-merge" invariant remains: helper writes to `main` directly via ff-push, which is not a "self-merge of an open PR" and is already PM-blessed by MP-1.
- **Reversibility:** revert this commit and monitor returns to opening backfill PRs; helper returns to its current retry behavior. No data migration. Existing rows stay.
- **Race rarity assumption:** even after hardening, two concurrent helper invocations remain possible (parallel PM + monitor). The helper handles this via `MAX_RETRIES` (default 3, override via env). On exhaustion, the existing fallback path (push ops branch + open audit PR + exit 2) survives unchanged.

## Task 1: Add failing test for the race scenario

**Objective:** Codify the regression — when `AUDIT_LOG_PRE_PUSH_HOOK` simulates an interleaving append on `origin/main` between branch cut and push, the helper must retry, re-cut, and succeed within `MAX_RETRIES`.

**Files:**
- New test script under `.claude/lib/tests/` (path TBD by implementer; mirror existing test layout if any, else create `audit-log-append.race.test.sh`).

**Steps:**

1. Locate any existing tests for `audit-log-append.sh` (search `.claude/lib/tests/` and `.claude/lib/`). If none, create the directory + a minimal harness (bash with `set -eu` + `trap` for cleanup of test repos).
2. Write a test that:
   - Initializes a bare "origin" repo + a clone.
   - Seeds `docs/auto-merge-log.md` with one row.
   - Sets `AUDIT_LOG_PRE_PUSH_HOOK` to a script that, on the first invocation only, makes an unrelated append to `docs/auto-merge-log.md` directly on the bare origin (simulating a concurrent writer).
   - Invokes `audit-log-append.sh --log auto-merge --row '<test row>'`.
   - Asserts: exit code 0, helper logs at least one retry, both the concurrent row AND the helper's row are present on `origin/main` HEAD, no duplicate rows.
3. Run the test; expect it to FAIL on current helper behavior (this is the regression characterization).
4. Commit the failing test with message `test(audit-helper): characterize race-on-rebase regression`.

**Definition of done:** Test exists, runs, and fails on current `main`'s helper.

## Task 2: Harden helper retry semantics

**Objective:** Make Task 1's test pass without weakening the existing dedupe/fallback contract.

**Files:**
- `.claude/lib/audit-log-append.sh`

**Steps:**

1. In the retry loop (current lines ~150-192), classify push rejection broadly: any non-zero exit from `git push origin HEAD:main` triggers refetch + re-cut + re-append, regardless of stderr content. (Currently it does this implicitly, but the rejection-text path could short-circuit; make it explicit.)
2. Before each push attempt (after attempt 1), re-assert freshness:
   - `git fetch --quiet origin main`.
   - If `git rev-parse origin/main` differs from the SHA captured at branch cut, force a re-cut: discard local commit, `git checkout -B "$OPS_BRANCH" origin/main`, re-append, re-commit. (This collapses to the existing retry path; the change is asserting it on every retry, not only on push-rejection.)
3. Tighten the dedupe re-check: run it BEFORE every retry attempt (not only after fetch fails), so if a concurrent helper appended the exact same row in the meantime, we exit 0 cleanly.
4. Keep `AUDIT_LOG_MAX_RETRIES` default at 3; document that test harnesses can raise it for chaos testing.
5. Re-run Task 1's test; expect PASS.
6. Commit with message `fix(audit-helper): tighten rebase-retry to close race window`.

**Definition of done:** Task 1 test passes; existing dedupe + fallback paths unchanged (verify by reading existing tests if any, else by manual smoke).

## Task 3: Route loop-monitor AUDIT_DRIFT backfill through the helper

**Objective:** Eliminate the parallel "monitor opens its own backfill PR" path that produced #278 / #280. After this, monitor invokes `audit-log-append.sh` directly for the < 5-row backfill case.

**Files:**
- `.claude/agents/loop-monitor.md`

**Steps:**

1. Locate the AUDIT_DRIFT "Auto-fix" paragraph.
2. Replace the "open a backfill PR with append-only rows" instruction with: "for each missing row, invoke `.claude/lib/audit-log-append.sh --log <kind> --row '<row>' --dedupe`. The helper handles race + retry + fallback. Do NOT open a backfill PR; the helper opens one only if its retry loop exhausts (exit 2)."
3. Preserve the `< 5 rows` auto-fix bound and the `>= 5 rows` escalate path. The escalate path stays a human notification (no auto-PR).
4. Add a Risks line: "If helper exits 2 (fallback PR opened), monitor reports `AUDIT_DRIFT auto-fix DEFERRED, fallback PR opened` and waits for next tick."
5. Commit with message `ops(loop-monitor): route AUDIT_DRIFT backfill through audit-log-append helper`.

**Definition of done:** Reading loop-monitor's AUDIT_DRIFT section, an implementer would never open a backfill PR by hand — they would always invoke the helper.

## Task 4: Document the new race contract

**Objective:** Make the "all audit-row writes go through one helper" invariant discoverable.

**Files:**
- `.claude/rules/agent-loop.md` — add one sentence to the PM-row description (or footnote) stating that `loop-monitor` AUDIT_DRIFT auto-fix also flows through `audit-log-append.sh` (not an independent backfill PR path).
- This plan's Change log entry on ship.

**Steps:**

1. Append a single bullet to the existing MP-1 footnote: "MP-1.1 (2026-04-22): `loop-monitor` AUDIT_DRIFT backfill < 5 rows now invokes the same helper inline; no backfill PR unless helper exhausts retries."
2. Commit with message `docs(agent-loop): note MP-1.1 monitor-via-helper`.

**Definition of done:** A new agent reading `agent-loop.md` understands there is exactly one audit-row write path.

---

## Scope

**In:**
- One new test for `audit-log-append.sh` race scenario.
- Helper retry loop hardening (no semantic change to dedupe / fallback).
- `loop-monitor.md` AUDIT_DRIFT auto-fix instruction change.
- `agent-loop.md` MP-1.1 footnote.

**Out:**
- C2 (full re-architecture so monitor never writes audit rows directly) — bigger scope, deferred until C1 is observed for 7+ days and proven insufficient.
- C3 (PM holds appends while ops/audit-backfill PR open) — requires new PM coordination state; deferred.
- Any change to `pr-review-log.md` / `auto-merge-log.md` schema.
- Any change to PM's invocation pattern (PM already uses the helper).
- Backfill of rows missing from #275 (separate concern; will be revisited after C1 ships).

---

## Risks / open questions

- **Risk: hardening introduces a new infinite-loop class.** If the pre-push freshness re-cut + re-append always finds origin/main has advanced (pathological case: dozens of concurrent writers), the helper would burn `MAX_RETRIES=3` quickly and fall back to opening a PR — same as today. The fallback path is the safety net; verify Task 1 test does NOT mask this by setting MAX_RETRIES very high.
- **Risk: monitor-via-helper means monitor now invokes `git push origin HEAD:main` (via helper).** Monitor previously only opened PRs. The trust boundary is the same — helper is PM-trusted, monitor invoking it is equivalent to PM invoking it for the same row content. But verify `loop-monitor.md` self-merge rules don't conflict with "helper writes to main"; they shouldn't, since the helper's main-write is documented as MP-1 PM-blessed, not as a "self-merge."
- **Open: should the helper take a `--source <agent-name>` flag for traceability?** Currently rows are anonymous. Adding `--source loop-monitor` to backfill rows would let retrospective distinguish PM appends from monitor backfills. Probably yes — but row format change requires log-schema review. Defer unless implementer finds it trivial to add as a comment-only annotation that doesn't break existing parsers.
- **Open: should `--dedupe` become the default (instead of opt-in)?** Currently every caller passes it; making it default would simplify monitor's invocation. Decision deferred to implementer.
- **Open: do we need a chaos-test mode (`MAX_RETRIES=1` + always-on pre-push hook) in CI?** Probably overkill for now; revisit after one retrospective measures C1 effectiveness.

---

## Verification (post-ship)

- T+7d retrospective measures: count of monitor-opened backfill PRs (target: 0), count of helper exit-2 fallback PRs (acceptable: < 2 / week), count of duplicate audit rows on `(PR #, verdict)` pair (target: 0).
- If monitor-opened backfill PRs ever return (helper exit 2 should be the only source), the AUDIT_APPEND_RACE class is unresolved — escalate to C2.

---

## Related journey

None — this is a meta-plan touching `.claude/` only. Owning surface is the audit-log invariant maintained collectively by `project-manager`, `loop-monitor`, and `.claude/lib/audit-log-append.sh`. Verification trace lives in `docs/loop-retrospective-log.md` and the audit logs themselves.

## Change log

- 2026-04-22: Drafted by `gap-planner` after AUDIT_APPEND_RACE meta-issue surfaced ~10 ticks (incidents PR #278 BACKFILL_CONTAMINATION + PR #280 race-close). User pre-selected C1 (helper retry hardening) over C2 (monitor-via-helper full) and C3 (PM coordination state) for minimal scope.
