# Coverage-Guardian Log

Append-only record of coverage analyses performed by the `coverage-guardian` agent. Exists so the test-to-prod ratio trajectory is visible at a glance — if the ratio trends down over weeks, someone (agent or human) can catch the drift before the suite stops protecting anything.

**Every coverage-guardian run adds one row per analyzed PR**, regardless of classification.

## Format

```
| Date (UTC) | PR | Classification | Prod Δ | Test Δ | Ratio | Notes |
|---|---|---|---|---|---|---|
| 2026-04-20T12:34:56Z | #123 | ADEQUATE | 42 | 15 | 0.36 | — |
| 2026-04-20T13:00:12Z | #124 | MISSING | 88 | 0 | 0.00 | cc'd project-manager |
```

Classifications:
- **OK** — docs/config/test-only change, no prod lines touched.
- **ADEQUATE** — prod changes accompanied by test changes, ratio ≥ 0.2.
- **THIN** — ratio in [0.05, 0.2). Flagged in comment.
- **MISSING** — prod changes with zero test changes. Flagged in comment, project-manager notified.

Rows are ordered chronologically (newest at the bottom). Never edit past rows — if a classification turns out to have been wrong, add a new row referencing the original.

## Rows

<!-- append rows below this line, newest at the bottom -->

| Date (UTC) | PR | Classification | Prod Δ | Test Δ | Ratio | Notes |
|---|---|---|---|---|---|---|
