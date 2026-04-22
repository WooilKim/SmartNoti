# Loop Retrospective Log

Append-only snapshots of the loop's health over time. Each row captures one retrospective's verdict; the full narrative stays in the chat message that produced the row (and, for `propose` runs, in any meta-plan drafted under `docs/plans/YYYY-MM-DD-meta-*.md`).

**Every `loop-retrospective` run adds one row** regardless of mode. An empty window (no activity to analyze) still gets a row — silence is itself a signal.

## Format

```
| Date (UTC) | Mode | Healthy | Quiet | Noisy | Blocking | Meta findings | Plans drafted | Recommendation |
|---|---|---|---|---|---|---|---|---|
| 2026-04-20T12:34:56Z | analyze | 6 | 2 | 0 | 1 | plans pile up at gap-planner | 0 | investigate planner throughput |
| 2026-04-27T09:10:00Z | propose tester | 7 | 1 | 0 | 0 | none | 1 (refactor tester gate 4) | meta-plan opened |
```

Never edit past rows. If a later retrospective reverses a prior recommendation ("remove X" → "keep X"), add a new row referencing the original; don't rewrite history.

Columns:
- **Mode**: `analyze`, `propose [focus]`, or `history`.
- **Healthy / Quiet / Noisy / Blocking**: counts of agents falling into each classification for the window.
- **Meta findings**: short phrase for REDUNDANT / MISSING / OVERLAPPING GATES kinds (or "none").
- **Plans drafted**: count of meta-plans created (always 0 for `analyze` and `history`).
- **Recommendation**: one-line summary of the chat output's Recommendation section.

## Rows

<!-- append rows below this line, newest at the bottom -->

| Date (UTC) | Mode | Healthy | Quiet | Noisy | Blocking | Meta findings | Plans drafted | Recommendation |
|---|---|---|---|---|---|---|---|---|
| 2026-04-20T13:06:39Z | analyze | 1 | 8 | 0 | 0 | cold-start: 8/9 agents QUIET (인프라 완료 직후 실사용 전), PM 이 self-merge 경로로 우회되어 review gate 아직 검증 안 됨 | 0 | no action — baseline snapshot, 다음 retrospective 는 1주 실사용 후 |
| 2026-04-21T21:40:00Z | analyze | 4 | 3 | 0 | 1 | BLOCKING: per-sweep PM audit PRs serialize on shared log files → ~10 rebases this session; REDUNDANT: audit PRs themselves need audits (chain-audit); OVERLAPPING GATES: PM sandbox warnings vs v3 carve-out drift | 0 | investigate audit-log serialization; candidate meta-plans drafted next tick |
| 2026-04-21T22:15:00Z | propose audit-log-churn | 4 | 3 | 0 | 1 | BLOCKING (carry-over from 2026-04-21T21:40Z): audit-log serialization confirmed — PM per-sweep audit PRs drive the churn; 20+ audit-only PRs in 30-day window | 1 (MP-1 audit-log direct-append) | meta-plan opened: docs/plans/2026-04-21-meta-audit-log-direct-append.md (PM carve-out → human review) |
