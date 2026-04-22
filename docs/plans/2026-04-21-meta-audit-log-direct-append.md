---
status: planned
kind: meta-plan
focus: audit-log serialization / PR churn
superseded-by:
---

# Meta-plan: audit-log direct-append (eliminate per-sweep PR churn)

> **For Hermes:** This plan mutates the loop architecture itself — it changes how `project-manager` (and optionally `journey-tester` / `ui-ux-inspector`) commits audit rows to `docs/pr-review-log.md` and `docs/auto-merge-log.md`. Treat as carve-out: `project-manager` will DEFER this PR; a human must decide. The change is reversible (revert the commit, restore per-sweep audit PRs).

**Goal:** Stop opening one PR per sweep just to append audit rows. PM writes audit rows directly to `main` via short-lived ops branch + immediate fast-forward merge (or `gh api` update-ref), retrying on race. The per-sweep PR churn disappears, conflict accumulation on shared log files goes to zero, and the audit rows themselves remain the traceability surface (they already are — the PR wrapper adds nothing beyond a review comment that nobody casts because the files are docs-only and append-only).

**Architecture:** Modifies `.claude/agents/project-manager.md` "Logging" and "Merging approved PRs" sections. Adds a `direct-append` helper procedure. Touches `.claude/rules/agent-loop.md` to document the new capability in the PM row. No code changes; no CI changes; no branch-protection changes (append-only docs are already outside the required-check matrix, but if branch protection blocks direct push, PM falls back to a single audit PR — same as today, but amortized across N sweeps, not per-sweep).

**Tech Stack:** markdown + agent specs + `gh api` + `git push` with `--force-with-lease=HEAD` pattern on an ops branch, then `gh pr merge --squash --auto` OR `gh api repos/:owner/:repo/git/refs/heads/main` update-ref via REST.

---

## Product intent / assumptions

