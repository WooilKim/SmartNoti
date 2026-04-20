# Auto-merge Log

Append-only record of PRs that were merged by an agent rather than by a human. Exists so a regression can be traced to the exact self-merge that introduced it.

**Every agent self-merge MUST add a row here before calling `gh pr merge`.** If you can't add the row (e.g. the file is locked), do not merge.

## Format

```
| Date (UTC) | Agent | PR | Journey touched | CI run |
|---|---|---|---|---|
| 2026-04-20T12:34:56Z | journey-tester | #123 | silent-auto-hide | https://github.com/WooilKim/SmartNoti/actions/runs/<id> |
```

Keep rows in chronological order (newest at the bottom). Never edit past rows. If a prior auto-merge turned out to be wrong, add a new row below it noting the revert PR — don't rewrite history.

## Scope

- **journey-tester** may self-merge PRs that touch only `docs/journeys/**` when every gate in its agent definition is green.
- **gap-planner** and **plan-implementer** never self-merge — their PRs always go through human review.
- If this file gets a row from any agent other than `journey-tester`, treat it as a bug and audit that agent.

## Revert procedure

If a self-merged row caused a regression:

1. Open a revert PR against the merge commit (`git revert -m 1 <merge-sha>` works for squash merges too).
2. Label the PR `reverts-auto-merge`.
3. Add a row below the offending entry here with the revert PR number in the PR column and a short "reverted — <reason>" note in the Journey column.

## Rows

<!-- append rows below this line, newest at the bottom -->

| Date (UTC) | Agent | PR | Journey touched | CI run |
|---|---|---|---|---|
| 2026-04-20T12:33:33Z | journey-tester | #20 | digest-inbox | https://github.com/WooilKim/SmartNoti/pull/20/checks |
| 2026-04-20T12:52:49Z | journey-tester | #24 | digest-suppression | https://github.com/WooilKim/SmartNoti/pull/24/checks |
| 2026-04-20T12:59:09Z | journey-tester | #25 | batch cycles 3-10 (5 PASS + 3 SKIP) | https://github.com/WooilKim/SmartNoti/pull/25/checks |
| 2026-04-20T13:06:57Z | loop-retrospective | #26 | baseline snapshot | https://github.com/WooilKim/SmartNoti/pull/26/checks |
| 2026-04-20T13:17:21Z | journey-tester | #27 | silent-auto-hide | https://github.com/WooilKim/SmartNoti/pull/27/checks |
| 2026-04-20T13:25:24Z | journey-tester | #28 | priority-inbox | https://github.com/WooilKim/SmartNoti/pull/28/checks |
| 2026-04-20T13:26:04Z | journey-tester | #29 | rules-management | https://github.com/WooilKim/SmartNoti/pull/29/checks |
| 2026-04-20T13:35:00Z | journey-tester | #30 | protected-source-notifications | https://github.com/WooilKim/SmartNoti/pull/30/checks |
| 2026-04-20T13:37:34Z | journey-tester | #31 | audit row for #30 | https://github.com/WooilKim/SmartNoti/pull/31/checks |
| 2026-04-20T13:37:35Z | journey-tester | #32 | notification-detail (재검증) | https://github.com/WooilKim/SmartNoti/pull/32/checks |
| 2026-04-20T13:40:00Z | journey-tester | #33 | rules-feedback-loop | https://github.com/WooilKim/SmartNoti/pull/33/checks |
| 2026-04-20T13:49:17Z | journey-tester | #35 | audit backfill for #31/#32/#33 | https://github.com/WooilKim/SmartNoti/pull/35/checks |
| 2026-04-20T13:49:18Z | journey-tester | #36 | notification-capture-classify (재검증) | https://github.com/WooilKim/SmartNoti/pull/36/checks |
| 2026-04-20T13:57:00Z | journey-tester | #38 | home-overview (재검증) + insight-drilldown | https://github.com/WooilKim/SmartNoti/pull/38/checks |
| 2026-04-20T17:40:25Z | journey-tester | #60 | digest-suppression (frontmatter refresh → 2026-04-21) | https://github.com/WooilKim/SmartNoti/pull/60/checks |
