# v3 Auto-merge Rollout Plan

> **For Hermes:** This plan is executed partly by a human (GitHub admin settings) and partly by code changes already landed in this PR. Track the steps sequentially — each gate makes the next safe.

**Goal:** `journey-tester` can merge its own docs-only PRs once CI is required and self-gated checks are in place, so the verification loop keeps running without piling up trivial review work on the human.

**Architecture:** Three layers of gating, each narrower than the last. Branch protection (GitHub) refuses any merge that bypasses CI. The agent (`.claude/agents/journey-tester.md`) refuses to merge anything outside `docs/journeys/`. The audit log (`docs/auto-merge-log.md`) records every self-merge so regressions are traceable. A human still decides branch-protection policy and can disable the loop at any time.

**Tech Stack:** GitHub branch protection + GitHub Actions (already added in PR #13) + `gh` CLI from the agent's Bash tool + plain markdown audit log.

---

## Product intent / assumptions

- The CI workflow (`unit-tests-and-build`) must have run green on at least 3 independent PRs before this plan proceeds past step 2. Flaky CI + auto-merge is a recipe for silent breakage.
- Docs-only self-merge is safe because journey markdown files cannot compile-break the app. Any temptation to extend this to code PRs goes through a new plan, not an extension of this one.
- Self-merge is reversible: the audit log + revert procedure in `docs/auto-merge-log.md` lets a human roll back a bad auto-merge without special tooling.
- The human ships this plan step by step and confirms each step completed before letting the loop advance.

## Step 1: Land the prerequisite PRs

- [ ] PR #11 (three-agent definitions) merged.
- [ ] PR #12 (`/journey-loop` Ralph tick) merged.
- [ ] PR #13 (CI workflow) merged.
- [ ] This PR merged.

Until all four are on `main`, the rest of the plan cannot be executed.

## Step 2: Observe CI on 3 unrelated PRs

After PR #13 merges, wait for at least three follow-up PRs (any kind) to run the `unit-tests-and-build` job. Record their result in a scratch note:

- Consistent green → proceed.
- Even one flake without a clear cause → pause. Fix the flakiness before Step 3.

Why: once CI is required, a flake blocks every PR. You want to know the failure modes before the whole repo depends on green.

## Step 3: Require the CI check on `main`

Choose either the web UI or `gh` CLI — both do the same thing.

**Web UI:**
1. Go to `Settings → Branches` on `WooilKim/SmartNoti`.
2. Add a branch protection rule for `main`.
3. Check "Require a pull request before merging".
4. Check "Require status checks to pass before merging" and search for `unit-tests-and-build`. Select it.
5. Check "Require branches to be up to date before merging" (optional but recommended).
6. Save.

**`gh` CLI (requires repo admin token):**

```bash
gh api -X PUT /repos/WooilKim/SmartNoti/branches/main/protection \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["unit-tests-and-build"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

Verify:

```bash
gh api /repos/WooilKim/SmartNoti/branches/main/protection \
  --jq '.required_status_checks.contexts'
```

Should print `["unit-tests-and-build"]`.

## Step 4: Smoke-test the self-merge path

This step is the first time the agent is allowed to merge. Do it under close supervision.

1. Invoke `/journey-test silent-auto-hide` (a journey you know is green). The tester opens a PR with the `last-verified` bump.
2. Watch the PR:
   - CI should queue and pass.
   - The agent's final report should say `PR: <url> (auto-merged)` once the four gates (docs-only, CI green, author match, audit row added) all pass.
3. Confirm `docs/auto-merge-log.md` on `main` now has a new row.
4. Confirm the `docs/journey-verify-*` branch was deleted by `gh pr merge --delete-branch`.

If the agent reports "docs-only gate failed" or "CI not green" — read the message, fix the cause, try again. Do not override the gate.

## Step 5: Let `/journey-loop` run for a week

With Steps 1–4 green, start the v1 loop via `/journey-loop`. Let it run for a week and check once a day:

- `docs/auto-merge-log.md` — how many self-merges per day?
- Any CI failures in the loop's PRs?
- Any drift the agent caught that a human missed?

After a week, decide whether to:

- Tighten gates (e.g. additional required checks).
- Extend auto-merge to other agents (requires a new plan, not a loosening of this one).
- Pause the loop if noise outweighs signal.

## Scope

**In:**
- `journey-tester` docs-only self-merge.
- Branch protection on `main` requiring the existing CI job.
- Audit log of every self-merge.

**Out:**
- Self-merge for `gap-planner` or `plan-implementer` — those remain human-reviewed.
- Any merge of a PR that touches files outside `docs/journeys/`.
- Loosening branch protection to accept self-merge without CI.
- Cross-repository automation or external webhooks.

## Risks / open questions

- **Flaky CI**: if the Gradle unit suite starts flaking, the loop either retries forever or pauses the whole loop. Mitigation: Step 2's observation period, plus Step 5's weekly review.
- **Squash merge history**: auto-merged commits will be authored by a bot-style actor. Audit log is the system of record, not git history — make sure anyone debugging a regression knows to check `docs/auto-merge-log.md` first.
- **Revert confusion**: if a human reverts a self-merge without adding a row to the audit log, the log becomes misleading. Revert procedure is documented in `docs/auto-merge-log.md`; add a lint/test later that fails if a revert PR doesn't carry the `reverts-auto-merge` label (out of this plan's scope).
- **Permission creep**: next plan that wants to expand self-merge must cite this plan and argue explicitly why the new scope is safer than the docs-only case. Do not amend this plan to cover code PRs.

## Related journey

No single journey owns this — the loop that verifies journeys is itself being changed. The mechanics of the loop are documented in `.claude/rules/agent-loop.md` (v3 section) and this plan is what moves the project into v3.