- The audit rows are append-only. Two PMs running concurrently on the same branch is the only meaningful race, and it's rare (single-account env, one tick at a time via `/journey-loop`).
- The PR wrapper on audit-log PRs has been providing near-zero review value: last 30 days of `docs/pr-review-log.md` + `docs/auto-merge-log.md` show ~20 audit-only PRs, none received a request-changes or substantive comment. CI was a no-op on these files (the `unit-tests-and-build` job skips markdown-only diffs by content, not by path — so CI runs but always passes).
- Retention of traceability: the row itself (timestamp + PR # + agent + CI run URL) IS the trace. The PR that wraps it adds a second-level trace (who opened the audit PR, when, with what message) that has never been consulted in any retrospective to date.
- **Reversibility**: If the next 2 retrospectives after shipping show worse metrics (e.g., audit rows lost, duplicate rows from races, main-branch commit noise overwhelming real feature commits), the human reverts the `.claude/agents/project-manager.md` changes and PM returns to per-sweep PRs. No data migration needed — existing rows stay.
- **Trust level**: PM already merges approved code PRs to `main` via `gh pr merge --squash`. Direct-append to `docs/pr-review-log.md` is strictly less privileged than merging arbitrary code. No new privilege boundary crossed.

## Task 1: Draft the direct-append procedure in PM spec

**Files:**
- `.claude/agents/project-manager.md` — modify the "Logging" section and add a new "Audit-row direct-append" subsection.

Replace the current "After you approve a PR, merge it with `gh pr merge` … open a tiny audit PR titled …" block with a single procedure:

1. After merge, compute the audit-row text locally.
2. Create ops branch `ops/audit-log-<ISO-compact>-<short-sha>` from latest `origin/main` (`git fetch origin main && git checkout -B <branch> origin/main`).
3. Append rows to `docs/pr-review-log.md` and/or `docs/auto-merge-log.md`.
4. `git commit -m "docs(audit): <sweep-tag> append <N> rows"`.
5. Attempt fast-forward merge to `main`:
   - First: `git push origin HEAD:main` (if branch protection allows direct push to docs-only).
   - On rejection (protected branch or non-ff): push to the ops branch, then `gh pr merge --squash --auto --delete-branch` with a minimal body.
6. Retry loop (max N=3): on non-ff rejection, `git fetch origin main && git rebase origin/main && <retry push>`. After N failures, emit PM report line `audit-append DEFERRED, retry next tick` and leave the branch unmerged — next sweep picks it up.

## Task 2: Update agent-loop doc

**Files:**
- `.claude/rules/agent-loop.md` — in the PM row of the "열한 agent" table, change the "건드리는 범위" column to note that PM may direct-append to audit logs on `main` via ops branch (no per-sweep PR).

Add one-paragraph rationale below the table pointing at this meta-plan.

## Task 3: Add `/retrospective` signal for audit-append health

**Files:**
- `.claude/agents/loop-retrospective.md` — in "Signals to read" add a note: "race-induced duplicate audit rows" is a new failure mode; the retrospective should grep for duplicate `(PR #, verdict)` pairs in `docs/pr-review-log.md` and surface them as a REDUNDANT meta-finding.

No metric threshold yet — observe first, add threshold after 2 retrospectives.

## Task 4: (Optional, deferred) Extend to `journey-tester` / `ui-ux-inspector`

Out of scope for this plan. Those agents also emit audit rows (`docs/auto-merge-log.md` after their docs-only self-merge). If this plan ships cleanly and the next retrospective confirms the churn drop, a follow-up meta-plan extends the procedure to tester + inspector.

## Scope

**In:**
- PM spec "Logging" + "Merging approved PRs" sections.
- `agent-loop.md` PM row description.
- `loop-retrospective.md` signals list.

**Out:**
- Any production code.
- Any CI / branch-protection change.
- `journey-tester` / `ui-ux-inspector` self-merge audit flow (separate plan if this one works).
- Historical audit-row reformatting.

## Tradeoffs (explicit)

| Dimension | Before (per-sweep PR) | After (direct-append) |
|---|---|---|
| PR count per sweep | 1 audit PR + N merged PRs | N merged PRs only |
| Traceability of audit row | PR wrapper + row + CI run | Row + CI run (PR wrapper gone) |
| Conflict on shared logs | Frequent rebases (~10/session) | Rare (only on concurrent PM runs) |
| Privilege required | `gh pr merge` (already held) | `git push origin HEAD:main` on docs/ (same trust; strictly less blast radius than code merge) |
| Rollback path | Revert PR | Revert commit (identical git operation) |
| Race handling | Git rejects, rebase, re-push — N retries | Same, but with smaller window (single commit, not a PR lifecycle) |

## Risks / open questions

- **The change affects the loop itself — `project-manager` carve-out will treat this as human-review required.** PM will DEFER its own PR. Human must approve.
- **Branch protection on `main`**: if `main` is protected to require PR + review even for docs-only paths, direct push fails. The fallback is a single audit PR (same outcome as today, no regression). Need to confirm current protection rules — see open question below.
- **Race between two PMs**: single-account + `/journey-loop` serialization makes this rare, but not impossible if a human runs `/pr-review` concurrently. The retry loop handles it; duplicate rows still possible if both PMs append the same verdict in overlapping windows. Mitigation: PM should grep the log for an existing row for the same (PR #, verdict) before appending.
- **Commit noise on `main`**: audit commits will land directly on main instead of being hidden behind squash merges of audit PRs. The commit subject pattern `docs(audit): …` already exists and tooling filters on it; no new noise vector.
- **Open question**: do we want to batch audit rows across multiple merges in one sweep into a single direct-append commit (N rows, 1 commit) rather than one-commit-per-merge? Probably yes — makes the retry window tighter and keeps main history cleaner. Decision deferred to the implementer; the spec should allow either.
- **Open question**: should the direct-append procedure be gated on the same CI-green assertion that the PR merge is gated on? Yes — PM asserts the originating PR's checks were SUCCESS before composing the row, same as today. The direct-append path doesn't introduce a new CI-skip risk.

## Verification (post-ship)

- Next retrospective (T+7d) measures: audit-only PR count (target: 0), main-branch commit count labeled `docs(audit):` (expected: roughly equal to prior audit-PR merge count), duplicate audit row count (target: 0).
- If any of the three targets miss by >2x, open a revert plan.

## Expected impact (from retrospective evidence)

- ~10 rebase events per session → ~0.
- BLOCKING classification on PM (per 2026-04-21 retrospective) → HEALTHY on next measurement.
- OVERLAPPING GATES finding (PM sandbox warnings vs v3 carve-out drift) partially resolved — the audit-PR carve-out disappears entirely.
