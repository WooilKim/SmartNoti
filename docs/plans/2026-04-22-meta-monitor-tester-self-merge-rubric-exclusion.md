---
status: in-progress
type: meta
related-agents:
  - .claude/agents/loop-monitor.md
---

# Meta — Monitor AUDIT_DRIFT rubric: exclude journey-tester docs-only self-merges from pr-review-log expectation

> **For Hermes:** This plan modifies `loop-monitor.md` rubric. Carve-out per `agent-loop.md` ("system architecture itself evolves through the same plan/implement/review pipeline it governs"). All changes go through one PR + human review; do NOT self-merge a `.claude/` change.

**Goal:** Stop the recurring AUDIT_DRIFT false-positive where `loop-monitor` flags `journey-tester` docs-only self-merges for missing `pr-review-log.md` rows. Tester self-merges by design have no PM review verdict, so a "missing review row" is not drift — it's the documented carve-out from `journey-tester.md` (docs-only + CI green + no human review requested + journey-only path = self-merge with no PM step).

**Diagnosis (recurring 2026-04-22):**
- 7+ ticks of monitor producing AUDIT_DRIFT anomaly + auto-fix backfill PRs (#268, #270, #274) for the same root cause: tester self-merges (#264 #266 #267 #269 #271) all have `auto-merge-log` rows (PM helper appends it post-merge) but no `pr-review-log` row (no PM review).
- Monitor's `meta-safety` correctly stopped further auto-fix on the 6th tick + escalated structural fix decision.
- One backfill (#274) further exhibited a `BACKFILL_CONTAMINATION` failure mode (cut from stale main → diff includes already-merged code) — PM correctly ESCALATED. That PR is being closed alongside this fix.

**Decision (user-approved 2026-04-22, path A):** Update `loop-monitor.md` AUDIT_DRIFT rubric to:
1. Be explicit that the check covers both `docs/auto-merge-log.md` AND `docs/pr-review-log.md`.
2. Exclude journey-tester docs-only self-merge PRs (branch pattern `docs/journey-verify-*`) from the `pr-review-log.md` expectation. Such PRs MUST still have an `auto-merge-log` row (that's the actual audit invariant); their absence from `pr-review-log` is intentional, not drift.

**Trade-off accepted:** Semantic of `pr-review-log.md` narrows from "every merge" to "every merge that went through PM review verdict." This matches reality (tester carve-out merges never had PM review) and matches user's mental model (review log = review record). Alternative (path B: tester appends own self-row) was rejected because tester writing to a log it doesn't conceptually own (review log) is awkward and adds tester behavior changes for no observability gain.

**Tech Stack:** Markdown only (`.claude/agents/loop-monitor.md`).

---

## Product intent / assumptions

- **The `auto-merge-log.md` invariant is unchanged**: every merged agent-origin PR (regardless of who merged) MUST have a row. This is the true audit ground truth — "did the loop actually merge this?" Path A does NOT relax this.
- **The `pr-review-log.md` invariant is being clarified**, not relaxed: it always meant "review verdict trace," not "every merge log." Tester docs-only self-merges have no review verdict by design; they were never expected to appear here. The monitor was overreading the rubric — this PR makes the rubric match the original product intent.
- The 4 currently-missing pr-review-log rows for #266/#267/#269/#271 stay missing post-fix — they are intentionally absent, not backfill candidates. The monitor will stop flagging them once the rubric exclusion lands.
- The `BACKFILL_CONTAMINATION` issue (#274) is an orthogonal problem — even if monitor's backfill mechanism stays brittle, after path A the AUDIT_DRIFT auto-fix flow rarely fires (only for genuine `auto-merge-log` gaps). So path A is also the right preventative for the backfill-contamination class.
- Future Phase B PM merges that DO go through PM review continue to write both logs (auto-merge + pr-review) via `.claude/lib/audit-log-append.sh` — unchanged.

---

## Task 1: Update `.claude/agents/loop-monitor.md` AUDIT_DRIFT rubric

**Objective:** Codify the check + exclusion explicitly.

**Files:**
- `.claude/agents/loop-monitor.md`

**Steps:**

1. Locate the `### AUDIT_DRIFT` section (line ~56 as of 2026-04-22).
2. Replace the **Check** paragraph with a more precise version that:
   - Names both logs (`docs/auto-merge-log.md` AND `docs/pr-review-log.md`).
   - States the invariant per log:
     - `auto-merge-log.md`: every merged agent-origin PR (any branch pattern) MUST have a row older than 1h or it's drift.
     - `pr-review-log.md`: every merged agent-origin PR **except** those whose branch matches `docs/journey-verify-*` (journey-tester docs-only self-merge carve-out per `journey-tester.md`) MUST have a row older than 1h or it's drift.
   - Notes that the carve-out is intentional: tester self-merges have no PM review verdict to log.
3. Leave the **Auto-fix** and **Escalate** paragraphs unchanged — the threshold (1h) and bounds (`< 5` rows for auto-fix, `>= 5` for escalate) remain.
4. Add a one-line cross-reference at the bottom of the AUDIT_DRIFT section: `# Carve-out source: .claude/agents/journey-tester.md (docs-only self-merge gates).`

**Definition of done:** Reading the AUDIT_DRIFT rubric makes it unambiguous that tester self-merges are exempt from pr-review-log expectation.

## Task 2: Close PR #274

**Objective:** Discard the obsolete backfill that this rubric change makes unnecessary.

**Steps:**

1. After Task 1's PR is open, post a closing comment on #274:
   > "Superseded by `docs/plans/2026-04-22-meta-monitor-tester-self-merge-rubric-exclusion.md` (path A). Once the rubric excludes journey-tester docs-only self-merges from pr-review-log expectation, the 4 backfill rows this PR would add (#266 #267 — and incidentally #269 #271 if that monitor ran today) become intentionally absent, not drift. Closing without merge."
2. `gh pr close 274 --delete-branch`.
3. Reference the close in this plan's Change log.

**Definition of done:** #274 is closed (not merged) and its branch deleted.

## Task 3: Verify on a live tick (post-merge)

**Objective:** Confirm the rubric change behaves as expected.

**Steps:**

1. After the human merges this PR, the next `/journey-loop` tick should:
   - Run monitor as usual.
   - Monitor's AUDIT_DRIFT check now sees that #266 #267 #269 #271 are exempt → no anomaly flagged for missing pr-review-log rows on tester self-merges.
   - Health should return to PASS (assuming no other anomalies).
2. If a new tester self-merge occurs in subsequent ticks, monitor should NOT flag it as AUDIT_DRIFT anymore.
3. If a NON-tester PR (gap-planner / plan-implementer / monitor-backfill / PM-direct-append) ever lands in `auto-merge-log` without a `pr-review-log` counterpart, monitor SHOULD still flag it (the exclusion is narrow to `docs/journey-verify-*` branch only).

**Definition of done:** One green tick on `main` post-merge with no AUDIT_DRIFT flag for the previously-stale tester self-merges.

---

## Scope

**In:**
- One section edit in `.claude/agents/loop-monitor.md` (AUDIT_DRIFT rubric).
- Close + delete-branch for PR #274 (the obsolete backfill).

**Out:**
- Backfill mechanism hardening for the `BACKFILL_CONTAMINATION` failure mode (separate plan; tracked as user-attention item from the previous-tick monitor report). Path A reduces the trigger frequency; if backfill ever runs again from a different anomaly source and contaminates again, that's when to invest.
- Retroactive backfill of #266/267/269/271 pr-review-log rows — by definition, these are intentionally absent.
- Path B (tester appends own self-row) — explicitly rejected.
- Any change to `journey-tester.md` self-merge gates (the carve-out is unchanged; only the monitor's interpretation of the carve-out is being made explicit).
- Any change to PM's `audit-log-append.sh` invocation — PM continues to write both logs for its own review verdicts.

---

## Risks / open questions

- **Risk: rubric becomes more verbose.** The clarification adds a paragraph to AUDIT_DRIFT. Acceptable — clarity beats brevity for a recurring fault source.
- **Risk: future agents add new self-merge carve-outs (e.g., ui-ux-inspector docs-only) that aren't covered by the exclusion.** The rubric as written only excludes `docs/journey-verify-*`. If `ui-ux-inspector` adds a docs-only self-merge path that writes auto-merge-log without pr-review-log, monitor would re-fault. Mitigation: when adding such a carve-out, update the AUDIT_DRIFT rubric in the same PR.
- **Open: should the exclusion be maintained as a list (extensible) or as branch-prefix logic?** Current approach: explicit branch-prefix check (`docs/journey-verify-*`). If multiple carve-outs accumulate, refactor to a maintained list at the top of `loop-monitor.md`. Out of scope for this PR.

---

## Change log

- 2026-04-22: Drafted in response to 7+ tick recurring AUDIT_DRIFT false-positive on tester self-merges + #274 BACKFILL_CONTAMINATION incident. User explicitly chose path A (rubric exclusion) over path B (tester appends self-row).
