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
| 2026-04-20T14:17:02Z | journey-tester | #40 | digest-suppression (SKIP→PASS upgrade) | https://github.com/WooilKim/SmartNoti/pull/40/checks |
| 2026-04-20T14:28:43Z | journey-tester | #43 | quiet-hours SKIP | https://github.com/WooilKim/SmartNoti/pull/43/checks |
| 2026-04-20T14:49:39Z | journey-tester | #44 | persistent-notification-protection SKIP | https://github.com/WooilKim/SmartNoti/pull/44/checks |
| 2026-04-20T15:10:33Z | journey-tester | #46 | silent-auto-hide (partial→PASS upgrade) | https://github.com/WooilKim/SmartNoti/pull/46/checks |
| 2026-04-20T15:29:00Z | journey-tester | #48 | notification-capture-classify (partial→PASS upgrade) | https://github.com/WooilKim/SmartNoti/pull/48/checks |
| 2026-04-20T15:38:00Z | journey-tester | #49 | home-overview (partial→PASS upgrade) | https://github.com/WooilKim/SmartNoti/pull/49/checks |
| 2026-04-20T15:59:49Z | journey-tester | #51 | notification-detail (partial→PASS upgrade) | https://github.com/WooilKim/SmartNoti/pull/51/checks |
| 2026-04-20T16:18:46Z | journey-tester | #52 | duplicate-suppression | https://github.com/WooilKim/SmartNoti/pull/52/checks |
| 2026-04-20T16:27:09Z | journey-tester | #53 | digest-inbox | https://github.com/WooilKim/SmartNoti/pull/53/checks |
| 2026-04-20T16:37:15Z | journey-tester | #54 | priority-inbox | https://github.com/WooilKim/SmartNoti/pull/54/checks |
| 2026-04-20T16:46:21Z | journey-tester | #55 | hidden-inbox | https://github.com/WooilKim/SmartNoti/pull/55/checks |
| 2026-04-20T16:54:33Z | journey-tester | #56 | protected-source-notifications | https://github.com/WooilKim/SmartNoti/pull/56/checks |
| 2026-04-20T17:11:38Z | journey-tester | #57 | rules-management | https://github.com/WooilKim/SmartNoti/pull/57/checks |
| 2026-04-20T17:21:30Z | journey-tester | #58 | rules-feedback-loop | https://github.com/WooilKim/SmartNoti/pull/58/checks |
| 2026-04-20T17:32:10Z | journey-tester | #59 | insight-drilldown | https://github.com/WooilKim/SmartNoti/pull/59/checks |
| 2026-04-20T17:40:41Z | journey-tester | #60 | digest-suppression (2) | https://github.com/WooilKim/SmartNoti/pull/60/checks |
| 2026-04-20T18:01:16Z | journey-tester | #63 | notification-capture-classify (re-verify) | https://github.com/WooilKim/SmartNoti/pull/63/checks |
| 2026-04-20T18:12:07Z | journey-tester | #65 | notification-detail (re-verify) | https://github.com/WooilKim/SmartNoti/pull/65/checks |
| 2026-04-20T18:22:22Z | journey-tester | #67 | home-overview (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/67/checks |
| 2026-04-20T18:31:42Z | journey-tester | #69 | duplicate-suppression (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/69/checks |
| 2026-04-20T18:39:44Z | journey-tester | #70 | digest-inbox (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/70/checks |
| 2026-04-20T18:51:21Z | journey-tester | #71 | priority-inbox (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/71/checks |
| 2026-04-20T19:04:04Z | journey-tester | #72 | hidden-inbox (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/72/checks |
| 2026-04-20T19:11:54Z | journey-tester | #73 | protected-source-notifications (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/73/checks |
| 2026-04-20T19:25:05Z | journey-tester | #74 | rules-management (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/74/checks |
| 2026-04-20T19:36:01Z | journey-tester | #75 | rules-feedback-loop (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/75/checks |
| 2026-04-20T19:50:38Z | journey-tester | #76 | insight-drilldown (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/76/checks |
| 2026-04-20T20:00:21Z | journey-tester | #78 | digest-suppression (re-verify #2) | https://github.com/WooilKim/SmartNoti/pull/78/checks |
| 2026-04-20T20:19:50Z | journey-tester | #81 | rules-feedback-loop KEEP_SILENT (re-verify #3) | https://github.com/WooilKim/SmartNoti/pull/81/checks |
| 2026-04-21T01:53:26Z | project-manager | #89 | collapsible Hidden-inbox groups (plan-implementer) | https://github.com/WooilKim/SmartNoti/pull/89/checks |
| 2026-04-21T01:53:43Z | project-manager | #91 | silent-split task 1 (SilentMode enum + nullable column, v5→6) | https://github.com/WooilKim/SmartNoti/pull/91/checks |
| 2026-04-21T01:53:52Z | project-manager | #92 | pr-review-log audit rows for #88–#91 (PM own) | https://github.com/WooilKim/SmartNoti/pull/92/checks |
| 2026-04-21T02:10:00Z | journey-tester | #97 | hidden-inbox re-verify vs #89 collapsible contract | https://github.com/WooilKim/SmartNoti/pull/97/checks |
| 2026-04-21T03:32:30Z | project-manager | #105 | silent-tray-sender-grouping Task 1 — SilentNotificationGroupingPolicy (pure additive) | https://github.com/WooilKim/SmartNoti/pull/105/checks |
| 2026-04-21T03:32:45Z | project-manager | #104 | listener-reconnect-sweep Task 3 — listener wiring + DAO exists-by-content-signature | https://github.com/WooilKim/SmartNoti/pull/104/checks |
| 2026-04-21T03:33:00Z | project-manager | #103 | silent-split Task 3 — Detail "처리 완료로 표시" button + markSilentProcessed | https://github.com/WooilKim/SmartNoti/pull/103/checks |
| 2026-04-21T01:53:26Z | project-manager | #89 | collapsible Hidden-inbox groups (plan-implementer) | https://github.com/WooilKim/SmartNoti/pull/89/checks |
| 2026-04-21T01:53:43Z | project-manager | #91 | silent-split task 1 (SilentMode enum + nullable column, v5→6) | https://github.com/WooilKim/SmartNoti/pull/91/checks |
| 2026-04-21T01:53:52Z | project-manager | #92 | pr-review-log audit rows for #88–#91 (PM own) | https://github.com/WooilKim/SmartNoti/pull/92/checks |
| 2026-04-21T03:53:03Z | project-manager | #107 | silent-split Task 4 — split Hidden into 보관/처리 tabs (backfill) | https://github.com/WooilKim/SmartNoti/actions/runs/24703096616 |
| 2026-04-21T03:52:51Z | project-manager | #108 | ops: add loop-monitor agent spec (docs-only, backfill) | https://github.com/WooilKim/SmartNoti/pull/108/checks |
| 2026-04-21T03:59:05Z | project-manager | #109 | ops(orchestrator): add Agent tool to loop-orchestrator spec (docs-only, backfill) | https://github.com/WooilKim/SmartNoti/actions/runs/24703251629 |
| 2026-04-21T04:14:45Z | project-manager | #110 | silent-archive T5 — SilentArchivedSummaryCount helper + notifier rename (backfill) | https://github.com/WooilKim/SmartNoti/actions/runs/24703677983 |
| 2026-04-21T05:05:00Z | project-manager | #115 | silent-archive T6 — docs-only rewrite of silent-auto-hide + hidden-inbox for ARCHIVED/PROCESSED split | https://github.com/WooilKim/SmartNoti/actions/runs/24704263267 |
| 2026-04-21T05:01:15Z | project-manager | #116 | silent-tray-sender-grouping Task 2 — group summary + child channel (additive + tests green) | https://github.com/WooilKim/SmartNoti/actions/runs/24704725756 |
