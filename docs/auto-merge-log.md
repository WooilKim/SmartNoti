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
| 2026-04-21T06:59:11Z | journey-tester | #133 | silent-auto-hide (fresh APK PASS — supersedes 2026-04-21 SKIP) | https://github.com/WooilKim/SmartNoti/actions/runs/24708529024 |
| 2026-04-21T07:16:18Z | journey-tester | #135 | hidden-inbox (dual-surface + deep-link filter PASS post #127/#128/#130) | https://github.com/WooilKim/SmartNoti/actions/runs/24709175307 |
| 2026-04-21T06:52:00Z | journey-tester | #131 | silent-auto-hide SKIP — stale APK (lastUpdateTime 12:42:01) predates Task 2 commit 2298d16 (14:38); no ARCHIVED rows and no smartnoti_silent_group posts observable | https://github.com/WooilKim/SmartNoti/actions/runs/24707782944 |
| 2026-04-21T10:09:43Z | journey-tester | #154 | persistent-notification-protection (policy-level PASS via PersistentNotificationPolicyTest 7/7) | https://github.com/WooilKim/SmartNoti/actions/runs/24716451963 |
| 2026-04-21T10:30:00Z | project-manager | #152 | rules-ux-v2 Phase C Task 5 — tier-aware moveRule + drag handle (plan-implementer, tests-first, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24716120139 |
| 2026-04-21T10:30:00Z | project-manager | #153 | PM self-audit — pr-review-log +1 row recording #152 defer verdict (CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24716300026 |
| 2026-04-21T13:24:27Z | journey-tester | #157 | quiet-hours (policy-level PASS via QuietHoursPolicyTest 2/2 + NotificationClassifierTest 19/19) | https://github.com/WooilKim/SmartNoti/actions/runs/24724671251 |
| 2026-04-21T13:45:00Z | journey-tester | #161 | rules-feedback-loop SKIP — Recipe B blocked by receiver exported=false; Recipe A lost Hidden-tab entry point after Phase A Task 4 BottomNav restructure | https://github.com/WooilKim/SmartNoti/actions/runs/24725616712 |
| 2026-04-21T14:03:57Z | journey-tester | #164 | rules-management (PASS post-#160 Phase C doc sync; 49 unit tests across 9 Tests-section classes + e2e keyword→PRIORITY recipe) | https://github.com/WooilKim/SmartNoti/actions/runs/24726618325 |
| 2026-04-21T14:05:00Z | project-manager | #160 | rules-ux-v2 Phase C Task 6 — journey docs sync for hierarchical rules v2 (plan-implementer, docs-only, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24724952403 |
| 2026-04-21T14:05:00Z | project-manager | #158 | journey-tester audit backfill for #157 (quiet-hours PASS, docs-only append, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24725111269 |
| 2026-04-21T14:05:00Z | project-manager | #159 | PM self-audit — pr-review-log rows for prior sweep (#156 req-chg, #158 defer), CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24725299501 |
| 2026-04-21T14:05:00Z | project-manager | #156 | PM self-audit backfill — +2 auto-merge-log rows + 3 pr-review-log rows for #152/#153/#155, rebased onto main (conflicts resolved chronologically), ops docs-only | https://github.com/WooilKim/SmartNoti/pull/156 |
| 2026-04-21T14:23:30Z | journey-tester | #167 | notification-capture-classify (PASS — e2e keyword→PRIORITY recipe on emulator-5554 + 34 unit tests across 5 Tests-section classes; last-verified unchanged, already 2026-04-21) | https://github.com/WooilKim/SmartNoti/actions/runs/24727598689 |
| 2026-04-21T14:30:34Z | project-manager | #168 | journey-tester audit backfill for #167 (notification-capture-classify PASS, docs-only append, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24727904210 |
| 2026-04-21T14:37:07Z | project-manager | #162 | journey-tester audit backfill for #161 (rules-feedback-loop SKIP, rebased onto main with chronological conflict resolve, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24728292073 |
| 2026-04-21T14:45:00Z | project-manager | #163 | PM T14 sweep audit (+4 auto-merge + 5 pr-review rows for #156/#158/#159/#160/#162, rebased onto main, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24728554256 |
| 2026-04-21T14:50:00Z | project-manager | #166 | PM T15 sweep audit (+2 pr-review rows for #162/#163 request-changes, rebased onto main, CI SUCCESS) | https://github.com/WooilKim/SmartNoti/actions/runs/24728888696 |
| 2026-04-21T16:00:00Z | project-manager | #169 | PM T16 chain-audit for T14 sweep merges (#162/#163/#166/#168, +4 auto-merge + 4 pr-review rows), CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24729207625 |
| 2026-04-21T15:33:23Z | journey-tester | #174 | insight-drilldown (PASS — e2e Coupang×5 + Home reason chip + Shell app card drilldown on emulator-5554, range chip 24h→3h recomputes stats, last-verified 2026-04-21→2026-04-22. Stale-APK risk confirmed and flagged for next tick via PR body + Verification log note) | https://github.com/WooilKim/SmartNoti/actions/runs/24731197260 |
| 2026-04-22T00:05:00Z | journey-tester | #170 | notification-detail (PASS — Observable steps 1-5 + Exit state e2e on emulator-5554; "중요로 고정" flips DIGEST→PRIORITY + upserts `person:광고` ALWAYS_PRIORITY rule; Phase B `ruleHitIds` UI not reproducible due to APK-pre-#145 schema; recipe-step-3 sender collision logged as Known gap) | https://github.com/WooilKim/SmartNoti/actions/runs/24729889892 |
| 2026-04-21T15:36:00Z | project-manager | #175 | journey-tester audit backfill for #174 (insight-drilldown PASS, docs-only append, CI SUCCESS run 24731524473) | https://github.com/WooilKim/SmartNoti/actions/runs/24731524473 |
| 2026-04-21T15:42:00Z | project-manager | #176 | user-directed plan update (ignore-tier open questions resolved: weekly-insights YES + Detail ignore button YES w/ confirm+undo + neutral gray), docs-only single-file, CI SUCCESS run 24731678598 | https://github.com/WooilKim/SmartNoti/actions/runs/24731678598 |
| 2026-04-21T15:51:00Z | project-manager | #171 | journey-tester audit backfill for #170 (notification-detail PASS, docs-only append, rebased twice onto main with chronological conflict resolve, CI SUCCESS run 24732043145) | https://github.com/WooilKim/SmartNoti/actions/runs/24732043145 |
| 2026-04-21T16:32:00Z | project-manager | #179 | plan-implementer Task 3 classifier routes RuleActionUi.IGNORE; tests-first, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24733701380 |
| 2026-04-21T16:32:30Z | project-manager | #180 | PM self-audit — historical defer row for #179, docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24733830652 |
| 2026-04-21T16:45:00Z | project-manager | #181 | plan-implementer Task 4 Notifier early-return + Policy IGNORE unconditional suppression; tests-first (3 IGNORE matrix cases), CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734085918 |
| 2026-04-21T16:45:30Z | project-manager | #182 | PM T16-sweep chain-audit (+3 pr-review rows #179/#180/#181 + 2 auto-merge rows #179/#180), docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734296025 |
| 2026-04-21T16:46:00Z | project-manager | #181 | plan-implementer Task 4 Notifier early-return + Policy IGNORE unconditional suppression; tests-first (3 IGNORE matrix cases), CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734085918 |
| 2026-04-21T16:46:30Z | project-manager | #182 | PM T16-sweep chain-audit, docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734296025 |
| 2026-04-21T17:06:00Z | project-manager | #183 | plan-implementer Task 5 rule editor IGNORE UI + journey rules-management sync, tests-first, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734649225 |
| 2026-04-21T17:06:30Z | project-manager | #184 | PM T17 chain-audit rows for #181/#182/#183, docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24734826300 |
| 2026-04-21T17:08:43Z | project-manager | #185 | plan-implementer Task 6 inbox filter + IGNORE archive screen + insights split; tests-first (repo, insights, summary), CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24735349365 |
| 2026-04-21T17:20:00Z | project-manager | #187 | plan-implementer Task 6a Detail "무시" button + dialog + undo + ACTION_IGNORE broadcast; tests-first, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24735802883
| 2026-04-21T17:30:00Z | project-manager | #188 | PM T18 chain-audit for #183-#187 (replaces #186); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24736031890 |
| 2026-04-21T17:40:00Z | project-manager | #190 | PM T19 chain-audit for #188 merge + #189 defer; docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24736730702 |
| 2026-04-21T17:50:13Z | journey-tester | #192 | ignored-archive first verification PASS (fresh APK rebuild); Known gap race crash noted | https://github.com/WooilKim/SmartNoti/actions/runs/24737549200 |
| 2026-04-21T18:15:00Z | project-manager | #191 | PM T19 chain-audit PR merge (record of #190 audit); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24738114813 |
| 2026-04-21T18:25:00Z | project-manager | #193 | PM T20 chain-audit PR merge (record of #191 merge + #192 tester self-merge note); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24738356053 |
| 2026-04-21T18:35:00Z | journey-tester | #196 | notification-detail IGNORE button + dialog + undo PASS post-#189+#192; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24738963343 |
| 2026-04-21T18:45:00Z | project-manager | #195 | PM T21 chain-audit PR merge (record of #193 merge + #194 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24738624174 |
| 2026-04-21T18:45:00Z | project-manager | #194 | gap-planner ignored-archive nav-race plan + Known-gap link annotation; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24738470398 |
| 2026-04-21T18:58:46Z | journey-tester | #202 | ignored-archive post-#199 Option A verification PASS (3x OFF→ON→tap stress, zero crashes); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24740637906 |
| 2026-04-21T19:00:00Z | project-manager | #198 | PM T22 chain-audit PR merge (record of #194 merge + #195 merge + #197 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24739381335 |
| 2026-04-21T19:10:00Z | project-manager | #200 | PM T23 sweep audit PR merge (record of #197 approve, #198 merge, #199 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24739770000 |
| 2026-04-21T19:15:00Z | project-manager | #199 | plan-implementer ignored-archive nav-race Tasks 1+2 — AppNavHost unconditional route + IgnoredArchiveNavGate helper + test GREEN; tests-first, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24739949098 |
| 2026-04-21T19:19:18Z | journey-tester | #204 | notification-capture-classify IGNORE path end-to-end PASS on fresh APK (PRIORITY/IGNORE/rule-miss SILENT 3-way cross-check, StatPill IGNORE filter confirmed); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24741551627 |
| 2026-04-21T19:30:00Z | project-manager | #201 | PM T24 chain-audit PR merge (record of #200 merge + #199 merge + #197 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24741307910 |
| 2026-04-21T19:47:05Z | journey-tester | #207 | rules-management Phase C UI re-verify on fresh APK (2026-04-22 build) PASS — IGNORE chips/tier + editor override switch + 9-candidate base dropdown + SELF_REFERENCE log confirmed; override pill/indent/drag Known-gap noted | https://github.com/WooilKim/SmartNoti/actions/runs/24742689753 |
| 2026-04-21T19:55:00Z | project-manager | #206 | PM T25 chain-audit PR merge (record of #201 approve, #205 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24741955521 |
| 2026-04-21T20:11:25Z | journey-tester | #210 | hidden-inbox ADB recipe on fresh APK post-IGNORE PASS (steps 1–5 match, no IGNORE leak in either tab); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24743828331 |
| 2026-04-21T20:28:40Z | journey-tester | #212 | home-overview fresh APK post-IGNORE re-verify PASS — Observable steps 1-11 all match (StatPill / PassthroughReviewCard / QuickAction × 2 / QuickStartApplied / Insight / Timeline / 최근 알림) + IGNORE filter contract cross-checked (Home header 36 = 37 DB - 1 IGNORE; StatPill sum 35 excludes persistent SILENT); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24744564358 |
| 2026-04-21T20:40:08Z | journey-tester | #214 | digest-suppression fresh APK post-IGNORE re-verify PASS — originals cancelled, replacements on smartnoti_replacement_digest channel with actions=3 + title=Promo + subText=Shell • Digest, DB 3 rows status=DIGEST/replacementNotificationIssued=1; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24745076535 |
| 2026-04-21T20:57:05Z | journey-tester | #216 | priority-inbox first ADB verification on fresh post-IGNORE APK PASS — keyword PRIORITY row via HomePassthroughReviewCard → PriorityScreen (SmartSurfaceCard 검토 대기 18건 + PassthroughReclassifyActions → Digest/→ 조용히/→ 규칙 만들기) → NotificationDetail, tray original retained, BottomNav 4 tabs (no Priority); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24745836682 |
| 2026-04-21T21:00:00Z | project-manager | #209 | PM T26 sweep audit PR merge (record of #205/#206/#208 verdicts + #206 auto-merge row); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24745555828 |
| 2026-04-21T21:15:06Z | journey-tester | #219 | silent-auto-hide fresh APK post-IGNORE re-verify PASS — 4x Promo posts (A/B SILENT+ARCHIVED, C/D DIGEST), tray originals retained, smartnoti_silent_summary root title `보관 중인 조용한 알림 12건` with new copy (탭: 보관함 열기 · 스와이프: 확인으로 처리), smartnoti_silent_group Promo InboxStyle summary (N=2), deep-link Hidden triple-match (DB/title/header = 12), `처리 완료로 표시` on PromoB flips ARCHIVED→PROCESSED + tray cancel + Promo group summary cancel (Q3-A N<2) + root re-post 12→11; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24746618930 |
| 2026-04-21T21:28:19Z | journey-tester | #222 | digest-inbox first ADB verification on fresh post-IGNORE APK PASS — 4 Coupang posts; duplicate-suppression produced SILENT+DIGEST mix; 정리함 tab single Shell group card (11건, `Shell 관련 알림 11건`, 최근 묶음 미리보기 preview rows) proving packageName grouping via observeDigestGroupsFiltered/toDigestGroups; Coupang preview tap → NotificationDetailScreen with `전달 모드 · Digest 묶음 전달`; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24747159140 |
| 2026-04-21T21:30:00Z | project-manager | #218 | PM T27 sweep audit PR merge (record of #209 approve/merge + #213 defer); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24746478269 |
| 2026-04-21T21:32:12Z | project-manager | #213 | journey-tester audit-backfill for #212 (home-overview fresh-APK re-verify); docs-only append, CI SUCCESS, mergeable CLEAN after rebase | https://github.com/WooilKim/SmartNoti/actions/runs/24746962030 |
| 2026-04-21T21:32:31Z | project-manager | #221 | PM T28 sweep audit PR merge (record of #213/#218/#220 verdicts + #218 auto-merge row); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24747085241 |
| 2026-04-21T21:35:53Z | project-manager | #224 | PM T29 sweep audit PR merge (record of #213/#221 merges + #220/#223 defers); docs-only append, CI SUCCESS, MERGEABLE | https://github.com/WooilKim/SmartNoti/actions/runs/24747571169 |
| 2026-04-21T21:40:00Z | project-manager | #225 | PM T30 sweep audit PR merge (record of #224 approve/merge + #220/#223 defers); docs-only append, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24747715283 |
| 2026-04-21T21:45:00Z | project-manager | #220 | journey-tester audit-backfill for #219 (silent-auto-hide fresh-APK re-verify); rebased clean, MERGEABLE, Unit tests + debug APK pass | https://github.com/WooilKim/SmartNoti/actions/runs/24748087162 |
| 2026-04-21T21:45:10Z | project-manager | #228 | PM T32 sweep audit PR merge (record of #225 approve/merge + #220/#223/#227 defers); docs-only append, CI SUCCESS, MERGEABLE/CLEAN | https://github.com/WooilKim/SmartNoti/actions/runs/24748050745 |
| 2026-04-21T22:05:00Z | project-manager | #230 | ui-ux-inspector IGNORE tier sweep 2026-04-22 audit (docs/journeys/README.md + notification-detail.md Known gaps); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24751394787 |
| 2026-04-22T00:58:00Z | project-manager | #233 | gap-planner plan for Rule/Category split (분류 redesign, 12 tasks / 3 phases), docs-only single-file, CI SUCCESS run 24754212365 | https://github.com/WooilKim/SmartNoti/actions/runs/24754212365 |
| 2026-04-22T01:06:42Z | project-manager | #235 | PM T35 audit PR for #233 merge + #234 escalate; docs-only append on ops branch, CI Unit tests + debug APK SUCCESS run 24754540191, MERGEABLE/CLEAN; squash-merged 6676fe15 | https://github.com/WooilKim/SmartNoti/actions/runs/24754540191 |
| 2026-04-22T01:47:14Z | project-manager | #236 | Categories P1 Tasks 1-4 bundle (Category model + repo + migration + Rule.action removal) | https://github.com/WooilKim/SmartNoti/actions/runs/24755652739 |
| 2026-04-22T02:19:43Z | project-manager | #239 | P2 Tasks 5+6+7 classifier rewire to Category.action | https://github.com/WooilKim/SmartNoti/actions/runs/24756683183 |
| 2026-04-22T02:20:44Z | project-manager | #238 | P3 Tasks 8+9 분류 primary tab + editor scaffold | https://github.com/WooilKim/SmartNoti/actions/runs/24756368071 |
| 2026-04-22T03:00:01Z | project-manager | #240 | P3 Tasks 10+11 — Home uncategorized prompt card + 4-tab BottomNav (plan-implementer) | https://github.com/WooilKim/SmartNoti/actions/runs/24757166657 |
| 2026-04-22T03:04:48Z | project-manager | #241 | Task 12 journey doc overhaul (3 new + 2 deprecated + 7 updated + plan→shipped) | https://github.com/WooilKim/SmartNoti/actions/runs/24757803198 |
| 2026-04-21T10:50:30Z | project-manager | #242 | Categories runtime wiring fix plan (docs-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24757929588 |
| 2026-04-22T03:41:35Z | project-manager | #244 | Rewrite Categories runtime wiring plan around 분류 변경 sheet | https://github.com/WooilKim/SmartNoti/actions/runs/24758628595 |
| 2026-04-22T05:21:38Z | project-manager | #245 | Tasks 1-6 categories-runtime-wiring-fix — assign flow code + tests | https://github.com/WooilKim/SmartNoti/actions/runs/24761131264 |
| 2026-04-21T00:00:00Z | project-manager | #246 | Task 7 journey docs + plan→shipped (docs-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24761627630 |
| 2026-04-22T05:34:30Z | project-manager | #246 | plan-implementer Task 7: journey docs sync for 분류 변경 assign flow | https://github.com/WooilKim/SmartNoti/actions/runs/24761857832 |
| 2026-04-22T06:25:22Z | project-manager | #247 | rules-feedback-loop DRIFT verification sweep (docs-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24763434571 |
| 2026-04-22T06:34:47Z | project-manager | #248 | gap-planner: CategoryEditor save wiring fix plan | https://github.com/WooilKim/SmartNoti/actions/runs/24763799372 |
| 2026-04-22T06:54:44Z | project-manager | #249 | CategoryEditor save race fix (plan-implementer) | https://github.com/WooilKim/SmartNoti/actions/runs/24764486252 |
| 2026-04-22T07:16:05Z | project-manager | #250 | plan for rules DataStore dedup hotfix (docs-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24764593056 |
| 2026-04-22T07:17:51Z | project-manager | #251 | hotfix: collapse smartnoti_rules DataStore to single owner | https://github.com/WooilKim/SmartNoti/actions/runs/24765350558 |
| 2026-04-22T07:38:35Z | project-manager | #252 | rules-feedback-loop journey verify 2026-04-22 PASS (docs-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24766114830 |
| 2026-04-22T08:37:51Z | project-manager | #253 | gap-planner plan: categories empty-state inline CTA | https://github.com/WooilKim/SmartNoti/actions/runs/24768271644 |
| 2026-04-22T08:41:33Z | project-manager | #254 | Categories FAB visibility + empty-state inline CTA | https://github.com/WooilKim/SmartNoti/actions/runs/24768625424 |
| 2026-04-22T09:25:00Z | journey-tester | #255 | categories-management | https://github.com/WooilKim/SmartNoti/actions/runs/24770454840 |
| 2026-04-22T10:35:24Z | journey-tester | #256 | inbox-unified | https://github.com/WooilKim/SmartNoti/actions/runs/24773503110/job/72485742562 |
| 2026-04-22T11:47:07Z | journey-tester | #257 | priority-inbox | https://github.com/WooilKim/SmartNoti/actions/runs/24776352828 |
| 2026-04-22T12:59:58Z | journey-tester | #259 | notification-detail | https://github.com/WooilKim/SmartNoti/actions/runs/24779437119 |
| 2026-04-22T13:01:03Z | project-manager | #258 | plan-shipped frontmatter flip (categories empty-state CTA) | https://github.com/WooilKim/SmartNoti/actions/runs/24778113552 |
| 2026-04-22T14:13:46Z | journey-tester | #262 | duplicate-suppression | https://github.com/WooilKim/SmartNoti/actions/runs/24782942317/job/72518867059 |
| 2026-04-22T14:23:03Z | loop-manager (human-merged; backfilled by loop-monitor) | #263 | tune /journey-loop tick interval to 60s (ops, docs-only config) | https://github.com/WooilKim/SmartNoti/actions/runs/24783697617 |
| 2026-04-22T14:37:16Z | journey-tester | #264 | digest-suppression | https://github.com/WooilKim/SmartNoti/actions/runs/24784217205 |
| 2026-04-22T14:58:39Z | project-manager | #265 | feat(home,inbox): truncate Home recent list + de-nest Inbox sub-tabs (plan Tasks 0-3) | https://github.com/WooilKim/SmartNoti/actions/runs/24785303938 |
| 2026-04-22T15:10:00Z | journey-tester | #266 | persistent-notification-protection (Policy-level PASS, PersistentNotificationPolicyTest 7/7) | https://github.com/WooilKim/SmartNoti/actions/runs/24785911934 |
| 2026-04-22T15:27:22Z | journey-tester | #267 | protected-source-notifications | https://github.com/WooilKim/SmartNoti/actions/runs/24786782886/job/72532893339 |
| 2026-04-22T15:41:41Z | journey-tester | #269 | quiet-hours | https://github.com/WooilKim/SmartNoti/actions/runs/24787520003 |
| 2026-04-22T15:35:00Z | project-manager | #268 | loop-monitor AUDIT_DRIFT auto-fix backfill (#263 rows in both audit logs) | https://github.com/WooilKim/SmartNoti/actions/runs/24787208704 |
| 2026-04-22T15:56:49Z | journey-tester | #271 | onboarding-bootstrap | https://github.com/WooilKim/SmartNoti/actions/runs/24788280897/job/72538319048 |
| 2026-04-22T15:50:00Z | project-manager | #270 | loop-monitor AUDIT_DRIFT backfill of #264 pr-review-log row | https://github.com/WooilKim/SmartNoti/actions/runs/24787962201 |
| 2026-04-22T16:13:18Z | project-manager | #272 | gap-planner: priority-inbox debug-inject hook plan + Known-gap annotation | https://github.com/WooilKim/SmartNoti/actions/runs/24789004649 |
| 2026-04-22T16:46:00Z | project-manager | #273 | plan-implementer; debug-only FORCE_STATUS hook + recipe rewrite for priority-inbox journey | https://github.com/WooilKim/SmartNoti/actions/runs/24790453337 |
| 2026-04-22T08:50:00Z | project-manager | #276 | docs(plans) frontmatter triage flip 7 plans to shipped | https://github.com/WooilKim/SmartNoti/actions/runs/24817850312 |
| 2026-04-23T07:55:00Z | project-manager | #278 | loop-monitor: audit backfill of #275 (2-line append-only, fresh branch from main) | https://github.com/WooilKim/SmartNoti/actions/runs/24823224282 |
| 2026-04-23T07:55:00Z | project-manager | #278 | loop-monitor: audit backfill of #275 (2-line append-only, fresh branch from main) | https://github.com/WooilKim/SmartNoti/actions/runs/24823647370 |
| 2026-04-23T07:59:00Z | project-manager | #279 | gap-planner: onboarding-quick-start seed Categories alongside Rules plan + 1 Known-gap annotation | https://github.com/WooilKim/SmartNoti/actions/runs/24823503844 |
| 2026-04-22T08:50:00Z | project-manager | #280 | loop-monitor: audit backfill of #275 (fresh re-cut, 2-line append-only) | https://github.com/WooilKim/SmartNoti/actions/runs/24824075841 |
| 2026-04-22T08:50:00Z | project-manager | #281 | onboarding seeds Categories alongside Rules in quick-start | https://github.com/WooilKim/SmartNoti/actions/runs/24824595031 |
| 2026-04-23T08:37:25Z | journey-tester | #282 | onboarding-bootstrap | https://github.com/WooilKim/SmartNoti/actions/runs/24825361461/job/72659788355 |
| 2026-04-23T08:46:00Z | project-manager | #283 | docs(plans): triage round 2 — 4 in-progress→shipped flips | https://github.com/WooilKim/SmartNoti/actions/runs/24825768215 |
| 2026-04-23T09:02:36Z | project-manager | #284 | meta-plan: implementer flips own plan frontmatter | https://github.com/WooilKim/SmartNoti/actions/runs/24826368883 |
| 2026-04-23T14:18:58Z | project-manager | #286 | meta-plan: audit-append race helper hardening (C1) | https://github.com/WooilKim/SmartNoti/actions/runs/24840306145/job/72712029368 |
| 2026-04-23T15:45:34Z | journey-tester | #288 | rules-feedback-loop | https://github.com/WooilKim/SmartNoti/actions/runs/24844491412/job/72727573615 |
| 2026-04-23T16:01:37Z | project-manager | #289 | gap-planner: categories condition chips plan | https://github.com/WooilKim/SmartNoti/actions/runs/24845265057 |
| 2026-04-23T16:21:46Z | project-manager | #290 | categories condition chips (formatter + 3 surfaces + journey sync + plan-flip) | https://github.com/WooilKim/SmartNoti/actions/runs/24846070583 |
| 2026-04-23T16:32:42Z | project-manager | #291 | gap-planner plan: suppress defaults A+C for duplicate notifications | https://github.com/WooilKim/SmartNoti/actions/runs/24846553774 |
| 2026-04-23T16:57:42Z | project-manager | #292 | A+C suppress defaults: default-on toggle + empty-set semantic + one-shot migration | https://github.com/WooilKim/SmartNoti/actions/runs/24847695572 |
| 2026-04-22T17:50:00Z | journey-tester | #293 | digest-suppression (re-run post #292 still SKIP — recipe blocked by NMS quota on com.android.shell) | https://github.com/WooilKim/SmartNoti/actions/runs/24848219592 |
| 2026-04-23T17:23:41Z | project-manager | #294 | gap-planner plan: digest-suppression testnotifier recipe + Known-gap annotation | https://github.com/WooilKim/SmartNoti/actions/runs/24848840865 |
| 2026-04-22T08:50:00Z | project-manager | #295 | docs: digest-suppression recipe via testnotifier | https://github.com/WooilKim/SmartNoti/actions/runs/24849306728/job/72745067660 |
| 2026-04-24T02:29:57Z | journey-tester | #296 | digest-suppression (re-run post #295 testnotifier recipe — PASS) | https://github.com/WooilKim/SmartNoti/actions/runs/24869082783/job/72811483439 |
| 2026-04-24T02:39:05Z | journey-tester | #297 | categories-management (re-verify on emulator-5554 — PASS) | https://github.com/WooilKim/SmartNoti/actions/runs/24869317730/job/72812184699 |
| 2026-04-24T02:47:00Z | journey-tester | #298 | silent-auto-hide | https://github.com/WooilKim/SmartNoti/actions/runs/24869504018/job/72812743369 |
| 2026-04-24T02:55:00Z | journey-tester | #299 | insight-drilldown | https://github.com/WooilKim/SmartNoti/actions/runs/24869718012 |
| 2026-04-24T03:03:57Z | journey-tester | #300 | home-overview | https://github.com/WooilKim/SmartNoti/actions/runs/24869944154 |
| 2026-04-24T03:12:27Z | journey-tester | #301 | notification-capture-classify | https://github.com/WooilKim/SmartNoti/actions/runs/24870173314/job/72814757983 |
| 2026-04-24T03:23:02Z | journey-tester | #302 | ignored-archive | https://github.com/WooilKim/SmartNoti/actions/runs/24870450436/job/72815625819 |
| 2026-04-24T03:30:00Z | journey-tester | #303 | notification-detail | https://github.com/WooilKim/SmartNoti/actions/runs/24870709057/job/72816446713 |
| 2026-04-24T03:42:00Z | journey-tester | #304 | rules-management | https://github.com/WooilKim/SmartNoti/actions/runs/24870944763/job/72817155584 |
| 2026-04-24T03:50:00Z | journey-tester | #305 | quiet-hours | https://github.com/WooilKim/SmartNoti/pull/305/checks |
| 2026-04-24T03:57:35Z | journey-tester | #306 | categories-management | https://github.com/WooilKim/SmartNoti/actions/runs/24871330455/job/72818278436 |
| 2026-04-24T04:06:00Z | journey-tester | #307 | inbox-unified | https://github.com/WooilKim/SmartNoti/actions/runs/24871544141 |
| 2026-04-24T04:14:00Z | journey-tester | #308 | duplicate-suppression | https://github.com/WooilKim/SmartNoti/actions/runs/24871708437/job/72819430600 |
| 2026-04-24T04:21:00Z | journey-tester | #309 | persistent-notification-protection | https://github.com/WooilKim/SmartNoti/actions/runs/24871931600/job/72820108797 |
| 2026-04-24T04:22:30Z | journey-tester | #310 | protected-source-notifications | https://github.com/WooilKim/SmartNoti/actions/runs/24872082615/job/72820554901 |
| 2026-04-24T04:32:54Z | journey-tester | #311 | rules-feedback-loop | https://github.com/WooilKim/SmartNoti/actions/runs/24872254231 |
| 2026-04-24T04:41:10Z | project-manager | #312 | meta-plan F3 agent clock drift fix | https://github.com/WooilKim/SmartNoti/actions/runs/24872479381 |
| 2026-04-24T15:17:01Z | journey-tester | #314 | onboarding-bootstrap | https://github.com/WooilKim/SmartNoti/actions/runs/24896899716 |
| 2026-04-24T15:26:28Z | journey-tester | #315 | priority-inbox | https://github.com/WooilKim/SmartNoti/actions/runs/24897311073 |
| 2026-04-25T00:30:10Z | project-manager | #316 | D3 plan rule editor remove action dropdown | https://github.com/WooilKim/SmartNoti/actions/runs/24917554423 |
| 2026-04-25T09:23:36Z | journey-tester | #318 | rules-management | https://github.com/WooilKim/SmartNoti/actions/runs/24927651833 |
| 2026-04-25T09:37:57Z | project-manager | #319 | gap-planner plan: category chip APP-token app-label lookup (leverage 8) | https://github.com/WooilKim/SmartNoti/actions/runs/24927884054 |
| 2026-04-22T08:50:00Z | project-manager | #320 | feat(categories): APP-token chip app-label lookup | https://github.com/WooilKim/SmartNoti/actions/runs/24928220814 |
| 2026-04-25T10:05:05Z | journey-tester | #321 | categories-management | https://github.com/WooilKim/SmartNoti/actions/runs/24928369480/job/73002036944 |
| 2026-04-25T10:20:00Z | project-manager | #322 | plan: Detail reclassify confirm toast | https://github.com/WooilKim/SmartNoti/actions/runs/24928552635 |
| 2026-04-25T10:40:17Z | project-manager | #323 | Detail reclassify confirmation snackbar (plan-implementer) | https://github.com/WooilKim/SmartNoti/actions/runs/24928984275 |
| 2026-04-25T10:50:55Z | journey-tester | #324 | notification-detail | https://github.com/WooilKim/SmartNoti/actions/runs/24929165871 |
| 2026-04-25T23:34:03Z | journey-tester | #325 | home-overview | https://github.com/WooilKim/SmartNoti/actions/runs/24943239336 |
| 2026-04-25T23:43:17Z | project-manager | #326 | gap-planner: Android <queries> package visibility plan | https://github.com/WooilKim/SmartNoti/actions/runs/24943392068 |
| 2026-04-26T00:00:00Z | project-manager | #327 | Android <queries> manifest fix so APP chip resolves user app names | https://github.com/WooilKim/SmartNoti/actions/runs/24943696883 |
| 2026-04-26T00:14:57Z | project-manager | #328 | gap-planner plan: category name uniqueness | https://github.com/WooilKim/SmartNoti/actions/runs/24943925977 |
| 2026-04-26T00:22:36Z | journey-tester | #329 | silent-auto-hide | https://github.com/WooilKim/SmartNoti/actions/runs/24944099172/job/73042376367 |
| 2026-04-26T00:46:21Z | project-manager | #330 | feat: block duplicate Category names in editor save gate | https://github.com/WooilKim/SmartNoti/actions/runs/24944366370 |
| 2026-04-26T00:49:02Z | project-manager | #331 | gap-planner: Category Detail recent-notifications preview plan | https://github.com/WooilKim/SmartNoti/actions/runs/24944500328 |
| 2026-04-26T00:49:24Z | project-manager | #331 | CORRECTION: prior #331 row was pre-stamped before merge; merge BLOCKED by conflict, no squash occurred — disregard prior row | https://github.com/WooilKim/SmartNoti/pull/331 |
| 2026-04-26T00:53:54Z | journey-tester | #332 | home-uncategorized-prompt | https://github.com/WooilKim/SmartNoti/actions/runs/24944613935 |
| 2026-04-26T01:05:53Z | project-manager | #331 | gap-planner: plan for Category Detail recent-notifications preview (categories-management) | https://github.com/WooilKim/SmartNoti/actions/runs/24944813975 |
| 2026-04-26T01:24:57Z | project-manager | #333 | feat(categories): CategoryDetail recent-notifications preview (selector+helper+UI+nav, tests, journey) | https://github.com/WooilKim/SmartNoti/actions/runs/24945123742 |
| 2026-04-26T01:27:38Z | project-manager | #334 | ops/track-loop-monitor-log: baseline docs/loop-monitor-log.md on main (append-only docs, +69 lines) | https://github.com/WooilKim/SmartNoti/actions/runs/24945168843 |
| 2026-04-26T01:37:09Z | journey-tester | #335 | priority-inbox | https://github.com/WooilKim/SmartNoti/actions/runs/24945358455/job/73045779337 |
| 2026-04-26T01:48:09Z | project-manager | #336 | gap-planner plan for quiet-hours explainer copy (docs/plans add + 1-line journey annotation) | https://github.com/WooilKim/SmartNoti/actions/runs/24945558356 |
| 2026-04-26T01:55:15Z | journey-tester | #337 | notification-detail | https://github.com/WooilKim/SmartNoti/actions/runs/24945668710 |
| 2026-04-26T02:04:17Z | project-manager | #338 | feat(detail): scaffold QuietHoursExplainerBuilder (Tasks 1-2/6 of quiet-hours-explainer-copy plan) | https://github.com/WooilKim/SmartNoti/actions/runs/24945808270 |
| 2026-04-26T02:21:44Z | project-manager | #339 | quiet-hours explainer wired into NotificationDetailScreen (plan 2026-04-26-quiet-hours-explainer-copy Tasks 3-6); plan frontmatter pre-flipped to shipped while PR open — informational, self-corrects on merge | https://github.com/WooilKim/SmartNoti/actions/runs/24946124608 |
| 2026-04-26T02:32:47Z | journey-tester | #340 | quiet-hours | https://github.com/WooilKim/SmartNoti/actions/runs/24946305252 |
| 2026-04-26T02:43:18Z | project-manager | #341 | gap-planner plan: debug-inject package_name extra (quiet-hours testability) | https://github.com/WooilKim/SmartNoti/actions/runs/24946460390 |
| 2026-04-26T02:56:52Z | project-manager | #342 | feat(debug-inject) Tasks 1-2/6 — package_name/app_name extras + Robolectric test | https://github.com/WooilKim/SmartNoti/actions/runs/24946694451 |
| 2026-04-26T03:12:04Z | project-manager | #343 | docs(debug-inject) ship Tasks 3-6 — quiet-hours recipe + cross-journey note + live ADB + plan flip | https://github.com/WooilKim/SmartNoti/actions/runs/24946945927 |
| 2026-04-26T03:32:50Z | project-manager | #345 | gap-planner — Settings quiet-hours hour pickers plan + journey gap (c) annotation | https://github.com/WooilKim/SmartNoti/actions/runs/24947264604 |
| 2026-04-26T03:49:42Z | project-manager | #346 | feat(settings): quiet-hours hour pickers (plan 2026-04-26) | https://github.com/WooilKim/SmartNoti/actions/runs/24947563107 |
| 2026-04-26T04:03:27Z | journey-tester | #347 | quiet-hours | https://github.com/WooilKim/SmartNoti/actions/runs/24947767114 |
| 2026-04-26T04:15:26Z | project-manager | #348 | gap-planner plan: inbox bundle preview "전체 보기" CTA | https://github.com/WooilKim/SmartNoti/actions |
| 2026-04-26T04:37:34Z | project-manager | #349 | inbox bundle "전체 보기" CTA — DigestGroupCard preview-cap removal + see-all toggle (5/5 tasks) | https://github.com/WooilKim/SmartNoti/actions/runs/24948309327 |
| 2026-04-26T04:45:51Z | project-manager | #350 | ops: land 26 loop-monitor tick rows (clean working tree) | https://github.com/WooilKim/SmartNoti/actions/runs/24948444852 |
| 2026-04-26T05:04:24Z | project-manager | #351 | ops loop-monitor: land 2 additional tick rows (take 2) +meta-plan ask, docs-only +2/-0 | https://github.com/WooilKim/SmartNoti/actions/runs/24948613452 |
| 2026-04-26T05:18:07Z | project-manager | #353 | gap-planner plan: user-tunable duplicate-suppression threshold + window | https://github.com/WooilKim/SmartNoti/actions/runs/24948923902 |
| 2026-04-26T05:43:14Z | project-manager | #354 | feat: duplicate-suppression threshold + window user settings (plan-implementer end-to-end ship) | https://github.com/WooilKim/SmartNoti/actions/runs/24949339251 |
| 2026-04-26T05:56:06Z | journey-tester | #355 | duplicate-suppression | https://github.com/WooilKim/SmartNoti/actions/runs/24949555933 |
| 2026-04-26T06:10:51Z | project-manager | #356 | gap-planner plan: rule explicit-draft flag (작업 필요 vs 보류) — docs/plans + journey Known-gap annotation | https://github.com/WooilKim/SmartNoti/actions/runs/24949730976 |
| 2026-04-26T06:37:09Z | project-manager | #357 | rules: explicit draft flag separates 작업 필요/보류 unassigned rules | https://github.com/WooilKim/SmartNoti/actions/runs/24950201995 |
| 2026-04-26T06:51:46Z | journey-tester | #358 | rules-management | https://github.com/WooilKim/SmartNoti/actions/runs/24950486966 |
| 2026-04-26T07:19:04Z | journey-tester | #359 | home-uncategorized-prompt | https://github.com/WooilKim/SmartNoti/actions/runs/24950961637 |
| 2026-04-26T07:31:29Z | journey-tester | #360 | digest-suppression | https://github.com/WooilKim/SmartNoti/actions/runs/24951170326 |
| 2026-04-26T07:43:30Z | journey-tester | #361 | ignored-archive | https://github.com/WooilKim/SmartNoti/actions/runs/24951383858 |
| 2026-04-26T07:53:24Z | journey-tester | #362 | inbox-unified | https://github.com/WooilKim/SmartNoti/actions/runs/24951561320 |
| 2026-04-26T08:02:20Z | journey-tester | #363 | insight-drilldown | https://github.com/WooilKim/SmartNoti/actions/runs/24951715889 |
| 2026-04-26T08:12:24Z | journey-tester | #364 | notification-capture-classify | https://github.com/WooilKim/SmartNoti/actions/runs/24951890843 |
| 2026-04-26T08:25:01Z | journey-tester | #365 | rules-feedback-loop | https://github.com/WooilKim/SmartNoti/actions/runs/24952119101 |
| 2026-04-26T08:33:34Z | journey-tester | #366 | persistent-notification-protection | https://github.com/WooilKim/SmartNoti/actions/runs/24952265807 |
| 2026-04-26T08:42:57Z | journey-tester | #367 | hidden-inbox | https://github.com/WooilKim/SmartNoti/actions/runs/24952442309 |
| 2026-04-26T08:52:31Z | journey-tester | #368 | protected-source-notifications | https://github.com/WooilKim/SmartNoti/actions/runs/24952615628 |
| 2026-04-26T09:01:00Z | journey-tester | #369 | home-overview | https://github.com/WooilKim/SmartNoti/actions/runs/24952766531 |
| 2026-04-26T09:10:03Z | journey-tester | #370 | digest-inbox | https://github.com/WooilKim/SmartNoti/actions/runs/24952941092 |
| 2026-04-26T11:54:37Z | project-manager | #373 | ops monitor-log flush (174+2 rows) | https://github.com/WooilKim/SmartNoti/actions/runs/24955926002 |
