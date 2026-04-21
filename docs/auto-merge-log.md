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
| 2026-04-21T20:11:25Z | journey-tester | #210 | hidden-inbox ADB recipe on fresh APK post-IGNORE PASS (steps 1–5 match, no IGNORE leak in either tab); docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24743828331 |
| 2026-04-21T20:40:08Z | journey-tester | #214 | digest-suppression fresh APK post-IGNORE re-verify PASS — originals cancelled, replacements on smartnoti_replacement_digest channel with actions=3 + title=Promo + subText=Shell • Digest, DB 3 rows status=DIGEST/replacementNotificationIssued=1; docs-only, CI SUCCESS | https://github.com/WooilKim/SmartNoti/actions/runs/24745076535 |
